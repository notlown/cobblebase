package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.EntityType
import net.minecraft.entity.decoration.DisplayEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.HatchLogManager
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import notlown.cobblebase.core.SkillRegistry
import notlown.cobblebase.core.effects.SkillEffects
import java.util.UUID

/**
 * Egg Hatcher executor (soft-dep on Cobbreeding).
 *
 * Behaviour: each hatcher Pokemon claims exactly ONE Cobbreeding egg at a time. It then
 * "incubates" that egg by repeatedly calling its `inventoryTick`, simulating the effect
 * of a player carrying the egg around. When the egg's hatch timer reaches zero, Cobbreeding's
 * own logic spawns the resulting Pokemon. The hatcher then releases the claim and looks for
 * the next egg.
 *
 * Sources of eggs (in priority order):
 *   1. The pasture's bottom-egg slot (Cobbreeding's PastureInventory on the pasture-block-entity).
 *   2. Vanilla / modded chests within [searchRadius] around the pasture origin.
 *
 * Sequential-only: another hatcher in the same pasture will pick a *different* egg via the
 * shared claim ledger. No two hatchers tick the same stack.
 *
 * Soft-dep: Cobbreeding is optional. If the `ludichat.cobbreeding.PokemonEgg` class isn't on
 * the classpath the executor short-circuits — `egg_hatcher` is selectable in the GUI but
 * does nothing without Cobbreeding installed.
 */
object EggHatcherExecutor : SkillExecutor {

    // ---- Cobbreeding reflection layer ----

    private val cobbreedingLoaded: Boolean by lazy {
        try {
            Class.forName("ludichat.cobbreeding.PokemonEgg")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    private val pokemonEggClass: Class<*>? by lazy {
        runCatching { Class.forName("ludichat.cobbreeding.PokemonEgg") }.getOrNull()
    }

    private val eggUtilitiesClass: Class<*>? by lazy {
        runCatching { Class.forName("ludichat.cobbreeding.EggUtilities") }.getOrNull()
    }

    private val extractPropertiesMethod by lazy {
        eggUtilitiesClass?.declaredMethods
            ?.firstOrNull { it.name == "extractProperties" && it.parameterCount == 1 }
    }

    /**
     * Cobbreeding stores the remaining hatch ticks in a `class_9331<Integer>` data component
     * on each egg ItemStack. We pull the ComponentType via reflection (PokemonEgg.Companion.getTIMER)
     * and then call `stack.get(componentType)` to read the live value.
     */
    private val timerComponentType: Any? by lazy {
        try {
            val cls = pokemonEggClass ?: return@lazy null
            val companionField = cls.getField("Companion")
            val companion = companionField.get(null)
            val getTimerMethod = companion.javaClass.getMethod("getTIMER")
            getTimerMethod.invoke(companion)
        } catch (_: Exception) { null }
    }

    /** DEFAULT_TIMER public static int field from PokemonEgg — fallback when no per-egg initial seen. */
    private val defaultTimer: Int by lazy {
        try {
            pokemonEggClass?.getField("DEFAULT_TIMER")?.getInt(null) ?: 12000
        } catch (_: Exception) { 12000 }
    }

    /** Returns true iff the ItemStack's item is a Cobbreeding egg. */
    private fun isPokemonEgg(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val cls = pokemonEggClass ?: return false
        return cls.isInstance(stack.item)
    }

    /** Reads the egg's current TIMER component, or [defaultTimer] if the component is absent. */
    private fun readEggTimer(stack: ItemStack): Int {
        val componentType = timerComponentType ?: return defaultTimer
        return try {
            val getMethod = stack.javaClass.methods.firstOrNull {
                it.name == "method_57824" || it.name == "get"
            } ?: return defaultTimer
            (getMethod.invoke(stack, componentType) as? Int) ?: defaultTimer
        } catch (_: Exception) { defaultTimer }
    }

    /** Tries to read the human-readable species name from an egg stack. Returns "Unknown egg" on failure. */
    private fun describeEgg(stack: ItemStack): String {
        val method = extractPropertiesMethod ?: return "Unknown egg"
        return try {
            val props = method.invoke(null, stack) ?: return "Unknown egg"
            val speciesField = props.javaClass.methods.firstOrNull { it.name == "getSpecies" && it.parameterCount == 0 }
            val species = speciesField?.invoke(props)?.toString()
            species ?: "Unknown egg"
        } catch (_: Exception) { "Unknown egg" }
    }

    // ---- Claim ledger ----

    /**
     * Identifies an in-world location of an egg stack. We can't store the ItemStack itself
     * across ticks (it might be moved/merged), so we re-resolve on each tick from the
     * (containerPos, slot) coordinates.
     */
    private data class EggLocation(val containerPos: BlockPos, val slot: Int)
    private data class Claim(
        val location: EggLocation,
        val pasturePos: BlockPos,
        val lastSeenTick: Long,
        val initialDescription: String,
        /** Timer value read at claim creation — used as the denominator for progress %. */
        val initialTimer: Int,
        /** Last read timer value; refreshed on each tickClaim. */
        val currentTimer: Int,
        /** Snapshot of the hatcher Pokemon at claim time so the GUI doesn't need a live entity. */
        val hatcherSpecies: String,
        val hatcherDisplayName: String,
        val proficiency: Int,
        /**
         * Network id of the visual ItemDisplay entity attached to this hatcher. -1 means
         * no display was spawned (e.g. EntityType.ITEM_DISPLAY.create returned null).
         */
        val displayEntityId: Int = -1
    )

    private val claims = mutableMapOf<UUID, Claim>()
    private const val CLAIM_TTL_TICKS = 200L  // 10 seconds without refresh = drop the claim
    private const val STALE_TTL_TICKS = 1200L  // periodic cleanup

    // ---- Executor entry point ----

    override fun tick(
        world: World,
        origin: BlockPos,
        pokemonEntity: PokemonEntity,
        skill: SkillDef,
        skillEntry: SkillEntry
    ) {
        if (world !is ServerWorld) return
        if (!cobbreedingLoaded) return  // Cobbreeding not present → no-op
        val pokemonId = pokemonEntity.pokemon.uuid
        val now = world.time

        // Cooldown gate — skill ticks at 1/sec, executor cooldown additionally throttles.
        // Standard cooldown gate.
        // (We use lastFuelTime-style — but separate map. Stored in claims.lastSeenTick.)
        val claim = claims[pokemonId]
        val activeClaim = if (claim != null && now - claim.lastSeenTick <= CLAIM_TTL_TICKS) claim else null

        if (activeClaim != null) {
            // Continue incubating the claimed egg.
            tickClaim(world, origin, pokemonEntity, skillEntry, activeClaim, now, skill)
            return
        }

        // No active claim → look for a new egg.
        val otherClaims = claims.filterKeys { it != pokemonId }.values.map { it.location }.toSet()
        val newLocation = findEgg(world, origin, skill.searchRadius, otherClaims)
        if (newLocation == null) {
            claims.remove(pokemonId)
            return
        }
        val stack = stackAt(world, newLocation) ?: return
        val initial = readEggTimer(stack)
        val species = pokemonEntity.pokemon.species.name
        val display = try { pokemonEntity.pokemon.getDisplayName().string.ifBlank { species.replaceFirstChar { c -> c.uppercase() } } }
            catch (_: Exception) { species.replaceFirstChar { c -> c.uppercase() } }
        val eggDisplay = spawnEggDisplay(world, pokemonEntity, stack)
        claims[pokemonId] = Claim(
            location = newLocation,
            pasturePos = origin,
            lastSeenTick = now,
            initialDescription = describeEgg(stack),
            initialTimer = initial,
            currentTimer = initial,
            hatcherSpecies = species,
            hatcherDisplayName = display,
            proficiency = skillEntry.proficiency,
            displayEntityId = eggDisplay?.id ?: -1
        )
        Cobblebase.LOGGER.info("[EggHatcher] Claim created: $species (prof ${skillEntry.proficiency}) → ${describeEgg(stack)} (timer=$initial) at $newLocation, pasture=$origin")
        if (now % 40L == 0L) SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
    }

    /**
     * Spawns a vanilla ItemDisplay entity carrying [stack] right above the hatcher Pokemon.
     * The display floats and billboards toward the camera, giving the visual impression the
     * Pokemon is "holding" the egg. Returns null if entity creation failed.
     */
    private fun spawnEggDisplay(world: ServerWorld, pokemonEntity: PokemonEntity, stack: ItemStack): DisplayEntity.ItemDisplayEntity? {
        return try {
            val display = EntityType.ITEM_DISPLAY.create(world) ?: return null
            // setItemStack + setBillboardMode are protected in DisplayEntity / ItemDisplayEntity,
            // so we invoke them via reflection. Match by parameter type to stay stable across
            // intermediary remapping.
            val setItemMethod = display.javaClass.declaredMethods.firstOrNull { m ->
                m.parameterCount == 1 && m.parameterTypes[0] == ItemStack::class.java
            }
            setItemMethod?.isAccessible = true
            setItemMethod?.invoke(display, stack.copyWithCount(1))

            val billboardMethod = DisplayEntity::class.java.declaredMethods.firstOrNull { m ->
                m.parameterCount == 1 && m.parameterTypes[0] == DisplayEntity.BillboardMode::class.java
            }
            billboardMethod?.isAccessible = true
            billboardMethod?.invoke(display, DisplayEntity.BillboardMode.CENTER)

            display.setPosition(
                pokemonEntity.x,
                pokemonEntity.y + pokemonEntity.height * 0.85,
                pokemonEntity.z
            )
            world.spawnEntity(display)
            display
        } catch (e: Exception) {
            Cobblebase.LOGGER.warn("[EggHatcher] Failed to spawn egg display: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** Teleports the egg display to follow the hatcher each tick. No-op if the entity is gone. */
    private fun updateEggDisplay(world: ServerWorld, pokemonEntity: PokemonEntity, displayId: Int) {
        if (displayId < 0) return
        val entity = world.getEntityById(displayId) ?: return
        if (entity.isRemoved) return
        entity.setPosition(
            pokemonEntity.x,
            pokemonEntity.y + pokemonEntity.height * 0.85,
            pokemonEntity.z
        )
    }

    /** Discards the display entity associated with a claim (egg hatched / claim dropped). */
    private fun killEggDisplay(world: ServerWorld, displayId: Int) {
        if (displayId < 0) return
        try {
            world.getEntityById(displayId)?.discard()
        } catch (_: Exception) {}
    }

    /** Returns the closest egg location not in [exclude], or null. */
    private fun findEgg(world: ServerWorld, origin: BlockPos, radius: Int, exclude: Set<EggLocation>): EggLocation? {
        // 1) Check the pasture's own block-entity inventory first — Cobbreeding's egg slot.
        val pastureEntity = world.getBlockEntity(origin)
        if (pastureEntity is Inventory) {
            for (slot in 0 until pastureEntity.size()) {
                val stack = pastureEntity.getStack(slot)
                if (isPokemonEgg(stack)) {
                    val loc = EggLocation(origin, slot)
                    if (loc !in exclude) return loc
                }
            }
        }
        // 2) Then scan nearby containers via the cached InventoryHelper container list.
        val nearby = InventoryHelper.findAllContainersPublic(world, origin, radius.coerceAtMost(12))
        // Sort by distance so closest-eggs go first.
        val sorted = nearby.sortedBy { it.getSquaredDistance(origin) }
        for (pos in sorted) {
            if (pos == origin) continue
            val be = world.getBlockEntity(pos) as? Inventory ?: continue
            for (slot in 0 until be.size()) {
                val stack = be.getStack(slot)
                if (isPokemonEgg(stack)) {
                    val loc = EggLocation(pos, slot)
                    if (loc !in exclude) return loc
                }
            }
        }
        return null
    }

    private fun stackAt(world: ServerWorld, loc: EggLocation): ItemStack? {
        val be = world.getBlockEntity(loc.containerPos) as? Inventory ?: return null
        if (loc.slot >= be.size()) return null
        val s = be.getStack(loc.slot)
        return if (isPokemonEgg(s)) s else null
    }

    /** Advance the timer on the claimed egg. If it hatches (stack becomes empty), release claim and log. */
    private fun tickClaim(
        world: ServerWorld,
        pasture: BlockPos,
        pokemonEntity: PokemonEntity,
        skillEntry: SkillEntry,
        claim: Claim,
        now: Long,
        skill: SkillDef
    ) {
        val pokemonId = pokemonEntity.pokemon.uuid
        val stack = stackAt(world, claim.location)
        if (stack == null) {
            // Egg's gone — either hatched OR removed by player. Assume hatched; release claim.
            HatchLogManager.append(
                worldTime = now,
                hatchedSpecies = claim.initialDescription,
                hatcherSpecies = pokemonEntity.pokemon.species.name,
                hatcherDisplay = pokemonEntity.pokemon.species.name,
                pasture = pasture,
                proficiency = skillEntry.proficiency
            )
            // Also feed the regular pasture Logs tab so hatch events show up alongside
            // mining/fishing/finding events instead of only in the dedicated Hatchery tab.
            notlown.cobblebase.core.LogManager.log(
                origin = pasture,
                worldTime = now,
                pokemonName = pokemonEntity.pokemon.species.name,
                action = "hatched",
                itemName = claim.initialDescription,
                rarity = notlown.cobblebase.core.LogManager.Rarity.UNCOMMON
            )
            killEggDisplay(world, claim.displayEntityId)
            claims.remove(pokemonId)
            SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
            return
        }
        // Keep the visual egg following the hatcher each tick.
        updateEggDisplay(world, pokemonEntity, claim.displayEntityId)
        // Per-proficiency speed multiplier comes straight from the tuning fields. Admin
        // controls each prof level independently — no hardcoded scaling. Default values:
        // Prof 1 = 1× (vanilla), 2 = 2×, 3 = 3×, 4 = 4×, 5 = 5× (linear).
        val prof = skillEntry.proficiency.coerceIn(1, 5)
        val tuningKey = "prof${prof}Speed"
        val defaultByProf = prof.toDouble()
        val tuningMult = SkillRegistry.getEffectiveTuning(skill.id, tuningKey, defaultByProf)
        val totalTicks = tuningMult.toInt().coerceAtLeast(1)

        // Simulate the egg's inventoryTick. Cobbreeding's PokemonEgg.inventoryTick EARLY-RETURNS
        // if the entity isn't a PlayerEntity — passing the Pokemon entity does nothing. We
        // resolve the hatcher Pokemon's owner (online ServerPlayerEntity) and use them as the
        // tick target so Cobbreeding's per-second SECOND/TIMER counters actually advance.
        // The hatched Pokemon will land in the owner's party (Cobbreeding's standard behavior).
        val ownerPlayer = try { pokemonEntity.pokemon.getOwnerPlayer() } catch (_: Exception) { null }
        if (ownerPlayer == null) {
            // Owner offline → cannot hatch. Still refresh claim timestamp so we don't lose it.
            claims[pokemonId] = claim.copy(lastSeenTick = now)
            return
        }
        val item = stack.item
        try {
            repeat(totalTicks) {
                item.inventoryTick(stack, world, ownerPlayer, claim.location.slot, false)
            }
        } catch (e: Exception) {
            Cobblebase.LOGGER.warn("[EggHatcher] inventoryTick threw for ${pokemonEntity.pokemon.species.name}: ${e.javaClass.simpleName}: ${e.message}")
        }

        // Refresh claim timestamp + current timer so the GUI sees fresh progress.
        val freshTimer = readEggTimer(stack)
        claims[pokemonId] = claim.copy(lastSeenTick = now, currentTimer = freshTimer)
        if (now % 40L == 0L) SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
    }

    /** Called by BaseManager periodic sweep — drops claims for Pokemon that have stopped ticking. */
    fun cleanupStale(now: Long) {
        claims.entries.removeAll { (_, c) -> now - c.lastSeenTick > STALE_TTL_TICKS }
    }

    /** Snapshot of an active hatcher — used by the Hatchery GUI to render live progress. */
    data class ActiveHatcherSnapshot(
        val hatcherPokemonId: UUID,
        val hatcherSpecies: String,
        val hatcherDisplayName: String,
        val eggSpecies: String,
        val initialTimer: Int,
        val currentTimer: Int,
        val proficiency: Int
    )

    /** Returns every active hatcher whose claim is anchored to [pasturePos]. */
    fun snapshotForPasture(pasturePos: BlockPos): List<ActiveHatcherSnapshot> {
        return claims.entries
            .filter { (_, c) -> c.pasturePos == pasturePos }
            .map { (id, c) ->
                ActiveHatcherSnapshot(
                    id, c.hatcherSpecies, c.hatcherDisplayName,
                    c.initialDescription, c.initialTimer, c.currentTimer, c.proficiency
                )
            }
    }

    /**
     * Counts eggs currently sitting in the pasture's own inventory + any nearby chests within
     * [radius], excluding those already claimed by an active hatcher. Used by the GUI to show
     * "X eggs available" so users know whether adding another hatcher would have work to do.
     */
    fun countAvailableEggs(world: ServerWorld, pasturePos: BlockPos, radius: Int): Int {
        if (!cobbreedingLoaded) return 0
        val claimedLocations = claims.values.map { it.location }.toSet()
        var count = 0

        val pastureEntity = world.getBlockEntity(pasturePos)
        if (pastureEntity is Inventory) {
            for (slot in 0 until pastureEntity.size()) {
                val stack = pastureEntity.getStack(slot)
                if (isPokemonEgg(stack) && EggLocation(pasturePos, slot) !in claimedLocations) {
                    count += stack.count
                }
            }
        }

        val nearby = InventoryHelper.findAllContainersPublic(world, pasturePos, radius.coerceAtMost(12))
        for (pos in nearby) {
            if (pos == pasturePos) continue
            val be = world.getBlockEntity(pos) as? Inventory ?: continue
            for (slot in 0 until be.size()) {
                val stack = be.getStack(slot)
                if (isPokemonEgg(stack) && EggLocation(pos, slot) !in claimedLocations) {
                    count += stack.count
                }
            }
        }
        return count
    }
}

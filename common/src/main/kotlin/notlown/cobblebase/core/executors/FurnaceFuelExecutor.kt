package notlown.cobblebase.core.executors

import notlown.cobblebase.core.effectiveRadius

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.AbstractFurnaceBlock
import net.minecraft.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.effects.SkillEffects
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import notlown.cobblebase.core.NavigationHelper
import java.util.UUID

/**
 * Furnace fuel executor: finds nearby furnaces and adds burn time to them.
 * Works with regular furnaces, blast furnaces, and smokers — plus any modded furnace-like
 * block added to the [FURNACE_COMPATIBLE_TAG] datapack tag.
 *
 * Two fueling paths:
 *  1. Vanilla / vanilla-derived furnaces (extend AbstractFurnaceBlockEntity) → fast NBT
 *     manipulation of BurnTime/CookTimeTotal.
 *  2. Custom BlockEntities (only implement [Inventory]) → drop a coal into the fuel slot
 *     (slot 1, vanilla furnace convention) and let the mod's own logic burn it down.
 */
object FurnaceFuelExecutor : SkillExecutor {

    /**
     * Datapack-extensible tag. Cobblebase ships with `minecraft:furnace`, `blast_furnace`,
     * `smoker` in the values. Modpack authors add their own furnace-like blocks via a
     * vanilla datapack tag — no code change needed.
     */
    val FURNACE_COMPATIBLE_TAG: TagKey<net.minecraft.block.Block> = TagKey.of(
        RegistryKeys.BLOCK,
        Identifier.of("cobblebase", "furnace_compatible")
    )

    private val lastFuelTime = mutableMapOf<UUID, Long>()
    private val lastScanTime = mutableMapOf<UUID, Long>()
    private const val SCAN_INTERVAL_TICKS = 20L  // throttle furnace scan when nothing needs fuel
    private const val STALE_TTL_TICKS = 1200L

    /** Drops per-Pokemon state for furnace-fuelers that have stopped ticking. */
    fun cleanupStale(now: Long) {
        val stale = lastFuelTime.entries.filter { now - it.value > STALE_TTL_TICKS }.map { it.key }
        for (id in stale) {
            lastFuelTime.remove(id)
            lastScanTime.remove(id)
            furnaceTarget.remove(id)
            targetRefreshTime.remove(id)
        }
    }
    /** Per-Pokemon claim on a specific furnace position. Acts as a soft reservation so multiple
     *  Furnace-Fuel Pokemon spread out instead of all rushing the same furnace. */
    private val furnaceTarget = mutableMapOf<UUID, BlockPos>()
    /** World-tick timestamp of the last refresh for each claim. Stale claims expire to recover
     *  furnaces when a Pokemon is recalled, despawned, or stops ticking for any reason. */
    private val targetRefreshTime = mutableMapOf<UUID, Long>()
    private const val BURN_TIME_ADDED = 1600 // Same as a piece of coal (80 seconds)
    private const val CLAIM_TTL_TICKS = 600L // 30 seconds — if a Pokemon hasn't refreshed its claim within this, drop it

    override fun tick(
        world: World,
        origin: BlockPos,
        pokemonEntity: PokemonEntity,
        skill: SkillDef,
        skillEntry: SkillEntry
    ) {
        val pokemonId = pokemonEntity.pokemon.uuid
        val now = world.time
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(skill.cooldownSeconds, skillEntry.proficiency, skill.id)

        // Cooldown check
        val lastTime = lastFuelTime[pokemonId] ?: 0L
        if (now - lastTime < cooldownTicks) return

        // Once cooldown has elapsed but no furnace work is available, the search would otherwise
        // run on every tick (radius³ block scan). Throttle to once per second when we don't
        // already have a claimed target.
        if (furnaceTarget[pokemonId] == null) {
            val lastScan = lastScanTime[pokemonId] ?: 0L
            if (now - lastScan < SCAN_INTERVAL_TICKS) return
            lastScanTime[pokemonId] = now
        }

        // Expire stale claims from other Pokemon that have stopped refreshing (recalled, despawned)
        targetRefreshTime.entries.removeAll { (otherId, lastSeen) ->
            otherId != pokemonId && now - lastSeen > CLAIM_TTL_TICKS
        }
        // Drop matching target entries for expired claims
        val livePokemonIds = targetRefreshTime.keys + pokemonId
        furnaceTarget.keys.retainAll(livePokemonIds)

        // Build the set of furnace positions currently claimed by OTHER Pokemon
        val otherClaims = furnaceTarget.filterKeys { it != pokemonId }.values.toSet()

        // Use our existing claim if valid; otherwise find an unclaimed furnace
        val existing = furnaceTarget[pokemonId]
        val target = if (existing != null && furnaceNeedsFuel(world, existing)) {
            existing
        } else {
            findFurnaceNeedingFuel(world, origin, skill.effectiveRadius, otherClaims)
        }
        if (target == null) {
            // No work available — release any stale claim
            furnaceTarget.remove(pokemonId)
            targetRefreshTime.remove(pokemonId)
            return
        }

        furnaceTarget[pokemonId] = target
        targetRefreshTime[pokemonId] = now

        // Verify the furnace still needs fuel
        if (!furnaceNeedsFuel(world, target)) {
            furnaceTarget.remove(pokemonId)
            targetRefreshTime.remove(pokemonId)
            return
        }

        NavigationHelper.navigateTo(pokemonEntity, target)
        if (NavigationHelper.isPokemonAtPosition(pokemonEntity, target)) {
            addFuel(world, target)
            lastFuelTime[pokemonId] = now
            furnaceTarget.remove(pokemonId)
            targetRefreshTime.remove(pokemonId)
            SkillEffects.playSuccess(world, pokemonEntity, skill.effectType, origin)
        }
    }

    /**
     * Find the closest furnace needing fuel that isn't already claimed by another Pokemon.
     * @param exclude positions currently reserved by other Pokemon — skip these so multiple
     *                Furnace-Fuel Pokemon distribute across different furnaces.
     */
    private fun findFurnaceNeedingFuel(world: World, origin: BlockPos, radius: Int, exclude: Set<BlockPos>): BlockPos? {
        var best: BlockPos? = null
        var bestDist = Double.MAX_VALUE

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = origin.add(x, y, z)
                    if (pos.toImmutable() in exclude) continue
                    if (furnaceNeedsFuel(world, pos)) {
                        val dist = pos.getSquaredDistance(origin)
                        if (dist < bestDist) {
                            bestDist = dist
                            best = pos.toImmutable()
                        }
                    }
                }
            }
        }
        return best
    }

    private fun furnaceNeedsFuel(world: World, pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        val block = state.block
        val blockEntity = world.getBlockEntity(pos) ?: return false

        // Brewing stand path — the brew_fuel skill is registered to this same executor, but
        // brewing stands don't extend AbstractFurnaceBlock. Vanilla slot layout is:
        // 0-2 = bottle slots, 3 = ingredient, 4 = blaze-powder fuel.
        if (blockEntity is net.minecraft.block.entity.BrewingStandBlockEntity) {
            val hasIngredient = !blockEntity.getStack(3).isEmpty
            val anyBottle = (0..2).any { !blockEntity.getStack(it).isEmpty }
            val fuelOut = try {
                val nbt = blockEntity.createNbt(world.registryManager)
                nbt.getShort("Fuel").toInt() <= 0
            } catch (_: Exception) { true }
            return hasIngredient && anyBottle && fuelOut
        }

        // Accept either a vanilla AbstractFurnaceBlock OR anything in the datapack tag.
        if (block !is AbstractFurnaceBlock && !state.isIn(FURNACE_COMPATIBLE_TAG)) return false

        // Vanilla-derived path: read BurnTime + check input slot.
        if (blockEntity is AbstractFurnaceBlockEntity) {
            val hasInput = !blockEntity.getStack(0).isEmpty
            val nbt = blockEntity.createNbt(world.registryManager)
            val burnTime = nbt.getShort("BurnTime").toInt()
            return hasInput && burnTime <= 0
        }

        // Inventory fallback: assume vanilla slot convention (0=input, 1=fuel, 2=output).
        // Considered "needs fuel" when input slot has something AND fuel slot is empty.
        if (blockEntity is Inventory) {
            return try {
                val hasInput = blockEntity.size() > 0 && !blockEntity.getStack(0).isEmpty
                val fuelEmpty = blockEntity.size() > 1 && blockEntity.getStack(1).isEmpty
                hasInput && fuelEmpty
            } catch (_: Exception) { false }
        }

        return false
    }

    private fun addFuel(world: World, pos: BlockPos) {
        val blockEntity = world.getBlockEntity(pos) ?: return

        // Brewing stand path: write the `Fuel` int directly (vanilla max is 20, equals
        // ~20 brews of unlimited ingredient). Same pattern as BurnTime for furnaces — we
        // don't make the Pokemon actually carry blaze powder.
        if (blockEntity is net.minecraft.block.entity.BrewingStandBlockEntity) {
            try {
                val nbt = blockEntity.createNbt(world.registryManager)
                nbt.putShort("Fuel", 20.toShort())  // vanilla max
                blockEntity.read(nbt, world.registryManager)
                blockEntity.markDirty()
            } catch (_: Exception) { }
            return
        }

        // Vanilla-derived path: write BurnTime directly via NBT.
        if (blockEntity is AbstractFurnaceBlockEntity) {
            val nbt = blockEntity.createNbt(world.registryManager)
            val currentBurnTime = nbt.getShort("BurnTime").toInt()
            nbt.putShort("BurnTime", (currentBurnTime + BURN_TIME_ADDED).toShort())
            if (nbt.getShort("CookTimeTotal").toInt() == 0) {
                nbt.putShort("CookTimeTotal", 200.toShort())
            }
            blockEntity.read(nbt, world.registryManager)
            blockEntity.markDirty()
            val state = world.getBlockState(pos)
            if (state.contains(AbstractFurnaceBlock.LIT)) {
                world.setBlockState(pos, state.with(AbstractFurnaceBlock.LIT, true), 3)
            }
            return
        }

        // Inventory fallback: drop one piece of coal into slot 1. The mod's own logic
        // burns it down according to its rules. Slot 1 is vanilla fuel-slot convention —
        // most furnace mods stick to it.
        if (blockEntity is Inventory) {
            try {
                if (blockEntity.size() > 1 && blockEntity.getStack(1).isEmpty) {
                    blockEntity.setStack(1, ItemStack(Items.COAL, 1))
                    blockEntity.markDirty()
                }
            } catch (_: Exception) { /* ignore — incompatible slot layout */ }
        }
    }



}

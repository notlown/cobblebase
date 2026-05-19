package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.block.ApricornBlock
import com.cobblemon.mod.common.block.BerryBlock
import com.cobblemon.mod.common.block.MintBlock
import com.cobblemon.mod.common.block.MedicinalLeekBlock
import com.cobblemon.mod.common.block.RevivalHerbBlock
import com.cobblemon.mod.common.block.HeartyGrainsBlock
import com.cobblemon.mod.common.block.NutBushBlock
import com.cobblemon.mod.common.block.BugwortBlock
import com.cobblemon.mod.common.block.VivichokeBlock
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.CropBlock
import net.minecraft.block.NetherWartBlock
import net.minecraft.block.SweetBerryBushBlock
import net.minecraft.item.ItemStack
import net.minecraft.loot.context.LootContextParameterSet
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.IntProperty
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.LogManager
import notlown.cobblebase.core.effects.SkillEffects
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import notlown.cobblebase.core.NavigationHelper
import java.util.UUID

/**
 * Unified harvester executor for crops, berries, apricorns, mints, and netherwart.
 * Scans for harvestable blocks in radius, navigates to them, harvests, deposits in chest.
 */
object HarvesterExecutor : SkillExecutor {

    private val APRICORNS_TAG = TagKey.of(RegistryKeys.BLOCK, Identifier.of("cobblemon", "apricorns"))

    private val heldItems = mutableMapOf<UUID, List<ItemStack>>()
    private val targetBlock = mutableMapOf<UUID, BlockPos>()
    private val targetSetTime = mutableMapOf<UUID, Long>()
    private val lastSearchTime = mutableMapOf<UUID, Long>()
    private val lastHarvestTime = mutableMapOf<UUID, Long>() // per-pokemon cooldown between harvests
    /** Per-Pokemon heartbeat for cleanup. Updated every tick (vs lastSearchTime which
     *  only fires during the scan phase), so on-cooldown or mid-navigation Pokemon
     *  don't have their cooldown timer wiped by the 60s sweep. */
    private val lastScanTime = mutableMapOf<UUID, Long>()
    private val harvestedBlockCooldown = mutableMapOf<BlockPos, Long>() // per-block cooldown to prevent instant re-harvest
    /**
     * Tracks the world-time tick at which each block was first observed ripe by any
     * Harvester scan. Used to prefer the longest-ripe crops (FIFO) on next harvest
     * decision, so newly-ripened blocks don't get picked before older ones.
     * Pruned automatically when a block stops being ripe (harvested, broken, regressed).
     */
    private val firstSeenRipeTime = mutableMapOf<BlockPos, Long>()
    /**
     * BlockPos → UUID of the harvester that picked this block as its target. Prevents
     * multiple harvesters from converging on the same crop (previously every scan was
     * independent, so 3 harvesters with overlapping radii routinely chased one apricorn).
     * Expired when the owner reaches the target, releases voluntarily, or stops ticking.
     */
    private val reservedBlocks = mutableMapOf<BlockPos, UUID>()
    private const val RESERVATION_TIMEOUT_TICKS = 200L
    private const val STALE_TTL_TICKS = 1200L

    fun cleanupStale(now: Long) {
        val stale = lastScanTime.entries.filter { now - it.value > STALE_TTL_TICKS }.map { it.key }
        for (id in stale) {
            lastScanTime.remove(id)
            heldItems.remove(id)
            targetBlock.remove(id)
            targetSetTime.remove(id)
            lastSearchTime.remove(id)
            lastHarvestTime.remove(id)
            // Drop reservations owned by a stale harvester.
            reservedBlocks.entries.removeAll { it.value == id }
        }
        // The block-level cooldown map is keyed by BlockPos; expire entries older than 10s.
        harvestedBlockCooldown.entries.removeAll { now - it.value > 200L }
        // firstSeenRipeTime is self-pruned during findHarvestable (drops stale entries when
        // their block stops being ripe). No additional cleanup needed here.
    }
    private const val BLOCK_COOLDOWN_TICKS = 1200L // 60 seconds before same block can be harvested again
    private const val HARVEST_COOLDOWN_TICKS = 200L // 10 seconds between harvests (so Gatherer can keep up)
    private val NAV_TIMEOUT_TICKS = 100L // 5 seconds - auto-harvest if can't reach
    private val SEARCH_INTERVAL_TICKS = 40L // 2 seconds between scans when nothing is ripe

    /**
     * Returns movement speed based on proficiency (1-5).
     * Prof 1 = 0.4 (slow), Prof 5 = 1.2 (fast)
     */
    private fun getSpeedForProficiency(proficiency: Int): Double {
        return 0.2 + (proficiency * 0.2)
    }

    override fun tick(
        world: World,
        origin: BlockPos,
        pokemonEntity: PokemonEntity,
        skill: SkillDef,
        skillEntry: SkillEntry
    ) {
        if (world !is ServerWorld) return
        val pokemonId = pokemonEntity.pokemon.uuid
        val now = world.time
        lastScanTime[pokemonId] = now
        val speed = getSpeedForProficiency(skillEntry.proficiency)
        val items = heldItems[pokemonId]

        // Phase 1: deposit items if holding any
        if (!items.isNullOrEmpty()) {
            depositItems(world, origin, pokemonEntity, pokemonId, speed)
            return
        }

        // Cooldown between harvests (so Gatherer can keep up with drops)
        val lastHarvest = lastHarvestTime[pokemonId] ?: 0L
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(skill.cooldownSeconds, skillEntry.proficiency, skill.id)
        if (now - lastHarvest < cooldownTicks) {
            if (now % 60 == 0L) SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
            return
        }

        // Phase 2: navigate to target or harvest
        val target = targetBlock[pokemonId]
        if (target != null) {
            NavigationHelper.navigateTo(pokemonEntity, target, speed)
            val navStarted = targetSetTime[pokemonId] ?: now

            val timedOut = now - navStarted >= NAV_TIMEOUT_TICKS

            if (NavigationHelper.isPokemonAtPosition(pokemonEntity, target, 3.0) || timedOut) {
                reservedBlocks.remove(target) // release reservation regardless of harvest outcome
                harvest(world, target, pokemonEntity, pokemonId)
                targetBlock.remove(pokemonId)
                targetSetTime.remove(pokemonId)
                lastHarvestTime[pokemonId] = now
                Cobblebase.log("[Harvester] ${pokemonEntity.pokemon.species.name} HARVESTED at $target")
                SkillEffects.playSuccess(world, pokemonEntity, skill.effectType, origin)

                // Log harvested items
                val harvested = heldItems[pokemonId]
                if (!harvested.isNullOrEmpty()) {
                    for (item in harvested) {
                        LogManager.log(
                            origin, world.time,
                            pokemonEntity.pokemon.species.name,
                            "Harvested",
                            "${item.name.string} x${item.count}",
                            LogManager.Rarity.COMMON
                        )
                    }
                }
            }
            return
        }

        // Phase 3: search for harvestable block (throttled to avoid lag)
        val lastSearch = lastSearchTime[pokemonId] ?: 0L
        if (now - lastSearch < SEARCH_INTERVAL_TICKS) return
        lastSearchTime[pokemonId] = now

        // Use the server-wide pasture range (Admin → Server Settings) so the harvest scan
        // matches what the player set as the pasture's working radius. Falls through to the
        // skill's own JSON default when no server setting exists.
        val pastureRadius = notlown.cobblebase.core.CobblebaseConfig.jobSearchRadius
        // Debug: prove the scan really uses the admin-set radius. Logs once per scan
        // cycle per Pokemon (SEARCH_INTERVAL_TICKS apart so it's not spammy).
        Cobblebase.LOGGER.info("[Harvester] ${pokemonEntity.pokemon.species.name} scan @${origin.toShortString()} radius=$pastureRadius")
        val found = findHarvestable(world, origin, pastureRadius, pokemonId, now)
        if (found != null) {
            val d = kotlin.math.sqrt(((found.x - origin.x).toDouble().let { it*it } + (found.y - origin.y).toDouble().let { it*it } + (found.z - origin.z).toDouble().let { it*it }))
            Cobblebase.LOGGER.info("[Harvester]   -> found ripe @${found.toShortString()} dist=$d")
            targetBlock[pokemonId] = found
            targetSetTime[pokemonId] = now
            reservedBlocks[found] = pokemonId
        } else {
            // Nothing ripe - wander towards nearest growing (not yet ripe) harvestable block
            val growingPos = findGrowing(world, origin, pastureRadius)
            if (growingPos != null) {
                NavigationHelper.navigateTo(pokemonEntity, growingPos, speed * 0.4)
            }
        }
    }

    private fun findHarvestable(world: World, origin: BlockPos, radius: Int, requester: UUID, now: Long): BlockPos? {
        // Periodic cooldown sweep + drop reservations whose owner stopped ticking.
        if (now % 200 == 0L) {
            harvestedBlockCooldown.entries.removeIf { now - it.value > BLOCK_COOLDOWN_TICKS }
            // A reservation is stale if its owner hasn't refreshed `targetSetTime` recently.
            reservedBlocks.entries.removeIf { (_, ownerId) ->
                val tset = targetSetTime[ownerId] ?: return@removeIf true
                now - tset > RESERVATION_TIMEOUT_TICKS
            }
        }
        val candidates = mutableListOf<BlockPos>()
        val seenThisScan = HashSet<BlockPos>()

        // Admin-configurable downward scan buffer (Admin → Server Settings).
        // Small values prevent mons from trying to dig into caves under the base;
        // larger values let them reach cliff-edge and hillside harvestables.
        val yDown = minOf(radius, notlown.cobblebase.core.CobblebaseConfig.belowPastureReach)
        for (x in -radius..radius) {
            // Y range: full `radius` upward (tall berry bushes, hanging apricorns)
            // but only DOWNWARD_SCAN_BUFFER downward — enough for cliff edges and
            // stair gardens, not enough to crawl into caves under the base.
            for (y in -yDown..radius) {
                for (z in -radius..radius) {
                    val pos = origin.add(x, y, z)
                    // Skip blocks still on cooldown (prevents instant re-harvest with Irrigator)
                    val immPos = pos.toImmutable()
                    val lastHarvest = harvestedBlockCooldown[immPos]
                    if (lastHarvest != null && now - lastHarvest < BLOCK_COOLDOWN_TICKS) continue
                    if (isReadyToHarvest(world, pos)) {
                        firstSeenRipeTime.putIfAbsent(immPos, now)
                        seenThisScan.add(immPos)
                        // Reservations: skip blocks claimed by another harvester. The
                        // requester's own claim (e.g. re-scan after a tick gap) is allowed.
                        val owner = reservedBlocks[immPos]
                        if (owner != null && owner != requester) continue
                        candidates.add(immPos)
                    }
                }
            }
        }
        // Prune any tracked positions inside the radius that are no longer ripe (harvested
        // by another mon, broken by a player, age regressed, etc.). Positions outside the
        // current scan radius are left alone — another pasture's harvester may track them.
        val radiusSquared = (radius.toLong() * radius)
        firstSeenRipeTime.entries.removeIf { (pos, _) ->
            val dx = (pos.x - origin.x).toLong()
            val dy = (pos.y - origin.y).toLong()
            val dz = (pos.z - origin.z).toLong()
            val inRange = (dx * dx + dy * dy + dz * dz) <= radiusSquared * 3
            inRange && pos !in seenThisScan
        }
        // Prefer the block that has been ripe the longest (FIFO). Stable secondary sort
        // by squared distance to the pasture so ties go to the nearest mon-reachable one.
        return candidates.minWithOrNull(
            compareBy<BlockPos>(
                { firstSeenRipeTime[it] ?: now },
                { (it.x - origin.x).let { d -> d * d } + (it.y - origin.y).let { d -> d * d } + (it.z - origin.z).let { d -> d * d } }
            )
        )
    }

    /**
     * Finds a random growing (not yet ripe) harvestable block to wander towards.
     */
    private fun findGrowing(world: World, origin: BlockPos, radius: Int): BlockPos? {
        val candidates = mutableListOf<BlockPos>()
        // Admin-configurable downward scan buffer (Admin → Server Settings).
        // Small values prevent mons from trying to dig into caves under the base;
        // larger values let them reach cliff-edge and hillside harvestables.
        val yDown = minOf(radius, notlown.cobblebase.core.CobblebaseConfig.belowPastureReach)
        for (x in -radius..radius) {
            for (y in -yDown..radius) {
                for (z in -radius..radius) {
                    val pos = origin.add(x, y, z)
                    if (isHarvestableBlock(world, pos) && !isReadyToHarvest(world, pos)) {
                        candidates.add(pos.toImmutable())
                    }
                }
            }
        }
        return candidates.randomOrNull()
    }

    /**
     * Checks if a block is a harvestable type at all (crop, berry, apricorn, etc.) regardless of age.
     */
    private fun isHarvestableBlock(world: World, pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        val block = state.block
        return state.isIn(APRICORNS_TAG) || block is BerryBlock || block is CropBlock
            || block is NetherWartBlock || block is SweetBerryBushBlock || block is MintBlock
            || block is MedicinalLeekBlock || block is RevivalHerbBlock || block is HeartyGrainsBlock
            || block is NutBushBlock || block is BugwortBlock || block is VivichokeBlock
    }

    private fun isReadyToHarvest(world: World, pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        val block = state.block

        // Apricorns (Cobblemon)
        if (state.isIn(APRICORNS_TAG)) {
            return try {
                state.get(ApricornBlock.AGE) == ApricornBlock.MAX_AGE
            } catch (_: Exception) { false }
        }

        // Cobblemon berries
        if (block is BerryBlock) {
            return try {
                state.get(BerryBlock.AGE) >= BerryBlock.FRUIT_AGE
            } catch (_: Exception) { false }
        }

        // Vanilla crops (wheat, carrots, potatoes, beetroot)
        if (block is CropBlock) {
            return block.isMature(state)
        }

        // Nether wart
        if (block is NetherWartBlock) {
            return state.get(NetherWartBlock.AGE) == 3
        }

        // Sweet berry bushes
        if (block is SweetBerryBushBlock) {
            return state.get(SweetBerryBushBlock.AGE) >= 2
        }

        // Cobblemon mints
        if (block is MintBlock) {
            return try { state.get(MintBlock.AGE) >= MintBlock.MATURE_AGE } catch (_: Exception) { false }
        }

        // Cobblemon medicinal leek
        if (block is MedicinalLeekBlock) {
            return try { state.get(MedicinalLeekBlock.AGE) >= MedicinalLeekBlock.MATURE_AGE } catch (_: Exception) { false }
        }

        // Cobblemon revival herb
        if (block is RevivalHerbBlock) {
            return try { state.get(RevivalHerbBlock.AGE) >= RevivalHerbBlock.MAX_AGE } catch (_: Exception) { false }
        }

        // Cobblemon hearty grains
        if (block is HeartyGrainsBlock) {
            return try { state.get(HeartyGrainsBlock.AGE) >= HeartyGrainsBlock.MATURE_AGE } catch (_: Exception) { false }
        }

        // Cobblemon nut bush (Galarica Nuts etc.)
        if (block is NutBushBlock) {
            return try { state.get(NutBushBlock.AGE) >= NutBushBlock.MAX_AGE } catch (_: Exception) { false }
        }

        // Cobblemon bugwort
        if (block is BugwortBlock) {
            return try { state.get(BugwortBlock.AGE) >= BugwortBlock.MATURE_AGE } catch (_: Exception) { false }
        }

        // Cobblemon vivichoke
        if (block is VivichokeBlock) {
            return try { state.get(net.minecraft.state.property.Properties.AGE_4) >= 4 } catch (_: Exception) { false }
        }

        return false
    }

    private fun harvest(world: ServerWorld, pos: BlockPos, pokemonEntity: PokemonEntity, pokemonId: UUID) {
        // Drop ripe-time tracker — block resets to growing, will be re-tracked when ripe again.
        firstSeenRipeTime.remove(pos.toImmutable())
        val state = world.getBlockState(pos)
        val block = state.block
        // Record harvest time for this block (prevents instant re-harvest)
        harvestedBlockCooldown[pos.toImmutable()] = world.time

        if (state.isIn(APRICORNS_TAG)) {
            // Apricorn: get drops, reset age to 0
            val drops = getBlockDrops(world, pos, state, pokemonEntity)
            if (drops.isNotEmpty()) heldItems[pokemonId] = drops
            world.setBlockState(pos, state.with(ApricornBlock.AGE, 0), Block.NOTIFY_ALL)
            return
        }

        if (block is BerryBlock) {
            // Cobblemon berry: use BerryBlockEntity.harvest() — handles drops, age reset, and mulch
            try {
                val blockEntity = world.getBlockEntity(pos)
                if (blockEntity is com.cobblemon.mod.common.block.entity.BerryBlockEntity) {
                    val drops = blockEntity.harvest(world, state, pos)
                    if (drops.isNotEmpty()) heldItems[pokemonId] = drops.toList()
                }
            } catch (_: Exception) {
                // Fallback: just reset age, no drops
                try {
                    world.setBlockState(pos, state.with(BerryBlock.AGE, BerryBlock.MATURE_AGE), Block.NOTIFY_ALL)
                } catch (_: Exception) { }
            }
            return
        }

        if (block is CropBlock) {
            // Crop: get drops, break and replant (set age to 0)
            val drops = getBlockDrops(world, pos, state, pokemonEntity)
            if (drops.isNotEmpty()) heldItems[pokemonId] = drops
            world.setBlockState(pos, block.defaultState, Block.NOTIFY_ALL)
            return
        }

        if (block is NetherWartBlock) {
            val drops = getBlockDrops(world, pos, state, pokemonEntity)
            if (drops.isNotEmpty()) heldItems[pokemonId] = drops
            world.setBlockState(pos, block.defaultState, Block.NOTIFY_ALL)
            return
        }

        if (block is SweetBerryBushBlock) {
            val drops = getBlockDrops(world, pos, state, pokemonEntity)
            if (drops.isNotEmpty()) heldItems[pokemonId] = drops
            world.setBlockState(pos, state.with(SweetBerryBushBlock.AGE, 1), Block.NOTIFY_ALL)
            return
        }

        // Cobblemon hearty grains (2 blocks tall) — harvest both top and bottom only if mature
        if (block is HeartyGrainsBlock) {
            val drops = mutableListOf<ItemStack>()
            drops.addAll(getBlockDrops(world, pos, state, pokemonEntity))
            world.setBlockState(pos, block.defaultState, Block.NOTIFY_ALL)
            // Also harvest adjacent HeartyGrains block (above or below) only if it's also mature
            for (adjacentPos in listOf(pos.up(), pos.down())) {
                val adjState = world.getBlockState(adjacentPos)
                if (adjState.block is HeartyGrainsBlock) {
                    val adjMature = try { adjState.get(HeartyGrainsBlock.AGE) >= HeartyGrainsBlock.MATURE_AGE } catch (_: Exception) { false }
                    if (adjMature) {
                        drops.addAll(getBlockDrops(world, adjacentPos, adjState, pokemonEntity))
                        world.setBlockState(adjacentPos, adjState.block.defaultState, Block.NOTIFY_ALL)
                    }
                }
            }
            if (drops.isNotEmpty()) heldItems[pokemonId] = drops
            return
        }

        // Generic Cobblemon growable: get drops, reset to default state (regrow)
        val drops = getBlockDrops(world, pos, state, pokemonEntity)
        if (drops.isNotEmpty()) heldItems[pokemonId] = drops
        try {
            world.setBlockState(pos, block.defaultState, Block.NOTIFY_ALL)
        } catch (_: Exception) {
            world.breakBlock(pos, false)
        }
    }

    private fun getBlockDrops(world: ServerWorld, pos: BlockPos, state: BlockState, pokemonEntity: PokemonEntity): List<ItemStack> {
        val lootParams = LootContextParameterSet.Builder(world)
            .add(LootContextParameters.ORIGIN, pos.toCenterPos())
            .add(LootContextParameters.BLOCK_STATE, state)
            .add(LootContextParameters.TOOL, ItemStack.EMPTY)
            .addOptional(LootContextParameters.THIS_ENTITY, pokemonEntity)
        return state.getDroppedStacks(lootParams)
    }

    private fun depositItems(world: World, origin: BlockPos, pokemonEntity: PokemonEntity, pokemonId: UUID, speed: Double = 1.0) {
        val items = heldItems[pokemonId] ?: return
        // Drop items on the ground — Gatherer will pick them up and sort into chests
        InventoryHelper.dropItems(world, pokemonEntity.blockPos, items, origin)
        heldItems.remove(pokemonId)
    }



}

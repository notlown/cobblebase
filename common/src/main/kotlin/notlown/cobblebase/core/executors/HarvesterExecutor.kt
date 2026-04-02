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
    private val harvestedBlockCooldown = mutableMapOf<BlockPos, Long>() // per-block cooldown to prevent instant re-harvest
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
        val speed = getSpeedForProficiency(skillEntry.proficiency)
        val items = heldItems[pokemonId]

        // Phase 1: deposit items if holding any
        if (!items.isNullOrEmpty()) {
            depositItems(world, origin, pokemonEntity, pokemonId, speed)
            return
        }

        // Cooldown between harvests (so Gatherer can keep up with drops)
        val lastHarvest = lastHarvestTime[pokemonId] ?: 0L
        val cooldownTicks = if (CobblebaseConfig.devMode) 40L else HARVEST_COOLDOWN_TICKS
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
                harvest(world, target, pokemonEntity, pokemonId)
                targetBlock.remove(pokemonId)
                targetSetTime.remove(pokemonId)
                lastHarvestTime[pokemonId] = now
                Cobblebase.LOGGER.info("[Harvester] ${pokemonEntity.pokemon.species.name} HARVESTED at $target")
                SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)

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

        val found = findHarvestable(world, origin, skill.searchRadius)
        if (found != null) {
            targetBlock[pokemonId] = found
            targetSetTime[pokemonId] = now
        } else {
            // Nothing ripe - wander towards nearest growing (not yet ripe) harvestable block
            val growingPos = findGrowing(world, origin, skill.searchRadius)
            if (growingPos != null) {
                NavigationHelper.navigateTo(pokemonEntity, growingPos, speed * 0.4)
            }
        }
    }

    private fun findHarvestable(world: World, origin: BlockPos, radius: Int): BlockPos? {
        val now = world.time
        // Cleanup old block cooldowns periodically
        if (now % 200 == 0L) {
            harvestedBlockCooldown.entries.removeIf { now - it.value > BLOCK_COOLDOWN_TICKS }
        }
        val candidates = mutableListOf<BlockPos>()

        for (x in -radius..radius) {
            for (y in 0..radius) { // Only search at pasture level and ABOVE, never below
                for (z in -radius..radius) {
                    val pos = origin.add(x, y, z)
                    // Skip blocks still on cooldown (prevents instant re-harvest with Irrigator)
                    val immPos = pos.toImmutable()
                    val lastHarvest = harvestedBlockCooldown[immPos]
                    if (lastHarvest != null && now - lastHarvest < BLOCK_COOLDOWN_TICKS) continue
                    if (isReadyToHarvest(world, pos)) {
                        candidates.add(immPos)
                    }
                }
            }
        }
        return candidates.randomOrNull()
    }

    /**
     * Finds a random growing (not yet ripe) harvestable block to wander towards.
     */
    private fun findGrowing(world: World, origin: BlockPos, radius: Int): BlockPos? {
        val candidates = mutableListOf<BlockPos>()
        for (x in -radius..radius) {
            for (y in -radius..radius) {
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
            // Cobblemon berry: get drops, reset age to 0
            val drops = getBlockDrops(world, pos, state, pokemonEntity)
            if (drops.isNotEmpty()) heldItems[pokemonId] = drops
            try {
                world.setBlockState(pos, state.with(BerryBlock.AGE, 0), Block.NOTIFY_ALL)
            } catch (_: Exception) {
                world.setBlockState(pos, block.defaultState, Block.NOTIFY_ALL)
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
        InventoryHelper.dropItems(world, pokemonEntity.blockPos, items)
        heldItems.remove(pokemonId)
    }



}

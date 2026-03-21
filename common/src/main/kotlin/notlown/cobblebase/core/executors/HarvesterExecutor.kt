package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.block.ApricornBlock
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
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.effects.SkillEffects
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
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
    private val lastHarvestTime = mutableMapOf<UUID, Long>()
    private val NAV_TIMEOUT_TICKS = 100L // 5 seconds - auto-harvest if can't reach

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
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(skill.cooldownSeconds, skillEntry.proficiency)
        val items = heldItems[pokemonId]

        if (!items.isNullOrEmpty()) {
            depositItems(world, origin, pokemonEntity, pokemonId)
            return
        }

        // Cooldown check (only if cooldown > 0)
        if (cooldownTicks > 0) {
            val lastTime = lastHarvestTime[pokemonId] ?: 0L
            if (now - lastTime < cooldownTicks) return
        }

        // Find a harvestable block
        val target = targetBlock[pokemonId]
        if (target != null) {
            NavigationHelper.navigateTo(pokemonEntity, target)
            val navStarted = targetSetTime[pokemonId] ?: now
            val timedOut = now - navStarted >= NAV_TIMEOUT_TICKS

            if (NavigationHelper.isPokemonAtPosition(pokemonEntity, target) || timedOut) {
                // Harvest - either we reached it or timed out (auto-pick)
                harvest(world, target, pokemonEntity, pokemonId)
                targetBlock.remove(pokemonId)
                targetSetTime.remove(pokemonId)
                lastHarvestTime[pokemonId] = now
                SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
            }
        } else {
            val found = findHarvestable(world, origin, skill.searchRadius)
            if (found != null) {
                targetBlock[pokemonId] = found
                targetSetTime[pokemonId] = now
            }
        }
    }

    private fun findHarvestable(world: World, origin: BlockPos, radius: Int): BlockPos? {
        var best: BlockPos? = null
        var bestDist = Double.MAX_VALUE

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = origin.add(x, y, z)
                    if (isReadyToHarvest(world, pos)) {
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

    private fun isReadyToHarvest(world: World, pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        val block = state.block

        // Apricorns (Cobblemon)
        if (state.isIn(APRICORNS_TAG)) {
            return try {
                state.get(ApricornBlock.AGE) == ApricornBlock.MAX_AGE
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

        return false
    }

    private fun harvest(world: ServerWorld, pos: BlockPos, pokemonEntity: PokemonEntity, pokemonId: UUID) {
        val state = world.getBlockState(pos)
        val block = state.block

        if (state.isIn(APRICORNS_TAG)) {
            // Apricorn: get drops, reset age to 0
            val drops = getBlockDrops(world, pos, state, pokemonEntity)
            if (drops.isNotEmpty()) heldItems[pokemonId] = drops
            world.setBlockState(pos, state.with(ApricornBlock.AGE, 0), Block.NOTIFY_ALL)
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

        // Generic fallback: break and collect drops
        val drops = getBlockDrops(world, pos, state, pokemonEntity)
        if (drops.isNotEmpty()) heldItems[pokemonId] = drops
        world.breakBlock(pos, false)
    }

    private fun getBlockDrops(world: ServerWorld, pos: BlockPos, state: BlockState, pokemonEntity: PokemonEntity): List<ItemStack> {
        val lootParams = LootContextParameterSet.Builder(world)
            .add(LootContextParameters.ORIGIN, pos.toCenterPos())
            .add(LootContextParameters.BLOCK_STATE, state)
            .add(LootContextParameters.TOOL, ItemStack.EMPTY)
            .addOptional(LootContextParameters.THIS_ENTITY, pokemonEntity)
        return state.getDroppedStacks(lootParams)
    }

    private fun depositItems(world: World, origin: BlockPos, pokemonEntity: PokemonEntity, pokemonId: UUID) {
        val items = heldItems[pokemonId] ?: return
        val chestPos = InventoryHelper.findBestContainer(world, pokemonEntity.blockPos, 10, items) ?: return

        NavigationHelper.navigateTo(pokemonEntity, chestPos)
        if (NavigationHelper.isPokemonAtPosition(pokemonEntity, chestPos)) {
            InventoryHelper.insertItems(world, chestPos, items)
            heldItems.remove(pokemonId)
        }
    }



}

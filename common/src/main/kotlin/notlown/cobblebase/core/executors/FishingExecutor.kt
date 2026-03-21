package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.loot.LootTables
import net.minecraft.loot.context.LootContextParameterSet
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.loot.context.LootContextTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.effects.SkillEffects
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import java.util.UUID

/**
 * Fishing executor: Pokemon navigates to water, generates fishing loot, deposits in chest.
 */
object FishingExecutor : SkillExecutor {

    private val lastGenerationTime = mutableMapOf<UUID, Long>()
    private val heldItems = mutableMapOf<UUID, List<ItemStack>>()
    private val successTime = mutableMapOf<UUID, Long>()
    private val waterTarget = mutableMapOf<UUID, BlockPos>()
    private const val SUCCESS_PAUSE_TICKS = 40L

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
            // Has items - wait for success pause, then deposit
            val catchTime = successTime[pokemonId]
            if (catchTime != null && now - catchTime < SUCCESS_PAUSE_TICKS) return
            successTime.remove(pokemonId)
            depositItems(world, origin, pokemonEntity, pokemonId)
            return
        }

        if (isNearWater(world, pokemonEntity)) {
            waterTarget.remove(pokemonId)
            // At water - check cooldown
            val lastTime = lastGenerationTime[pokemonId] ?: 0L
            if (now - lastTime < cooldownTicks) {
                if (world.time % 20 == 0L) SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
                return
            }

            // Generate fishing loot
            generateLoot(world, pokemonEntity, pokemonId, now)
            if (!heldItems[pokemonId].isNullOrEmpty()) {
                SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
            }
        } else {
            // Navigate to water
            val target = waterTarget[pokemonId] ?: findWater(world, origin, skill.searchRadius)
            if (target != null) {
                waterTarget[pokemonId] = target
                NavigationHelper.navigateTo(pokemonEntity, target.up())
            }
        }
    }

    private fun isNearWater(world: World, pokemonEntity: PokemonEntity): Boolean {
        if (pokemonEntity.isTouchingWater) return true
        val pos = pokemonEntity.blockPos
        return BlockPos.iterate(pos.add(-1, -1, -1), pos.add(1, 1, 1)).any { checkPos ->
            world.getBlockState(checkPos).block == Blocks.WATER
        }
    }

    private fun findWater(world: World, origin: BlockPos, radius: Int): BlockPos? {
        var best: BlockPos? = null
        var bestDist = Double.MAX_VALUE

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = origin.add(x, y, z)
                    if (world.getBlockState(pos).block == Blocks.WATER) {
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

    private fun generateLoot(world: ServerWorld, pokemonEntity: PokemonEntity, pokemonId: UUID, now: Long) {
        val treasureChance = 0.05
        val useTreasure = world.random.nextFloat() < treasureChance

        val lootParams = LootContextParameterSet.Builder(world)
            .add(LootContextParameters.ORIGIN, pokemonEntity.blockPos.toCenterPos())
            .add(LootContextParameters.TOOL, ItemStack(Items.FISHING_ROD))
            .addOptional(LootContextParameters.THIS_ENTITY, pokemonEntity)
            .build(LootContextTypes.FISHING)

        val lootTable = if (useTreasure) {
            world.server.reloadableRegistries.getLootTable(LootTables.FISHING_TREASURE_GAMEPLAY)
        } else {
            world.server.reloadableRegistries.getLootTable(LootTables.FISHING_GAMEPLAY)
        }

        val drops = lootTable.generateLoot(lootParams)
        if (drops.isNotEmpty()) {
            lastGenerationTime[pokemonId] = now
            heldItems[pokemonId] = drops
            successTime[pokemonId] = now
        }
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

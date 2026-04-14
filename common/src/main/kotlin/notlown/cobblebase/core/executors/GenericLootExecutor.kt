package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.item.ItemStack
import net.minecraft.loot.context.LootContextParameterSet
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.loot.context.LootContextTypes
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.LogManager
import notlown.cobblebase.core.effects.SkillEffects
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import notlown.cobblebase.core.NavigationHelper
import java.util.UUID

/**
 * Generic loot executor for pickup, archeology, diving, and any other loot-table-based skill.
 * Generates loot from the configured loot table on cooldown and deposits in a nearby chest.
 */
object GenericLootExecutor : SkillExecutor {

    private val lastLootTime = mutableMapOf<UUID, Long>()
    private val heldItems = mutableMapOf<UUID, List<ItemStack>>()

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

        // If holding items, go deposit
        if (!items.isNullOrEmpty()) {
            depositItems(world, origin, pokemonEntity, pokemonId)
            return
        }

        // Cooldown check
        val lastTime = lastLootTime[pokemonId] ?: 0L
        if (now - lastTime < cooldownTicks) {
            if (world.time % 20 == 0L) SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
            return
        }

        // Generate loot from the skill's loot table (admin override > bundled > vanilla)
        val lootTableId = skill.lootTable ?: return
        val drops = notlown.cobblebase.core.LootHelper.generateLoot(
            lootTableId, world, pokemonEntity.blockPos, pokemonEntity
        )

        // Always reset cooldown even if no drops (random chance can produce empty rolls)
        lastLootTime[pokemonId] = now

        if (drops.isNotEmpty()) {
            heldItems[pokemonId] = drops
            SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)

            // Log loot
            val speciesDisplay = pokemonEntity.pokemon.species.name
            for (drop in drops) {
                LogManager.log(
                    origin, world.time,
                    speciesDisplay,
                    skill.name,
                    "${drop.name.string} x${drop.count}",
                    LogManager.Rarity.COMMON
                )
            }
        }
    }

    private val depositTarget = mutableMapOf<UUID, BlockPos>()

    private fun depositItems(world: World, origin: BlockPos, pokemonEntity: PokemonEntity, pokemonId: UUID) {
        val items = heldItems[pokemonId] ?: return

        // Find chest if not already targeted
        val target = depositTarget[pokemonId] ?: run {
            val found = InventoryHelper.findBestContainer(world, origin, 15, items)
            if (found != null) {
                depositTarget[pokemonId] = found
                found
            } else {
                // No chest found — drop near pasture block
                InventoryHelper.dropItems(world, origin, items, origin)
                heldItems.remove(pokemonId)
                return
            }
        }

        // Navigate to chest visually
        val dist = pokemonEntity.squaredDistanceTo(target.x + 0.5, target.y.toDouble(), target.z + 0.5)
        if (dist > 4.0) {
            // Still walking to chest
            NavigationHelper.navigateTo(pokemonEntity, target)
            return
        }

        // At chest — deposit items
        val remaining = InventoryHelper.insertItems(world, target, items)
        if (remaining.isEmpty()) {
            heldItems.remove(pokemonId)
        } else {
            heldItems[pokemonId] = remaining
        }
        depositTarget.remove(pokemonId)
        NavigationHelper.clearTargets(pokemonEntity)
    }



}

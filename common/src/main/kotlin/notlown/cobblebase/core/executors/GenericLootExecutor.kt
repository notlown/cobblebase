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
            depositItems(world, origin, pokemonEntity, pokemonId, skill)
            return
        }

        // Cooldown check
        val lastTime = lastLootTime[pokemonId] ?: 0L
        if (now - lastTime < cooldownTicks) {
            if (world.time % 20 == 0L) SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
            return
        }

        // Generate loot from the skill's loot table (admin override > bundled > vanilla)
        val lootTableId = skill.lootTable ?: run {
            Cobblebase.LOGGER.warn("[GenericLoot] ${pokemonEntity.pokemon.species.name} skill '${skill.name}' has no lootTable configured!")
            return
        }

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

    private fun depositItems(world: World, origin: BlockPos, pokemonEntity: PokemonEntity, pokemonId: UUID, skill: SkillDef) {
        val items = heldItems[pokemonId] ?: return

        // Instant deposit into nearest matching chest (no walking needed)
        val containerPos = InventoryHelper.findBestContainer(world, origin, skill.searchRadius, items)
        if (containerPos != null) {
            InventoryHelper.insertItems(world, containerPos, items)
        } else {
            // No chest found — drop near pasture block (not at pokemon, may be underwater)
            InventoryHelper.dropItems(world, origin, items, origin)
        }
        heldItems.remove(pokemonId)
    }



}

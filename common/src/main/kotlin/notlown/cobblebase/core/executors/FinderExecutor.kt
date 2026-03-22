package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.item.ItemStack
import net.minecraft.loot.LootTable
import net.minecraft.loot.context.LootContextParameterSet
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.loot.context.LootContextTypes
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import notlown.cobblebase.core.effects.SkillEffects
import java.util.UUID

/**
 * Finder: Pokemon searches the area and finds random items/treasures.
 * Long cooldown (10 minutes default) to keep items valuable.
 * Deposits found items in nearest chest.
 */
object FinderExecutor : SkillExecutor {

    private val lastFindTime = mutableMapOf<UUID, Long>()
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
        val items = heldItems[pokemonId]

        // Deposit phase
        if (!items.isNullOrEmpty()) {
            val chestPos = InventoryHelper.findBestContainer(world, pokemonEntity.blockPos, 10, items) ?: return
            NavigationHelper.navigateTo(pokemonEntity, chestPos)
            if (NavigationHelper.isPokemonAtPosition(pokemonEntity, chestPos)) {
                InventoryHelper.insertItems(world, chestPos, items)
                heldItems.remove(pokemonId)
            }
            return
        }

        // Cooldown
        val baseCooldown = CobblebaseConfig.finderCooldownSeconds
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(baseCooldown, skillEntry.proficiency)
        val lastTime = lastFindTime[pokemonId] ?: now.also { lastFindTime[pokemonId] = now }
        if (now - lastTime < cooldownTicks) {
            if (now % 60 == 0L) {
                SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
            }
            return
        }

        // Generate loot
        lastFindTime[pokemonId] = now

        try {
            val lootTableKey = RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of("cobblebase", "finder_loot"))
            val lootTable = world.server.reloadableRegistries.getLootTable(lootTableKey)

            val lootParams = LootContextParameterSet.Builder(world)
                .add(LootContextParameters.ORIGIN, pokemonEntity.pos)
                .addOptional(LootContextParameters.THIS_ENTITY, pokemonEntity)
                .build(LootContextTypes.CHEST)

            val drops = lootTable.generateLoot(lootParams)

            if (drops.isNotEmpty()) {
                heldItems[pokemonId] = drops
                SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
                Cobblebase.LOGGER.info("[Finder] ${pokemonEntity.pokemon.species.name} found ${drops.size} items!")
            }
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Finder] Error generating loot: ${e.message}")
        }
    }
}

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
        // Finder cooldown only slightly reduced by proficiency (not halved like other skills)
        // Prof 1: 100%, Prof 2: 90%, Prof 3: 80%, Prof 4: 70%, Prof 5: 60%
        val baseCooldown = CobblebaseConfig.finderCooldownSeconds
        val cooldownTicks = if (CobblebaseConfig.devMode) 100L
            else (baseCooldown * 20L * (11 - skillEntry.proficiency) / 10)
        val lastTime = lastFindTime[pokemonId] ?: now.also { lastFindTime[pokemonId] = now }
        if (now - lastTime < cooldownTicks) {
            if (now % 60 == 0L) {
                SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
            }
            return
        }

        // Generate loot - proficiency affects quality, not just speed
        lastFindTime[pokemonId] = now

        try {
            // Pick a loot tier based on proficiency
            // Higher prof = much better items, less trash
            val lootTableName = pickLootTable(world, skillEntry.proficiency)
            val lootTableKey = RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(lootTableName))
            val lootTable = world.server.reloadableRegistries.getLootTable(lootTableKey)

            val lootParams = LootContextParameterSet.Builder(world)
                .add(LootContextParameters.ORIGIN, pokemonEntity.pos)
                .addOptional(LootContextParameters.THIS_ENTITY, pokemonEntity)
                .build(LootContextTypes.CHEST)

            val drops = lootTable.generateLoot(lootParams)

            if (drops.isNotEmpty()) {
                heldItems[pokemonId] = drops
                SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
                Cobblebase.LOGGER.info("[Finder] ${pokemonEntity.pokemon.species.name} (prof ${skillEntry.proficiency}) found: ${drops.map { "${it.name.string}x${it.count}" }}")
            }
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Finder] Error generating loot: ${e.message}")
        }
    }

    /**
     * Higher proficiency = much better loot tables selected more often.
     * Prof 1: mostly common (pokeballs, seeds)
     * Prof 5: mostly rare (evo stones, held items, exp candy)
     */
    private fun pickLootTable(world: World, proficiency: Int): String {
        val roll = world.random.nextInt(100)

        // Rare item chance scales steeply: Prof1=5%, Prof2=15%, Prof3=30%, Prof4=50%, Prof5=70%
        val rareChance = proficiency * proficiency * 3 - proficiency + 3 // 5, 15, 30, 47, 70 roughly

        return if (roll < rareChance) {
            // Rare pool
            when (world.random.nextInt(3)) {
                0 -> "cobblemon:sets/any_evo_stone"
                1 -> "cobblemon:sets/any_ancient_held_item"
                else -> "cobblemon:sets/any_exp_candy"
            }
        } else {
            // Common pool
            when (world.random.nextInt(4)) {
                0 -> "cobblemon:sets/any_common_pokeball"
                1 -> "cobblemon:sets/any_natural_heal_item"
                2 -> "cobblemon:sets/any_type_gem"
                else -> "cobblemon:sets/any_apricorn_seed"
            }
        }
    }
}

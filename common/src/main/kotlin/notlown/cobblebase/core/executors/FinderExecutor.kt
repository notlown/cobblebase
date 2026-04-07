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
import notlown.cobblebase.core.NavigationHelper
import notlown.cobblebase.core.LogManager
import notlown.cobblebase.core.effects.SkillEffects
import java.util.UUID

/**
 * Finder: Pokemon searches the area and finds random items/treasures.
 * Long cooldown (10 minutes default) to keep items valuable.
 * Deposits found items in nearest chest.
 *
 * Supports specialized finder types via the [finderType] parameter.
 * Each type uses its own loot tables (e.g., finder_evo_common, finder_ore_rare).
 * The generic "finder" type uses the original loot tables (finder_common, etc.).
 */
class FinderExecutor(private val finderType: String = "finder") : SkillExecutor {

    private val lastFindTime = mutableMapOf<UUID, Long>()
    private val heldItems = mutableMapOf<UUID, List<ItemStack>>()

    private val logTag = "[${finderType.replaceFirstChar { it.uppercase() }}]"

    // Loot table ids resolved through LootHelper (admin override > bundled default > vanilla)
    private val lootIdCommon = "cobblebase:${finderType}_common"
    private val lootIdUncommon = "cobblebase:${finderType}_uncommon"
    private val lootIdRare = "cobblebase:${finderType}_rare"
    private val lootIdUltraRare = "cobblebase:${finderType}_ultra_rare"

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

        // Throttle: Finders have minute-long cooldowns, no need to check every tick
        if (now % 20L != 0L) return

        val items = heldItems[pokemonId]

        // Drop items on ground — Gatherer will sort into chests
        if (!items.isNullOrEmpty()) {
            InventoryHelper.dropItems(world, pokemonEntity.blockPos, items, origin)
            heldItems.remove(pokemonId)
            return
        }

        // Cooldown
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(skill.cooldownSeconds, skillEntry.proficiency)
        val lastTime = lastFindTime[pokemonId] ?: now.also { lastFindTime[pokemonId] = now }
        if (now - lastTime < cooldownTicks) return

        // Generate loot - proficiency affects quality, not just speed
        lastFindTime[pokemonId] = now

        try {
            // Pick a loot tier based on proficiency
            val tier = pickLootTier(world, skillEntry.proficiency)
            val lootTableId = when (tier) {
                3 -> lootIdUltraRare
                2 -> lootIdRare
                1 -> lootIdUncommon
                else -> lootIdCommon
            }
            val drops = notlown.cobblebase.core.LootHelper.generateLoot(
                lootTableId, world, pokemonEntity.blockPos, pokemonEntity
            )

            if (drops.isNotEmpty()) {
                heldItems[pokemonId] = drops
                SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
                Cobblebase.log("$logTag ${pokemonEntity.pokemon.species.name} (prof ${skillEntry.proficiency}) found: ${drops.map { "${it.name.string}x${it.count}" }}")

                // Log to activity log
                val rarity = when (tier) {
                    3 -> LogManager.Rarity.ULTRA_RARE
                    2 -> LogManager.Rarity.RARE
                    1 -> LogManager.Rarity.UNCOMMON
                    else -> LogManager.Rarity.COMMON
                }
                for (drop in drops) {
                    LogManager.log(
                        origin, world.time,
                        pokemonEntity.pokemon.species.name,
                        "Found",
                        "${drop.name.string} x${drop.count}",
                        rarity
                    )
                }
            }
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("$logTag Error generating loot: ${e.message}")
        }
    }

    /**
     * 4 tiers: Common, Uncommon, Rare, Ultra Rare.
     * Higher proficiency shifts the distribution toward rarer tiers.
     *
     * Prof 1: Common 80%, Uncommon 15%, Rare 4%, Ultra Rare 1%
     * Prof 2: Common 65%, Uncommon 25%, Rare 8%, Ultra Rare 2%
     * Prof 3: Common 50%, Uncommon 30%, Rare 15%, Ultra Rare 5%
     * Prof 4: Common 30%, Uncommon 35%, Rare 25%, Ultra Rare 10%
     * Prof 5: Common 15%, Uncommon 30%, Rare 35%, Ultra Rare 20%
     */
    /**
     * Returns tier: 0=common, 1=uncommon, 2=rare, 3=ultra_rare
     */
    private fun pickLootTier(world: World, proficiency: Int): Int {
        val roll = world.random.nextInt(100)

        val ultraRare = when (proficiency) { 1->1; 2->2; 3->5; 4->10; else->20 }
        val rare = when (proficiency) { 1->4; 2->8; 3->15; 4->25; else->35 }
        val uncommon = when (proficiency) { 1->15; 2->25; 3->30; 4->35; else->30 }

        return when {
            roll < ultraRare -> 3
            roll < ultraRare + rare -> 2
            roll < ultraRare + rare + uncommon -> 1
            else -> 0
        }
    }

    companion object {
        /** The original generic Finder instance (backward compatible). */
        val Generic = FinderExecutor("finder")

        /** Alchemist -- Evolution items only. */
        val Evo = FinderExecutor("finder_evo")

        /** Pharmacist -- Healing items only. */
        val Hea = FinderExecutor("finder_hea")

        /** Architect -- Building materials only (no ores). */
        val Bui = FinderExecutor("finder_bui")

        /** Excavator -- Ores and raw materials only. */
        val Ore = FinderExecutor("finder_ore")

        /** Botanist -- Seeds and plantable items only. */
        val See = FinderExecutor("finder_see")

        /** Collector -- Pokeballs only. */
        val Bal = FinderExecutor("finder_bal")

        /** Scholar -- XP Candies only. */
        val Exp = FinderExecutor("finder_exp")

        /** Chef -- Food and cooking items. */
        val Food = FinderExecutor("finder_food")

        /** Trainer -- Stat vitamins and training items. */
        val Stat = FinderExecutor("finder_stat")

        /** Armorer -- Battle held items. */
        val Held = FinderExecutor("finder_held")

        /** Prospector -- Relics and treasure items. */
        val Treasure = FinderExecutor("finder_treasure")

        /** Smith -- Smithing templates and pottery. */
        val Smith = FinderExecutor("finder_smith")
    }
}

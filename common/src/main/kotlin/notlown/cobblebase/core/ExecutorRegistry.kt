package notlown.cobblebase.core

import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.executors.CauldronFillExecutor
import notlown.cobblebase.core.executors.FishingExecutor
import notlown.cobblebase.core.executors.FurnaceFuelExecutor
import notlown.cobblebase.core.executors.GathererExecutor
import notlown.cobblebase.core.executors.GenericLootExecutor
import notlown.cobblebase.core.executors.GuardExecutor
import notlown.cobblebase.core.executors.HarvesterExecutor
import notlown.cobblebase.core.executors.FinderExecutor
import notlown.cobblebase.core.executors.IrrigatorExecutor
import notlown.cobblebase.core.executors.MiningExecutor
import notlown.cobblebase.core.executors.HealerExecutor
import notlown.cobblebase.core.executors.MentorExecutor
import notlown.cobblebase.core.executors.RecruiterExecutor
import notlown.cobblebase.core.executors.ScoutExecutor

/**
 * Maps executor names (from Skill JSON) to executor implementations.
 */
object ExecutorRegistry {
    private val executors = mutableMapOf<String, SkillExecutor>()

    fun init() {
        // -- Gathering --
        register("harvester", HarvesterExecutor)
        register("fishing", FishingExecutor)
        register("mining", MiningExecutor)                 // Mining: cooldown-based loot (ores, fossils, gems)

        // -- Loot-table based (pickup, archeology, diving, honey) --
        register("pickup", GenericLootExecutor)
        register("archeology", GenericLootExecutor)
        register("diving", GenericLootExecutor)
        register("honey", GenericLootExecutor)

        // -- Generation --
        register("cauldron_fill", CauldronFillExecutor)
        register("furnace_fuel", FurnaceFuelExecutor)
        register("brew_fuel", FurnaceFuelExecutor)        // Brewing stands reuse furnace fuel logic

        // -- Combat --
        register("guard", GuardExecutor)

        // -- Specialized Finders --
        register("finder_evo", FinderExecutor.Evo)
        register("finder_hea", FinderExecutor.Hea)
        register("finder_bui", FinderExecutor.Bui)
        register("finder_ore", FinderExecutor.Ore)
        register("finder_see", FinderExecutor.See)
        register("finder_bal", FinderExecutor.Bal)
        register("finder_exp", FinderExecutor.Exp)

        // -- Support --
        register("healer", HealerExecutor)
        register("mentor", MentorExecutor)

        // -- Utility --
        register("irrigate", IrrigatorExecutor)
        register("extinguish", GenericLootExecutor)        // Placeholder: extinguisher removes fire
        register("gather_items", GathererExecutor)
        register("scout", ScoutExecutor)                     // Scout: discovers wild Pokemon, structures, biomes

        // -- Legendary / Fairy --
        register("recruiter", RecruiterExecutor)           // Spawns rare wild Pokemon nearby
        register("aura", GenericLootExecutor)              // Placeholder: passive aura buff
        register("passive_buff", GenericLootExecutor)      // Placeholder: lucky charm
        register("growth", GenericLootExecutor)            // Placeholder: growth aura

        Cobblebase.LOGGER.info("ExecutorRegistry: ${executors.size} executors registered")
    }

    fun register(name: String, executor: SkillExecutor) {
        executors[name] = executor
    }

    fun get(name: String): SkillExecutor? = executors[name]
}

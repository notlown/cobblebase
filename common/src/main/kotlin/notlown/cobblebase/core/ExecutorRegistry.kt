package notlown.cobblebase.core

import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.executors.CauldronFillExecutor
import notlown.cobblebase.core.executors.FishingExecutor
import notlown.cobblebase.core.executors.FurnaceFuelExecutor
import notlown.cobblebase.core.executors.GenericLootExecutor
import notlown.cobblebase.core.executors.GuardExecutor
import notlown.cobblebase.core.executors.HarvesterExecutor
import notlown.cobblebase.core.executors.HealerExecutor

/**
 * Maps executor names (from Skill JSON) to executor implementations.
 */
object ExecutorRegistry {
    private val executors = mutableMapOf<String, SkillExecutor>()

    fun init() {
        // -- Gathering --
        register("harvester", HarvesterExecutor)
        register("fishing", FishingExecutor)
        register("mining", HarvesterExecutor)            // Mining uses harvester logic (amethyst, tumblestone)

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

        // -- Support --
        register("healer", HealerExecutor)

        // -- Utility (stubs - reuse closest matching executor) --
        register("irrigate", HarvesterExecutor)           // Placeholder: irrigator waters farmland
        register("extinguish", GenericLootExecutor)        // Placeholder: extinguisher removes fire
        register("gather_items", GenericLootExecutor)      // Placeholder: item gatherer
        register("scout", GenericLootExecutor)             // Placeholder: scout creates maps

        // -- Legendary (stubs) --
        register("recruiter", GenericLootExecutor)         // Placeholder: spawns rare Pokemon
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

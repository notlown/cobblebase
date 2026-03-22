package notlown.cobblebase.core

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object Cobblebase {
    const val MODID = "cobblebase"
    val LOGGER: Logger = LogManager.getLogger(MODID)

    fun init() {
        LOGGER.info("Launching Cobblebase...")
        CobblebaseConfigBridge.init()
        SkillRegistry.init()
        ExecutorRegistry.init()
        SpeciesSkillRegistry.init()
        loadSpawnData()
    }

    private fun loadSpawnData() {
        try {
            val stream = Cobblebase::class.java.getResourceAsStream("/data/cobblebase/spawn_buckets.csv")
            if (stream != null) {
                val csv = stream.bufferedReader().readText()
                SpawnData.loadFromCsv(csv)
            } else {
                LOGGER.warn("[Cobblebase] spawn_buckets.csv not found in resources")
            }
        } catch (e: Exception) {
            LOGGER.error("[Cobblebase] Failed to load spawn data: ${e.message}")
        }
    }
}

package notlown.cobblebase.core

import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object Cobblebase {
    const val MODID = "cobblebase"
    val LOGGER: Logger = LogManager.getLogger(MODID)

    fun init() {
        LOGGER.info("Launching Cobblebase...")
        AutoConfig.register(CobblebaseClothConfig::class.java, ::GsonConfigSerializer)
        SkillRegistry.init()
        ExecutorRegistry.init()
        SpeciesSkillRegistry.init()
        loadSpawnData()
    }

    private fun loadSpawnData() {
        try {
            val stream = Cobblebase::class.java.getResourceAsStream("/data/cobblebase/spawn_buckets.csv")
            if (stream != null) {
                SpawnData.loadFromCsv(stream.bufferedReader().readText())
            } else {
                LOGGER.warn("[Cobblebase] spawn_buckets.csv not found in resources")
            }
        } catch (e: Exception) {
            LOGGER.error("[Cobblebase] Failed to load spawn data: ${e.message}")
        }
    }
}

package notlown.cobblebase.core

import com.google.gson.JsonParser
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object Cobblebase {
    const val MODID = "cobblebase"
    val LOGGER: Logger = LogManager.getLogger(MODID)
    val registeredSounds = mutableSetOf<Identifier>()

    /** Logs info only when console logging is enabled in config. Use for runtime job/tick logs. */
    fun log(message: String) {
        if (CobblebaseConfig.enableConsoleLogging) LOGGER.info(message)
    }

    fun init() {
        LOGGER.info("Launching Cobblebase...")
        AutoConfig.register(CobblebaseClothConfig::class.java, ::GsonConfigSerializer)
        registerSounds()
        SkillRegistry.init()
        CobblebaseLootRegistry.init()
        ExecutorRegistry.init()
        SpeciesSkillRegistry.init()
        loadSpawnData()
    }

    private fun registerSounds() {
        try {
            val stream = Cobblebase::class.java.getResourceAsStream("/assets/cobblebase/sounds.json")
            if (stream != null) {
                val json = JsonParser.parseReader(stream.bufferedReader()).asJsonObject
                var count = 0
                for (key in json.keySet()) {
                    try {
                        val id = Identifier.of(MODID, key)
                        Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id))
                        registeredSounds.add(id)
                        count++
                    } catch (e: Exception) {
                        // Skip entries with invalid chars (spaces, apostrophes, etc.)
                    }
                }
                LOGGER.info("[Cobblebase] Registered $count sound events")
            } else {
                LOGGER.warn("[Cobblebase] sounds.json not found")
            }
        } catch (e: Exception) {
            LOGGER.error("[Cobblebase] Failed to register sounds: ${e.message}")
        }
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

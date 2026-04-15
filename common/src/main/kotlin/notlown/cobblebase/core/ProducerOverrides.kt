package notlown.cobblebase.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.WorldSavePath
import notlown.cobblebase.core.executors.ProducerExecutor
import java.io.File

/**
 * Stores per-species producer item overrides.
 * When an override exists, ProducerExecutor uses it instead of the hardcoded map.
 */
object ProducerOverrides {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val overrides = mutableMapOf<String, ProducerExecutor.ProduceEntry>()

    fun getOverride(species: String): ProducerExecutor.ProduceEntry? = overrides[species.lowercase()]

    fun hasOverride(species: String): Boolean = overrides.containsKey(species.lowercase())

    fun setOverride(species: String, entry: ProducerExecutor.ProduceEntry, world: ServerWorld) {
        overrides[species.lowercase()] = entry
        save(world)
        Cobblebase.LOGGER.info("[Cobblebase] Producer override set for '$species': ${entry.itemId} x${entry.count}")
    }

    fun removeOverride(species: String, world: ServerWorld) {
        overrides.remove(species.lowercase())
        save(world)
        Cobblebase.LOGGER.info("[Cobblebase] Producer override removed for '$species'")
    }

    private fun getSaveFile(world: ServerWorld): File {
        val saveDir = world.server.getSavePath(WorldSavePath.ROOT).toFile()
        return File(saveDir, "cobblebase_producer_overrides.json")
    }

    fun save(world: ServerWorld) {
        try {
            val file = getSaveFile(world)
            val data = overrides.mapValues { (_, entry) ->
                val map = mutableMapOf<String, Any>(
                    "itemId" to entry.itemId,
                    "count" to entry.count,
                    "displayName" to entry.displayName
                )
                if (entry.cooldownSeconds != null) map["cooldownSeconds"] = entry.cooldownSeconds
                map
            }
            file.writeText(gson.toJson(data))
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to save producer overrides: ${e.message}")
        }
    }

    fun load(world: ServerWorld) {
        try {
            val file = getSaveFile(world)
            if (!file.exists()) return
            val type = object : TypeToken<Map<String, Map<String, Any>>>() {}.type
            val data: Map<String, Map<String, Any>> = gson.fromJson(file.readText(), type) ?: return
            overrides.clear()
            for ((species, fields) in data) {
                val itemId = fields["itemId"] as? String ?: continue
                val count = (fields["count"] as? Double)?.toInt() ?: 1
                val displayName = fields["displayName"] as? String ?: itemId
                val cooldown = (fields["cooldownSeconds"] as? Double)?.toLong()
                overrides[species.lowercase()] = ProducerExecutor.ProduceEntry(itemId, count, displayName, cooldown)
            }
            Cobblebase.LOGGER.info("[Cobblebase] Loaded ${overrides.size} producer overrides")
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to load producer overrides: ${e.message}")
        }
    }
}

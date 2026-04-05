package notlown.cobblebase.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.WorldSavePath
import net.minecraft.util.math.BlockPos
import java.io.File

/**
 * Stores per-Pasture-Block settings, keyed by BlockPos (as Long).
 * Each player can configure their own Pasture Block's search radius
 * within the limits set by the server admin in JobConfigOverrides.
 *
 * Persists to cobblebase_pasture_settings.json in the world save directory.
 */
object PastureSettings {

    data class Settings(
        val searchRadius: Int = 10
    )

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val settings = mutableMapOf<Long, Settings>()

    fun get(pos: BlockPos): Settings {
        return settings[pos.asLong()] ?: Settings()
    }

    fun set(pos: BlockPos, value: Settings) {
        settings[pos.asLong()] = value
    }

    /**
     * Returns the search radius for a pasture, clamped to the admin-configured
     * global radius range. Uses the widest range across all skills.
     */
    fun getSearchRadius(pos: BlockPos): Int {
        val stored = get(pos).searchRadius
        val range = getGlobalRadiusRange()
        return stored.coerceIn(range.first, range.second)
    }

    /**
     * Returns the global min/max radius range across all skills from admin config.
     * Falls back to 3..20 if no overrides exist.
     */
    fun getGlobalRadiusRange(): Pair<Int, Int> {
        val allOverrides = JobConfigOverrides.getAllOverrides()
        if (allOverrides.isEmpty()) return Pair(3, 20)

        var globalMin = Int.MAX_VALUE
        var globalMax = Int.MIN_VALUE
        for ((_, override) in allOverrides) {
            val min = override.radiusMin ?: 3
            val max = override.radiusMax ?: 20
            if (min < globalMin) globalMin = min
            if (max > globalMax) globalMax = max
        }

        if (globalMin == Int.MAX_VALUE) globalMin = 3
        if (globalMax == Int.MIN_VALUE) globalMax = 20
        return Pair(globalMin, globalMax)
    }

    private fun getSaveFile(world: ServerWorld): File {
        val saveDir = world.server.getSavePath(WorldSavePath.ROOT).toFile()
        return File(saveDir, "cobblebase_pasture_settings.json")
    }

    fun save(world: ServerWorld) {
        try {
            val file = getSaveFile(world)
            val data = settings.mapKeys { (key, _) -> key.toString() }
                .mapValues { (_, value) -> mapOf("searchRadius" to value.searchRadius) }
            file.writeText(gson.toJson(data))
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to save pasture settings: ${e.message}")
        }
    }

    fun load(world: ServerWorld) {
        try {
            val file = getSaveFile(world)
            if (!file.exists()) return
            val type = object : TypeToken<Map<String, Map<String, Any?>>>() {}.type
            val data: Map<String, Map<String, Any?>> = gson.fromJson(file.readText(), type) ?: return
            settings.clear()
            for ((posKey, fields) in data) {
                val posLong = posKey.toLongOrNull() ?: continue
                val radius = (fields["searchRadius"] as? Double)?.toInt() ?: 10
                settings[posLong] = Settings(searchRadius = radius)
            }
            Cobblebase.LOGGER.info("[Cobblebase] Loaded ${settings.size} pasture settings")
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to load pasture settings: ${e.message}")
        }
    }
}

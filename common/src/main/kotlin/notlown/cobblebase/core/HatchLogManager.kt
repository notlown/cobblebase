package notlown.cobblebase.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.WorldSavePath
import net.minecraft.util.math.BlockPos
import java.io.File

/**
 * Persistent log of every Pokemon hatched by an [EggHatcherExecutor] in this world.
 *
 * Drives the Hatchery GUI tab (recent hatches + stats). Each entry records the world tick,
 * real-world timestamp, hatched species, the hatcher Pokemon that did the work, and the
 * pasture origin. Capped at [MAX_ENTRIES] to keep the JSON file bounded.
 */
object HatchLogManager {

    private const val MAX_ENTRIES = 1000

    data class HatchEntry(
        val realTimestamp: Long,
        val worldTime: Long,
        val hatchedSpecies: String,
        val hatcherSpecies: String,
        val hatcherDisplayName: String,
        val pastureX: Int,
        val pastureY: Int,
        val pastureZ: Int,
        val proficiency: Int
    )

    private val entries = mutableListOf<HatchEntry>()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun append(
        worldTime: Long,
        hatchedSpecies: String,
        hatcherSpecies: String,
        hatcherDisplay: String,
        pasture: BlockPos,
        proficiency: Int
    ) {
        synchronized(entries) {
            entries.add(0, HatchEntry(
                realTimestamp = System.currentTimeMillis(),
                worldTime = worldTime,
                hatchedSpecies = hatchedSpecies,
                hatcherSpecies = hatcherSpecies,
                hatcherDisplayName = hatcherDisplay,
                pastureX = pasture.x, pastureY = pasture.y, pastureZ = pasture.z,
                proficiency = proficiency
            ))
            while (entries.size > MAX_ENTRIES) entries.removeAt(entries.size - 1)
        }
    }

    fun getAll(): List<HatchEntry> = synchronized(entries) { entries.toList() }

    /** Returns simple stats for the Hatchery tab. */
    data class Stats(
        val total: Int,
        val totalThisSession: Int,
        val uniqueSpecies: Int,
        val topHatchers: List<Pair<String, Int>>  // hatcher species → count, top 5
    )

    fun stats(sessionStartTime: Long = sessionStart): Stats {
        synchronized(entries) {
            val total = entries.size
            val thisSession = entries.count { it.realTimestamp >= sessionStartTime }
            val uniqueSpecies = entries.map { it.hatchedSpecies }.toSet().size
            val topHatchers = entries.groupingBy { it.hatcherSpecies }.eachCount()
                .entries.sortedByDescending { it.value }.take(5)
                .map { it.key to it.value }
            return Stats(total, thisSession, uniqueSpecies, topHatchers)
        }
    }

    private val sessionStart = System.currentTimeMillis()

    // -------- Persistence --------

    private fun saveFile(world: ServerWorld): File {
        val saveDir = world.server.getSavePath(WorldSavePath.ROOT).toFile()
        return File(saveDir, "cobblebase_hatchery_log.json")
    }

    fun save(world: ServerWorld) {
        try {
            saveFile(world).writeText(gson.toJson(synchronized(entries) { entries.toList() }))
        } catch (e: Exception) {
            Cobblebase.LOGGER.warn("[HatchLog] save failed: ${e.message}")
        }
    }

    fun load(world: ServerWorld) {
        synchronized(entries) {
            entries.clear()
            try {
                val file = saveFile(world)
                if (!file.exists()) return
                val type = object : TypeToken<List<HatchEntry>>() {}.type
                val data: List<HatchEntry> = gson.fromJson(file.readText(), type) ?: return
                entries.addAll(data)
                Cobblebase.LOGGER.debug("[HatchLog] loaded ${entries.size} hatch entries")
            } catch (e: Exception) {
                Cobblebase.LOGGER.warn("[HatchLog] load failed: ${e.message}")
            }
        }
    }
}

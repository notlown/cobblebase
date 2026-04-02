package notlown.cobblebase.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.WorldSavePath
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.io.File

/**
 * Manages activity logs for each Pasture Block.
 * Stores recent events (finds, harvests, guard actions, etc.) per BlockPos.
 * Max 100 entries per pasture, auto-cleanup of entries older than 24 real-world hours.
 */
object LogManager {

    enum class Rarity(val color: Int, val displayName: String) {
        COMMON(0xAAAAAA, "Common"),
        UNCOMMON(0x55FF55, "Uncommon"),
        RARE(0x5555FF, "Rare"),
        ULTRA_RARE(0xFFAA00, "Ultra Rare");
    }

    data class LogEntry(
        val timestamp: Long,       // System.currentTimeMillis()
        val pokemonName: String,
        val action: String,
        val itemName: String,
        val rarity: Rarity,
        val worldTime: Long        // world.time for display
    )

    /**
     * Serializable wrapper for JSON persistence.
     */
    private data class LogData(
        val timestamp: Long,
        val pokemonName: String,
        val action: String,
        val itemName: String,
        val rarity: String,
        val worldTime: Long
    )

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val logs = mutableMapOf<String, MutableList<LogEntry>>()
    private const val MAX_ENTRIES_PER_PASTURE = 100
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L // 24 hours

    /**
     * Maps pasture UUID (from Cobblemon) to BlockPos.
     * Populated server-side when pasture blocks tick.
     */
    private val pasturePositions = mutableMapOf<java.util.UUID, BlockPos>()

    /**
     * Client-side cache of log entries, populated via LogSyncS2CPacket.
     */
    private var clientLogCache: List<LogEntry> = emptyList()

    fun registerPasturePosition(pastureId: java.util.UUID, pos: BlockPos) {
        pasturePositions[pastureId] = pos
    }

    fun getPasturePosition(pastureId: java.util.UUID): BlockPos? = pasturePositions[pastureId]

    fun setClientLogs(entries: List<LogEntry>) {
        clientLogCache = entries
    }

    fun getClientLogs(): List<LogEntry> = clientLogCache

    fun getClientLogs(minRarity: Rarity): List<LogEntry> =
        clientLogCache.filter { it.rarity.ordinal >= minRarity.ordinal }

    private fun posKey(pos: BlockPos): String = "${pos.x},${pos.y},${pos.z}"

    /**
     * Log an event for a given pasture origin.
     * Called by executors when they produce or find items.
     */
    fun log(origin: BlockPos, entry: LogEntry) {
        val key = posKey(origin)
        val list = logs.getOrPut(key) { mutableListOf() }
        list.add(0, entry) // newest first
        // Trim to max
        while (list.size > MAX_ENTRIES_PER_PASTURE) {
            list.removeAt(list.size - 1)
        }
    }

    /**
     * Convenience: log with auto-generated timestamp.
     */
    fun log(
        origin: BlockPos,
        worldTime: Long,
        pokemonName: String,
        action: String,
        itemName: String,
        rarity: Rarity
    ) {
        log(origin, LogEntry(
            timestamp = System.currentTimeMillis(),
            pokemonName = pokemonName,
            action = action,
            itemName = itemName,
            rarity = rarity,
            worldTime = worldTime
        ))
    }

    /**
     * Get all log entries for a pasture, newest first.
     */
    fun getEntries(origin: BlockPos): List<LogEntry> {
        return logs[posKey(origin)] ?: emptyList()
    }

    /**
     * Get filtered entries by minimum rarity.
     */
    fun getEntries(origin: BlockPos, minRarity: Rarity): List<LogEntry> {
        return getEntries(origin).filter { it.rarity.ordinal >= minRarity.ordinal }
    }

    /**
     * Remove entries older than 24 real-world hours.
     */
    fun cleanup() {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        for ((_, list) in logs) {
            list.removeAll { it.timestamp < cutoff }
        }
        // Remove empty keys
        logs.entries.removeAll { it.value.isEmpty() }
    }

    // -- Persistence --

    private fun getSaveFile(world: ServerWorld): File {
        val saveDir = world.server.getSavePath(WorldSavePath.ROOT).toFile()
        return File(saveDir, "cobblebase_logs.json")
    }

    fun save(world: World) {
        if (world !is ServerWorld) return
        try {
            cleanup()
            val file = getSaveFile(world)
            val data = mutableMapOf<String, List<LogData>>()
            for ((key, entries) in logs) {
                data[key] = entries.map { e ->
                    LogData(e.timestamp, e.pokemonName, e.action, e.itemName, e.rarity.name, e.worldTime)
                }
            }
            file.writeText(gson.toJson(data))
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to save logs: ${e.message}")
        }
    }

    fun load(world: World) {
        if (world !is ServerWorld) return
        try {
            val file = getSaveFile(world)
            if (!file.exists()) return
            val type = object : TypeToken<Map<String, List<LogData>>>() {}.type
            val data: Map<String, List<LogData>> = gson.fromJson(file.readText(), type)
            logs.clear()
            for ((key, entries) in data) {
                logs[key] = entries.mapNotNull { d ->
                    try {
                        LogEntry(d.timestamp, d.pokemonName, d.action, d.itemName, Rarity.valueOf(d.rarity), d.worldTime)
                    } catch (_: Exception) { null }
                }.toMutableList()
            }
            val totalEntries = logs.values.sumOf { it.size }
            Cobblebase.LOGGER.info("[Cobblebase] Loaded $totalEntries log entries for ${logs.size} pastures")
            cleanup()
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to load logs: ${e.message}")
        }
    }
}

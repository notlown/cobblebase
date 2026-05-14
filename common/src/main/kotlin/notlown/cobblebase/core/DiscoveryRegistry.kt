package notlown.cobblebase.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.WorldSavePath
import java.io.File

/**
 * Tracks discoveries made by Scout Pokemon: structures, biomes, and wild Pokemon sightings.
 * Permanent discoveries (structures + biomes) persist forever.
 * Recent pokemon sightings are cleared after 30 minutes.
 */
object DiscoveryRegistry {

    enum class DiscoveryType(val displayName: String, val color: Int) {
        WILD_POKEMON("Pokemon", 0xFFFF55),
        STRUCTURE("Structure", 0x5555FF),
        BIOME("Biome", 0x55FF55);
    }

    data class Discovery(
        val type: DiscoveryType,
        val name: String,
        val x: Int,
        val y: Int,
        val z: Int,
        val discoveredBy: String,
        val timestamp: Long,
        val rarity: LogManager.Rarity
    )

    /**
     * Serializable wrapper for JSON persistence.
     */
    private data class DiscoveryData(
        val type: String,
        val name: String,
        val x: Int,
        val y: Int,
        val z: Int,
        val discoveredBy: String,
        val timestamp: Long,
        val rarity: String
    )

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // Permanent discoveries (structures + biomes) — never removed
    private val permanentDiscoveries = mutableListOf<Discovery>()

    // Recent pokemon sightings — cleared after 30 min
    private val recentSightings = mutableListOf<Discovery>()

    // Duplicate check: position hash to prevent re-reporting same thing
    private val reportedPositions = mutableSetOf<Long>()

    // Client-side cache, populated via DiscoverySyncS2CPacket
    private var clientCache: List<Discovery> = emptyList()

    private const val SIGHTING_MAX_AGE_MS = 30 * 60 * 1000L // 30 minutes

    fun setClientDiscoveries(discoveries: List<Discovery>) {
        clientCache = discoveries
    }

    fun getClientDiscoveries(): List<Discovery> = clientCache

    /**
     * Add a discovery. Returns false if duplicate (same chunk + same name).
     */
    fun addDiscovery(discovery: Discovery): Boolean {
        val hash = positionHash(discovery.x, discovery.z, discovery.name)
        if (hash in reportedPositions) return false
        reportedPositions.add(hash)

        if (discovery.type == DiscoveryType.WILD_POKEMON) {
            recentSightings.add(0, discovery)
        } else {
            permanentDiscoveries.add(0, discovery)
        }
        return true
    }

    /**
     * Get permanent discoveries only (structures + biomes) — for the Discovery tab.
     */
    fun getDiscoveries(): List<Discovery> = permanentDiscoveries.toList()

    /**
     * Get all discoveries for syncing to client: permanent + recent sightings combined.
     */
    fun getAllForSync(): List<Discovery> {
        cleanupOldSightings()
        return permanentDiscoveries + recentSightings
    }

    /**
     * Get recent wild Pokemon sightings.
     */
    fun getRecentSightings(): List<Discovery> = recentSightings.toList()

    /**
     * Check if a position+name combo was already discovered.
     */
    fun isAlreadyDiscovered(x: Int, z: Int, name: String): Boolean {
        return positionHash(x, z, name) in reportedPositions
    }

    /**
     * Remove pokemon sightings older than 30 minutes.
     * Also removes their position hashes so they can be re-reported.
     */
    fun cleanupOldSightings() {
        val cutoff = System.currentTimeMillis() - SIGHTING_MAX_AGE_MS
        val removed = recentSightings.filter { it.timestamp < cutoff }
        recentSightings.removeAll { it.timestamp < cutoff }
        // Remove position hashes for expired sightings so they can be spotted again
        for (r in removed) {
            reportedPositions.remove(positionHash(r.x, r.z, r.name))
        }
    }

    /**
     * Chunk-based position hash combined with name for dedup.
     */
    private fun positionHash(x: Int, z: Int, name: String): Long {
        val chunkHash = (x / 16).toLong() * 100000 + (z / 16).toLong()
        return chunkHash * 31 + name.lowercase().hashCode()
    }

    // -- Persistence --

    private fun getSaveFile(world: ServerWorld): File {
        val saveDir = world.server.getSavePath(WorldSavePath.ROOT).toFile()
        return File(saveDir, "cobblebase_discoveries.json")
    }

    fun save(world: ServerWorld) {
        try {
            cleanupOldSightings()
            val file = getSaveFile(world)
            val allDiscoveries = permanentDiscoveries + recentSightings
            val data = allDiscoveries.map { d ->
                DiscoveryData(d.type.name, d.name, d.x, d.y, d.z, d.discoveredBy, d.timestamp, d.rarity.name)
            }
            file.writeText(gson.toJson(data))
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to save discoveries: ${e.message}")
        }
    }

    fun load(world: ServerWorld) {
        try {
            val file = getSaveFile(world)
            if (!file.exists()) return
            val type = object : TypeToken<List<DiscoveryData>>() {}.type
            val data: List<DiscoveryData> = gson.fromJson(file.readText(), type)
            permanentDiscoveries.clear()
            recentSightings.clear()
            reportedPositions.clear()
            for (d in data) {
                try {
                    val discovery = Discovery(
                        type = DiscoveryType.valueOf(d.type),
                        name = d.name,
                        x = d.x, y = d.y, z = d.z,
                        discoveredBy = d.discoveredBy,
                        timestamp = d.timestamp,
                        rarity = LogManager.Rarity.valueOf(d.rarity)
                    )
                    val hash = positionHash(d.x, d.z, d.name)
                    reportedPositions.add(hash)
                    if (discovery.type == DiscoveryType.WILD_POKEMON) {
                        recentSightings.add(discovery)
                    } else {
                        permanentDiscoveries.add(discovery)
                    }
                } catch (_: Exception) {}
            }
            val total = permanentDiscoveries.size + recentSightings.size
            Cobblebase.LOGGER.debug("[Cobblebase] Loaded $total discoveries (${permanentDiscoveries.size} permanent, ${recentSightings.size} sightings)")
            cleanupOldSightings()
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to load discoveries: ${e.message}")
        }
    }
}

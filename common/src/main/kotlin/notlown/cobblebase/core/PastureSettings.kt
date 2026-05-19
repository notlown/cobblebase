package notlown.cobblebase.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.WorldSavePath
import net.minecraft.util.math.BlockPos
import java.io.File

/**
 * Per-pasture overrides for `pastureRange` and `belowPastureReach`. Each pasture
 * owner may pick a value within the admin-configured caps; when no override is
 * set, the pasture falls back to the admin maximum (= current behavior).
 *
 * Saved per-world in `cobblebase_pasture_settings.json`.
 */
object PastureSettings {

    /**
     * Access mode for non-owner players.
     *  - OWNER_ONLY: nobody else can open the pasture UI
     *  - VIEW_ONLY:  others see skills + state but can't change anything
     *  - PUBLIC:     others can also assign skills and edit settings
     *
     * Default = OWNER_ONLY (safer than the legacy "anyone can edit" behavior;
     * owners explicitly opt-in to sharing).
     */
    enum class AccessMode { OWNER_ONLY, VIEW_ONLY, PUBLIC }

    /** Per-pasture overrides. A missing range/belowReach = "use admin cap." */
    data class Entry(
        var range: Int? = null,
        var belowReach: Int? = null,
        var access: AccessMode = AccessMode.OWNER_ONLY
    )

    /** Persisted form. Map key is BlockPos serialized as "x,y,z". */
    private data class FileShape(
        var pastures: MutableMap<String, Entry> = mutableMapOf()
    )

    private var data = FileShape()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /** Server-side per-pasture lookup. Falls back to null when nothing is stored. */
    fun get(pasturePos: BlockPos): Entry? = data.pastures[keyOf(pasturePos)]

    /**
     * Write or clear a per-pasture override. Passing `null` for range/belowReach
     * clears that single override; the entry stays for the access field. The
     * entry is fully removed only when everything is at defaults.
     * Caller is responsible for clamping to admin caps before calling.
     */
    fun set(pasturePos: BlockPos, range: Int?, belowReach: Int?, access: AccessMode) {
        val key = keyOf(pasturePos)
        val isDefault = range == null && belowReach == null && access == AccessMode.OWNER_ONLY
        if (isDefault) {
            data.pastures.remove(key)
            return
        }
        val existing = data.pastures.getOrPut(key) { Entry() }
        existing.range = range
        existing.belowReach = belowReach
        existing.access = access
    }

    /** All current overrides. Used by server → client sync on login. */
    fun snapshot(): Map<BlockPos, Entry> {
        return data.pastures.mapNotNull { (k, v) ->
            val p = parseKey(k) ?: return@mapNotNull null
            p to Entry(v.range, v.belowReach, v.access)
        }.toMap()
    }

    private fun keyOf(pos: BlockPos): String = "${pos.x},${pos.y},${pos.z}"
    private fun parseKey(key: String): BlockPos? {
        val parts = key.split(",")
        if (parts.size != 3) return null
        val x = parts[0].toIntOrNull() ?: return null
        val y = parts[1].toIntOrNull() ?: return null
        val z = parts[2].toIntOrNull() ?: return null
        return BlockPos(x, y, z)
    }

    private fun getSaveFile(world: ServerWorld): File {
        val saveDir = world.server.getSavePath(WorldSavePath.ROOT).toFile()
        return File(saveDir, "cobblebase_pasture_settings.json")
    }

    fun save(world: ServerWorld) {
        try {
            getSaveFile(world).writeText(gson.toJson(data))
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[PastureSettings] save failed: ${e.message}")
        }
    }

    fun load(world: ServerWorld) {
        try {
            val file = getSaveFile(world)
            if (!file.exists()) return
            val loaded = gson.fromJson(file.readText(), FileShape::class.java)
            if (loaded != null) data = loaded
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[PastureSettings] load failed: ${e.message}")
        }
    }

    /**
     * Apply server-side clamping to every stored entry so any value above the
     * (lowered) admin caps snaps back. Called after the admin changes the caps.
     */
    fun clampToAdminCaps(rangeMin: Int, rangeMax: Int, belowMin: Int, belowMax: Int) {
        for (entry in data.pastures.values) {
            entry.range = entry.range?.coerceIn(rangeMin, rangeMax)
            entry.belowReach = entry.belowReach?.coerceIn(belowMin, belowMax)
        }
    }
}

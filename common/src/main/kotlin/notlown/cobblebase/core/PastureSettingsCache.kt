package notlown.cobblebase.core

import net.minecraft.util.math.BlockPos

/**
 * Client-side mirror of [PastureSettings]. Updated via PastureSettingsSyncS2CPacket.
 * Used by the Base-Settings modal to seed slider values, by RadiusRenderer to
 * draw the per-pasture wireframe bounds, and by the Pasture UI to gate access.
 */
object PastureSettingsCache {

    /** pos → IntArray(range, belowReach, accessOrdinal). -1 = unset for range/below. */
    private val entries = mutableMapOf<BlockPos, IntArray>()

    fun replaceAll(newEntries: Map<BlockPos, IntArray>) {
        entries.clear()
        for ((k, v) in newEntries) entries[k.toImmutable()] = v
    }

    /** Per-pasture override or null. */
    fun get(pos: BlockPos): IntArray? = entries[pos]

    /** Per-pasture range override, or null if unset. */
    fun getRange(pos: BlockPos): Int? = entries[pos]?.let { if (it[0] < 0) null else it[0] }

    /** Per-pasture below-reach override, or null if unset. */
    fun getBelowReach(pos: BlockPos): Int? = entries[pos]?.let { if (it[1] < 0) null else it[1] }

    /** Access mode ordinal — defaults to OWNER_ONLY (0) when nothing stored. */
    fun getAccessOrdinal(pos: BlockPos): Int = entries[pos]?.getOrElse(2) { 0 } ?: 0
}

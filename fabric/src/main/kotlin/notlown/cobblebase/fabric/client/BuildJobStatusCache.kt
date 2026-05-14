package notlown.cobblebase.fabric.client

import net.minecraft.util.math.BlockPos
import notlown.cobblebase.core.net.BuildJobStatusSyncS2CPacket

/**
 * Client-side cache of the most recent build-job status received per pasture.
 * Populated by the S2C handler; the Builder GUI reads it to render the progress panel.
 */
object BuildJobStatusCache {

    @Volatile
    private var latest: BuildJobStatusSyncS2CPacket? = null

    fun update(packet: BuildJobStatusSyncS2CPacket) {
        latest = packet
    }

    /** Returns the cached status for [pasturePos] only if it matches and is recent. */
    fun get(pasturePos: BlockPos): BuildJobStatusSyncS2CPacket? {
        val l = latest ?: return null
        if (l.pasturePos != pasturePos) return null
        return l
    }

    fun clear() {
        latest = null
    }
}

package notlown.cobblebase.fabric.client

import notlown.cobblebase.core.net.HatchLogSyncS2CPacket

/** Client-side cache of the most recent hatch log + stats received from the server. */
object HatchLogCache {

    @Volatile
    private var latest: HatchLogSyncS2CPacket? = null

    fun update(packet: HatchLogSyncS2CPacket) {
        latest = packet
    }

    fun get(): HatchLogSyncS2CPacket? = latest

    fun clear() {
        latest = null
    }
}

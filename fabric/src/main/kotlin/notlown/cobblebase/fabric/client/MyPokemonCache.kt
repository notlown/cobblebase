package notlown.cobblebase.fabric.client

import notlown.cobblebase.core.net.MyPokemonSyncS2CPacket

/** Client-side cache of the player's owned-Pokemon snapshot. Updated by S2C packet. */
object MyPokemonCache {

    @Volatile
    private var latest: MyPokemonSyncS2CPacket? = null

    fun update(packet: MyPokemonSyncS2CPacket) {
        latest = packet
    }

    fun get(): MyPokemonSyncS2CPacket? = latest
    fun boxes(): List<MyPokemonSyncS2CPacket.Box> = latest?.boxes ?: emptyList()
}

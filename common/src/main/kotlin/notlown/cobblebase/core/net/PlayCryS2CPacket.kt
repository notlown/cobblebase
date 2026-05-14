package notlown.cobblebase.core.net

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import notlown.cobblebase.core.Cobblebase

/**
 * Server -> Client: requests a Pokemon cry sound. The receiver checks the local
 * `cryEnabled` / `cryVolume` cloth-config and plays the sound if enabled. Server-side
 * `world.playSound(null, ...)` would ignore the player's local config — the entire reason
 * mute/volume controls didn't actually work before. The 60-second per-Pokemon cooldown
 * still lives server-side so this packet is rate-limited at the source.
 */
data class PlayCryS2CPacket(
    val speciesName: String,
    val x: Double,
    val y: Double,
    val z: Double
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<PlayCryS2CPacket>(Identifier.of(Cobblebase.MODID, "play_cry"))

        val CODEC: PacketCodec<PacketByteBuf, PlayCryS2CPacket> = object : PacketCodec<PacketByteBuf, PlayCryS2CPacket> {
            override fun decode(buf: PacketByteBuf): PlayCryS2CPacket =
                PlayCryS2CPacket(buf.readString(), buf.readDouble(), buf.readDouble(), buf.readDouble())

            override fun encode(buf: PacketByteBuf, packet: PlayCryS2CPacket) {
                buf.writeString(packet.speciesName)
                buf.writeDouble(packet.x)
                buf.writeDouble(packet.y)
                buf.writeDouble(packet.z)
            }
        }
    }

    override fun getId(): CustomPayload.Id<PlayCryS2CPacket> = ID
}

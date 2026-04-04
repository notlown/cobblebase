package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

data class VersionHandshakeC2SPacket(
    val version: String
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<VersionHandshakeC2SPacket>(Identifier.of(Cobblebase.MODID, "version_handshake"))

        val CODEC: PacketCodec<PacketByteBuf, VersionHandshakeC2SPacket> = object : PacketCodec<PacketByteBuf, VersionHandshakeC2SPacket> {
            override fun decode(buf: PacketByteBuf): VersionHandshakeC2SPacket {
                return VersionHandshakeC2SPacket(buf.readString())
            }
            override fun encode(buf: PacketByteBuf, packet: VersionHandshakeC2SPacket) {
                buf.writeString(packet.version)
            }
        }
    }

    override fun getId(): CustomPayload.Id<VersionHandshakeC2SPacket> = ID
}

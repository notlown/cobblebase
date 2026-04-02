package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Client -> Server: request discoveries for the Discovery tab.
 */
class DiscoveryRequestC2SPacket : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<DiscoveryRequestC2SPacket>(Identifier.of(Cobblebase.MODID, "discovery_request"))

        val CODEC: PacketCodec<PacketByteBuf, DiscoveryRequestC2SPacket> = object : PacketCodec<PacketByteBuf, DiscoveryRequestC2SPacket> {
            override fun decode(buf: PacketByteBuf): DiscoveryRequestC2SPacket {
                return DiscoveryRequestC2SPacket()
            }
            override fun encode(buf: PacketByteBuf, packet: DiscoveryRequestC2SPacket) {
                // No data needed
            }
        }
    }

    override fun getId(): CustomPayload.Id<DiscoveryRequestC2SPacket> = ID
}

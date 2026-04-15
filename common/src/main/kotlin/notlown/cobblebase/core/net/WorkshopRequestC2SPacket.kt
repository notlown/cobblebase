package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Client -> Server: request recipe list and workshop state for the Workshop tab.
 */
class WorkshopRequestC2SPacket : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<WorkshopRequestC2SPacket>(Identifier.of(Cobblebase.MODID, "workshop_request"))

        val CODEC: PacketCodec<PacketByteBuf, WorkshopRequestC2SPacket> = object : PacketCodec<PacketByteBuf, WorkshopRequestC2SPacket> {
            override fun decode(buf: PacketByteBuf): WorkshopRequestC2SPacket {
                return WorkshopRequestC2SPacket()
            }

            override fun encode(buf: PacketByteBuf, packet: WorkshopRequestC2SPacket) {
                // No data needed
            }
        }
    }

    override fun getId(): CustomPayload.Id<WorkshopRequestC2SPacket> = ID
}

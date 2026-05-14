package notlown.cobblebase.core.net

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import notlown.cobblebase.core.Cobblebase

/**
 * Client → Server: request the list of available building templates.
 * Sent when the player opens the Builder tab.
 */
class StructureTemplateListRequestC2SPacket : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<StructureTemplateListRequestC2SPacket>(
            Identifier.of(Cobblebase.MODID, "structure_template_request")
        )
        val CODEC: PacketCodec<PacketByteBuf, StructureTemplateListRequestC2SPacket> =
            object : PacketCodec<PacketByteBuf, StructureTemplateListRequestC2SPacket> {
                override fun decode(buf: PacketByteBuf) = StructureTemplateListRequestC2SPacket()
                override fun encode(buf: PacketByteBuf, packet: StructureTemplateListRequestC2SPacket) {}
            }
    }

    override fun getId(): CustomPayload.Id<StructureTemplateListRequestC2SPacket> = ID
}

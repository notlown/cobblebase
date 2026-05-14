package notlown.cobblebase.core.net

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import notlown.cobblebase.core.Cobblebase

/**
 * Server → Client: list of building templates available on the server.
 * Sent in response to [StructureTemplateListRequestC2SPacket].
 *
 * Each [TemplateDTO] is the lightweight metadata version — clients use it to render
 * the Builder tab list. Full templates only exist on the server.
 */
data class StructureTemplateListSyncS2CPacket(
    val templates: List<TemplateDTO>
) : CustomPayload {

    data class TemplateDTO(
        val id: String,
        val displayName: String,
        val sizeX: Int,
        val sizeY: Int,
        val sizeZ: Int,
        /** Top-N blocks by count (block id → count). Empty for legacy entries. */
        val topBlocks: List<Pair<String, Int>> = emptyList(),
        /** Total non-air block count for the whole template. */
        val totalBlockCount: Int = 0
    )

    companion object {
        val ID = CustomPayload.Id<StructureTemplateListSyncS2CPacket>(
            Identifier.of(Cobblebase.MODID, "structure_template_sync")
        )

        val CODEC: PacketCodec<PacketByteBuf, StructureTemplateListSyncS2CPacket> =
            object : PacketCodec<PacketByteBuf, StructureTemplateListSyncS2CPacket> {
                override fun decode(buf: PacketByteBuf): StructureTemplateListSyncS2CPacket {
                    val count = buf.readVarInt()
                    val list = ArrayList<TemplateDTO>(count)
                    repeat(count) {
                        val id = buf.readString()
                        val displayName = buf.readString()
                        val sx = buf.readInt(); val sy = buf.readInt(); val sz = buf.readInt()
                        val topCount = buf.readVarInt()
                        val top = ArrayList<Pair<String, Int>>(topCount)
                        repeat(topCount) {
                            val blockId = buf.readString()
                            val blockCount = buf.readVarInt()
                            top.add(blockId to blockCount)
                        }
                        val total = buf.readVarInt()
                        list.add(TemplateDTO(id, displayName, sx, sy, sz, top, total))
                    }
                    return StructureTemplateListSyncS2CPacket(list)
                }
                override fun encode(buf: PacketByteBuf, packet: StructureTemplateListSyncS2CPacket) {
                    buf.writeVarInt(packet.templates.size)
                    for (t in packet.templates) {
                        buf.writeString(t.id)
                        buf.writeString(t.displayName)
                        buf.writeInt(t.sizeX); buf.writeInt(t.sizeY); buf.writeInt(t.sizeZ)
                        buf.writeVarInt(t.topBlocks.size)
                        for ((blockId, blockCount) in t.topBlocks) {
                            buf.writeString(blockId)
                            buf.writeVarInt(blockCount)
                        }
                        buf.writeVarInt(t.totalBlockCount)
                    }
                }
            }
    }

    override fun getId(): CustomPayload.Id<StructureTemplateListSyncS2CPacket> = ID
}

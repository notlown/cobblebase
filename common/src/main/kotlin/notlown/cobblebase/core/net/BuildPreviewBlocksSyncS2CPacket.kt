package notlown.cobblebase.core.net

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import notlown.cobblebase.core.Cobblebase

/**
 * Server → Client: list of (local position, block-state-id) tuples for a structure template
 * so the client can render translucent ghost blocks for the in-world preview.
 *
 * Block state IDs are the raw `Block.STATE_IDS.getRawId(state)` integers — client resolves
 * them via the same registry. Positions are template-local (0,0,0 to size-1); the client
 * applies rotation/mirror/origin offsets on its end.
 *
 * Air variants are filtered server-side to keep payload small.
 */
data class BuildPreviewBlocksSyncS2CPacket(
    val templateId: String,
    val sizeX: Int,
    val sizeY: Int,
    val sizeZ: Int,
    /** List of (x, y, z, stateRawId) — one entry per non-air block. */
    val blocks: List<IntArray>
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<BuildPreviewBlocksSyncS2CPacket>(
            Identifier.of(Cobblebase.MODID, "build_preview_blocks_sync")
        )

        val CODEC: PacketCodec<PacketByteBuf, BuildPreviewBlocksSyncS2CPacket> =
            object : PacketCodec<PacketByteBuf, BuildPreviewBlocksSyncS2CPacket> {
                override fun decode(buf: PacketByteBuf): BuildPreviewBlocksSyncS2CPacket {
                    val id = buf.readString()
                    val sx = buf.readVarInt(); val sy = buf.readVarInt(); val sz = buf.readVarInt()
                    val n = buf.readVarInt()
                    val blocks = ArrayList<IntArray>(n)
                    repeat(n) {
                        blocks.add(intArrayOf(
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()
                        ))
                    }
                    return BuildPreviewBlocksSyncS2CPacket(id, sx, sy, sz, blocks)
                }
                override fun encode(buf: PacketByteBuf, packet: BuildPreviewBlocksSyncS2CPacket) {
                    buf.writeString(packet.templateId)
                    buf.writeVarInt(packet.sizeX); buf.writeVarInt(packet.sizeY); buf.writeVarInt(packet.sizeZ)
                    buf.writeVarInt(packet.blocks.size)
                    for (b in packet.blocks) {
                        buf.writeVarInt(b[0]); buf.writeVarInt(b[1]); buf.writeVarInt(b[2]); buf.writeVarInt(b[3])
                    }
                }
            }
    }

    override fun getId(): CustomPayload.Id<BuildPreviewBlocksSyncS2CPacket> = ID
}

package notlown.cobblebase.core.net

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import notlown.cobblebase.core.Cobblebase

/**
 * Client → Server: request the block list for a structure template so the in-world
 * preview can render the building as translucent ghost blocks instead of just a wireframe.
 *
 * Server responds with [BuildPreviewBlocksSyncS2CPacket]. The client asks once per
 * template selection — rotation/mirror are applied client-side using the same transform
 * the BuilderExecutor uses on the server.
 */
data class BuildPreviewBlocksRequestC2SPacket(
    val templateId: String
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<BuildPreviewBlocksRequestC2SPacket>(
            Identifier.of(Cobblebase.MODID, "build_preview_blocks_request")
        )

        val CODEC: PacketCodec<PacketByteBuf, BuildPreviewBlocksRequestC2SPacket> =
            object : PacketCodec<PacketByteBuf, BuildPreviewBlocksRequestC2SPacket> {
                override fun decode(buf: PacketByteBuf): BuildPreviewBlocksRequestC2SPacket =
                    BuildPreviewBlocksRequestC2SPacket(buf.readString())
                override fun encode(buf: PacketByteBuf, packet: BuildPreviewBlocksRequestC2SPacket) {
                    buf.writeString(packet.templateId)
                }
            }
    }

    override fun getId(): CustomPayload.Id<BuildPreviewBlocksRequestC2SPacket> = ID
}

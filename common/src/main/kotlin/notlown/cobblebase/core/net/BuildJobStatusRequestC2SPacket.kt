package notlown.cobblebase.core.net

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import notlown.cobblebase.core.Cobblebase

/**
 * Client → Server: poll the current status of a pasture's active build job so the Builder GUI
 * can show progress, missing-material info, and completion state. Sent periodically by
 * BuilderPanel while the tab is open.
 */
data class BuildJobStatusRequestC2SPacket(
    val pasturePos: BlockPos
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<BuildJobStatusRequestC2SPacket>(
            Identifier.of(Cobblebase.MODID, "build_job_status_request")
        )

        val CODEC: PacketCodec<PacketByteBuf, BuildJobStatusRequestC2SPacket> =
            object : PacketCodec<PacketByteBuf, BuildJobStatusRequestC2SPacket> {
                override fun decode(buf: PacketByteBuf): BuildJobStatusRequestC2SPacket =
                    BuildJobStatusRequestC2SPacket(buf.readBlockPos())
                override fun encode(buf: PacketByteBuf, packet: BuildJobStatusRequestC2SPacket) {
                    buf.writeBlockPos(packet.pasturePos)
                }
            }
    }

    override fun getId(): CustomPayload.Id<BuildJobStatusRequestC2SPacket> = ID
}

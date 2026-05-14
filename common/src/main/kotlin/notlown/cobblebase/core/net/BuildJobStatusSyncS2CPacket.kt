package notlown.cobblebase.core.net

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import notlown.cobblebase.core.Cobblebase

/**
 * Server → Client: snapshot of the build-job status for a pasture. Sent in response to
 * [BuildJobStatusRequestC2SPacket]. If [present] is false the pasture has no active job.
 */
data class BuildJobStatusSyncS2CPacket(
    val pasturePos: BlockPos,
    val present: Boolean,
    val templateId: String,
    val displayName: String,
    val totalBlocks: Int,
    val placedBlocks: Int,
    /** Empty string when not waiting on a specific block (or job complete). */
    val nextMissingBlockId: String,
    val completed: Boolean
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<BuildJobStatusSyncS2CPacket>(
            Identifier.of(Cobblebase.MODID, "build_job_status_sync")
        )

        val CODEC: PacketCodec<PacketByteBuf, BuildJobStatusSyncS2CPacket> =
            object : PacketCodec<PacketByteBuf, BuildJobStatusSyncS2CPacket> {
                override fun decode(buf: PacketByteBuf): BuildJobStatusSyncS2CPacket =
                    BuildJobStatusSyncS2CPacket(
                        pasturePos = buf.readBlockPos(),
                        present = buf.readBoolean(),
                        templateId = buf.readString(),
                        displayName = buf.readString(),
                        totalBlocks = buf.readVarInt(),
                        placedBlocks = buf.readVarInt(),
                        nextMissingBlockId = buf.readString(),
                        completed = buf.readBoolean()
                    )
                override fun encode(buf: PacketByteBuf, packet: BuildJobStatusSyncS2CPacket) {
                    buf.writeBlockPos(packet.pasturePos)
                    buf.writeBoolean(packet.present)
                    buf.writeString(packet.templateId)
                    buf.writeString(packet.displayName)
                    buf.writeVarInt(packet.totalBlocks)
                    buf.writeVarInt(packet.placedBlocks)
                    buf.writeString(packet.nextMissingBlockId)
                    buf.writeBoolean(packet.completed)
                }
            }
    }

    override fun getId(): CustomPayload.Id<BuildJobStatusSyncS2CPacket> = ID
}

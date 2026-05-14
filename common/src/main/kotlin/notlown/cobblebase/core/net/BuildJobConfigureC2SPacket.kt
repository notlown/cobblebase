package notlown.cobblebase.core.net

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.BlockMirror
import net.minecraft.util.BlockRotation
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import notlown.cobblebase.core.BuildJob
import notlown.cobblebase.core.BuildJobManager
import notlown.cobblebase.core.Cobblebase

/**
 * Client → Server: player confirms a build placement for a pasture.
 *
 * If [templateId] is empty the existing job for [pasturePos] is cleared. Otherwise a
 * new [BuildJob] is stored, replacing any prior job at that pasture. The Builder
 * Pokemon assigned to that pasture (Phase 3) picks it up on its next tick.
 */
data class BuildJobConfigureC2SPacket(
    val pasturePos: BlockPos,
    val templateId: String,    // empty → clear job
    val originX: Int, val originY: Int, val originZ: Int,
    val rotationName: String,
    val mirrorName: String
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<BuildJobConfigureC2SPacket>(Identifier.of(Cobblebase.MODID, "build_configure"))

        val CODEC: PacketCodec<PacketByteBuf, BuildJobConfigureC2SPacket> = object : PacketCodec<PacketByteBuf, BuildJobConfigureC2SPacket> {
            override fun decode(buf: PacketByteBuf) = BuildJobConfigureC2SPacket(
                buf.readBlockPos(),
                buf.readString(),
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readString(), buf.readString()
            )
            override fun encode(buf: PacketByteBuf, packet: BuildJobConfigureC2SPacket) {
                buf.writeBlockPos(packet.pasturePos)
                buf.writeString(packet.templateId)
                buf.writeInt(packet.originX); buf.writeInt(packet.originY); buf.writeInt(packet.originZ)
                buf.writeString(packet.rotationName)
                buf.writeString(packet.mirrorName)
            }
        }
    }

    override fun getId(): CustomPayload.Id<BuildJobConfigureC2SPacket> = ID

    fun handle(player: ServerPlayerEntity) {
        val world = player.serverWorld
        if (templateId.isBlank()) {
            BuildJobManager.clearJob(pasturePos)
        } else {
            val tid = Identifier.tryParse(templateId) ?: return
            val rot = runCatching { BlockRotation.valueOf(rotationName) }.getOrDefault(BlockRotation.NONE)
            val mir = runCatching { BlockMirror.valueOf(mirrorName) }.getOrDefault(BlockMirror.NONE)
            BuildJobManager.setJob(pasturePos, BuildJob(
                templateId = tid,
                origin = BlockPos(originX, originY, originZ),
                rotation = rot,
                mirror = mir,
                completed = false
            ))
            Cobblebase.LOGGER.debug(
                "[Cobblebase] BuildJob set @ $pasturePos: template=$tid origin=($originX,$originY,$originZ) rot=$rot"
            )
        }
        BuildJobManager.save(world)
    }
}

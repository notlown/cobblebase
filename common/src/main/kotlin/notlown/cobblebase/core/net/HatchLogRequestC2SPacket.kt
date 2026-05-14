package notlown.cobblebase.core.net

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import notlown.cobblebase.core.Cobblebase

/**
 * Client → Server: request the latest hatchery log + stats.
 *
 * [pasturePos] is the position of the pasture the GUI is showing. The server uses it to
 * fill the response's active-hatcher list and egg-availability count. Pass `null` (encoded
 * as `BlockPos.ORIGIN` with a flag) when the requester has no pasture context (e.g. an
 * out-of-world preview); the active-hatcher list will be empty in that case.
 */
data class HatchLogRequestC2SPacket(val pasturePos: BlockPos?) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<HatchLogRequestC2SPacket>(
            Identifier.of(Cobblebase.MODID, "hatch_log_request")
        )
        val CODEC: PacketCodec<PacketByteBuf, HatchLogRequestC2SPacket> =
            object : PacketCodec<PacketByteBuf, HatchLogRequestC2SPacket> {
                override fun decode(buf: PacketByteBuf): HatchLogRequestC2SPacket {
                    val has = buf.readBoolean()
                    val pos = if (has) buf.readBlockPos() else null
                    return HatchLogRequestC2SPacket(pos)
                }
                override fun encode(buf: PacketByteBuf, packet: HatchLogRequestC2SPacket) {
                    buf.writeBoolean(packet.pasturePos != null)
                    packet.pasturePos?.let { buf.writeBlockPos(it) }
                }
            }
    }
    override fun getId(): CustomPayload.Id<HatchLogRequestC2SPacket> = ID
}

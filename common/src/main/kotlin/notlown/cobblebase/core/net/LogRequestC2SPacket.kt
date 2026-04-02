package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * Client -> Server: request activity logs for the nearest pasture.
 * The pastureId UUID is from PasturePCGUIConfiguration.getPastureId().
 * The server will look up the BlockPos from LogManager's pasturePositions map
 * and respond with a LogSyncS2CPacket.
 */
data class LogRequestC2SPacket(
    val pastureId: UUID
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<LogRequestC2SPacket>(Identifier.of(Cobblebase.MODID, "log_request"))

        val CODEC: PacketCodec<PacketByteBuf, LogRequestC2SPacket> = object : PacketCodec<PacketByteBuf, LogRequestC2SPacket> {
            override fun decode(buf: PacketByteBuf): LogRequestC2SPacket {
                return LogRequestC2SPacket(buf.readUuid())
            }
            override fun encode(buf: PacketByteBuf, packet: LogRequestC2SPacket) {
                buf.writeUuid(packet.pastureId)
            }
        }
    }

    override fun getId(): CustomPayload.Id<LogRequestC2SPacket> = ID
}

package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Client -> Server: request all job definitions and overrides for the Admin GUI.
 * Marker packet with no data.
 */
class AdminJobsRequestC2SPacket : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<AdminJobsRequestC2SPacket>(Identifier.of(Cobblebase.MODID, "admin_jobs_request"))

        val CODEC: PacketCodec<PacketByteBuf, AdminJobsRequestC2SPacket> = object : PacketCodec<PacketByteBuf, AdminJobsRequestC2SPacket> {
            override fun decode(buf: PacketByteBuf): AdminJobsRequestC2SPacket {
                return AdminJobsRequestC2SPacket()
            }
            override fun encode(buf: PacketByteBuf, packet: AdminJobsRequestC2SPacket) {
                // No data needed
            }
        }
    }

    override fun getId(): CustomPayload.Id<AdminJobsRequestC2SPacket> = ID
}

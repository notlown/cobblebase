package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Client -> Server: request species list and skill assignments for the Admin GUI.
 * Marker packet with no data.
 */
class AdminSpeciesRequestC2SPacket : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<AdminSpeciesRequestC2SPacket>(Identifier.of(Cobblebase.MODID, "admin_species_request"))

        val CODEC: PacketCodec<PacketByteBuf, AdminSpeciesRequestC2SPacket> = object : PacketCodec<PacketByteBuf, AdminSpeciesRequestC2SPacket> {
            override fun decode(buf: PacketByteBuf): AdminSpeciesRequestC2SPacket {
                return AdminSpeciesRequestC2SPacket()
            }
            override fun encode(buf: PacketByteBuf, packet: AdminSpeciesRequestC2SPacket) {
                // No data needed
            }
        }
    }

    override fun getId(): CustomPayload.Id<AdminSpeciesRequestC2SPacket> = ID
}

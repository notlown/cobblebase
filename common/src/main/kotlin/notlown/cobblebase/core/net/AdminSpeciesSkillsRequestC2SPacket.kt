package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Client -> Server: request the skill list for a single species.
 * Used for lazy loading in the Admin GUI.
 */
data class AdminSpeciesSkillsRequestC2SPacket(
    val species: String
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<AdminSpeciesSkillsRequestC2SPacket>(Identifier.of(Cobblebase.MODID, "admin_species_skills_request"))

        val CODEC: PacketCodec<PacketByteBuf, AdminSpeciesSkillsRequestC2SPacket> = object : PacketCodec<PacketByteBuf, AdminSpeciesSkillsRequestC2SPacket> {
            override fun decode(buf: PacketByteBuf): AdminSpeciesSkillsRequestC2SPacket {
                return AdminSpeciesSkillsRequestC2SPacket(buf.readString())
            }

            override fun encode(buf: PacketByteBuf, packet: AdminSpeciesSkillsRequestC2SPacket) {
                buf.writeString(packet.species)
            }
        }
    }

    override fun getId(): CustomPayload.Id<AdminSpeciesSkillsRequestC2SPacket> = ID
}

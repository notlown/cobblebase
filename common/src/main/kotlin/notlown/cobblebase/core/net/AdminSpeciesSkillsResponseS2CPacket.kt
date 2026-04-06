package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.SkillEntry
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -> Client: response with skills for a single requested species.
 */
data class AdminSpeciesSkillsResponseS2CPacket(
    val species: String,
    val skills: List<SkillEntry>
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<AdminSpeciesSkillsResponseS2CPacket>(Identifier.of(Cobblebase.MODID, "admin_species_skills_response"))

        val CODEC: PacketCodec<PacketByteBuf, AdminSpeciesSkillsResponseS2CPacket> = object : PacketCodec<PacketByteBuf, AdminSpeciesSkillsResponseS2CPacket> {
            override fun decode(buf: PacketByteBuf): AdminSpeciesSkillsResponseS2CPacket {
                val species = buf.readString()
                val skillCount = buf.readVarInt()
                val skills = mutableListOf<SkillEntry>()
                repeat(skillCount) {
                    skills.add(SkillEntry(
                        skillId = buf.readString(),
                        proficiency = buf.readVarInt()
                    ))
                }
                return AdminSpeciesSkillsResponseS2CPacket(species, skills)
            }

            override fun encode(buf: PacketByteBuf, packet: AdminSpeciesSkillsResponseS2CPacket) {
                buf.writeString(packet.species)
                buf.writeVarInt(packet.skills.size)
                for (skill in packet.skills) {
                    buf.writeString(skill.skillId)
                    buf.writeVarInt(skill.proficiency)
                }
            }
        }
    }

    override fun getId(): CustomPayload.Id<AdminSpeciesSkillsResponseS2CPacket> = ID
}

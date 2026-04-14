package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.SkillEntry
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -> Client: response with skills for a single requested species.
 * Includes optional producer data (item ID, count, display name).
 */
data class AdminSpeciesSkillsResponseS2CPacket(
    val species: String,
    val skills: List<SkillEntry>,
    val producerItemId: String? = null,
    val producerCount: Int = 0,
    val producerDisplayName: String? = null
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
                val hasProducer = buf.readBoolean()
                val producerItemId: String?
                val producerCount: Int
                val producerDisplayName: String?
                if (hasProducer) {
                    producerItemId = buf.readString()
                    producerCount = buf.readVarInt()
                    producerDisplayName = buf.readString()
                } else {
                    producerItemId = null
                    producerCount = 0
                    producerDisplayName = null
                }
                return AdminSpeciesSkillsResponseS2CPacket(species, skills, producerItemId, producerCount, producerDisplayName)
            }

            override fun encode(buf: PacketByteBuf, packet: AdminSpeciesSkillsResponseS2CPacket) {
                buf.writeString(packet.species)
                buf.writeVarInt(packet.skills.size)
                for (skill in packet.skills) {
                    buf.writeString(skill.skillId)
                    buf.writeVarInt(skill.proficiency)
                }
                val hasProducer = packet.producerItemId != null
                buf.writeBoolean(hasProducer)
                if (hasProducer) {
                    buf.writeString(packet.producerItemId!!)
                    buf.writeVarInt(packet.producerCount)
                    buf.writeString(packet.producerDisplayName ?: "")
                }
            }
        }
    }

    override fun getId(): CustomPayload.Id<AdminSpeciesSkillsResponseS2CPacket> = ID
}

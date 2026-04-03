package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.SkillEntry
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -> Client: sends all species names, their skill assignments, and which are overridden.
 */
data class AdminSpeciesSyncS2CPacket(
    val allSpecies: List<String>,
    val speciesSkills: Map<String, List<SkillEntry>>,
    val overriddenSpecies: Set<String>
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<AdminSpeciesSyncS2CPacket>(Identifier.of(Cobblebase.MODID, "admin_species_sync"))

        val CODEC: PacketCodec<PacketByteBuf, AdminSpeciesSyncS2CPacket> = object : PacketCodec<PacketByteBuf, AdminSpeciesSyncS2CPacket> {
            override fun decode(buf: PacketByteBuf): AdminSpeciesSyncS2CPacket {
                // Read all species names
                val speciesCount = buf.readVarInt()
                val allSpecies = mutableListOf<String>()
                repeat(speciesCount) {
                    allSpecies.add(buf.readString())
                }

                // Read species -> skills map
                val skillMapCount = buf.readVarInt()
                val speciesSkills = mutableMapOf<String, List<SkillEntry>>()
                repeat(skillMapCount) {
                    val species = buf.readString()
                    val skillCount = buf.readVarInt()
                    val skills = mutableListOf<SkillEntry>()
                    repeat(skillCount) {
                        skills.add(SkillEntry(
                            skillId = buf.readString(),
                            proficiency = buf.readVarInt()
                        ))
                    }
                    speciesSkills[species] = skills
                }

                // Read overridden species set
                val overriddenCount = buf.readVarInt()
                val overriddenSpecies = mutableSetOf<String>()
                repeat(overriddenCount) {
                    overriddenSpecies.add(buf.readString())
                }

                return AdminSpeciesSyncS2CPacket(allSpecies, speciesSkills, overriddenSpecies)
            }

            override fun encode(buf: PacketByteBuf, packet: AdminSpeciesSyncS2CPacket) {
                // Write all species names
                buf.writeVarInt(packet.allSpecies.size)
                for (species in packet.allSpecies) {
                    buf.writeString(species)
                }

                // Write species -> skills map
                buf.writeVarInt(packet.speciesSkills.size)
                for ((species, skills) in packet.speciesSkills) {
                    buf.writeString(species)
                    buf.writeVarInt(skills.size)
                    for (skill in skills) {
                        buf.writeString(skill.skillId)
                        buf.writeVarInt(skill.proficiency)
                    }
                }

                // Write overridden species set
                buf.writeVarInt(packet.overriddenSpecies.size)
                for (species in packet.overriddenSpecies) {
                    buf.writeString(species)
                }
            }
        }
    }

    override fun getId(): CustomPayload.Id<AdminSpeciesSyncS2CPacket> = ID
}

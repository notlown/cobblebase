package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SpeciesSkillOverrides
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

/**
 * Client -> Server: update skill assignments for a species.
 * Requires OP permission level 2.
 */
data class AdminSpeciesUpdateC2SPacket(
    val species: String,
    val skills: List<SkillEntry>,
    val resetToDefault: Boolean
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<AdminSpeciesUpdateC2SPacket>(Identifier.of(Cobblebase.MODID, "admin_species_update"))

        val CODEC: PacketCodec<PacketByteBuf, AdminSpeciesUpdateC2SPacket> = object : PacketCodec<PacketByteBuf, AdminSpeciesUpdateC2SPacket> {
            override fun decode(buf: PacketByteBuf): AdminSpeciesUpdateC2SPacket {
                val species = buf.readString()
                val resetToDefault = buf.readBoolean()
                val skillCount = buf.readVarInt()
                val skills = mutableListOf<SkillEntry>()
                repeat(skillCount) {
                    skills.add(SkillEntry(
                        skillId = buf.readString(),
                        proficiency = buf.readVarInt()
                    ))
                }
                return AdminSpeciesUpdateC2SPacket(species, skills, resetToDefault)
            }

            override fun encode(buf: PacketByteBuf, packet: AdminSpeciesUpdateC2SPacket) {
                buf.writeString(packet.species)
                buf.writeBoolean(packet.resetToDefault)
                buf.writeVarInt(packet.skills.size)
                for (skill in packet.skills) {
                    buf.writeString(skill.skillId)
                    buf.writeVarInt(skill.proficiency)
                }
            }
        }
    }

    override fun getId(): CustomPayload.Id<AdminSpeciesUpdateC2SPacket> = ID

    fun handle(player: ServerPlayerEntity) {
        // Require OP permission level 2
        if (!player.hasPermissionLevel(2)) {
            Cobblebase.LOGGER.warn("[Cobblebase] Player ${player.name.string} tried to update species skills without OP permission")
            return
        }

        val world = player.serverWorld

        if (resetToDefault) {
            SpeciesSkillOverrides.removeOverride(species, world)
        } else {
            SpeciesSkillOverrides.setOverride(species, skills, world)
        }
    }
}

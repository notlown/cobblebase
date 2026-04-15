package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.ProducerOverrides
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SpeciesSkillOverrides
import notlown.cobblebase.core.executors.ProducerExecutor
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

/**
 * Client -> Server: update skill assignments for a species.
 * Optionally includes producer item override.
 * Requires OP permission level 2.
 */
data class AdminSpeciesUpdateC2SPacket(
    val species: String,
    val skills: List<SkillEntry>,
    val resetToDefault: Boolean,
    val producerItemId: String? = null,
    val producerCount: Int = 0,
    val producerCooldown: Long = 0,
    val producerResetToDefault: Boolean = false
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
                val hasProducerUpdate = buf.readBoolean()
                val producerItemId: String?
                val producerCount: Int
                val producerCooldown: Long
                val producerResetToDefault: Boolean
                if (hasProducerUpdate) {
                    producerResetToDefault = buf.readBoolean()
                    producerItemId = if (!producerResetToDefault) buf.readString() else null
                    producerCount = if (!producerResetToDefault) buf.readVarInt() else 0
                    producerCooldown = if (!producerResetToDefault) buf.readLong() else 0L
                } else {
                    producerItemId = null
                    producerCount = 0
                    producerCooldown = 0L
                    producerResetToDefault = false
                }
                return AdminSpeciesUpdateC2SPacket(species, skills, resetToDefault, producerItemId, producerCount, producerCooldown, producerResetToDefault)
            }

            override fun encode(buf: PacketByteBuf, packet: AdminSpeciesUpdateC2SPacket) {
                buf.writeString(packet.species)
                buf.writeBoolean(packet.resetToDefault)
                buf.writeVarInt(packet.skills.size)
                for (skill in packet.skills) {
                    buf.writeString(skill.skillId)
                    buf.writeVarInt(skill.proficiency)
                }
                val hasProducerUpdate = packet.producerItemId != null || packet.producerResetToDefault
                buf.writeBoolean(hasProducerUpdate)
                if (hasProducerUpdate) {
                    buf.writeBoolean(packet.producerResetToDefault)
                    if (!packet.producerResetToDefault && packet.producerItemId != null) {
                        buf.writeString(packet.producerItemId)
                        buf.writeVarInt(packet.producerCount)
                        buf.writeLong(packet.producerCooldown)
                    }
                }
            }
        }
    }

    override fun getId(): CustomPayload.Id<AdminSpeciesUpdateC2SPacket> = ID

    fun handle(player: ServerPlayerEntity) {
        if (!player.hasPermissionLevel(2)) {
            Cobblebase.LOGGER.warn("[Cobblebase] Player ${player.name.string} tried to update species skills without OP permission")
            return
        }

        val world = player.serverWorld

        // Handle skill overrides
        if (resetToDefault) {
            SpeciesSkillOverrides.removeOverride(species, world)
        } else {
            SpeciesSkillOverrides.setOverride(species, skills, world)
        }

        // Handle producer overrides
        if (producerResetToDefault) {
            ProducerOverrides.removeOverride(species, world)
        } else if (producerItemId != null) {
            val displayName = producerItemId.substringAfterLast(":")
                .replace("_", " ")
                .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            val cooldown = if (producerCooldown > 0) producerCooldown else null
            ProducerOverrides.setOverride(
                species,
                ProducerExecutor.ProduceEntry(producerItemId, producerCount.coerceIn(1, 64), displayName, cooldown),
                world
            )
        }
    }
}

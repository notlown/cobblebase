package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * Server -> Client: sends all current skill assignments so the client GUI stays in sync.
 * Map of Pokemon UUID -> assigned skill ID (empty string = idle/unassigned).
 */
data class SkillAssignmentSyncS2CPacket(
    val assignments: Map<UUID, String>
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<SkillAssignmentSyncS2CPacket>(Identifier.of(Cobblebase.MODID, "skill_sync"))

        val CODEC: PacketCodec<PacketByteBuf, SkillAssignmentSyncS2CPacket> = object : PacketCodec<PacketByteBuf, SkillAssignmentSyncS2CPacket> {
            override fun decode(buf: PacketByteBuf): SkillAssignmentSyncS2CPacket {
                val count = buf.readVarInt()
                val assignments = mutableMapOf<UUID, String>()
                repeat(count) {
                    val uuid = buf.readUuid()
                    val skillId = buf.readString()
                    assignments[uuid] = skillId
                }
                return SkillAssignmentSyncS2CPacket(assignments)
            }

            override fun encode(buf: PacketByteBuf, packet: SkillAssignmentSyncS2CPacket) {
                buf.writeVarInt(packet.assignments.size)
                for ((uuid, skillId) in packet.assignments) {
                    buf.writeUuid(uuid)
                    buf.writeString(skillId)
                }
            }
        }
    }

    override fun getId(): CustomPayload.Id<SkillAssignmentSyncS2CPacket> = ID
}

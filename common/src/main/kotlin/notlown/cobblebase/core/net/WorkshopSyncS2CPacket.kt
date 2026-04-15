package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * Server -> Client: syncs workshop project states for all Craftsman Pokemon.
 */
data class WorkshopSyncS2CPacket(
    val projects: Map<UUID, ProjectDTO>
) : CustomPayload {

    data class ProjectDTO(
        val recipeId: String,
        val gatheredItems: Map<String, Int>,
        val phase: String,
        val requiredItems: Map<String, Int>
    )

    companion object {
        val ID = CustomPayload.Id<WorkshopSyncS2CPacket>(Identifier.of(Cobblebase.MODID, "workshop_sync"))

        val CODEC: PacketCodec<PacketByteBuf, WorkshopSyncS2CPacket> = object : PacketCodec<PacketByteBuf, WorkshopSyncS2CPacket> {
            override fun decode(buf: PacketByteBuf): WorkshopSyncS2CPacket {
                val count = buf.readVarInt()
                val projects = mutableMapOf<UUID, ProjectDTO>()
                repeat(count) {
                    val uuid = buf.readUuid()
                    val recipeId = buf.readString()
                    val gatheredCount = buf.readVarInt()
                    val gathered = mutableMapOf<String, Int>()
                    repeat(gatheredCount) { gathered[buf.readString()] = buf.readVarInt() }
                    val phase = buf.readString()
                    val reqCount = buf.readVarInt()
                    val required = mutableMapOf<String, Int>()
                    repeat(reqCount) { required[buf.readString()] = buf.readVarInt() }
                    projects[uuid] = ProjectDTO(recipeId, gathered, phase, required)
                }
                return WorkshopSyncS2CPacket(projects)
            }

            override fun encode(buf: PacketByteBuf, packet: WorkshopSyncS2CPacket) {
                buf.writeVarInt(packet.projects.size)
                for ((uuid, proj) in packet.projects) {
                    buf.writeUuid(uuid)
                    buf.writeString(proj.recipeId)
                    buf.writeVarInt(proj.gatheredItems.size)
                    for ((itemId, count) in proj.gatheredItems) {
                        buf.writeString(itemId)
                        buf.writeVarInt(count)
                    }
                    buf.writeString(proj.phase)
                    buf.writeVarInt(proj.requiredItems.size)
                    for ((itemId, count) in proj.requiredItems) {
                        buf.writeString(itemId)
                        buf.writeVarInt(count)
                    }
                }
            }
        }
    }

    override fun getId(): CustomPayload.Id<WorkshopSyncS2CPacket> = ID
}

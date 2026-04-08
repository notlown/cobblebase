package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.JobConfigOverrides
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -> Client: broadcasts every job config override (cooldown,
 * radius, enabled flag) to the player. The client's Pasture Skills tab
 * uses `JobConfigOverrides.isEnabled()` to hide disabled jobs, and
 * without this sync the client's copy of `JobConfigOverrides` is always
 * empty so every job appears available — even ones the admin disabled.
 *
 * Sent on player join and re-sent after every successful admin jobs
 * update (AdminJobsUpdateC2SPacket).
 */
data class JobOverrideSyncS2CPacket(
    val overrides: Map<String, JobConfigOverrides.JobOverride>
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<JobOverrideSyncS2CPacket>(Identifier.of(Cobblebase.MODID, "job_override_sync"))

        val CODEC: PacketCodec<PacketByteBuf, JobOverrideSyncS2CPacket> = object : PacketCodec<PacketByteBuf, JobOverrideSyncS2CPacket> {
            override fun decode(buf: PacketByteBuf): JobOverrideSyncS2CPacket {
                val count = buf.readVarInt()
                val map = HashMap<String, JobConfigOverrides.JobOverride>(count)
                repeat(count) {
                    val skillId = buf.readString()
                    val hasCooldown = buf.readBoolean()
                    val cooldown = if (hasCooldown) buf.readLong() else null
                    val hasRadius = buf.readBoolean()
                    val radius = if (hasRadius) buf.readVarInt() else null
                    val enabled = buf.readBoolean()
                    map[skillId] = JobConfigOverrides.JobOverride(
                        cooldownSeconds = cooldown,
                        searchRadius = radius,
                        enabled = enabled
                    )
                }
                return JobOverrideSyncS2CPacket(map)
            }

            override fun encode(buf: PacketByteBuf, packet: JobOverrideSyncS2CPacket) {
                buf.writeVarInt(packet.overrides.size)
                for ((skillId, o) in packet.overrides) {
                    buf.writeString(skillId)
                    buf.writeBoolean(o.cooldownSeconds != null)
                    if (o.cooldownSeconds != null) buf.writeLong(o.cooldownSeconds)
                    buf.writeBoolean(o.searchRadius != null)
                    if (o.searchRadius != null) buf.writeVarInt(o.searchRadius)
                    buf.writeBoolean(o.enabled)
                }
            }
        }
    }

    override fun getId(): CustomPayload.Id<JobOverrideSyncS2CPacket> = ID
}

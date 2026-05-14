package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.JobConfigOverrides
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.TuningField
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -> Client: sends all job definitions (SkillDefs) and their overrides.
 */
data class AdminJobsSyncS2CPacket(
    val jobs: Map<String, SkillDef>,
    val overrides: Map<String, JobConfigOverrides.JobOverride>
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<AdminJobsSyncS2CPacket>(Identifier.of(Cobblebase.MODID, "admin_jobs_sync"))

        val CODEC: PacketCodec<PacketByteBuf, AdminJobsSyncS2CPacket> = object : PacketCodec<PacketByteBuf, AdminJobsSyncS2CPacket> {
            override fun decode(buf: PacketByteBuf): AdminJobsSyncS2CPacket {
                // Read job definitions
                val jobCount = buf.readVarInt()
                val jobs = mutableMapOf<String, SkillDef>()
                repeat(jobCount) {
                    val id = buf.readString()
                    val name = buf.readString()
                    val description = buf.readString()
                    val category = buf.readString()
                    val cooldownSeconds = buf.readLong()
                    val searchRadius = buf.readVarInt()
                    val executor = buf.readString()
                    val tuningCount = buf.readVarInt()
                    val tuning = if (tuningCount > 0) {
                        val map = linkedMapOf<String, TuningField>()
                        repeat(tuningCount) {
                            val key = buf.readString()
                            val label = buf.readString()
                            val defaultValue = buf.readDouble()
                            val min = buf.readDouble()
                            val max = buf.readDouble()
                            val step = buf.readDouble()
                            val hasUnit = buf.readBoolean()
                            val unit = if (hasUnit) buf.readString() else null
                            val hasTip = buf.readBoolean()
                            val tip = if (hasTip) buf.readString() else null
                            map[key] = TuningField(label, defaultValue, min, max, step, unit, tip)
                        }
                        map
                    } else null
                    jobs[id] = SkillDef(
                        id = id,
                        name = name,
                        description = description,
                        category = category,
                        cooldownSeconds = cooldownSeconds,
                        searchRadius = searchRadius,
                        executor = executor,
                        tuning = tuning
                    )
                }

                // Read overrides
                val overrideCount = buf.readVarInt()
                val overrides = mutableMapOf<String, JobConfigOverrides.JobOverride>()
                repeat(overrideCount) {
                    val skillId = buf.readString()
                    val hasCooldown = buf.readBoolean()
                    val cooldown = if (hasCooldown) buf.readLong() else null
                    val hasRadius = buf.readBoolean()
                    val radius = if (hasRadius) buf.readVarInt() else null
                    val enabled = buf.readBoolean()
                    val tuningCount = buf.readVarInt()
                    val tuning = if (tuningCount > 0) {
                        val map = mutableMapOf<String, Double>()
                        repeat(tuningCount) { map[buf.readString()] = buf.readDouble() }
                        map
                    } else null
                    overrides[skillId] = JobConfigOverrides.JobOverride(
                        cooldownSeconds = cooldown,
                        searchRadius = radius,
                        enabled = enabled,
                        tuning = tuning
                    )
                }

                return AdminJobsSyncS2CPacket(jobs, overrides)
            }

            override fun encode(buf: PacketByteBuf, packet: AdminJobsSyncS2CPacket) {
                // Write job definitions
                buf.writeVarInt(packet.jobs.size)
                for ((_, job) in packet.jobs) {
                    buf.writeString(job.id)
                    buf.writeString(job.name)
                    buf.writeString(job.description)
                    buf.writeString(job.category)
                    buf.writeLong(job.cooldownSeconds)
                    buf.writeVarInt(job.searchRadius)
                    buf.writeString(job.executor)
                    val tuning = job.tuning ?: emptyMap()
                    buf.writeVarInt(tuning.size)
                    for ((key, field) in tuning) {
                        buf.writeString(key)
                        buf.writeString(field.label)
                        buf.writeDouble(field.defaultValue)
                        buf.writeDouble(field.min)
                        buf.writeDouble(field.max)
                        buf.writeDouble(field.step)
                        buf.writeBoolean(field.unit != null)
                        if (field.unit != null) buf.writeString(field.unit)
                        buf.writeBoolean(field.tooltip != null)
                        if (field.tooltip != null) buf.writeString(field.tooltip)
                    }
                }

                // Write overrides
                buf.writeVarInt(packet.overrides.size)
                for ((skillId, override) in packet.overrides) {
                    buf.writeString(skillId)
                    buf.writeBoolean(override.cooldownSeconds != null)
                    if (override.cooldownSeconds != null) buf.writeLong(override.cooldownSeconds)
                    buf.writeBoolean(override.searchRadius != null)
                    if (override.searchRadius != null) buf.writeVarInt(override.searchRadius)
                    buf.writeBoolean(override.enabled)
                    val tuning = override.tuning ?: emptyMap()
                    buf.writeVarInt(tuning.size)
                    for ((key, value) in tuning) {
                        buf.writeString(key)
                        buf.writeDouble(value)
                    }
                }
            }
        }
    }

    override fun getId(): CustomPayload.Id<AdminJobsSyncS2CPacket> = ID
}

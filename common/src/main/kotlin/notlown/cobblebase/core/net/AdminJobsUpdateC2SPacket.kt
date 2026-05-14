package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.JobConfigOverrides
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

/**
 * Client -> Server: update a single job's configuration override.
 * Requires OP permission level 2.
 */
data class AdminJobsUpdateC2SPacket(
    val skillId: String,
    val cooldownSeconds: Long?,
    val searchRadius: Int?,
    val enabled: Boolean,
    /** Per-skill tuning overrides. Empty map means "no tuning overrides". */
    val tuning: Map<String, Double> = emptyMap()
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<AdminJobsUpdateC2SPacket>(Identifier.of(Cobblebase.MODID, "admin_jobs_update"))

        val CODEC: PacketCodec<PacketByteBuf, AdminJobsUpdateC2SPacket> = object : PacketCodec<PacketByteBuf, AdminJobsUpdateC2SPacket> {
            override fun decode(buf: PacketByteBuf): AdminJobsUpdateC2SPacket {
                val skillId = buf.readString()
                val hasCooldown = buf.readBoolean()
                val cooldown = if (hasCooldown) buf.readLong() else null
                val hasRadius = buf.readBoolean()
                val radius = if (hasRadius) buf.readVarInt() else null
                val enabled = buf.readBoolean()
                val tuningCount = buf.readVarInt()
                val tuning = mutableMapOf<String, Double>()
                repeat(tuningCount) {
                    val key = buf.readString()
                    val value = buf.readDouble()
                    tuning[key] = value
                }
                return AdminJobsUpdateC2SPacket(skillId, cooldown, radius, enabled, tuning)
            }

            override fun encode(buf: PacketByteBuf, packet: AdminJobsUpdateC2SPacket) {
                buf.writeString(packet.skillId)
                buf.writeBoolean(packet.cooldownSeconds != null)
                if (packet.cooldownSeconds != null) buf.writeLong(packet.cooldownSeconds)
                buf.writeBoolean(packet.searchRadius != null)
                if (packet.searchRadius != null) buf.writeVarInt(packet.searchRadius)
                buf.writeBoolean(packet.enabled)
                buf.writeVarInt(packet.tuning.size)
                for ((key, value) in packet.tuning) {
                    buf.writeString(key)
                    buf.writeDouble(value)
                }
            }
        }
    }

    override fun getId(): CustomPayload.Id<AdminJobsUpdateC2SPacket> = ID

    fun handle(player: ServerPlayerEntity) {
        if (!player.hasPermissionLevel(2)) {
            Cobblebase.LOGGER.warn("[Cobblebase] Player ${player.name.string} tried to update job config without OP permission")
            return
        }

        val world = player.serverWorld
        val tuningMap = tuning.takeIf { it.isNotEmpty() }
        val override = JobConfigOverrides.JobOverride(
            cooldownSeconds = cooldownSeconds,
            searchRadius = searchRadius,
            enabled = enabled,
            tuning = tuningMap
        )

        // If all values are default (null cooldown, null radius, enabled=true, no tuning), remove the override
        if (cooldownSeconds == null && searchRadius == null && enabled && tuningMap == null) {
            JobConfigOverrides.removeOverride(skillId)
        } else {
            JobConfigOverrides.setOverride(skillId, override)
        }

        JobConfigOverrides.save(world)
        Cobblebase.LOGGER.debug("[Cobblebase] Job override updated for '$skillId' by ${player.name.string}")
    }
}

package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

/**
 * Server → Client: full snapshot of every persisted per-pasture override.
 * Sent on login and after any owner-driven update. Clients use this to draw the
 * correct per-pasture range/below-reach (incl. the Show-Radius wireframe) and to
 * gate the Pasture UI (skill assignments, base-settings modal) by access mode.
 *
 * `range` / `belowReach` of -1 mean "no override" — fall back to admin caps.
 * `access` ordinal: 0 = OWNER_ONLY, 1 = VIEW_ONLY, 2 = PUBLIC.
 */
data class PastureSettingsSyncS2CPacket(
    /** pos → IntArray(range, belowReach, accessOrdinal). -1 = unset for range/below. */
    val entries: Map<BlockPos, IntArray>
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<PastureSettingsSyncS2CPacket>(
            Identifier.of(Cobblebase.MODID, "pasture_settings_sync")
        )

        val CODEC: PacketCodec<PacketByteBuf, PastureSettingsSyncS2CPacket> =
            object : PacketCodec<PacketByteBuf, PastureSettingsSyncS2CPacket> {
                override fun decode(buf: PacketByteBuf): PastureSettingsSyncS2CPacket {
                    val n = buf.readVarInt()
                    val map = HashMap<BlockPos, IntArray>(n)
                    repeat(n) {
                        val pos = buf.readBlockPos()
                        val range = buf.readVarInt() - 1   // shift so -1 sentinel round-trips
                        val below = buf.readVarInt() - 1
                        val access = buf.readVarInt()
                        map[pos] = intArrayOf(range, below, access)
                    }
                    return PastureSettingsSyncS2CPacket(map)
                }

                override fun encode(buf: PacketByteBuf, packet: PastureSettingsSyncS2CPacket) {
                    buf.writeVarInt(packet.entries.size)
                    for ((pos, vals) in packet.entries) {
                        buf.writeBlockPos(pos)
                        buf.writeVarInt(vals[0] + 1)
                        buf.writeVarInt(vals[1] + 1)
                        buf.writeVarInt(vals.getOrElse(2) { 0 })
                    }
                }
            }
    }

    override fun getId(): CustomPayload.Id<PastureSettingsSyncS2CPacket> = ID
}

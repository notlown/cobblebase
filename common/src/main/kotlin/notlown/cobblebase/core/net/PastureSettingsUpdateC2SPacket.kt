package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

/**
 * Client → Server: pasture owner adjusts the per-pasture range / below-reach
 * override for [pasturePos]. Sentinel -1 means "clear the override; fall back
 * to admin cap." Server validates ownership (pasture.ownerId == player) OR
 * OP permission before persisting.
 */
data class PastureSettingsUpdateC2SPacket(
    val pasturePos: BlockPos,
    val range: Int,        // -1 = unset (use admin cap)
    val belowReach: Int,   // -1 = unset (use admin cap)
    val access: Int        // ordinal of PastureSettings.AccessMode (0/1/2)
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<PastureSettingsUpdateC2SPacket>(
            Identifier.of(Cobblebase.MODID, "pasture_settings_update")
        )

        val CODEC: PacketCodec<PacketByteBuf, PastureSettingsUpdateC2SPacket> =
            object : PacketCodec<PacketByteBuf, PastureSettingsUpdateC2SPacket> {
                override fun decode(buf: PacketByteBuf): PastureSettingsUpdateC2SPacket {
                    val pos = buf.readBlockPos()
                    val range = buf.readVarInt() - 1
                    val below = buf.readVarInt() - 1
                    val access = buf.readVarInt()
                    return PastureSettingsUpdateC2SPacket(pos, range, below, access)
                }

                override fun encode(buf: PacketByteBuf, packet: PastureSettingsUpdateC2SPacket) {
                    buf.writeBlockPos(packet.pasturePos)
                    buf.writeVarInt(packet.range + 1)
                    buf.writeVarInt(packet.belowReach + 1)
                    buf.writeVarInt(packet.access)
                }
            }
    }

    override fun getId(): CustomPayload.Id<PastureSettingsUpdateC2SPacket> = ID
}

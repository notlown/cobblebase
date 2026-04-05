package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -> Client: sends the current pasture settings + admin-configured radius range.
 */
data class PastureSettingsSyncS2CPacket(
    val searchRadius: Int,
    val radiusMin: Int,
    val radiusMax: Int
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<PastureSettingsSyncS2CPacket>(Identifier.of(Cobblebase.MODID, "pasture_settings_sync"))

        val CODEC: PacketCodec<PacketByteBuf, PastureSettingsSyncS2CPacket> = object : PacketCodec<PacketByteBuf, PastureSettingsSyncS2CPacket> {
            override fun decode(buf: PacketByteBuf): PastureSettingsSyncS2CPacket {
                return PastureSettingsSyncS2CPacket(
                    searchRadius = buf.readVarInt(),
                    radiusMin = buf.readVarInt(),
                    radiusMax = buf.readVarInt()
                )
            }
            override fun encode(buf: PacketByteBuf, packet: PastureSettingsSyncS2CPacket) {
                buf.writeVarInt(packet.searchRadius)
                buf.writeVarInt(packet.radiusMin)
                buf.writeVarInt(packet.radiusMax)
            }
        }
    }

    override fun getId(): CustomPayload.Id<PastureSettingsSyncS2CPacket> = ID
}

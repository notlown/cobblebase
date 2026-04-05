package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Client -> Server: request the pasture settings for the nearest pasture block.
 * Server will respond with PastureSettingsSyncS2CPacket.
 */
class PastureSettingsRequestC2SPacket : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<PastureSettingsRequestC2SPacket>(Identifier.of(Cobblebase.MODID, "pasture_settings_request"))

        val CODEC: PacketCodec<PacketByteBuf, PastureSettingsRequestC2SPacket> = object : PacketCodec<PacketByteBuf, PastureSettingsRequestC2SPacket> {
            override fun decode(buf: PacketByteBuf): PastureSettingsRequestC2SPacket {
                return PastureSettingsRequestC2SPacket()
            }
            override fun encode(buf: PacketByteBuf, packet: PastureSettingsRequestC2SPacket) {
                // No data needed
            }
        }
    }

    override fun getId(): CustomPayload.Id<PastureSettingsRequestC2SPacket> = ID
}

package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -> Client: syncs general settings (Discord URL, Discord enabled, etc.)
 */
data class GeneralSettingsSyncS2CPacket(
    val discordUrl: String,
    val discordEnabled: Boolean
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<GeneralSettingsSyncS2CPacket>(Identifier.of(Cobblebase.MODID, "general_settings_sync"))

        val CODEC: PacketCodec<PacketByteBuf, GeneralSettingsSyncS2CPacket> = object : PacketCodec<PacketByteBuf, GeneralSettingsSyncS2CPacket> {
            override fun decode(buf: PacketByteBuf): GeneralSettingsSyncS2CPacket {
                return GeneralSettingsSyncS2CPacket(
                    discordUrl = buf.readString(),
                    discordEnabled = buf.readBoolean()
                )
            }

            override fun encode(buf: PacketByteBuf, packet: GeneralSettingsSyncS2CPacket) {
                buf.writeString(packet.discordUrl)
                buf.writeBoolean(packet.discordEnabled)
            }
        }
    }

    override fun getId(): CustomPayload.Id<GeneralSettingsSyncS2CPacket> = ID
}

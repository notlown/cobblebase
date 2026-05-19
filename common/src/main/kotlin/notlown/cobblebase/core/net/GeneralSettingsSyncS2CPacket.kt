package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -> Client: syncs general settings (Discord URL/enabled, PokeWiki visibility).
 */
data class GeneralSettingsSyncS2CPacket(
    val discordUrl: String,
    val discordEnabled: Boolean,
    val pokeWikiEnabled: Boolean,
    val pastureRange: Int = 0,
    val maxWorkingPokemonPerPasture: Int = 0,
    val harvesterDownwardLimit: Int = 6
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<GeneralSettingsSyncS2CPacket>(Identifier.of(Cobblebase.MODID, "general_settings_sync"))

        val CODEC: PacketCodec<PacketByteBuf, GeneralSettingsSyncS2CPacket> = object : PacketCodec<PacketByteBuf, GeneralSettingsSyncS2CPacket> {
            override fun decode(buf: PacketByteBuf): GeneralSettingsSyncS2CPacket {
                return GeneralSettingsSyncS2CPacket(
                    discordUrl = buf.readString(),
                    discordEnabled = buf.readBoolean(),
                    pokeWikiEnabled = buf.readBoolean(),
                    pastureRange = buf.readVarInt(),
                    maxWorkingPokemonPerPasture = buf.readVarInt(),
                    harvesterDownwardLimit = buf.readVarInt()
                )
            }

            override fun encode(buf: PacketByteBuf, packet: GeneralSettingsSyncS2CPacket) {
                buf.writeString(packet.discordUrl)
                buf.writeBoolean(packet.discordEnabled)
                buf.writeBoolean(packet.pokeWikiEnabled)
                buf.writeVarInt(packet.pastureRange)
                buf.writeVarInt(packet.maxWorkingPokemonPerPasture)
                buf.writeVarInt(packet.harvesterDownwardLimit)
            }
        }
    }

    override fun getId(): CustomPayload.Id<GeneralSettingsSyncS2CPacket> = ID
}

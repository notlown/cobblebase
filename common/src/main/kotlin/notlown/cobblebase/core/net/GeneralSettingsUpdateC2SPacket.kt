package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Client -> Server: admin updates general settings.
 */
data class GeneralSettingsUpdateC2SPacket(
    val discordUrl: String,
    val discordEnabled: Boolean,
    val pokeWikiEnabled: Boolean,
    val pastureRange: Int = 0,
    val maxWorkingPokemonPerPasture: Int = 0,
    val belowPastureReach: Int = 6
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<GeneralSettingsUpdateC2SPacket>(Identifier.of(Cobblebase.MODID, "general_settings_update"))

        val CODEC: PacketCodec<PacketByteBuf, GeneralSettingsUpdateC2SPacket> = object : PacketCodec<PacketByteBuf, GeneralSettingsUpdateC2SPacket> {
            override fun decode(buf: PacketByteBuf): GeneralSettingsUpdateC2SPacket {
                return GeneralSettingsUpdateC2SPacket(
                    discordUrl = buf.readString(),
                    discordEnabled = buf.readBoolean(),
                    pokeWikiEnabled = buf.readBoolean(),
                    pastureRange = buf.readVarInt(),
                    maxWorkingPokemonPerPasture = buf.readVarInt(),
                    belowPastureReach = buf.readVarInt()
                )
            }

            override fun encode(buf: PacketByteBuf, packet: GeneralSettingsUpdateC2SPacket) {
                buf.writeString(packet.discordUrl)
                buf.writeBoolean(packet.discordEnabled)
                buf.writeBoolean(packet.pokeWikiEnabled)
                buf.writeVarInt(packet.pastureRange)
                buf.writeVarInt(packet.maxWorkingPokemonPerPasture)
                buf.writeVarInt(packet.belowPastureReach)
            }
        }
    }

    override fun getId(): CustomPayload.Id<GeneralSettingsUpdateC2SPacket> = ID
}

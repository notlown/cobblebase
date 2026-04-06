package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -> Client: sends list of all species names + which are overridden.
 * Skills are loaded lazily via AdminSpeciesSkillsRequestC2SPacket when needed.
 */
data class AdminSpeciesSyncS2CPacket(
    val allSpecies: List<String>,
    val overriddenSpecies: Set<String>
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<AdminSpeciesSyncS2CPacket>(Identifier.of(Cobblebase.MODID, "admin_species_sync"))

        val CODEC: PacketCodec<PacketByteBuf, AdminSpeciesSyncS2CPacket> = object : PacketCodec<PacketByteBuf, AdminSpeciesSyncS2CPacket> {
            override fun decode(buf: PacketByteBuf): AdminSpeciesSyncS2CPacket {
                val speciesCount = buf.readVarInt()
                val allSpecies = mutableListOf<String>()
                repeat(speciesCount) {
                    allSpecies.add(buf.readString())
                }

                val overriddenCount = buf.readVarInt()
                val overriddenSpecies = mutableSetOf<String>()
                repeat(overriddenCount) {
                    overriddenSpecies.add(buf.readString())
                }

                return AdminSpeciesSyncS2CPacket(allSpecies, overriddenSpecies)
            }

            override fun encode(buf: PacketByteBuf, packet: AdminSpeciesSyncS2CPacket) {
                buf.writeVarInt(packet.allSpecies.size)
                for (species in packet.allSpecies) {
                    buf.writeString(species)
                }

                buf.writeVarInt(packet.overriddenSpecies.size)
                for (species in packet.overriddenSpecies) {
                    buf.writeString(species)
                }
            }
        }
    }

    override fun getId(): CustomPayload.Id<AdminSpeciesSyncS2CPacket> = ID
}

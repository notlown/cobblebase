package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.SkillEntry
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -> Client: broadcasts every species skill override that currently
 * exists in `SpeciesSkillOverrides` so the client can apply them to its
 * local `SpeciesSkillRegistry` and surface them in the Pasture Skills /
 * Buffs tabs.
 *
 * Without this sync the client only knows the bundled defaults from the
 * jar, which is why overrides set via `/cobblebase admin` never showed up
 * in the Pasture Skills tab for players on a dedicated server — they
 * "worked in singleplayer" only because SP shares the registry instance
 * between server and client.
 *
 * Sent on player join and re-sent after every successful admin update.
 */
data class SpeciesOverrideSyncS2CPacket(
    /** species name (lowercase) -> ordered skill list */
    val overrides: Map<String, List<SkillEntry>>
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<SpeciesOverrideSyncS2CPacket>(Identifier.of(Cobblebase.MODID, "species_override_sync"))

        val CODEC: PacketCodec<PacketByteBuf, SpeciesOverrideSyncS2CPacket> = object : PacketCodec<PacketByteBuf, SpeciesOverrideSyncS2CPacket> {
            override fun decode(buf: PacketByteBuf): SpeciesOverrideSyncS2CPacket {
                val count = buf.readVarInt()
                val map = HashMap<String, List<SkillEntry>>(count)
                repeat(count) {
                    val species = buf.readString()
                    val n = buf.readVarInt()
                    val skills = ArrayList<SkillEntry>(n)
                    repeat(n) {
                        skills.add(SkillEntry(buf.readString(), buf.readVarInt()))
                    }
                    map[species] = skills
                }
                return SpeciesOverrideSyncS2CPacket(map)
            }

            override fun encode(buf: PacketByteBuf, packet: SpeciesOverrideSyncS2CPacket) {
                buf.writeVarInt(packet.overrides.size)
                for ((species, skills) in packet.overrides) {
                    buf.writeString(species)
                    buf.writeVarInt(skills.size)
                    for (entry in skills) {
                        buf.writeString(entry.skillId)
                        buf.writeVarInt(entry.proficiency)
                    }
                }
            }
        }
    }

    override fun getId(): CustomPayload.Id<SpeciesOverrideSyncS2CPacket> = ID
}

package notlown.cobblebase.core.net

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import notlown.cobblebase.core.Cobblebase

/**
 * Server → Client: the player's owned Pokemon arranged in Cobblemon's native box layout —
 * one "Party" box (6 slots) plus every PC box (typically 30 slots / 6×5). Slots preserve
 * the actual position in storage so the in-game grid mirrors the player's PC 1:1.
 */
data class MyPokemonSyncS2CPacket(
    val boxes: List<Box>
) : CustomPayload {

    /** A logical container (Party or one PC box). Empty slots are encoded as null entries. */
    data class Box(
        val name: String,
        val cols: Int,
        val slots: List<Entry?>
    )

    /** One occupied slot. Skill IDs are re-resolved client-side from SpeciesSkillRegistry. */
    data class Entry(
        val species: String,
        val displayName: String,
        val level: Int,
        val skillIds: List<String>,
        /**
         * Cobblemon aspects on the actual stored Pokemon (gender, shiny, regional form,
         * mega, etc.). Without these the client renders the default sprite, which doesn't
         * exist for species like Pyroar (female-only sprite) or Hisuian/Alolan variants —
         * the icon then falls back to a colored badge.
         */
        val aspects: List<String> = emptyList(),
        /**
         * Canonical species resource id as a string ("modid:species_path"). Sent from the
         * server because re-resolving by name on the client has edge cases that fail for
         * some fakemons whose display name doesn't match Cobblemon's strict ID strip rules.
         * Empty when the server couldn't read it (legacy clients / unknown species).
         */
        val speciesId: String = ""
    )

    companion object {
        val ID = CustomPayload.Id<MyPokemonSyncS2CPacket>(
            Identifier.of(Cobblebase.MODID, "my_pokemon_sync")
        )
        val CODEC: PacketCodec<PacketByteBuf, MyPokemonSyncS2CPacket> =
            object : PacketCodec<PacketByteBuf, MyPokemonSyncS2CPacket> {
                override fun decode(buf: PacketByteBuf): MyPokemonSyncS2CPacket {
                    val boxN = buf.readVarInt()
                    val boxes = ArrayList<Box>(boxN)
                    repeat(boxN) {
                        val name = buf.readString()
                        val cols = buf.readVarInt()
                        val slotN = buf.readVarInt()
                        val slots = ArrayList<Entry?>(slotN)
                        repeat(slotN) {
                            val occupied = buf.readBoolean()
                            slots.add(if (!occupied) null else {
                                val species = buf.readString()
                                val display = buf.readString()
                                val level = buf.readVarInt()
                                val m = buf.readVarInt()
                                val ids = ArrayList<String>(m).also { l -> repeat(m) { l.add(buf.readString()) } }
                                val a = buf.readVarInt()
                                val aspects = ArrayList<String>(a).also { l -> repeat(a) { l.add(buf.readString()) } }
                                val sid = buf.readString()
                                Entry(species, display, level, ids, aspects, sid)
                            })
                        }
                        boxes.add(Box(name, cols, slots))
                    }
                    return MyPokemonSyncS2CPacket(boxes)
                }
                override fun encode(buf: PacketByteBuf, packet: MyPokemonSyncS2CPacket) {
                    buf.writeVarInt(packet.boxes.size)
                    for (box in packet.boxes) {
                        buf.writeString(box.name)
                        buf.writeVarInt(box.cols)
                        buf.writeVarInt(box.slots.size)
                        for (s in box.slots) {
                            if (s == null) buf.writeBoolean(false)
                            else {
                                buf.writeBoolean(true)
                                buf.writeString(s.species)
                                buf.writeString(s.displayName)
                                buf.writeVarInt(s.level)
                                buf.writeVarInt(s.skillIds.size)
                                for (id in s.skillIds) buf.writeString(id)
                                buf.writeVarInt(s.aspects.size)
                                for (a in s.aspects) buf.writeString(a)
                                buf.writeString(s.speciesId)
                            }
                        }
                    }
                }
            }
    }
    override fun getId() = ID
}

package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Client -> Server: admin toggles one or more recipes on/off.
 *
 * Batches an arbitrary set of recipeId → enabled changes. Single-toggle clicks send
 * a packet with one entry; the "enable/disable category" button sends a packet with
 * the whole category in one go. Server applies, persists, and re-broadcasts a sync
 * snapshot to all players.
 */
data class RecipeOverridesUpdateC2SPacket(
    val changes: Map<String, Boolean>
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<RecipeOverridesUpdateC2SPacket>(
            Identifier.of(Cobblebase.MODID, "recipe_overrides_update")
        )

        val CODEC: PacketCodec<PacketByteBuf, RecipeOverridesUpdateC2SPacket> =
            object : PacketCodec<PacketByteBuf, RecipeOverridesUpdateC2SPacket> {
                override fun decode(buf: PacketByteBuf): RecipeOverridesUpdateC2SPacket {
                    val n = buf.readVarInt()
                    val map = HashMap<String, Boolean>(n.coerceAtLeast(0))
                    repeat(n) {
                        val id = buf.readString()
                        val enabled = buf.readBoolean()
                        map[id] = enabled
                    }
                    return RecipeOverridesUpdateC2SPacket(map)
                }

                override fun encode(buf: PacketByteBuf, packet: RecipeOverridesUpdateC2SPacket) {
                    buf.writeVarInt(packet.changes.size)
                    for ((id, enabled) in packet.changes) {
                        buf.writeString(id)
                        buf.writeBoolean(enabled)
                    }
                }
            }
    }

    override fun getId(): CustomPayload.Id<RecipeOverridesUpdateC2SPacket> = ID
}

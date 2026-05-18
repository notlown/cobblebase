package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -> Client: full snapshot of disabled recipe IDs for the Craftsman job.
 * Sent on player join and on every admin update. The wire format is just the set
 * of disabled IDs — "enabled" is the default for unlisted recipes.
 */
data class RecipeOverridesSyncS2CPacket(
    val disabledRecipeIds: Set<String>
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<RecipeOverridesSyncS2CPacket>(
            Identifier.of(Cobblebase.MODID, "recipe_overrides_sync")
        )

        val CODEC: PacketCodec<PacketByteBuf, RecipeOverridesSyncS2CPacket> =
            object : PacketCodec<PacketByteBuf, RecipeOverridesSyncS2CPacket> {
                override fun decode(buf: PacketByteBuf): RecipeOverridesSyncS2CPacket {
                    val n = buf.readVarInt()
                    val set = HashSet<String>(n.coerceAtLeast(0))
                    repeat(n) { set.add(buf.readString()) }
                    return RecipeOverridesSyncS2CPacket(set)
                }

                override fun encode(buf: PacketByteBuf, packet: RecipeOverridesSyncS2CPacket) {
                    buf.writeVarInt(packet.disabledRecipeIds.size)
                    for (id in packet.disabledRecipeIds) buf.writeString(id)
                }
            }
    }

    override fun getId(): CustomPayload.Id<RecipeOverridesSyncS2CPacket> = ID
}

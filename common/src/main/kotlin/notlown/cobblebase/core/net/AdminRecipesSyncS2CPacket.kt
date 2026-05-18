package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -> Client: full unfiltered recipe list for the admin Recipes tab. Each
 * entry carries enough metadata to render the row (id + display name + category)
 * without needing a second lookup. The disabled set is bundled so the client can
 * paint each row as enabled/disabled in one pass.
 */
data class AdminRecipesSyncS2CPacket(
    val recipes: List<Entry>,
    val disabledIds: Set<String>
) : CustomPayload {

    data class Entry(
        val recipeId: String,
        val outputItemId: String,
        val outputDisplayName: String,
        val category: String,
        val subCategory: String,
    )

    companion object {
        val ID = CustomPayload.Id<AdminRecipesSyncS2CPacket>(
            Identifier.of(Cobblebase.MODID, "admin_recipes_sync")
        )

        val CODEC: PacketCodec<PacketByteBuf, AdminRecipesSyncS2CPacket> =
            object : PacketCodec<PacketByteBuf, AdminRecipesSyncS2CPacket> {
                override fun decode(buf: PacketByteBuf): AdminRecipesSyncS2CPacket {
                    val recipeCount = buf.readVarInt()
                    val recipes = ArrayList<Entry>(recipeCount.coerceAtLeast(0))
                    repeat(recipeCount) {
                        recipes.add(Entry(
                            recipeId = buf.readString(),
                            outputItemId = buf.readString(),
                            outputDisplayName = buf.readString(),
                            category = buf.readString(),
                            subCategory = buf.readString(),
                        ))
                    }
                    val disabledCount = buf.readVarInt()
                    val disabled = HashSet<String>(disabledCount.coerceAtLeast(0))
                    repeat(disabledCount) { disabled.add(buf.readString()) }
                    return AdminRecipesSyncS2CPacket(recipes, disabled)
                }

                override fun encode(buf: PacketByteBuf, packet: AdminRecipesSyncS2CPacket) {
                    buf.writeVarInt(packet.recipes.size)
                    for (r in packet.recipes) {
                        buf.writeString(r.recipeId)
                        buf.writeString(r.outputItemId)
                        buf.writeString(r.outputDisplayName)
                        buf.writeString(r.category)
                        buf.writeString(r.subCategory)
                    }
                    buf.writeVarInt(packet.disabledIds.size)
                    for (id in packet.disabledIds) buf.writeString(id)
                }
            }
    }

    override fun getId(): CustomPayload.Id<AdminRecipesSyncS2CPacket> = ID
}

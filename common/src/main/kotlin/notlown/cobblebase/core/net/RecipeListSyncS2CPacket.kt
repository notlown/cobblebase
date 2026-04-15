package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -> Client: sends the full list of craftable recipes for the Workshop GUI.
 * Sent once when the player opens the Workshop tab.
 */
data class RecipeListSyncS2CPacket(
    val recipes: List<RecipeDTO>
) : CustomPayload {

    data class RecipeDTO(
        val recipeId: String,
        val outputItemId: String,
        val outputCount: Int,
        val outputDisplayName: String,
        val inputs: List<Pair<String, Int>>,
        val category: String
    )

    companion object {
        val ID = CustomPayload.Id<RecipeListSyncS2CPacket>(Identifier.of(Cobblebase.MODID, "recipe_list_sync"))

        val CODEC: PacketCodec<PacketByteBuf, RecipeListSyncS2CPacket> = object : PacketCodec<PacketByteBuf, RecipeListSyncS2CPacket> {
            override fun decode(buf: PacketByteBuf): RecipeListSyncS2CPacket {
                val count = buf.readVarInt()
                val recipes = mutableListOf<RecipeDTO>()
                repeat(count) {
                    val recipeId = buf.readString()
                    val outputItemId = buf.readString()
                    val outputCount = buf.readVarInt()
                    val outputDisplayName = buf.readString()
                    val inputCount = buf.readVarInt()
                    val inputs = mutableListOf<Pair<String, Int>>()
                    repeat(inputCount) {
                        inputs.add(buf.readString() to buf.readVarInt())
                    }
                    val category = buf.readString()
                    recipes.add(RecipeDTO(recipeId, outputItemId, outputCount, outputDisplayName, inputs, category))
                }
                return RecipeListSyncS2CPacket(recipes)
            }

            override fun encode(buf: PacketByteBuf, packet: RecipeListSyncS2CPacket) {
                buf.writeVarInt(packet.recipes.size)
                for (recipe in packet.recipes) {
                    buf.writeString(recipe.recipeId)
                    buf.writeString(recipe.outputItemId)
                    buf.writeVarInt(recipe.outputCount)
                    buf.writeString(recipe.outputDisplayName)
                    buf.writeVarInt(recipe.inputs.size)
                    for ((itemId, count) in recipe.inputs) {
                        buf.writeString(itemId)
                        buf.writeVarInt(count)
                    }
                    buf.writeString(recipe.category)
                }
            }
        }
    }

    override fun getId(): CustomPayload.Id<RecipeListSyncS2CPacket> = ID
}

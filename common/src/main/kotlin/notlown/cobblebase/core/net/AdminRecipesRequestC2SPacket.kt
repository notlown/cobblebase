package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Client -> Server: admin asks for the full recipe list (including admin-disabled
 * ones) so the Admin → Jobs → Craftsman → Recipes tab can render the toggle list.
 *
 * Distinct from the Workshop's player-facing recipe-list request because the
 * Workshop's packet filters out disabled recipes — the admin tab must see them all.
 */
data object AdminRecipesRequestC2SPacket : CustomPayload {
    val ID = CustomPayload.Id<AdminRecipesRequestC2SPacket>(
        Identifier.of(Cobblebase.MODID, "admin_recipes_request")
    )

    val CODEC: PacketCodec<PacketByteBuf, AdminRecipesRequestC2SPacket> =
        object : PacketCodec<PacketByteBuf, AdminRecipesRequestC2SPacket> {
            override fun decode(buf: PacketByteBuf) = AdminRecipesRequestC2SPacket
            override fun encode(buf: PacketByteBuf, packet: AdminRecipesRequestC2SPacket) {}
        }

    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
}

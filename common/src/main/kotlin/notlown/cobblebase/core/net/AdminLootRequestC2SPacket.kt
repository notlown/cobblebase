package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Client -> Server: request the full loot table list (default + overridden).
 */
class AdminLootRequestC2SPacket : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<AdminLootRequestC2SPacket>(Identifier.of(Cobblebase.MODID, "admin_loot_request"))
        val CODEC: PacketCodec<PacketByteBuf, AdminLootRequestC2SPacket> = object : PacketCodec<PacketByteBuf, AdminLootRequestC2SPacket> {
            override fun decode(buf: PacketByteBuf) = AdminLootRequestC2SPacket()
            override fun encode(buf: PacketByteBuf, packet: AdminLootRequestC2SPacket) {}
        }
    }
    override fun getId(): CustomPayload.Id<AdminLootRequestC2SPacket> = ID
}

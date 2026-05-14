package notlown.cobblebase.core.net

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import notlown.cobblebase.core.Cobblebase

/** Client → Server: request the player's owned-Pokemon list (Party + PC). */
class MyPokemonRequestC2SPacket : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<MyPokemonRequestC2SPacket>(
            Identifier.of(Cobblebase.MODID, "my_pokemon_request")
        )
        val CODEC: PacketCodec<PacketByteBuf, MyPokemonRequestC2SPacket> =
            object : PacketCodec<PacketByteBuf, MyPokemonRequestC2SPacket> {
                override fun decode(buf: PacketByteBuf) = MyPokemonRequestC2SPacket()
                override fun encode(buf: PacketByteBuf, packet: MyPokemonRequestC2SPacket) {}
            }
    }
    override fun getId() = ID
}

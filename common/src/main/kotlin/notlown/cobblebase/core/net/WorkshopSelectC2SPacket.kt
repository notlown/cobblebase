package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.WorkshopManager
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * Client -> Server: player selects a crafting project for a Craftsman Pokemon.
 */
data class WorkshopSelectC2SPacket(
    val pokemonId: UUID,
    val recipeId: String  // empty = clear project
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<WorkshopSelectC2SPacket>(Identifier.of(Cobblebase.MODID, "workshop_select"))

        val CODEC: PacketCodec<PacketByteBuf, WorkshopSelectC2SPacket> = object : PacketCodec<PacketByteBuf, WorkshopSelectC2SPacket> {
            override fun decode(buf: PacketByteBuf): WorkshopSelectC2SPacket {
                val pokemonId = buf.readUuid()
                val recipeId = buf.readString()
                return WorkshopSelectC2SPacket(pokemonId, recipeId)
            }

            override fun encode(buf: PacketByteBuf, packet: WorkshopSelectC2SPacket) {
                buf.writeUuid(packet.pokemonId)
                buf.writeString(packet.recipeId)
            }
        }
    }

    override fun getId(): CustomPayload.Id<WorkshopSelectC2SPacket> = ID

    fun handle(player: ServerPlayerEntity) {
        val world = player.serverWorld
        if (recipeId.isBlank()) {
            WorkshopManager.clearProject(pokemonId)
        } else {
            WorkshopManager.setProject(pokemonId, recipeId)
        }
        WorkshopManager.save(world)
        Cobblebase.log("[Workshop] ${player.name.string} set project for $pokemonId: '$recipeId'")
    }
}

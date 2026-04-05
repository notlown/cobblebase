package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.PastureSettings
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

/**
 * Client -> Server: update the search radius for the nearest pasture block.
 */
data class PastureSettingsUpdateC2SPacket(
    val searchRadius: Int
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<PastureSettingsUpdateC2SPacket>(Identifier.of(Cobblebase.MODID, "pasture_settings_update"))

        val CODEC: PacketCodec<PacketByteBuf, PastureSettingsUpdateC2SPacket> = object : PacketCodec<PacketByteBuf, PastureSettingsUpdateC2SPacket> {
            override fun decode(buf: PacketByteBuf): PastureSettingsUpdateC2SPacket {
                return PastureSettingsUpdateC2SPacket(buf.readVarInt())
            }
            override fun encode(buf: PacketByteBuf, packet: PastureSettingsUpdateC2SPacket) {
                buf.writeVarInt(packet.searchRadius)
            }
        }
    }

    override fun getId(): CustomPayload.Id<PastureSettingsUpdateC2SPacket> = ID

    fun handle(player: ServerPlayerEntity) {
        val world = player.serverWorld
        val playerPos = player.blockPos

        // Find nearest pasture block
        var nearestPos: BlockPos? = null
        var nearestDist = Double.MAX_VALUE
        val scanRadius = 16

        for (x in -scanRadius..scanRadius) {
            for (y in -scanRadius..scanRadius) {
                for (z in -scanRadius..scanRadius) {
                    val pos = playerPos.add(x, y, z)
                    val blockEntity = world.getBlockEntity(pos)
                    if (blockEntity is PokemonPastureBlockEntity) {
                        val dist = pos.getSquaredDistance(playerPos)
                        if (dist < nearestDist) {
                            nearestDist = dist
                            nearestPos = pos
                        }
                    }
                }
            }
        }

        val pasturePos = nearestPos
        if (pasturePos == null) {
            Cobblebase.LOGGER.warn("[Cobblebase] Player ${player.name.string} tried to update pasture settings but no pasture found nearby")
            return
        }

        // Clamp to admin range
        val range = PastureSettings.getGlobalRadiusRange()
        val clampedRadius = searchRadius.coerceIn(range.first, range.second)

        PastureSettings.set(pasturePos, PastureSettings.Settings(searchRadius = clampedRadius))
        PastureSettings.save(world)
        Cobblebase.LOGGER.info("[Cobblebase] Pasture settings updated at $pasturePos by ${player.name.string}: radius=$clampedRadius")
    }
}

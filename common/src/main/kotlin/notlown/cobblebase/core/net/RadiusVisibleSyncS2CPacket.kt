package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

/**
 * Server → all clients: the current set of pastures whose Show-Radius wireframe
 * is visible. Full-replace each broadcast — easier to reason about than diffs.
 */
data class RadiusVisibleSyncS2CPacket(val positions: List<BlockPos>) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<RadiusVisibleSyncS2CPacket>(
            Identifier.of(Cobblebase.MODID, "radius_visible_sync")
        )

        val CODEC: PacketCodec<PacketByteBuf, RadiusVisibleSyncS2CPacket> =
            object : PacketCodec<PacketByteBuf, RadiusVisibleSyncS2CPacket> {
                override fun decode(buf: PacketByteBuf): RadiusVisibleSyncS2CPacket {
                    val n = buf.readVarInt()
                    val list = ArrayList<BlockPos>(n)
                    repeat(n) { list.add(buf.readBlockPos()) }
                    return RadiusVisibleSyncS2CPacket(list)
                }
                override fun encode(buf: PacketByteBuf, packet: RadiusVisibleSyncS2CPacket) {
                    buf.writeVarInt(packet.positions.size)
                    for (p in packet.positions) buf.writeBlockPos(p)
                }
            }
    }

    override fun getId(): CustomPayload.Id<RadiusVisibleSyncS2CPacket> = ID
}

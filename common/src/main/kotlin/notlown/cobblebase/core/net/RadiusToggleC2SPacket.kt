package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

/**
 * Client → Server: the pasture owner asks to flip the Show-Radius wireframe
 * for [pasturePos] in or out of the global visible set. Server validates
 * ownership (BaseManager.canEditPasture) before mutating + broadcasting.
 */
data class RadiusToggleC2SPacket(val pasturePos: BlockPos) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<RadiusToggleC2SPacket>(
            Identifier.of(Cobblebase.MODID, "radius_toggle")
        )

        val CODEC: PacketCodec<PacketByteBuf, RadiusToggleC2SPacket> =
            object : PacketCodec<PacketByteBuf, RadiusToggleC2SPacket> {
                override fun decode(buf: PacketByteBuf) = RadiusToggleC2SPacket(buf.readBlockPos())
                override fun encode(buf: PacketByteBuf, packet: RadiusToggleC2SPacket) {
                    buf.writeBlockPos(packet.pasturePos)
                }
            }
    }

    override fun getId(): CustomPayload.Id<RadiusToggleC2SPacket> = ID
}

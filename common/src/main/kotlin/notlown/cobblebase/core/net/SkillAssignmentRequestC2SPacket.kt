package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Client -> Server: request current skill assignments.
 * The server responds with a SkillAssignmentSyncS2CPacket.
 */
class SkillAssignmentRequestC2SPacket : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<SkillAssignmentRequestC2SPacket>(Identifier.of(Cobblebase.MODID, "skill_request"))

        val CODEC: PacketCodec<PacketByteBuf, SkillAssignmentRequestC2SPacket> = object : PacketCodec<PacketByteBuf, SkillAssignmentRequestC2SPacket> {
            override fun decode(buf: PacketByteBuf): SkillAssignmentRequestC2SPacket {
                return SkillAssignmentRequestC2SPacket()
            }
            override fun encode(buf: PacketByteBuf, packet: SkillAssignmentRequestC2SPacket) {
                // No data needed
            }
        }
    }

    override fun getId(): CustomPayload.Id<SkillAssignmentRequestC2SPacket> = ID
}

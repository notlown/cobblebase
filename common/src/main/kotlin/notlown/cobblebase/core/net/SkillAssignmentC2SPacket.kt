package notlown.cobblebase.core.net

import notlown.cobblebase.core.BaseManager
import notlown.cobblebase.core.Cobblebase
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import java.util.UUID

data class SkillAssignmentC2SPacket(
    val pokemonId: UUID,
    val skillId: String // empty = auto
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<SkillAssignmentC2SPacket>(Identifier.of(Cobblebase.MODID, "skill_assign"))

        val CODEC: PacketCodec<PacketByteBuf, SkillAssignmentC2SPacket> = object : PacketCodec<PacketByteBuf, SkillAssignmentC2SPacket> {
            override fun decode(buf: PacketByteBuf): SkillAssignmentC2SPacket {
                return SkillAssignmentC2SPacket(buf.readUuid(), buf.readString())
            }
            override fun encode(buf: PacketByteBuf, packet: SkillAssignmentC2SPacket) {
                buf.writeUuid(packet.pokemonId)
                buf.writeString(packet.skillId)
            }
        }
    }

    override fun getId(): CustomPayload.Id<SkillAssignmentC2SPacket> = ID

    fun handle(player: ServerPlayerEntity) {
        val id = if (skillId.isEmpty()) null else skillId
        // Working-cap pre-check: refuse new (non-null) assignments that would push the
        // pasture past the admin-configured cap. The caller broadcasts a sync packet
        // regardless of whether `handle` mutated state, so the client cache flips its
        // optimistic update back when we no-op here.
        if (id != null) {
            val pasturePos = BaseManager.findPokemonPasture(player, pokemonId)
            if (pasturePos != null &&
                BaseManager.wouldExceedWorkingCap(player.serverWorld, pasturePos, pokemonId, id)) {
                return
            }
        }
        BaseManager.assignSkill(pokemonId, id)
    }
}

package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.LootEntry
import notlown.cobblebase.core.LootHelper
import notlown.cobblebase.core.LootOverrides
import notlown.cobblebase.core.LootTableDef
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

/**
 * Client -> Server: write a single loot table override (or reset it).
 *
 * If [reset] is true, [LootOverrides.removeOverride] is called and the entries
 * payload is ignored. Otherwise the entries become the new override for [id].
 *
 * The handler also checks whether the new entries equal the bundled default —
 * if so, the override is removed instead of stored. This keeps the on-disk
 * file clean.
 */
data class AdminLootUpdateC2SPacket(
    val id: String,
    val reset: Boolean,
    val rolls: Int,
    val entries: List<LootEntry>
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<AdminLootUpdateC2SPacket>(Identifier.of(Cobblebase.MODID, "admin_loot_update"))

        val CODEC: PacketCodec<PacketByteBuf, AdminLootUpdateC2SPacket> = object : PacketCodec<PacketByteBuf, AdminLootUpdateC2SPacket> {
            override fun decode(buf: PacketByteBuf): AdminLootUpdateC2SPacket {
                val id = buf.readString()
                val reset = buf.readBoolean()
                val rolls = buf.readVarInt()
                val n = buf.readVarInt()
                val entries = ArrayList<LootEntry>(n)
                repeat(n) {
                    entries.add(
                        LootEntry(
                            itemId = buf.readString(),
                            weight = buf.readVarInt(),
                            minCount = buf.readVarInt(),
                            maxCount = buf.readVarInt()
                        )
                    )
                }
                return AdminLootUpdateC2SPacket(id, reset, rolls, entries)
            }

            override fun encode(buf: PacketByteBuf, packet: AdminLootUpdateC2SPacket) {
                buf.writeString(packet.id)
                buf.writeBoolean(packet.reset)
                buf.writeVarInt(packet.rolls)
                buf.writeVarInt(packet.entries.size)
                for (e in packet.entries) {
                    buf.writeString(e.itemId)
                    buf.writeVarInt(e.weight)
                    buf.writeVarInt(e.minCount)
                    buf.writeVarInt(e.maxCount)
                }
            }
        }
    }

    override fun getId(): CustomPayload.Id<AdminLootUpdateC2SPacket> = ID

    fun handle(player: ServerPlayerEntity) {
        if (!player.hasPermissionLevel(2)) {
            Cobblebase.LOGGER.warn("[Cobblebase] ${player.name.string} tried to update loot without OP")
            return
        }
        val world = player.serverWorld
        if (reset) {
            LootOverrides.removeOverride(id)
        } else {
            // If the new entries match the bundled default exactly, drop the override
            val def = LootTableDef(id, rolls, entries)
            val bundled = LootHelper.getEffective(id) // walks override > default
            if (bundled != null &&
                bundled.rolls == rolls &&
                bundled.entries == entries &&
                !LootOverrides.hasOverride(id)
            ) {
                // Equal to default, no override needed
            } else if (notlown.cobblebase.core.CobblebaseLootRegistry.get(id)?.let {
                    it.rolls == rolls && it.entries == entries
                } == true) {
                LootOverrides.removeOverride(id)
            } else {
                LootOverrides.setOverride(def)
            }
        }
        LootOverrides.save(world)
        Cobblebase.LOGGER.info("[Cobblebase] Loot override updated for '$id' by ${player.name.string}")
    }
}

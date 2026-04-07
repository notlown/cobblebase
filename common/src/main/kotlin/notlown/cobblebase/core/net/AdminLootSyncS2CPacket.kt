package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.LootEntry
import notlown.cobblebase.core.LootTableDef
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -> Client: full snapshot of all bundled loot tables overlaid with
 * any active overrides. Each table is sent in its already-merged "effective"
 * form so the client doesn't need to know about the override layer.
 *
 * The set of [overriddenIds] tells the client which entries have an active
 * admin override (so the GUI can render the "modified" badge and the reset
 * button).
 */
data class AdminLootSyncS2CPacket(
    val tables: List<LootTableDef>,
    val overriddenIds: Set<String>,
    /** For each table id, the set of item ids that exist in the bundled default
     *  (used by the GUI to know which entries get an On/Off toggle vs a delete). */
    val defaultItemIds: Map<String, List<String>>
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<AdminLootSyncS2CPacket>(Identifier.of(Cobblebase.MODID, "admin_loot_sync"))

        val CODEC: PacketCodec<PacketByteBuf, AdminLootSyncS2CPacket> = object : PacketCodec<PacketByteBuf, AdminLootSyncS2CPacket> {
            override fun decode(buf: PacketByteBuf): AdminLootSyncS2CPacket {
                val count = buf.readVarInt()
                val tables = ArrayList<LootTableDef>(count)
                repeat(count) {
                    val id = buf.readString()
                    val rolls = buf.readVarInt()
                    val entryCount = buf.readVarInt()
                    val entries = ArrayList<LootEntry>(entryCount)
                    repeat(entryCount) {
                        entries.add(
                            LootEntry(
                                itemId = buf.readString(),
                                weight = buf.readVarInt(),
                                minCount = buf.readVarInt(),
                                maxCount = buf.readVarInt(),
                                disabled = buf.readBoolean()
                            )
                        )
                    }
                    tables.add(LootTableDef(id, rolls, entries))
                }
                val overrideCount = buf.readVarInt()
                val overridden = HashSet<String>(overrideCount)
                repeat(overrideCount) { overridden.add(buf.readString()) }

                val defCount = buf.readVarInt()
                val defaults = HashMap<String, List<String>>(defCount)
                repeat(defCount) {
                    val tid = buf.readString()
                    val n = buf.readVarInt()
                    val items = ArrayList<String>(n)
                    repeat(n) { items.add(buf.readString()) }
                    defaults[tid] = items
                }
                return AdminLootSyncS2CPacket(tables, overridden, defaults)
            }

            override fun encode(buf: PacketByteBuf, packet: AdminLootSyncS2CPacket) {
                buf.writeVarInt(packet.tables.size)
                for (def in packet.tables) {
                    buf.writeString(def.id)
                    buf.writeVarInt(def.rolls)
                    buf.writeVarInt(def.entries.size)
                    for (e in def.entries) {
                        buf.writeString(e.itemId)
                        buf.writeVarInt(e.weight)
                        buf.writeVarInt(e.minCount)
                        buf.writeVarInt(e.maxCount)
                        buf.writeBoolean(e.disabled)
                    }
                }
                buf.writeVarInt(packet.overriddenIds.size)
                for (id in packet.overriddenIds) buf.writeString(id)

                buf.writeVarInt(packet.defaultItemIds.size)
                for ((tid, items) in packet.defaultItemIds) {
                    buf.writeString(tid)
                    buf.writeVarInt(items.size)
                    for (i in items) buf.writeString(i)
                }
            }
        }
    }

    override fun getId(): CustomPayload.Id<AdminLootSyncS2CPacket> = ID
}

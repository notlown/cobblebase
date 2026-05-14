package notlown.cobblebase.core.net

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import notlown.cobblebase.core.Cobblebase

/**
 * Server → Client: full hatchery log + summary stats + per-pasture live state for the
 * Hatchery GUI tab.
 *
 * The historical fields ([entries], [totalEver], etc.) are global. The live fields
 * ([activeHatchers], [availableEggs]) are scoped to the [pasturePos] the client sent
 * in its request — they describe *that* pasture only.
 */
data class HatchLogSyncS2CPacket(
    val entries: List<Entry>,
    val totalEver: Int,
    val totalThisSession: Int,
    val uniqueSpecies: Int,
    val topHatchers: List<Pair<String, Int>>,
    val activeHatchers: List<ActiveHatcher>,
    val availableEggs: Int
) : CustomPayload {

    data class Entry(
        val realTimestamp: Long,
        val worldTime: Long,
        val hatchedSpecies: String,
        val hatcherSpecies: String,
        val hatcherDisplayName: String,
        val px: Int, val py: Int, val pz: Int,
        val proficiency: Int
    )

    /**
     * One row in the live "Active Hatchers" panel. [progress] is `1 - currentTimer / initialTimer`
     * clamped to [0, 1]. Sent pre-clamped so the GUI just draws a bar.
     */
    data class ActiveHatcher(
        val hatcherSpecies: String,
        val hatcherDisplayName: String,
        val eggSpecies: String,
        val initialTimer: Int,
        val currentTimer: Int,
        val proficiency: Int
    )

    companion object {
        val ID = CustomPayload.Id<HatchLogSyncS2CPacket>(
            Identifier.of(Cobblebase.MODID, "hatch_log_sync")
        )
        val CODEC: PacketCodec<PacketByteBuf, HatchLogSyncS2CPacket> =
            object : PacketCodec<PacketByteBuf, HatchLogSyncS2CPacket> {
                override fun decode(buf: PacketByteBuf): HatchLogSyncS2CPacket {
                    val n = buf.readVarInt()
                    val list = ArrayList<Entry>(n)
                    repeat(n) {
                        list.add(Entry(
                            buf.readLong(), buf.readLong(),
                            buf.readString(), buf.readString(), buf.readString(),
                            buf.readInt(), buf.readInt(), buf.readInt(),
                            buf.readVarInt()
                        ))
                    }
                    val total = buf.readVarInt()
                    val session = buf.readVarInt()
                    val unique = buf.readVarInt()
                    val topN = buf.readVarInt()
                    val top = ArrayList<Pair<String, Int>>(topN)
                    repeat(topN) { top.add(buf.readString() to buf.readVarInt()) }

                    val activeN = buf.readVarInt()
                    val active = ArrayList<ActiveHatcher>(activeN)
                    repeat(activeN) {
                        active.add(ActiveHatcher(
                            buf.readString(), buf.readString(), buf.readString(),
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt()
                        ))
                    }
                    val available = buf.readVarInt()
                    return HatchLogSyncS2CPacket(list, total, session, unique, top, active, available)
                }
                override fun encode(buf: PacketByteBuf, packet: HatchLogSyncS2CPacket) {
                    buf.writeVarInt(packet.entries.size)
                    for (e in packet.entries) {
                        buf.writeLong(e.realTimestamp); buf.writeLong(e.worldTime)
                        buf.writeString(e.hatchedSpecies); buf.writeString(e.hatcherSpecies); buf.writeString(e.hatcherDisplayName)
                        buf.writeInt(e.px); buf.writeInt(e.py); buf.writeInt(e.pz)
                        buf.writeVarInt(e.proficiency)
                    }
                    buf.writeVarInt(packet.totalEver)
                    buf.writeVarInt(packet.totalThisSession)
                    buf.writeVarInt(packet.uniqueSpecies)
                    buf.writeVarInt(packet.topHatchers.size)
                    for ((k, v) in packet.topHatchers) {
                        buf.writeString(k); buf.writeVarInt(v)
                    }
                    buf.writeVarInt(packet.activeHatchers.size)
                    for (a in packet.activeHatchers) {
                        buf.writeString(a.hatcherSpecies); buf.writeString(a.hatcherDisplayName); buf.writeString(a.eggSpecies)
                        buf.writeVarInt(a.initialTimer); buf.writeVarInt(a.currentTimer); buf.writeVarInt(a.proficiency)
                    }
                    buf.writeVarInt(packet.availableEggs)
                }
            }
    }

    override fun getId(): CustomPayload.Id<HatchLogSyncS2CPacket> = ID
}

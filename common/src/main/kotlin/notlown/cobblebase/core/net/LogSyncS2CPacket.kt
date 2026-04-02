package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.LogManager
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -> Client: sends activity log entries for a specific pasture.
 */
data class LogSyncS2CPacket(
    val entries: List<LogManager.LogEntry>
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<LogSyncS2CPacket>(Identifier.of(Cobblebase.MODID, "log_sync"))

        val CODEC: PacketCodec<PacketByteBuf, LogSyncS2CPacket> = object : PacketCodec<PacketByteBuf, LogSyncS2CPacket> {
            override fun decode(buf: PacketByteBuf): LogSyncS2CPacket {
                val count = buf.readVarInt()
                val entries = mutableListOf<LogManager.LogEntry>()
                repeat(count) {
                    entries.add(LogManager.LogEntry(
                        timestamp = buf.readLong(),
                        pokemonName = buf.readString(),
                        action = buf.readString(),
                        itemName = buf.readString(),
                        rarity = LogManager.Rarity.entries[buf.readVarInt()],
                        worldTime = buf.readLong()
                    ))
                }
                return LogSyncS2CPacket(entries)
            }

            override fun encode(buf: PacketByteBuf, packet: LogSyncS2CPacket) {
                buf.writeVarInt(packet.entries.size)
                for (entry in packet.entries) {
                    buf.writeLong(entry.timestamp)
                    buf.writeString(entry.pokemonName)
                    buf.writeString(entry.action)
                    buf.writeString(entry.itemName)
                    buf.writeVarInt(entry.rarity.ordinal)
                    buf.writeLong(entry.worldTime)
                }
            }
        }
    }

    override fun getId(): CustomPayload.Id<LogSyncS2CPacket> = ID
}

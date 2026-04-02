package notlown.cobblebase.core.net

import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.DiscoveryRegistry
import notlown.cobblebase.core.LogManager
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -> Client: sends permanent discoveries (structures + biomes) for the Discovery tab.
 */
data class DiscoverySyncS2CPacket(
    val discoveries: List<DiscoveryRegistry.Discovery>
) : CustomPayload {

    companion object {
        val ID = CustomPayload.Id<DiscoverySyncS2CPacket>(Identifier.of(Cobblebase.MODID, "discovery_sync"))

        val CODEC: PacketCodec<PacketByteBuf, DiscoverySyncS2CPacket> = object : PacketCodec<PacketByteBuf, DiscoverySyncS2CPacket> {
            override fun decode(buf: PacketByteBuf): DiscoverySyncS2CPacket {
                val count = buf.readVarInt()
                val discoveries = mutableListOf<DiscoveryRegistry.Discovery>()
                repeat(count) {
                    discoveries.add(DiscoveryRegistry.Discovery(
                        type = DiscoveryRegistry.DiscoveryType.entries[buf.readVarInt()],
                        name = buf.readString(),
                        x = buf.readInt(),
                        y = buf.readInt(),
                        z = buf.readInt(),
                        discoveredBy = buf.readString(),
                        timestamp = buf.readLong(),
                        rarity = LogManager.Rarity.entries[buf.readVarInt()]
                    ))
                }
                return DiscoverySyncS2CPacket(discoveries)
            }

            override fun encode(buf: PacketByteBuf, packet: DiscoverySyncS2CPacket) {
                buf.writeVarInt(packet.discoveries.size)
                for (d in packet.discoveries) {
                    buf.writeVarInt(d.type.ordinal)
                    buf.writeString(d.name)
                    buf.writeInt(d.x)
                    buf.writeInt(d.y)
                    buf.writeInt(d.z)
                    buf.writeString(d.discoveredBy)
                    buf.writeLong(d.timestamp)
                    buf.writeVarInt(d.rarity.ordinal)
                }
            }
        }
    }

    override fun getId(): CustomPayload.Id<DiscoverySyncS2CPacket> = ID
}

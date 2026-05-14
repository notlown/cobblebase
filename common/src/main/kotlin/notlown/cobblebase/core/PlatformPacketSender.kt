package notlown.cobblebase.core

import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity

/**
 * Platform-agnostic S2C packet sender. Common-side code (e.g. SkillEffects) needs to send
 * payloads without pulling in Fabric/NeoForge networking imports — each platform's init
 * code assigns [sendS2C] to its native send function on startup.
 */
object PlatformPacketSender {
    /** Sends a custom S2C payload to one player. Replaced by the Fabric/NeoForge init code. */
    var sendS2C: (player: ServerPlayerEntity, payload: CustomPayload) -> Unit = { _, _ -> }
}

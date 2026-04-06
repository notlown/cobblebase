package notlown.cobblebase.core

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object VersionChecker {

    const val MINIMUM_VERSION = "1.3.0"

    // TODO: Read from build config instead of hardcoding
    const val MOD_VERSION = "1.3.8"

    private val handshakes = ConcurrentHashMap<UUID, String>()

    /**
     * Called when the server receives a version handshake packet from a client.
     * Compares the client version against the minimum required version.
     * Kicks the player if their version is too old.
     */
    fun onHandshake(player: ServerPlayerEntity, clientVersion: String) {
        val cleanVersion = stripBuildMeta(clientVersion)

        if (compareVersions(cleanVersion, MINIMUM_VERSION) < 0) {
            player.networkHandler.disconnect(Text.literal(
                "\u00A7cCobblebase version mismatch!\n" +
                "\u00A77Your version: \u00A7f$clientVersion\n" +
                "\u00A77Required: \u00A7f$MINIMUM_VERSION+\n\n" +
                "\u00A7eDownload the latest version from Modrinth."
            ))
            return
        }

        handshakes[player.uuid] = clientVersion
        Cobblebase.LOGGER.info("[Cobblebase] Player ${player.name.string} connected with Cobblebase v$clientVersion")
    }

    /**
     * Called when a player joins the server.
     * Schedules a delayed check — if no handshake is received within 5 seconds (100 ticks),
     * the player is kicked (they either don't have the mod or have a version too old to support the handshake).
     */
    fun onPlayerJoin(player: ServerPlayerEntity) {
        val uuid = player.uuid
        val server = player.server

        // Skip handshake check in singleplayer — the integrated server always has the mod
        if (!server.isDedicated) {
            handshakes[uuid] = MOD_VERSION
            return
        }

        // Dedicated server: schedule a check after 10 seconds
        val task = Runnable {
            if (!handshakes.containsKey(uuid)) {
                val currentPlayer = server.playerManager.getPlayer(uuid)
                currentPlayer?.networkHandler?.disconnect(Text.literal(
                    "\u00A7cCobblebase is required to play on this server!\n\n" +
                    "\u00A77You either don't have Cobblebase installed\n" +
                    "\u00A77or your version is too old to connect.\n\n" +
                    "\u00A77Required: \u00A7f$MINIMUM_VERSION+\n\n" +
                    "\u00A7eDownload from Modrinth."
                ))
            }
        }

        Thread {
            try {
                Thread.sleep(10000)
                server.execute(task)
            } catch (_: InterruptedException) {
                // Player left before timeout, ignore
            }
        }.apply {
            isDaemon = true
            name = "CobblebaseHandshakeTimeout-$uuid"
            start()
        }
    }

    /**
     * Cleanup when a player disconnects.
     */
    fun onPlayerLeave(playerUuid: UUID) {
        handshakes.remove(playerUuid)
    }

    /**
     * Strips the build metadata suffix (everything after "+") from a version string.
     * Example: "1.3.5+1.7.0" → "1.3.5"
     */
    private fun stripBuildMeta(version: String): String {
        val plusIndex = version.indexOf('+')
        return if (plusIndex >= 0) version.substring(0, plusIndex) else version
    }

    /**
     * Compares two semantic version strings.
     * Returns negative if a < b, zero if equal, positive if a > b.
     * Handles versions with "+" suffix by stripping it before comparison.
     */
    fun compareVersions(a: String, b: String): Int {
        val partsA = stripBuildMeta(a).split(".").map { it.toIntOrNull() ?: 0 }
        val partsB = stripBuildMeta(b).split(".").map { it.toIntOrNull() ?: 0 }

        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val partA = partsA.getOrElse(i) { 0 }
            val partB = partsB.getOrElse(i) { 0 }
            if (partA != partB) return partA - partB
        }
        return 0
    }
}

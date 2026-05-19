package notlown.cobblebase.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.WorldSavePath
import java.io.File

/**
 * Stores general Cobblebase settings that are server-side and synced to all clients.
 * Covers things like the in-game Discord button URL / enabled state.
 *
 * Saved per-world in cobblebase_general.json.
 */
object GeneralSettings {

    data class Settings(
        var discordUrl: String = "https://discord.gg/6As3sVZgVT",
        var discordEnabled: Boolean = true,
        /**
         * Whether the in-game WorkerWiki sub-tab (Pokemon tab → WorkerWiki) is visible to
         * players. Some server admins prefer their players discover skills by experimenting
         * rather than being able to read the full species database upfront. Default true.
         */
        var pokeWikiEnabled: Boolean = true,
        /**
         * Server-wide pasture range — the radius (in blocks) within which Pokemon scan for
         * work targets and the pasture-owned area extends. Overrides the local cloth-config
         * `jobSearchRadius` value when running on a dedicated server. Bounded [5, 30].
         */
        var pastureRange: Int = 10,
        /**
         * Max number of Pokemon per pasture that may actively work simultaneously.
         * "Working" means a Pokemon with a non-null job assignment; Relax mons (and
         * pure passive-aura mons whose species grants a buff but has no assignment)
         * never count toward the cap.
         *
         * Order is the pasture's own tethering order — the first N assigned Pokemon
         * are active; the rest are queued and their executors are skipped. A queued
         * Pokemon keeps its assignment so that raising the cap, or another worker
         * leaving the pasture, resumes it immediately.
         *
         * 0 = unlimited (no enforcement, equivalent to the pasture's own max). This is
         * the default so existing servers see no behavior change until an admin sets
         * an explicit cap. Sane upper bound 64 — well above Cobblemon's stock 16-slot
         * pasture but leaves room for modded larger pastures.
         */
        var maxWorkingPokemonPerPasture: Int = 0,
        /**
         * How far BELOW the pasture Y the Harvester scans for ripe crops and the
         * Show-Radius wireframe extends downward. Smaller = safer (mons won't try
         * to dig into caves under the base); larger = catches harvestables on
         * cliffs and stair gardens that grow below pasture level.
         *
         * Bounded [0, 30]. 0 = pasture-level-and-above only (original behavior).
         * 6 (default) covers typical surface variation — stairs, small cliffs,
         * hillside trees — without reaching cave systems.
         */
        var harvesterDownwardLimit: Int = 6
    )

    private var settings = Settings()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun getSettings(): Settings = settings

    fun setSettings(newSettings: Settings) {
        settings = newSettings
    }

    fun getDiscordUrl(): String = settings.discordUrl
    fun isDiscordEnabled(): Boolean = settings.discordEnabled
    fun isPokeWikiEnabled(): Boolean = settings.pokeWikiEnabled

    private fun getSaveFile(world: ServerWorld): File {
        val saveDir = world.server.getSavePath(WorldSavePath.ROOT).toFile()
        return File(saveDir, "cobblebase_general.json")
    }

    fun save(world: ServerWorld) {
        try {
            val file = getSaveFile(world)
            file.writeText(gson.toJson(settings))
            Cobblebase.LOGGER.debug("[GeneralSettings] Saved to ${file.name}")
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[GeneralSettings] Failed to save: ${e.message}")
        }
    }

    fun load(world: ServerWorld) {
        try {
            val file = getSaveFile(world)
            if (!file.exists()) {
                Cobblebase.LOGGER.debug("[GeneralSettings] No saved file — using defaults")
                return
            }
            val loaded = gson.fromJson(file.readText(), Settings::class.java)
            if (loaded != null) {
                settings = loaded
                Cobblebase.LOGGER.debug("[GeneralSettings] Loaded: discordEnabled=${settings.discordEnabled} url=${settings.discordUrl}")
            }
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[GeneralSettings] Failed to load: ${e.message}")
        }
    }
}

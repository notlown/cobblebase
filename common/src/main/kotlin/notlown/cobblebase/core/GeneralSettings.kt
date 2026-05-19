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
         * Admin **maximum** pasture range. Each pasture owner may pick any value in
         * [pastureRangeMin, pastureRangeMax] via the Base Settings modal; unset
         * pastures default to the max. Bounded [5, 30].
         *
         * Renamed from `pastureRange` for clarity once per-pasture overrides
         * landed — the single global value is now the *cap*, not the value.
         */
        var pastureRangeMax: Int = 10,
        /** Admin **minimum** pasture range — owners can't pick below this. Bounded [5, 30]. */
        var pastureRangeMin: Int = 5,
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
         * Admin **maximum** below-pasture reach — pasture owners may pick any
         * value in [belowPastureReachMin, belowPastureReachMax]; unset pastures
         * default to the max. Bounded [0, 30].
         */
        var belowPastureReachMax: Int = 6,
        /** Admin **minimum** below-pasture reach. Bounded [0, 30]. */
        var belowPastureReachMin: Int = 0
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

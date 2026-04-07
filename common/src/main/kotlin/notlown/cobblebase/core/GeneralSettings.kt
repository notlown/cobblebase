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
        var discordEnabled: Boolean = true
    )

    private var settings = Settings()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun getSettings(): Settings = settings

    fun setSettings(newSettings: Settings) {
        settings = newSettings
    }

    fun getDiscordUrl(): String = settings.discordUrl
    fun isDiscordEnabled(): Boolean = settings.discordEnabled

    private fun getSaveFile(world: ServerWorld): File {
        val saveDir = world.server.getSavePath(WorldSavePath.ROOT).toFile()
        return File(saveDir, "cobblebase_general.json")
    }

    fun save(world: ServerWorld) {
        try {
            val file = getSaveFile(world)
            file.writeText(gson.toJson(settings))
            Cobblebase.LOGGER.info("[GeneralSettings] Saved to ${file.name}")
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[GeneralSettings] Failed to save: ${e.message}")
        }
    }

    fun load(world: ServerWorld) {
        try {
            val file = getSaveFile(world)
            if (!file.exists()) {
                Cobblebase.LOGGER.info("[GeneralSettings] No saved file — using defaults")
                return
            }
            val loaded = gson.fromJson(file.readText(), Settings::class.java)
            if (loaded != null) {
                settings = loaded
                Cobblebase.LOGGER.info("[GeneralSettings] Loaded: discordEnabled=${settings.discordEnabled} url=${settings.discordUrl}")
            }
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[GeneralSettings] Failed to load: ${e.message}")
        }
    }
}

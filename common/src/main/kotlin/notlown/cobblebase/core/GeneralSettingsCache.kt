package notlown.cobblebase.core

/**
 * Client-side cache for server-synced GeneralSettings.
 * Updated when the client receives a GeneralSettingsSyncS2CPacket.
 */
object GeneralSettingsCache {

    var discordUrl: String = "https://discord.gg/6As3sVZgVT"
        private set
    var discordEnabled: Boolean = true
        private set
    var pokeWikiEnabled: Boolean = true
        private set
    /** Server-wide pasture range (radius in blocks). 0 = not set, fall back to local config. */
    var pastureRange: Int = 0
        private set

    fun update(url: String, enabled: Boolean, pokeWiki: Boolean = true, range: Int = 0) {
        discordUrl = url
        discordEnabled = enabled
        pokeWikiEnabled = pokeWiki
        pastureRange = range
    }
}

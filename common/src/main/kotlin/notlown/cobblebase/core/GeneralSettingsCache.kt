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

    fun update(url: String, enabled: Boolean) {
        discordUrl = url
        discordEnabled = enabled
    }
}

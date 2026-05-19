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
    /** Max simultaneously-working Pokemon per pasture. 0 = unlimited (use pasture's own max). */
    var maxWorkingPokemonPerPasture: Int = 0
        private set
    /**
     * How many blocks BELOW the pasture Y the Harvester scans / the wireframe reaches.
     * -1 = not synced (use default 6). Otherwise in [0, 30].
     */
    var harvesterDownwardLimit: Int = -1
        private set

    fun update(
        url: String,
        enabled: Boolean,
        pokeWiki: Boolean = true,
        range: Int = 0,
        maxWorking: Int = 0,
        downwardLimit: Int = -1
    ) {
        discordUrl = url
        discordEnabled = enabled
        pokeWikiEnabled = pokeWiki
        pastureRange = range
        maxWorkingPokemonPerPasture = maxWorking
        harvesterDownwardLimit = downwardLimit
    }
}

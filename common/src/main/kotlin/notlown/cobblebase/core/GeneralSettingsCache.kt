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
    var pastureRangeMax: Int = 0
        private set
    /** Max simultaneously-working Pokemon per pasture. 0 = unlimited (use pasture's own max). */
    var maxWorkingPokemonPerPasture: Int = 0
        private set
    /**
     * Admin **maximum** below-pasture reach. -1 = not synced (use default 6).
     * Otherwise in [0, 30].
     */
    var belowPastureReachMax: Int = -1
        private set
    /** Admin **minimum** pasture range — 0 = not synced (use default 5). */
    var pastureRangeMin: Int = 0
        private set
    /** Admin **minimum** below-pasture reach — -1 = not synced (use default 0). */
    var belowPastureReachMin: Int = -1
        private set

    fun update(
        url: String,
        enabled: Boolean,
        pokeWiki: Boolean = true,
        rangeMax: Int = 0,
        maxWorking: Int = 0,
        belowReachMax: Int = -1,
        rangeMin: Int = 0,
        belowReachMin: Int = -1
    ) {
        discordUrl = url
        discordEnabled = enabled
        pokeWikiEnabled = pokeWiki
        pastureRangeMax = rangeMax
        maxWorkingPokemonPerPasture = maxWorking
        belowPastureReachMax = belowReachMax
        pastureRangeMin = rangeMin
        belowPastureReachMin = belowReachMin
    }
}

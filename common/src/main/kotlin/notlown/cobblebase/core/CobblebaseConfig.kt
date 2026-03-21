package notlown.cobblebase.core

/**
 * Simple config holder. Will be replaced with Cloth Config later.
 * For now values are set here and can be toggled via dev mode.
 */
object CobblebaseConfig {
    // Dev mode: reduces all cooldowns to 1 second for testing
    var devMode = true // TODO: set to false for release

    // Passive XP
    var passiveXpEnabled = true
    var passiveXpAmount = 250
    var passiveXpIntervalSeconds = 60L

    // General
    var defaultSearchRadius = 10

    /**
     * Returns the effective cooldown in ticks, respecting dev mode.
     */
    fun getEffectiveCooldownTicks(baseCooldownSeconds: Long, proficiency: Int): Long {
        if (devMode) return 20L // 1 second in dev mode
        if (baseCooldownSeconds <= 0) return 10L
        return baseCooldownSeconds * 20L * (6 - proficiency) / 3
    }
}

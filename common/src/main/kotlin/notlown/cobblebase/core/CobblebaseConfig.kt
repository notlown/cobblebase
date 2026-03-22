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
    var passiveXpPercent = 5 // 5% of XP needed for next level per tick
    var passiveXpIntervalSeconds = 60L

    // General
    var defaultSearchRadius = 10
    var friendRecruiterCooldownSeconds = 300L
    var legendaryRecruiterCooldownSeconds = 600L

    // Recruiter spawn rates (base rates at proficiency 1, scale up to 2x at proficiency 5)
    var recruiterCommonRate = 93.8
    var recruiterUncommonRate = 5.0
    var recruiterRareRate = 1.0
    var recruiterUltraRareRate = 0.2

    /**
     * Returns the effective cooldown in ticks, respecting dev mode.
     */
    fun getEffectiveCooldownTicks(baseCooldownSeconds: Long, proficiency: Int): Long {
        if (devMode) return 100L // 5 seconds in dev mode
        if (baseCooldownSeconds <= 0) return 10L
        return baseCooldownSeconds * 20L * (6 - proficiency) / 3
    }
}

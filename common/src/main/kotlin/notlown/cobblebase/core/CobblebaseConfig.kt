package notlown.cobblebase.core

import me.shedaniel.autoconfig.AutoConfig

/**
 * Direct access to config values. Reads from ClothConfig holder every time.
 * Changes in the settings menu apply immediately - no sync needed.
 */
object CobblebaseConfig {

    private val holder get() = AutoConfig.getConfigHolder(CobblebaseClothConfig::class.java).config

    // General
    val devMode get() = holder.general.devMode
    val defaultSearchRadius get() = holder.general.defaultSearchRadius

    // Passive XP
    val passiveXpEnabled get() = holder.passiveXp.enabled
    val passiveXpPercent get() = holder.passiveXp.xpPercent
    val passiveXpIntervalSeconds get() = holder.passiveXp.intervalSeconds

    // Skills
    val finderCooldownSeconds get() = holder.finder.finderCooldownSeconds.toLong()
    val friendRecruiterCooldownSeconds get() = holder.recruiterRates.spawnCooldownSeconds.toLong()
    val legendaryRecruiterCooldownSeconds get() = holder.skills.legendaryRecruiterCooldownSeconds.toLong()

    // Recruiter rates
    val recruiterCommonRate get() = holder.recruiterRates.commonRate
    val recruiterUncommonRate get() = holder.recruiterRates.uncommonRate
    val recruiterRareRate get() = holder.recruiterRates.rareRate
    val recruiterUltraRareRate get() = holder.recruiterRates.ultraRareRate

    fun getEffectiveCooldownTicks(baseCooldownSeconds: Long, proficiency: Int): Long {
        if (devMode) return 100L // 5 seconds in dev mode
        if (baseCooldownSeconds <= 0) return 10L
        return baseCooldownSeconds * 20L * (6 - proficiency) / 3
    }
}

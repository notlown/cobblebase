package notlown.cobblebase.core

import me.shedaniel.autoconfig.AutoConfig

/**
 * Direct access to config values. Reads from ClothConfig holder every time.
 * Changes in the settings menu apply immediately - no sync needed.
 */
object CobblebaseConfig {

    private val holder get() = AutoConfig.getConfigHolder(CobblebaseClothConfig::class.java).config

    // General
    val devMode = false // Dev mode removed from settings
    val jobSearchRadius get() = holder.general.jobSearchRadius
    val enableSafetyTeleport get() = holder.general.enableSafetyTeleport
    val safetyTeleportDistance get() = holder.general.safetyTeleportDistance
    val enableConsoleLogging get() = holder.general.enableConsoleLogging

    // Cry
    val cryEnabled get() = holder.cry.cryEnabled
    val cryVolume get() = holder.cry.cryVolume

    // Passive XP (internal, hidden from GUI - controlled by Mentor skill)
    val passiveXpEnabled get() = holder.passiveXp.enabled
    val passiveXpPercent get() = holder.passiveXp.xpPercent
    val passiveXpIntervalSeconds get() = holder.passiveXp.intervalSeconds

    // Mentor
    val mentorEnabled get() = holder.mentor.mentorEnabled
    val mentorMaxBoost get() = holder.mentor.mentorMaxBoost

    // Skills
    // Gatherer
    val gathererEnabled get() = holder.skills.gathererEnabled
    val gathererCooldownSeconds get() = holder.skills.gathererCooldownSeconds.toLong()

    // Irrigator
    val irrigatorEnabled get() = holder.irrigator.irrigatorEnabled
    val irrigatorCooldownSeconds get() = holder.irrigator.irrigatorCooldownSeconds.toLong()
    val irrigatorRadius get() = holder.irrigator.irrigatorRadius

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

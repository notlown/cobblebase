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
    /**
     * Job search radius. Source resolution:
     *   1. Server-side `GeneralSettings.pastureRangeMax` (admin-configured, persisted per-world).
     *      Wins on dedicated servers + integrated server with admin-set value.
     *   2. Client-side `GeneralSettingsCache.pastureRangeMax` synced from the server.
     *      Wins on multiplayer clients.
     *   3. Local cloth-config `holder.general.jobSearchRadius` as the final fallback.
     */
    /**
     * Admin **maximum** pasture range. Per-pasture overrides may pick a lower
     * value via the Base Settings modal; use [jobSearchRadius] with a pasture
     * origin to get the effective per-pasture value.
     */
    val jobSearchRadius: Int get() {
        val server = try { GeneralSettings.getSettings().pastureRangeMax } catch (_: Throwable) { 0 }
        if (server in 5..30) return server
        val cache = GeneralSettingsCache.pastureRangeMax
        if (cache in 5..30) return cache
        return holder.general.jobSearchRadius
    }

    /** Admin minimum pasture range (= the floor below which owners can't pick). */
    val pastureRangeMin: Int get() {
        val server = try { GeneralSettings.getSettings().pastureRangeMin } catch (_: Throwable) { 0 }
        if (server in 5..30) return server
        val cache = GeneralSettingsCache.pastureRangeMin
        if (cache in 5..30) return cache
        return 5
    }

    /**
     * Pasture-aware pasture range: per-pasture override clamped to the admin
     * [min, max] cap when one exists, otherwise the admin max. Block-scanning
     * executors and RadiusRenderer pass the pasture origin so each pasture uses
     * its owner-picked value.
     */
    fun jobSearchRadius(pasturePos: net.minecraft.util.math.BlockPos?): Int {
        val maxV = jobSearchRadius
        if (pasturePos == null) return maxV
        val minV = pastureRangeMin
        val override = try {
            PastureSettings.get(pasturePos)?.range
        } catch (_: Throwable) { null } ?: PastureSettingsCache.getRange(pasturePos)
        return override?.coerceIn(minV, maxV) ?: maxV
    }

    /**
     * Admin **maximum** below-pasture reach. Resolved server → client-cache →
     * default 6. Bounded [0, 30].
     */
    val belowPastureReachMax: Int get() {
        val server = try { GeneralSettings.getSettings().belowPastureReachMax } catch (_: Throwable) { -1 }
        if (server in 0..30) return server
        val cache = GeneralSettingsCache.belowPastureReachMax
        if (cache in 0..30) return cache
        return 6
    }

    /** Admin minimum below-pasture reach. */
    val belowPastureReachMin: Int get() {
        val server = try { GeneralSettings.getSettings().belowPastureReachMin } catch (_: Throwable) { -1 }
        if (server in 0..30) return server
        val cache = GeneralSettingsCache.belowPastureReachMin
        if (cache in 0..30) return cache
        return 0
    }

    /**
     * Pasture-aware below-reach: per-pasture override clamped to [min, max],
     * or the admin max if no override is set.
     */
    fun belowPastureReach(pasturePos: net.minecraft.util.math.BlockPos?): Int {
        val maxV = belowPastureReachMax
        if (pasturePos == null) return maxV
        val minV = belowPastureReachMin
        val override = try {
            PastureSettings.get(pasturePos)?.belowReach
        } catch (_: Throwable) { null } ?: PastureSettingsCache.getBelowReach(pasturePos)
        return override?.coerceIn(minV, maxV) ?: maxV
    }
    val enableSafetyTeleport get() = holder.general.enableSafetyTeleport
    val safetyTeleportDistance get() = holder.general.safetyTeleportDistance
    val enableUnstickTeleport get() = holder.general.enableUnstickTeleport
    val keepEntitiesAlive get() = holder.general.keepEntitiesAlive
    val enforceVersionCheck get() = holder.general.enforceVersionCheck
    val enableConsoleLogging get() = holder.general.enableConsoleLogging
    val mainButtonCorner get() = holder.general.mainButtonCorner

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
    val gathererPickupPlayerDrops get() = holder.skills.gathererPickupPlayerDrops
    // gathererCooldownSeconds removed — uses skill.cooldownSeconds from JSON/admin overrides

    // Irrigator
    val irrigatorEnabled get() = holder.irrigator.irrigatorEnabled
    // irrigatorCooldownSeconds removed — uses skill.cooldownSeconds from JSON/admin overrides
    val irrigatorRadius get() = holder.irrigator.irrigatorRadius


    // Recruiter rates
    val recruiterCommonRate get() = holder.recruiterRates.commonRate
    val recruiterUncommonRate get() = holder.recruiterRates.uncommonRate
    val recruiterRareRate get() = holder.recruiterRates.rareRate
    val recruiterUltraRareRate get() = holder.recruiterRates.ultraRareRate

    // Buffs / Auras — per-skill enable flags. Server admins can disable individual buffs.
    fun isBuffEnabled(buffType: String): Boolean = when (buffType) {
        "speed_boost" -> holder.buffs.speedBoostEnabled
        "strength_boost" -> holder.buffs.strengthBoostEnabled
        "resistance_boost" -> holder.buffs.resistanceBoostEnabled
        "night_vision" -> holder.buffs.nightVisionEnabled
        "water_breathing" -> holder.buffs.waterBreathingEnabled
        "jump_boost" -> holder.buffs.jumpBoostEnabled
        "haste_boost" -> holder.buffs.hasteBoostEnabled
        "saturation_boost" -> holder.buffs.saturationBoostEnabled
        "aura" -> holder.buffs.auraEnabled
        else -> true
    }

    fun getEffectiveCooldownTicks(baseCooldownSeconds: Long, proficiency: Int): Long {
        return getEffectiveCooldownTicks(baseCooldownSeconds, proficiency, null)
    }

    /**
     * Cooldown ticks for a specific skill/prof. If the admin set a per-prof override via
     * `_prof{N}Cd` (cooldown in seconds at that proficiency), that takes precedence over the
     * default formula `base × (6 - prof) / 3`. Falls through to the old formula otherwise.
     */
    fun getEffectiveCooldownTicks(baseCooldownSeconds: Long, proficiency: Int, skillId: String?): Long {
        if (devMode) return 100L
        if (baseCooldownSeconds <= 0) return 10L
        if (skillId != null) {
            val skill = notlown.cobblebase.core.SkillRegistry.get(skillId)
            if (skill != null) {
                val override = JobConfigOverrides.getEffectiveTuning(skill, "_prof${proficiency}Cd", -1.0)
                if (override > 0) return (override * 20.0).toLong().coerceAtLeast(10L)
            }
        }
        return baseCooldownSeconds * 20L * (6 - proficiency) / 3
    }
}

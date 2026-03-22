package notlown.cobblebase.core

import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer

object CobblebaseConfigBridge {

    fun init() {
        AutoConfig.register(CobblebaseClothConfig::class.java, ::GsonConfigSerializer)
        sync()
    }

    /**
     * Sync cloth config values to runtime config.
     * Called at init and periodically to pick up in-game changes.
     */
    fun sync() {
        try {
            val cfg = AutoConfig.getConfigHolder(CobblebaseClothConfig::class.java).config

            CobblebaseConfig.devMode = cfg.general.devMode
            CobblebaseConfig.defaultSearchRadius = cfg.general.defaultSearchRadius
            CobblebaseConfig.passiveXpEnabled = cfg.passiveXp.enabled
            CobblebaseConfig.passiveXpPercent = cfg.passiveXp.xpPercent
            CobblebaseConfig.passiveXpIntervalSeconds = cfg.passiveXp.intervalSeconds
            CobblebaseConfig.friendRecruiterCooldownSeconds = cfg.recruiterRates.spawnCooldownSeconds.toLong()
            CobblebaseConfig.legendaryRecruiterCooldownSeconds = cfg.skills.legendaryRecruiterCooldownSeconds.toLong()
            CobblebaseConfig.recruiterCommonRate = cfg.recruiterRates.commonRate
            CobblebaseConfig.recruiterUncommonRate = cfg.recruiterRates.uncommonRate
            CobblebaseConfig.recruiterRareRate = cfg.recruiterRates.rareRate
            CobblebaseConfig.recruiterUltraRareRate = cfg.recruiterRates.ultraRareRate
        } catch (_: Exception) {}
    }
}

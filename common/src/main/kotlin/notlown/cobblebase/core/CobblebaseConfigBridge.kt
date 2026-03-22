package notlown.cobblebase.core

import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer

/**
 * Bridges ClothConfig with CobblebaseConfig.
 * Syncs values from the config file to the runtime config object.
 */
object CobblebaseConfigBridge {

    fun init() {
        AutoConfig.register(CobblebaseClothConfig::class.java, ::GsonConfigSerializer)
        sync()
    }

    fun sync() {
        val cfg = AutoConfig.getConfigHolder(CobblebaseClothConfig::class.java).config

        CobblebaseConfig.devMode = cfg.general.devMode
        CobblebaseConfig.defaultSearchRadius = cfg.general.defaultSearchRadius
        CobblebaseConfig.passiveXpEnabled = cfg.passiveXp.enabled
        CobblebaseConfig.passiveXpPercent = cfg.passiveXp.xpPercent
        CobblebaseConfig.passiveXpIntervalSeconds = cfg.passiveXp.intervalSeconds
        CobblebaseConfig.friendRecruiterCooldownSeconds = cfg.skills.friendRecruiterCooldownSeconds.toLong()
        CobblebaseConfig.legendaryRecruiterCooldownSeconds = cfg.skills.legendaryRecruiterCooldownSeconds.toLong()
        CobblebaseConfig.recruiterCommonRate = cfg.recruiterRates.commonRate
        CobblebaseConfig.recruiterUncommonRate = cfg.recruiterRates.uncommonRate
        CobblebaseConfig.recruiterRareRate = cfg.recruiterRates.rareRate
        CobblebaseConfig.recruiterUltraRareRate = cfg.recruiterRates.ultraRareRate
        CobblebaseConfig.friendRecruiterCooldownSeconds = cfg.recruiterRates.spawnCooldownSeconds.toLong()
    }
}

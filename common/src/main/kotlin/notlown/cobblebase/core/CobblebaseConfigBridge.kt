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
        CobblebaseConfig.passiveXpAmount = cfg.passiveXp.xpAmount
        CobblebaseConfig.passiveXpIntervalSeconds = cfg.passiveXp.intervalSeconds
    }
}

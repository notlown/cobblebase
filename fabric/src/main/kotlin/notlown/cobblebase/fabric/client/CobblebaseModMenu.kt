package notlown.cobblebase.fabric.client

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import me.shedaniel.autoconfig.AutoConfig
import notlown.cobblebase.core.CobblebaseClothConfig

class CobblebaseModMenu : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parent ->
            AutoConfig.getConfigScreen(CobblebaseClothConfig::class.java, parent).get()
        }
    }
}

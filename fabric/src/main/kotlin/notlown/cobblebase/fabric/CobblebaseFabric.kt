package notlown.cobblebase.fabric

import net.fabricmc.api.ModInitializer
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.ExecutorRegistry

object CobblebaseFabric : ModInitializer {
    override fun onInitialize() {
        Cobblebase.init()
        ExecutorRegistry.init()
    }
}

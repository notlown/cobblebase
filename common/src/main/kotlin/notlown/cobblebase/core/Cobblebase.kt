package notlown.cobblebase.core

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object Cobblebase {
    const val MODID = "cobblebase"
    val LOGGER: Logger = LogManager.getLogger(MODID)

    fun init() {
        LOGGER.info("Launching Cobblebase...")
        SkillRegistry.init()
        SpeciesSkillRegistry.init()
    }
}

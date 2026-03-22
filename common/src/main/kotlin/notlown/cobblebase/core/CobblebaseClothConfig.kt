package notlown.cobblebase.core

import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.annotation.Config
import me.shedaniel.autoconfig.annotation.ConfigEntry

@Config(name = "cobblebase")
class CobblebaseClothConfig : ConfigData {

    @ConfigEntry.Gui.CollapsibleObject
    var general = GeneralGroup()

    @ConfigEntry.Gui.CollapsibleObject
    var passiveXp = PassiveXpGroup()

    @ConfigEntry.Gui.CollapsibleObject
    var skills = SkillsGroup()

    class GeneralGroup {
        var devMode = false
        @ConfigEntry.BoundedDiscrete(min = 5, max = 20)
        var defaultSearchRadius = 10
    }

    class PassiveXpGroup {
        var enabled = true
        @ConfigEntry.BoundedDiscrete(min = 10, max = 1000)
        var xpAmount = 250
        var intervalSeconds: Long = 60
    }

    class SkillsGroup {
        var harvestingEnabled = true
        var fishingEnabled = true
        var guardEnabled = true
        var healingEnabled = true
        var recruitingEnabled = true
        var generationEnabled = true
        var utilityEnabled = true
    }
}

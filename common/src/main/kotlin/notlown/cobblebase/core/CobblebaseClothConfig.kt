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
        @ConfigEntry.BoundedDiscrete(min = 1, max = 50)
        var xpPercent = 5
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
        @ConfigEntry.BoundedDiscrete(min = 30, max = 1800)
        var friendRecruiterCooldownSeconds = 300
        @ConfigEntry.BoundedDiscrete(min = 60, max = 3600)
        var legendaryRecruiterCooldownSeconds = 600
    }

    @ConfigEntry.Gui.CollapsibleObject
    var recruiterRates = RecruiterRatesGroup()

    @ConfigEntry.Gui.CollapsibleObject
    var irrigator = IrrigatorGroup()

    @ConfigEntry.Gui.CollapsibleObject
    var finder = FinderGroup()

    class IrrigatorGroup {
        var irrigatorEnabled = true
        @ConfigEntry.BoundedDiscrete(min = 1, max = 60)
        var irrigatorCooldownSeconds = 3
        @ConfigEntry.BoundedDiscrete(min = 1, max = 5)
        var irrigatorRadius = 1
    }

    class FinderGroup {
        @ConfigEntry.BoundedDiscrete(min = 60, max = 3600)
        var finderCooldownSeconds = 240
    }

    class RecruiterRatesGroup {
        var commonRate = 93.8
        var uncommonRate = 5.0
        var rareRate = 1.0
        var ultraRareRate = 0.2
        @ConfigEntry.BoundedDiscrete(min = 10, max = 1800)
        var spawnCooldownSeconds = 300
    }
}

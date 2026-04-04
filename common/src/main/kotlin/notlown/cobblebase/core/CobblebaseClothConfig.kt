package notlown.cobblebase.core

import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.annotation.Config
import me.shedaniel.autoconfig.annotation.ConfigEntry

@Config(name = "cobblebase")
class CobblebaseClothConfig : ConfigData {

    @ConfigEntry.Gui.CollapsibleObject
    var general = GeneralGroup()

    @ConfigEntry.Gui.CollapsibleObject
    var cry = CryGroup()

    @ConfigEntry.Gui.CollapsibleObject
    var skills = SkillsGroup()

    class GeneralGroup {
        @ConfigEntry.BoundedDiscrete(min = 5, max = 20)
        var defaultSearchRadius = 10
    }

    class CryGroup {
        var cryEnabled = true
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        var cryVolume = 30
    }

    // Passive XP is now controlled by the Mentor skill internally.
    // These backing fields remain for PassiveXp.kt but are hidden from the GUI.
    @ConfigEntry.Gui.Excluded
    var passiveXp = PassiveXpGroup()

    class PassiveXpGroup {
        var enabled = true
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
        var gathererEnabled = true
        @ConfigEntry.BoundedDiscrete(min = 5, max = 300)
        var gathererCooldownSeconds = 10
        @ConfigEntry.BoundedDiscrete(min = 30, max = 1800)
        var friendRecruiterCooldownSeconds = 300
        @ConfigEntry.BoundedDiscrete(min = 60, max = 3600)
        var legendaryRecruiterCooldownSeconds = 600
    }

    @ConfigEntry.Gui.CollapsibleObject
    var mentor = MentorGroup()

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

    class MentorGroup {
        var mentorEnabled = true
        var mentorMaxBoost = 1.0
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

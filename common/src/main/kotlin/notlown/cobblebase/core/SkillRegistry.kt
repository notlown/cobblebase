package notlown.cobblebase.core

import com.google.gson.Gson

/**
 * Registry that loads and holds all skill definitions.
 */
object SkillRegistry {
    private val gson = Gson()
    private val skills = mutableMapOf<String, SkillDef>()

    fun init() {
        registerDefaults()
        Cobblebase.LOGGER.info("SkillRegistry: ${skills.size} skills registered")
    }

    fun get(id: String): SkillDef? = skills[id]
    fun getAll(): Map<String, SkillDef> = skills.toMap()
    fun register(skill: SkillDef) { skills[skill.id] = skill }

    private fun registerDefaults() {
        // -- Gathering --
        register(SkillDef("cobblebase:harvester", "Harvester", "Harvests crops, berries, apricorns, mints, netherwart, and more", "gathering", 0, 10, "harvester", "harvest"))
        register(SkillDef("cobblebase:fishing", "Fishing", "Catches fish from nearby water", "gathering", 60, 10, "fishing", "water", "minecraft:gameplay/fishing", "minecraft:water", true))
        register(SkillDef("cobblebase:diving", "Diving", "Dives for underwater treasure", "gathering", 210, 10, "diving", "water", "cobblebase:dive_treasure", "minecraft:water", true))
        register(SkillDef("cobblebase:mining", "Mining", "Harvests amethyst and tumblestone", "gathering", 0, 10, "mining", "harvest"))
        register(SkillDef("cobblebase:honey_collect", "Honey Collect", "Collects honey from beehives", "gathering", 120, 10, "honey", "harvest"))

        // -- Generation --
        register(SkillDef("cobblebase:lava_fill", "Lava Fill", "Fills cauldrons with lava", "generation", 90, 10, "cauldron_fill", "fire"))
        register(SkillDef("cobblebase:water_fill", "Water Fill", "Fills cauldrons with water", "generation", 90, 10, "cauldron_fill", "water"))
        register(SkillDef("cobblebase:snow_fill", "Snow Fill", "Fills cauldrons with powder snow", "generation", 90, 10, "cauldron_fill", "water"))
        register(SkillDef("cobblebase:furnace_fuel", "Furnace Fuel", "Adds fuel to furnaces", "generation", 80, 10, "furnace_fuel", "fire"))
        register(SkillDef("cobblebase:brew_fuel", "Brewing Fuel", "Adds fuel to brewing stands", "generation", 80, 10, "brew_fuel", "fire"))

        // -- Combat --
        register(SkillDef("cobblebase:guard", "Guard", "Repels wild Pokemon, earns XP or loot", "combat", 120, 10, "guard", "combat", "cobblebase:guard_loot", xpReward = 50))

        // -- Support --
        register(SkillDef("cobblebase:healer", "Healer", "Heals nearby players with regeneration", "support", 0, 10, "healer", "heal"))

        // -- Utility --
        register(SkillDef("cobblebase:irrigator", "Irrigator", "Hydrates nearby farmland", "utility", 0, 10, "irrigate", "water"))
        register(SkillDef("cobblebase:extinguisher", "Extinguisher", "Puts out nearby fires", "utility", 0, 10, "extinguish", "water"))
        register(SkillDef("cobblebase:gatherer", "Item Gatherer", "Picks up items on the ground", "utility", 0, 10, "gather_items", "default"))
        register(SkillDef("cobblebase:scout", "Scout", "Creates explorer maps to structures", "utility", 80, 10, "scout", "default"))
        register(SkillDef("cobblebase:archeologist", "Archeologist", "Digs for fossils and ancient treasures", "gathering", 80, 10, "archeology", "default", "cobblebase:archeology_treasure"))
        register(SkillDef("cobblebase:pickup", "Pick-up", "Finds random loot items", "gathering", 120, 10, "pickup", "default"))

        // -- Legendary / Unique --
        register(SkillDef("cobblebase:recruiter", "Recruiter", "Attracts rare wild Pokemon to spawn near your base", "legendary", 600, 20, "recruiter", "special"))
        register(SkillDef("cobblebase:aura_boost", "Aura Boost", "All nearby workers perform jobs faster", "legendary", 0, 15, "aura", "special"))
        register(SkillDef("cobblebase:lucky_charm", "Lucky Charm", "Increases loot quality for all workers", "legendary", 0, 15, "passive_buff", "special"))
        register(SkillDef("cobblebase:growth_aura", "Growth Aura", "Crops and berries grow faster nearby", "legendary", 0, 10, "growth", "special"))
    }
}

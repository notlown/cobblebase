package notlown.cobblebase.core

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import notlown.cobblebase.skill.Skill

/**
 * Registry that loads and holds all skill definitions from datapacks.
 * Skills are loaded from: data/cobblebase/skills/*.json
 */
object SkillRegistry {
    private val gson = Gson()
    private val skills = mutableMapOf<String, Skill>()

    fun init() {
        // Built-in skills are registered here as defaults
        // Datapacks can override or add new ones
        registerDefaults()
        Cobblebase.LOGGER.info("SkillRegistry: ${skills.size} skills registered")
    }

    fun get(id: String): Skill? = skills[id]
    fun getAll(): Map<String, Skill> = skills.toMap()

    fun register(skill: Skill) {
        skills[skill.id] = skill
    }

    fun loadFromJson(json: String) {
        val skill = gson.fromJson(json, Skill::class.java)
        register(skill)
    }

    private fun registerDefaults() {
        // Gathering skills
        register(Skill("cobblebase:fishing", "Fishing", "Catches fish from nearby water", "gathering", 60, 10, "fishing", "water", "minecraft:gameplay/fishing", "minecraft:water", true))
        register(Skill("cobblebase:diving", "Diving", "Dives for underwater treasure", "gathering", 210, 10, "diving", "water", "cobblebase:dive_treasure", "minecraft:water", true))
        register(Skill("cobblebase:apricorn_harvest", "Apricorn Harvest", "Harvests ripe apricorns", "gathering", 0, 10, "harvest_block", "harvest", null, "cobblemon:apricorns", true))
        register(Skill("cobblebase:berry_harvest", "Berry Harvest", "Harvests ripe berries", "gathering", 0, 10, "harvest_block", "harvest", null, "cobblemon:berry_leaves", true))
        register(Skill("cobblebase:crop_harvest", "Crop Harvest", "Harvests and replants crops", "gathering", 0, 10, "harvest_crop", "harvest"))
        register(Skill("cobblebase:amethyst_harvest", "Amethyst Harvest", "Harvests amethyst clusters", "gathering", 0, 10, "harvest_block", "harvest", null, "minecraft:amethyst_cluster", true))
        register(Skill("cobblebase:mint_harvest", "Mint Harvest", "Harvests mints", "gathering", 0, 10, "harvest_block", "harvest"))
        register(Skill("cobblebase:netherwart_harvest", "Netherwart Harvest", "Harvests netherwart", "gathering", 0, 10, "harvest_block", "harvest"))
        register(Skill("cobblebase:tumblestone_harvest", "Tumblestone Harvest", "Harvests tumblestones", "gathering", 0, 10, "harvest_block", "harvest"))
        register(Skill("cobblebase:honey_collect", "Honey Collect", "Collects honey from beehives", "gathering", 120, 10, "honey", "harvest"))

        // Generation skills
        register(Skill("cobblebase:lava_generate", "Lava Generation", "Fills cauldrons with lava", "generation", 90, 10, "cauldron_fill", "fire"))
        register(Skill("cobblebase:water_generate", "Water Generation", "Fills cauldrons with water", "generation", 90, 10, "cauldron_fill", "water"))
        register(Skill("cobblebase:snow_generate", "Snow Generation", "Fills cauldrons with powder snow", "generation", 90, 10, "cauldron_fill", "water"))
        register(Skill("cobblebase:fuel_generate", "Fuel Generation", "Adds fuel to furnaces", "generation", 80, 10, "furnace_fuel", "fire"))
        register(Skill("cobblebase:brew_fuel", "Brewing Fuel", "Adds fuel to brewing stands", "generation", 80, 10, "brew_fuel", "fire"))

        // Utility skills
        register(Skill("cobblebase:guard", "Guard", "Repels wild Pokemon for XP", "combat", 120, 10, "guard", "combat", "cobblebase:guard_loot", xpReward = 50))
        register(Skill("cobblebase:healer", "Healer", "Heals nearby players", "support", 0, 10, "healer", "heal"))
        register(Skill("cobblebase:fire_extinguisher", "Fire Extinguisher", "Puts out nearby fires", "utility", 0, 10, "extinguish", "water"))
        register(Skill("cobblebase:irrigator", "Irrigator", "Hydrates nearby farmland", "utility", 0, 10, "irrigate", "water"))
        register(Skill("cobblebase:item_gatherer", "Item Gatherer", "Picks up items on the ground", "utility", 0, 10, "gather_items", "default"))
        register(Skill("cobblebase:scout", "Scout", "Creates explorer maps", "utility", 80, 10, "scout", "default"))
        register(Skill("cobblebase:archeologist", "Archeologist", "Digs for ancient treasures", "gathering", 80, 10, "archeology", "default", "cobblebase:archeology_treasure"))
        register(Skill("cobblebase:pickup", "Pick-up", "Finds random loot", "gathering", 120, 10, "pickup", "default"))

        // Unique/Legendary skills
        register(Skill("cobblebase:recruiter", "Recruiter", "Attracts rare Pokemon to your base", "legendary", 600, 20, "recruiter", "special"))
        register(Skill("cobblebase:aura_boost", "Aura Boost", "Boosts all nearby workers' speed", "legendary", 0, 15, "aura", "special"))
        register(Skill("cobblebase:lucky_charm", "Lucky Charm", "Increases loot quality for all workers", "legendary", 0, 15, "passive_buff", "special"))
        register(Skill("cobblebase:growth_aura", "Growth Aura", "Makes crops and berries grow faster", "legendary", 0, 10, "growth", "special"))
    }
}

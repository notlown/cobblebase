package notlown.cobblebase.core

import com.google.gson.Gson
import notlown.cobblebase.skill.SkillEntry
import notlown.cobblebase.skill.SpeciesSkills

/**
 * Registry mapping Pokemon species to their available skills.
 * Loaded from: data/cobblebase/species_skills/*.json
 *
 * If no species JSON exists, falls back to type-based defaults.
 */
object SpeciesSkillRegistry {
    private val gson = Gson()
    private val speciesMap = mutableMapOf<String, SpeciesSkills>()

    fun init() {
        registerDefaults()
        Cobblebase.LOGGER.info("SpeciesSkillRegistry: ${speciesMap.size} species registered")
    }

    fun getSkills(species: String): SpeciesSkills? = speciesMap[species.lowercase()]

    fun register(speciesSkills: SpeciesSkills) {
        speciesMap[speciesSkills.species.lowercase()] = speciesSkills
    }

    fun loadFromJson(json: String) {
        val species = gson.fromJson(json, SpeciesSkills::class.java)
        register(species)
    }

    private fun registerDefaults() {
        // Water Pokemon
        species("squirtle", sk("cobblebase:fishing", 3), sk("cobblebase:water_generate", 2), sk("cobblebase:fire_extinguisher", 2))
        species("wartortle", sk("cobblebase:fishing", 4), sk("cobblebase:water_generate", 3), sk("cobblebase:fire_extinguisher", 3))
        species("blastoise", sk("cobblebase:fishing", 5), sk("cobblebase:water_generate", 4), sk("cobblebase:fire_extinguisher", 4), sk("cobblebase:guard", 3))
        species("psyduck", sk("cobblebase:fishing", 2), sk("cobblebase:water_generate", 1))
        species("golduck", sk("cobblebase:fishing", 4), sk("cobblebase:water_generate", 3), sk("cobblebase:diving", 2))
        species("slowpoke", sk("cobblebase:fishing", 2))
        species("slowbro", sk("cobblebase:fishing", 3), sk("cobblebase:healer", 2))
        species("magikarp", sk("cobblebase:fishing", 1))
        species("gyarados", sk("cobblebase:fishing", 5), sk("cobblebase:guard", 4), sk("cobblebase:diving", 3))
        species("lapras", sk("cobblebase:fishing", 4), sk("cobblebase:diving", 4), sk("cobblebase:snow_generate", 2))
        species("vaporeon", sk("cobblebase:fishing", 4), sk("cobblebase:water_generate", 4), sk("cobblebase:diving", 3))
        species("basculin", sk("cobblebase:fishing", 3), sk("cobblebase:diving", 2))
        species("basculegion", sk("cobblebase:fishing", 4), sk("cobblebase:diving", 4), sk("cobblebase:guard", 2))

        // Bug Pokemon
        species("caterpie", sk("cobblebase:apricorn_harvest", 1))
        species("butterfree", sk("cobblebase:apricorn_harvest", 3), sk("cobblebase:berry_harvest", 2), sk("cobblebase:honey_collect", 2))
        species("beedrill", sk("cobblebase:apricorn_harvest", 3), sk("cobblebase:honey_collect", 4), sk("cobblebase:guard", 2))
        species("ninjask", sk("cobblebase:apricorn_harvest", 4), sk("cobblebase:scout", 3), sk("cobblebase:item_gatherer", 2))
        species("shedinja", sk("cobblebase:archeologist", 3))
        species("combee", sk("cobblebase:honey_collect", 4), sk("cobblebase:apricorn_harvest", 1))
        species("vespiquen", sk("cobblebase:honey_collect", 5), sk("cobblebase:apricorn_harvest", 3), sk("cobblebase:guard", 3))

        // Fire Pokemon
        species("charmander", sk("cobblebase:lava_generate", 2), sk("cobblebase:fuel_generate", 2))
        species("charmeleon", sk("cobblebase:lava_generate", 3), sk("cobblebase:fuel_generate", 3))
        species("charizard", sk("cobblebase:lava_generate", 4), sk("cobblebase:fuel_generate", 4), sk("cobblebase:guard", 3), sk("cobblebase:scout", 4))
        species("vulpix", sk("cobblebase:fuel_generate", 2))
        species("ninetales", sk("cobblebase:fuel_generate", 4), sk("cobblebase:lava_generate", 3))
        species("growlithe", sk("cobblebase:guard", 3), sk("cobblebase:fuel_generate", 2))
        species("arcanine", sk("cobblebase:guard", 5), sk("cobblebase:fuel_generate", 3), sk("cobblebase:scout", 3))

        // Grass Pokemon
        species("bulbasaur", sk("cobblebase:crop_harvest", 2), sk("cobblebase:berry_harvest", 2))
        species("ivysaur", sk("cobblebase:crop_harvest", 3), sk("cobblebase:berry_harvest", 3))
        species("venusaur", sk("cobblebase:crop_harvest", 5), sk("cobblebase:berry_harvest", 4), sk("cobblebase:irrigator", 3))
        species("oddish", sk("cobblebase:crop_harvest", 1), sk("cobblebase:berry_harvest", 1))
        species("vileplume", sk("cobblebase:crop_harvest", 3), sk("cobblebase:berry_harvest", 3), sk("cobblebase:healer", 2))

        // Ground/Rock Pokemon
        species("nidoking", sk("cobblebase:archeologist", 4), sk("cobblebase:guard", 3), sk("cobblebase:amethyst_harvest", 2))
        species("nidoqueen", sk("cobblebase:archeologist", 3), sk("cobblebase:guard", 3))
        species("geodude", sk("cobblebase:amethyst_harvest", 2), sk("cobblebase:tumblestone_harvest", 2))
        species("golem", sk("cobblebase:amethyst_harvest", 4), sk("cobblebase:tumblestone_harvest", 4), sk("cobblebase:guard", 3))
        species("onix", sk("cobblebase:amethyst_harvest", 3), sk("cobblebase:tumblestone_harvest", 3), sk("cobblebase:archeologist", 2))
        species("boldore", sk("cobblebase:amethyst_harvest", 3), sk("cobblebase:tumblestone_harvest", 3))

        // Fighting Pokemon
        species("machop", sk("cobblebase:guard", 2), sk("cobblebase:item_gatherer", 1))
        species("machoke", sk("cobblebase:guard", 3), sk("cobblebase:item_gatherer", 2))
        species("machamp", sk("cobblebase:guard", 5), sk("cobblebase:item_gatherer", 3))
        species("primeape", sk("cobblebase:guard", 4))
        species("lucario", sk("cobblebase:guard", 5), sk("cobblebase:scout", 3))

        // Flying Pokemon
        species("pidgey", sk("cobblebase:scout", 1))
        species("pidgeotto", sk("cobblebase:scout", 3))
        species("pidgeot", sk("cobblebase:scout", 5))
        species("spearow", sk("cobblebase:scout", 2), sk("cobblebase:guard", 1))
        species("fearow", sk("cobblebase:scout", 4), sk("cobblebase:guard", 2))

        // Psychic Pokemon
        species("abra", sk("cobblebase:item_gatherer", 2))
        species("kadabra", sk("cobblebase:item_gatherer", 4), sk("cobblebase:scout", 2))
        species("alakazam", sk("cobblebase:item_gatherer", 5), sk("cobblebase:scout", 4))

        // Fairy Pokemon
        species("clefairy", sk("cobblebase:mint_harvest", 2), sk("cobblebase:healer", 2))
        species("clefable", sk("cobblebase:mint_harvest", 4), sk("cobblebase:healer", 3))
        species("jigglypuff", sk("cobblebase:healer", 2))
        species("wigglytuff", sk("cobblebase:healer", 4))

        // Ghost/Dark Pokemon
        species("gastly", sk("cobblebase:netherwart_harvest", 1))
        species("haunter", sk("cobblebase:netherwart_harvest", 3))
        species("gengar", sk("cobblebase:netherwart_harvest", 4), sk("cobblebase:guard", 3))

        // Normal Pokemon
        species("ditto", sk("cobblebase:item_gatherer", 3), sk("cobblebase:pickup", 3))
        species("eevee", sk("cobblebase:item_gatherer", 2), sk("cobblebase:pickup", 2))
        species("snorlax", sk("cobblebase:guard", 4))
        species("chansey", sk("cobblebase:healer", 5))
        species("blissey", sk("cobblebase:healer", 5), sk("cobblebase:pickup", 3))

        // Electric Pokemon
        species("pikachu", sk("cobblebase:item_gatherer", 3), sk("cobblebase:scout", 2))
        species("raichu", sk("cobblebase:item_gatherer", 4), sk("cobblebase:scout", 3))

        // Ice Pokemon
        species("lapras", sk("cobblebase:fishing", 4), sk("cobblebase:diving", 4), sk("cobblebase:snow_generate", 3))
        species("articuno", sk("cobblebase:snow_generate", 5), sk("cobblebase:scout", 5))

        // Dragon Pokemon
        species("dragonite", sk("cobblebase:guard", 5), sk("cobblebase:scout", 5), sk("cobblebase:brew_fuel", 3))

        // Steel Pokemon
        species("steelix", sk("cobblebase:tumblestone_harvest", 5), sk("cobblebase:amethyst_harvest", 4), sk("cobblebase:archeologist", 3))

        // Legendaries - unique skills
        species("mew", sk("cobblebase:recruiter", 5), sk("cobblebase:lucky_charm", 5), sk("cobblebase:aura_boost", 3))
        species("jirachi", sk("cobblebase:recruiter", 5), sk("cobblebase:lucky_charm", 4))
        species("celebi", sk("cobblebase:growth_aura", 5), sk("cobblebase:crop_harvest", 5), sk("cobblebase:berry_harvest", 5))
        species("shaymin", sk("cobblebase:growth_aura", 4), sk("cobblebase:crop_harvest", 4), sk("cobblebase:berry_harvest", 4))
    }

    // Helper functions
    private fun sk(skill: String, proficiency: Int) = SkillEntry(skill, proficiency)

    private fun species(name: String, vararg skills: SkillEntry) {
        register(SpeciesSkills(name, skills.toList()))
    }
}

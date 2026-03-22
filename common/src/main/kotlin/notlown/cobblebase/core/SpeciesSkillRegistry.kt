package notlown.cobblebase.core

import com.google.gson.Gson

/**
 * Registry mapping Pokemon species to their available skills.
 * Can be extended/overridden via datapacks (data/cobblebase/species_skills JSON files).
 */
object SpeciesSkillRegistry {
    private val gson = Gson()
    private val speciesMap = mutableMapOf<String, SpeciesSkills>()

    fun init() {
        registerDefaults()
        Cobblebase.LOGGER.info("SpeciesSkillRegistry: ${speciesMap.size} species registered")
    }

    fun getSkills(species: String): SpeciesSkills? = speciesMap[species.lowercase()]
    fun register(speciesSkills: SpeciesSkills) { speciesMap[speciesSkills.species.lowercase()] = speciesSkills }

    private fun sk(skillId: String, prof: Int) = SkillEntry(skillId, prof)
    private fun mon(name: String, vararg skills: SkillEntry) = register(SpeciesSkills(name, skills.toList()))

    private fun registerDefaults() {
        // -- Water --
        mon("squirtle",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 3), sk("cobblebase:water_fill", 2), sk("cobblebase:extinguisher", 2))
        mon("wartortle",    sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 4), sk("cobblebase:water_fill", 3), sk("cobblebase:extinguisher", 3))
        mon("blastoise",    sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 5), sk("cobblebase:water_fill", 4), sk("cobblebase:guard", 3))
        mon("psyduck",      sk("cobblebase:fishing", 2), sk("cobblebase:water_fill", 1))
        mon("golduck",      sk("cobblebase:fishing", 4), sk("cobblebase:diving", 3), sk("cobblebase:water_fill", 3))
        mon("slowpoke",     sk("cobblebase:fishing", 2))
        mon("slowbro",      sk("cobblebase:fishing", 3), sk("cobblebase:healer", 2))
        mon("magikarp",     sk("cobblebase:fishing", 1))
        mon("gyarados",     sk("cobblebase:fishing", 5), sk("cobblebase:guard", 4), sk("cobblebase:diving", 3))
        mon("lapras",       sk("cobblebase:fishing", 4), sk("cobblebase:diving", 4), sk("cobblebase:snow_fill", 3))
        mon("vaporeon",     sk("cobblebase:fishing", 4), sk("cobblebase:water_fill", 5), sk("cobblebase:diving", 3))
        mon("basculin",     sk("cobblebase:fishing", 3), sk("cobblebase:diving", 2))
        mon("basculegion",  sk("cobblebase:fishing", 4), sk("cobblebase:diving", 4), sk("cobblebase:guard", 2))
        mon("binacle",      sk("cobblebase:fishing", 2), sk("cobblebase:mining", 2))
        mon("barbaracle",   sk("cobblebase:fishing", 3), sk("cobblebase:mining", 4))
        mon("tentacool",    sk("cobblebase:fishing", 2), sk("cobblebase:diving", 2))
        mon("tentacruel",   sk("cobblebase:fishing", 3), sk("cobblebase:diving", 4))

        // -- Bug --
        mon("caterpie",     sk("cobblebase:harvester", 1))
        mon("butterfree",   sk("cobblebase:harvester", 3), sk("cobblebase:honey_collect", 2))
        mon("beedrill",     sk("cobblebase:honey_collect", 4), sk("cobblebase:harvester", 2), sk("cobblebase:guard", 2))
        mon("ninjask",      sk("cobblebase:harvester", 4), sk("cobblebase:scout", 3), sk("cobblebase:gatherer", 2))
        mon("shedinja",     sk("cobblebase:archeologist", 3))
        mon("combee",       sk("cobblebase:honey_collect", 4), sk("cobblebase:harvester", 1))
        mon("vespiquen",    sk("cobblebase:honey_collect", 5), sk("cobblebase:harvester", 3), sk("cobblebase:guard", 3))
        mon("scyther",      sk("cobblebase:harvester", 4), sk("cobblebase:guard", 3))
        mon("scizor",       sk("cobblebase:harvester", 5), sk("cobblebase:guard", 4), sk("cobblebase:mining", 2))
        mon("heracross",    sk("cobblebase:harvester", 3), sk("cobblebase:guard", 4), sk("cobblebase:mining", 3))

        // -- Fire --
        mon("charmander",   sk("cobblebase:friend_recruiter", 3), sk("cobblebase:lava_fill", 2), sk("cobblebase:furnace_fuel", 2))
        mon("charmeleon",   sk("cobblebase:friend_recruiter", 3), sk("cobblebase:lava_fill", 3), sk("cobblebase:furnace_fuel", 3))
        mon("charizard",    sk("cobblebase:friend_recruiter", 3), sk("cobblebase:lava_fill", 4), sk("cobblebase:furnace_fuel", 4), sk("cobblebase:guard", 3), sk("cobblebase:scout", 4))
        mon("vulpix",       sk("cobblebase:furnace_fuel", 2))
        mon("ninetales",    sk("cobblebase:furnace_fuel", 4), sk("cobblebase:lava_fill", 3))
        mon("growlithe",    sk("cobblebase:guard", 3), sk("cobblebase:furnace_fuel", 2))
        mon("arcanine",     sk("cobblebase:guard", 5), sk("cobblebase:furnace_fuel", 3), sk("cobblebase:scout", 3))
        mon("flareon",      sk("cobblebase:lava_fill", 4), sk("cobblebase:furnace_fuel", 4))
        mon("magmar",       sk("cobblebase:lava_fill", 4), sk("cobblebase:furnace_fuel", 3))
        mon("magmortar",    sk("cobblebase:lava_fill", 5), sk("cobblebase:furnace_fuel", 4))

        // -- Grass --
        mon("bulbasaur",    sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 2), sk("cobblebase:irrigator", 1))
        mon("ivysaur",      sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 3), sk("cobblebase:irrigator", 2))
        mon("venusaur",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 5), sk("cobblebase:irrigator", 4))
        mon("oddish",       sk("cobblebase:harvester", 1))
        mon("vileplume",    sk("cobblebase:harvester", 3), sk("cobblebase:healer", 2))
        mon("bellsprout",   sk("cobblebase:harvester", 2))
        mon("leafeon",      sk("cobblebase:harvester", 4), sk("cobblebase:irrigator", 3))
        mon("tropius",      sk("cobblebase:harvester", 5), sk("cobblebase:scout", 2))

        // -- Ground/Rock --
        mon("nidoking",     sk("cobblebase:archeologist", 4), sk("cobblebase:guard", 3), sk("cobblebase:mining", 2))
        mon("nidoqueen",    sk("cobblebase:archeologist", 3), sk("cobblebase:guard", 3))
        mon("geodude",      sk("cobblebase:mining", 2))
        mon("graveler",     sk("cobblebase:mining", 3))
        mon("golem",        sk("cobblebase:mining", 5), sk("cobblebase:guard", 3))
        mon("onix",         sk("cobblebase:mining", 3), sk("cobblebase:archeologist", 2))
        mon("steelix",      sk("cobblebase:mining", 5), sk("cobblebase:archeologist", 3))
        mon("boldore",      sk("cobblebase:mining", 3))
        mon("gigalith",     sk("cobblebase:mining", 5), sk("cobblebase:guard", 2))
        mon("rhyhorn",      sk("cobblebase:mining", 3), sk("cobblebase:guard", 2))
        mon("rhydon",       sk("cobblebase:mining", 4), sk("cobblebase:guard", 3))

        // -- Fighting --
        mon("machop",       sk("cobblebase:guard", 2), sk("cobblebase:gatherer", 1))
        mon("machoke",      sk("cobblebase:guard", 3), sk("cobblebase:gatherer", 2))
        mon("machamp",      sk("cobblebase:guard", 5), sk("cobblebase:gatherer", 3))
        mon("primeape",     sk("cobblebase:guard", 4))
        mon("lucario",      sk("cobblebase:guard", 5), sk("cobblebase:scout", 3))
        mon("hitmonchan",   sk("cobblebase:guard", 4))
        mon("hitmonlee",    sk("cobblebase:guard", 4))

        // -- Flying --
        mon("pidgey",       sk("cobblebase:scout", 1))
        mon("pidgeotto",    sk("cobblebase:scout", 3))
        mon("pidgeot",      sk("cobblebase:scout", 5))
        mon("spearow",      sk("cobblebase:scout", 2), sk("cobblebase:guard", 1))
        mon("fearow",       sk("cobblebase:scout", 4), sk("cobblebase:guard", 2))
        mon("zubat",        sk("cobblebase:scout", 1))
        mon("golbat",       sk("cobblebase:scout", 3))
        mon("crobat",       sk("cobblebase:scout", 4), sk("cobblebase:guard", 3))
        mon("staraptor",    sk("cobblebase:scout", 5), sk("cobblebase:guard", 3))

        // -- Psychic --
        mon("abra",         sk("cobblebase:gatherer", 2))
        mon("kadabra",      sk("cobblebase:gatherer", 4), sk("cobblebase:scout", 2))
        mon("alakazam",     sk("cobblebase:gatherer", 5), sk("cobblebase:scout", 4))
        mon("espeon",       sk("cobblebase:gatherer", 4), sk("cobblebase:scout", 3))

        // -- Fairy --
        mon("cleffa",       sk("cobblebase:healer", 1))
        mon("clefairy",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 2), sk("cobblebase:healer", 2))
        mon("clefable",     sk("cobblebase:friend_recruiter", 4), sk("cobblebase:harvester", 3), sk("cobblebase:healer", 3))
        mon("igglybuff",    sk("cobblebase:healer", 1))
        mon("jigglypuff",   sk("cobblebase:friend_recruiter", 3), sk("cobblebase:healer", 2))
        mon("wigglytuff",   sk("cobblebase:friend_recruiter", 4), sk("cobblebase:healer", 3))
        mon("sylveon",      sk("cobblebase:friend_recruiter", 4), sk("cobblebase:healer", 4))
        mon("togetic",      sk("cobblebase:friend_recruiter", 3), sk("cobblebase:healer", 3))
        mon("togekiss",     sk("cobblebase:friend_recruiter", 5), sk("cobblebase:healer", 4))
        mon("gardevoir",    sk("cobblebase:friend_recruiter", 4), sk("cobblebase:healer", 3), sk("cobblebase:gatherer", 3))
        mon("florges",      sk("cobblebase:friend_recruiter", 4), sk("cobblebase:harvester", 4))
        mon("primarina",    sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 3))
        mon("alcremie",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:healer", 3))
        mon("hatterene",    sk("cobblebase:friend_recruiter", 4), sk("cobblebase:healer", 3))

        // -- Ghost/Dark --
        mon("gastly",       sk("cobblebase:harvester", 1)) // netherwart
        mon("haunter",      sk("cobblebase:harvester", 2), sk("cobblebase:guard", 2))
        mon("gengar",       sk("cobblebase:harvester", 3), sk("cobblebase:guard", 4))
        mon("umbreon",      sk("cobblebase:guard", 4), sk("cobblebase:scout", 3))
        mon("absol",        sk("cobblebase:guard", 4), sk("cobblebase:scout", 3))

        // -- Normal --
        mon("ditto",        sk("cobblebase:gatherer", 3), sk("cobblebase:finder", 3))
        mon("eevee",        sk("cobblebase:gatherer", 2), sk("cobblebase:finder", 2))
        mon("snorlax",      sk("cobblebase:guard", 4))
        mon("happiny",      sk("cobblebase:healer", 3))
        mon("chansey",      sk("cobblebase:healer", 4))
        mon("blissey",      sk("cobblebase:healer", 5), sk("cobblebase:finder", 3))
        mon("meowth",       sk("cobblebase:finder", 4), sk("cobblebase:gatherer", 2))
        mon("persian",      sk("cobblebase:finder", 5), sk("cobblebase:gatherer", 3))
        mon("aipom",        sk("cobblebase:finder", 3), sk("cobblebase:gatherer", 2))
        mon("ambipom",      sk("cobblebase:finder", 4), sk("cobblebase:gatherer", 3))
        mon("zigzagoon",    sk("cobblebase:finder", 3))
        mon("linoone",      sk("cobblebase:finder", 4))
        mon("pachirisu",    sk("cobblebase:finder", 3))
        mon("lillipup",     sk("cobblebase:finder", 2))
        mon("herdier",      sk("cobblebase:finder", 3))
        mon("stoutland",    sk("cobblebase:finder", 5))
        mon("stufful",      sk("cobblebase:finder", 2))
        mon("bewear",       sk("cobblebase:finder", 3), sk("cobblebase:guard", 3))
        mon("munchlax",     sk("cobblebase:finder", 3))

        // -- Electric --
        mon("pikachu",      sk("cobblebase:gatherer", 3), sk("cobblebase:scout", 2))
        mon("raichu",       sk("cobblebase:gatherer", 4), sk("cobblebase:scout", 3))
        mon("jolteon",      sk("cobblebase:scout", 4), sk("cobblebase:gatherer", 3))

        // -- Ice --
        mon("glaceon",      sk("cobblebase:snow_fill", 4))
        mon("articuno",     sk("cobblebase:snow_fill", 5), sk("cobblebase:scout", 5))

        // -- Dragon --
        mon("dragonite",    sk("cobblebase:guard", 5), sk("cobblebase:scout", 5), sk("cobblebase:brew_fuel", 3))
        mon("salamence",    sk("cobblebase:guard", 5), sk("cobblebase:scout", 4))
        mon("garchomp",     sk("cobblebase:guard", 5), sk("cobblebase:mining", 4))

        // -- Steel --
        mon("aron",         sk("cobblebase:mining", 2))
        mon("lairon",       sk("cobblebase:mining", 3))
        mon("aggron",       sk("cobblebase:mining", 5), sk("cobblebase:guard", 4))

        // -- Poison --
        mon("grimer",       sk("cobblebase:extinguisher", 2))
        mon("muk",          sk("cobblebase:extinguisher", 3), sk("cobblebase:guard", 2))
        mon("koffing",      sk("cobblebase:brew_fuel", 2))
        mon("weezing",      sk("cobblebase:brew_fuel", 4))

        // -- Starters (all have Friend Recruiter 3) --
        // Gen 2
        mon("chikorita",    sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 2))
        mon("bayleef",      sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 3))
        mon("meganium",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 5), sk("cobblebase:healer", 2))
        mon("cyndaquil",    sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 2))
        mon("quilava",      sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 3))
        mon("typhlosion",   sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 4), sk("cobblebase:lava_fill", 3))
        mon("totodile",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 2))
        mon("croconaw",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 3))
        mon("feraligatr",   sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 4), sk("cobblebase:guard", 3))
        // Gen 3
        mon("treecko",      sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 2))
        mon("grovyle",      sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 3))
        mon("sceptile",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 4), sk("cobblebase:guard", 2))
        mon("torchic",      sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 2))
        mon("combusken",    sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 3), sk("cobblebase:guard", 2))
        mon("blaziken",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 4), sk("cobblebase:guard", 4))
        mon("mudkip",       sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 2), sk("cobblebase:water_fill", 1))
        mon("marshtomp",    sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 3), sk("cobblebase:water_fill", 2))
        mon("swampert",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 4), sk("cobblebase:water_fill", 3), sk("cobblebase:guard", 3))
        // Gen 4
        mon("turtwig",      sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 2))
        mon("grotle",       sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 3))
        mon("torterra",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 5), sk("cobblebase:guard", 2))
        mon("chimchar",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 2))
        mon("monferno",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 3), sk("cobblebase:guard", 2))
        mon("infernape",    sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 4), sk("cobblebase:guard", 4))
        mon("piplup",       sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 2))
        mon("prinplup",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 3))
        mon("empoleon",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 4), sk("cobblebase:guard", 3))
        // Gen 5
        mon("snivy",        sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 2))
        mon("servine",      sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 3))
        mon("serperior",    sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 4))
        mon("tepig",        sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 2))
        mon("pignite",      sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 3), sk("cobblebase:guard", 2))
        mon("emboar",       sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 4), sk("cobblebase:guard", 3))
        mon("oshawott",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 2))
        mon("dewott",       sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 3))
        mon("samurott",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 4), sk("cobblebase:guard", 3))
        // Gen 6
        mon("chespin",      sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 2))
        mon("quilladin",    sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 3))
        mon("chesnaught",   sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 4), sk("cobblebase:guard", 3))
        mon("fennekin",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 2))
        mon("braixen",      sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 3))
        mon("delphox",      sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 4))
        mon("froakie",      sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 2))
        mon("frogadier",    sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 3))
        mon("greninja",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 4), sk("cobblebase:guard", 4))
        // Gen 7 (Primarina already has it)
        mon("rowlet",       sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 2), sk("cobblebase:scout", 1))
        mon("dartrix",      sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 3), sk("cobblebase:scout", 2))
        mon("decidueye",    sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 4), sk("cobblebase:scout", 3))
        mon("litten",       sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 2))
        mon("torracat",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 3))
        mon("incineroar",   sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 4), sk("cobblebase:guard", 4))
        mon("popplio",      sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 2))
        mon("brionne",      sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 3))
        // Gen 8
        mon("grookey",      sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 2))
        mon("thwackey",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 3))
        mon("rillaboom",    sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 5))
        mon("scorbunny",    sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 2))
        mon("raboot",       sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 3))
        mon("cinderace",    sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 4), sk("cobblebase:guard", 3))
        mon("sobble",       sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 2))
        mon("drizzile",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 3))
        mon("inteleon",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 4), sk("cobblebase:scout", 3))
        // Gen 9
        mon("sprigatito",   sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 2))
        mon("floragato",    sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 3))
        mon("meowscarada",  sk("cobblebase:friend_recruiter", 3), sk("cobblebase:harvester", 4), sk("cobblebase:guard", 3))
        mon("fuecoco",      sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 2))
        mon("crocalor",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 3))
        mon("skeledirge",   sk("cobblebase:friend_recruiter", 3), sk("cobblebase:furnace_fuel", 4))
        mon("quaxly",       sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 2))
        mon("quaxwell",     sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 3))
        mon("quaquaval",    sk("cobblebase:friend_recruiter", 3), sk("cobblebase:fishing", 4), sk("cobblebase:guard", 3))

        // == LEGENDARIES - Unique Skills ==
        mon("mew",          sk("cobblebase:recruiter", 5), sk("cobblebase:lucky_charm", 5), sk("cobblebase:aura_boost", 3))
        mon("jirachi",      sk("cobblebase:recruiter", 5), sk("cobblebase:lucky_charm", 4))
        mon("celebi",       sk("cobblebase:growth_aura", 5), sk("cobblebase:harvester", 5))
        mon("shaymin",      sk("cobblebase:growth_aura", 4), sk("cobblebase:harvester", 4))
        mon("manaphy",      sk("cobblebase:fishing", 5), sk("cobblebase:diving", 5), sk("cobblebase:recruiter", 3))
        mon("victini",      sk("cobblebase:aura_boost", 5), sk("cobblebase:lucky_charm", 3))
        mon("mewtwo",       sk("cobblebase:guard", 5), sk("cobblebase:gatherer", 5), sk("cobblebase:aura_boost", 4))
        mon("rayquaza",     sk("cobblebase:guard", 5), sk("cobblebase:scout", 5), sk("cobblebase:aura_boost", 5))
        mon("arceus",       sk("cobblebase:recruiter", 5), sk("cobblebase:aura_boost", 5), sk("cobblebase:lucky_charm", 5), sk("cobblebase:growth_aura", 5))
    }
}

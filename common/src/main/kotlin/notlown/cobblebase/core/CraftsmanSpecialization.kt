package notlown.cobblebase.core

/**
 * Maps Craftsman Pokemon species to their recipe specialization.
 * Determines which recipe categories each Craftsman can access.
 */
object CraftsmanSpecialization {

    enum class Spec(val displayName: String, val categories: Set<String>) {
        FURNITURE("Furniture", setOf("Furniture", "Decoration", "Lighting", "Doors", "Cobblefurnies", "Mod Furniture", "Cobblemon")),
        WEAPONS("Weapons", setOf("Weapons")),
        ARMOR("Armor", setOf("Armor")),
        TOOLS("Tools", setOf("Tools")),
        BREWER("Brewer", setOf("Brewing")),
        COOK("Cook", setOf("Food")),
        REDSTONE("Engineer", setOf("Redstone")),
        MASON("Mason", setOf("Masonry")),
        DYER("Dyer", setOf("Dyed")),
        JEWELER("Jeweler", setOf("Jewelry")),
        ALL("All Crafts", setOf()) // empty = no filter, show everything
    }

    private val speciesMap = mapOf(
        // Furniture/Decoration crafters
        "tinkaton" to Spec.FURNITURE,
        "tinkatuff" to Spec.FURNITURE,
        "tinkatink" to Spec.FURNITURE,
        "conkeldurr" to Spec.FURNITURE,
        "gurdurr" to Spec.FURNITURE,
        "timburr" to Spec.FURNITURE,
        "leavanny" to Spec.FURNITURE,
        "sudowoodo" to Spec.FURNITURE,
        "trevenant" to Spec.FURNITURE,
        "phantump" to Spec.FURNITURE,
        "smeargle" to Spec.FURNITURE,

        // Weapon smiths
        "aegislash" to Spec.WEAPONS,
        "doublade" to Spec.WEAPONS,
        "honedge" to Spec.WEAPONS,
        "bisharp" to Spec.WEAPONS,
        "kingambit" to Spec.WEAPONS,
        "pawniard" to Spec.WEAPONS,
        "gallade" to Spec.WEAPONS,
        "kartana" to Spec.WEAPONS,
        "ceruledge" to Spec.WEAPONS,
        "scizor" to Spec.WEAPONS,

        // Armor smiths
        "golurk" to Spec.ARMOR,
        "golett" to Spec.ARMOR,
        "bastiodon" to Spec.ARMOR,
        "shieldon" to Spec.ARMOR,
        "falinks" to Spec.ARMOR,
        "aggron" to Spec.ARMOR,
        "lairon" to Spec.ARMOR,
        "aron" to Spec.ARMOR,
        "copperajah" to Spec.ARMOR,
        "cufant" to Spec.ARMOR,

        // Tool makers
        "klinklang" to Spec.TOOLS,
        "klang" to Spec.TOOLS,
        "klink" to Spec.TOOLS,
        "melmetal" to Spec.TOOLS,
        "meltan" to Spec.TOOLS,
        "magnezone" to Spec.TOOLS,
        "magneton" to Spec.TOOLS,
        "magnemite" to Spec.TOOLS,

        // Brewers
        "toxtricity" to Spec.BREWER,
        "weezing" to Spec.BREWER,
        "weezing_galarian" to Spec.BREWER,
        "roserade" to Spec.BREWER,
        "salazzle" to Spec.BREWER,
        "nihilego" to Spec.BREWER,
        "koffing" to Spec.BREWER,
        "roselia" to Spec.BREWER,
        "budew" to Spec.BREWER,

        // Cooks
        "alcremie" to Spec.COOK,
        "slurpuff" to Spec.COOK,
        "swirlix" to Spec.COOK,
        "munchlax" to Spec.COOK,
        "snorlax" to Spec.COOK,
        "fidough" to Spec.COOK,
        "dachsbun" to Spec.COOK,
        "appletun" to Spec.COOK,
        "milcery" to Spec.COOK,

        // Redstone Engineers
        "rotom" to Spec.REDSTONE,
        "electrode" to Spec.REDSTONE,
        "electrode_hisuian" to Spec.REDSTONE,
        "porygon" to Spec.REDSTONE,
        "porygon2" to Spec.REDSTONE,
        "porygonz" to Spec.REDSTONE,
        "voltorb" to Spec.REDSTONE,
        "voltorb_hisuian" to Spec.REDSTONE,

        // Masons
        "golem" to Spec.MASON,
        "graveler" to Spec.MASON,
        "geodude" to Spec.MASON,
        "rhyperior" to Spec.MASON,
        "rhydon" to Spec.MASON,
        "rhyhorn" to Spec.MASON,
        "onix" to Spec.MASON,
        "steelix" to Spec.MASON,
        "stonjourner" to Spec.MASON,
        "stakataka" to Spec.MASON,

        // Dyers
        "kecleon" to Spec.DYER,
        "oricorio" to Spec.DYER,
        "minior" to Spec.DYER,
        "vivillon" to Spec.DYER,
        "spinda" to Spec.DYER,

        // Jewelers
        "sableye" to Spec.JEWELER,
        "carbink" to Spec.JEWELER,
        "diancie" to Spec.JEWELER,
        "starmie" to Spec.JEWELER,
        "staryu" to Spec.JEWELER
    )

    fun getSpecialization(speciesName: String): Spec {
        return speciesMap[speciesName.lowercase()] ?: Spec.FURNITURE
    }

    fun getDisplayName(speciesName: String): String {
        return getSpecialization(speciesName).displayName
    }

    /**
     * Filter recipes for a specific craftsman's specialization.
     */
    fun filterRecipesForSpecies(speciesName: String, recipes: List<net.minecraft.util.Identifier>): List<net.minecraft.util.Identifier> {
        // Not used directly — filtering happens in the GUI via category matching
        return recipes
    }

    /**
     * Check if a recipe category is allowed for a given species.
     */
    fun isRecipeAllowed(speciesName: String, recipeCategory: String): Boolean {
        val spec = getSpecialization(speciesName)
        if (spec == Spec.ALL || spec.categories.isEmpty()) return true
        return recipeCategory in spec.categories
    }
}

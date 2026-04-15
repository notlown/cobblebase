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
        "magnemite" to Spec.TOOLS
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

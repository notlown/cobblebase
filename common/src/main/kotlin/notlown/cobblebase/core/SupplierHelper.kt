package notlown.cobblebase.core

/**
 * Maps crafting materials to which Cobblebase jobs can produce them.
 * Used by the Workshop tab to suggest which Mons should be assigned as suppliers.
 */
object SupplierHelper {

    data class SupplierSuggestion(
        val skillId: String,
        val skillName: String,
        val description: String
    )

    /**
     * For a given material item ID, returns which Cobblebase jobs can produce it.
     */
    fun getSupplierJobs(itemId: String): List<SupplierSuggestion> {
        val id = itemId.lowercase()
        val suggestions = mutableListOf<SupplierSuggestion>()

        // Architect (finder_bui) produces building materials
        if (id.contains("planks") || id.contains("cobblestone") || id.contains("stone_bricks") ||
            id.contains("bricks") || id.contains("sand") || id.contains("glass") ||
            id.contains("terracotta") || id.contains("quartz") || id.contains("dirt") ||
            id.contains("gravel") || id.contains("prismarine") || id.contains("deepslate") ||
            id.contains("purpur") || id.contains("end_stone")) {
            suggestions.add(SupplierSuggestion("cobblebase:finder_bui", "Architect", "finds building materials"))
        }

        // Mining produces raw ores, coal, cobblestone
        if (id.contains("raw_iron") || id.contains("raw_copper") || id.contains("raw_gold") ||
            id.contains("coal") || id.contains("cobblestone") || id.contains("diamond") ||
            id.contains("emerald") || id.contains("lapis") || id.contains("redstone") ||
            id.contains("amethyst") || id.contains("flint") || id.contains("iron_nugget")) {
            suggestions.add(SupplierSuggestion("cobblebase:mining", "Miner", "digs for ores and minerals"))
        }

        // Producer — wool, string, leather, etc.
        if (id.contains("wool") || id.contains("white_wool")) {
            suggestions.add(SupplierSuggestion("cobblebase:producer", "Producer", "Wooloo/Dubwool produce wool"))
        }
        if (id.contains("string")) {
            suggestions.add(SupplierSuggestion("cobblebase:producer", "Producer", "Spinarak/Ariados produce string"))
        }
        if (id.contains("leather")) {
            suggestions.add(SupplierSuggestion("cobblebase:producer", "Producer", "species-specific production"))
        }
        if (id.contains("iron_ingot") || id.contains("iron_nugget")) {
            suggestions.add(SupplierSuggestion("cobblebase:producer", "Producer", "Magnezone/Magneton produce iron"))
        }
        if (id.contains("gold_nugget") || id.contains("gold_ingot")) {
            suggestions.add(SupplierSuggestion("cobblebase:producer", "Producer", "Persian/Meowth produce gold"))
        }
        if (id.contains("slime_ball")) {
            suggestions.add(SupplierSuggestion("cobblebase:producer", "Producer", "Goodra/Sliggoo produce slime"))
        }
        if (id.contains("honeycomb") || id.contains("honey")) {
            suggestions.add(SupplierSuggestion("cobblebase:producer", "Producer", "Vespiquen/Combee produce honey"))
        }
        if (id.contains("charcoal") || id.contains("coal")) {
            suggestions.add(SupplierSuggestion("cobblebase:producer", "Producer", "Coalossal/Torkoal produce charcoal"))
        }
        if (id.contains("egg")) {
            suggestions.add(SupplierSuggestion("cobblebase:producer", "Producer", "Chansey/Blissey produce eggs"))
        }
        if (id.contains("ink_sac")) {
            suggestions.add(SupplierSuggestion("cobblebase:producer", "Producer", "Octillery/Inkay produce ink"))
        }

        // Harvester produces crops, berries
        if (id.contains("wheat") || id.contains("carrot") || id.contains("potato") ||
            id.contains("beetroot") || id.contains("sugar_cane") || id.contains("bamboo") ||
            id.contains("cocoa") || id.contains("pumpkin") || id.contains("melon") ||
            id.contains("berry") || id.contains("apricorn")) {
            suggestions.add(SupplierSuggestion("cobblebase:harvester", "Harvester", "harvests crops and plants"))
        }

        // Player can always supply manually
        if (suggestions.isEmpty()) {
            suggestions.add(SupplierSuggestion("manual", "Manual", "place in a nearby chest"))
        }

        return suggestions.distinctBy { it.skillId }
    }

    /**
     * Get a summary of all supplier jobs needed for a recipe's materials.
     */
    fun getNeededSuppliers(materials: Map<String, Int>): List<SupplierSuggestion> {
        val allSuggestions = mutableListOf<SupplierSuggestion>()
        for ((itemId, _) in materials) {
            allSuggestions.addAll(getSupplierJobs(itemId))
        }
        return allSuggestions.distinctBy { it.skillId }.filter { it.skillId != "manual" }
    }
}

package notlown.cobblebase.core

import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.recipe.CraftingRecipe
import net.minecraft.recipe.RecipeEntry
import net.minecraft.recipe.RecipeType
import net.minecraft.recipe.Ingredient
import net.minecraft.registry.Registries
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier

/**
 * Helper for accessing vanilla crafting recipes at runtime.
 * Collapses shaped/shapeless ingredient grids into flat material requirements.
 */
object RecipeHelper {

    /**
     * Lightweight recipe representation for network sync and GUI display.
     */
    data class SimplifiedRecipe(
        val recipeId: String,
        val outputItemId: String,
        val outputCount: Int,
        val outputDisplayName: String,
        val inputs: List<Pair<String, Int>>,  // itemId -> count
        val category: String
    )

    /**
     * Get craftable recipes from the server, filtered to furniture and decoration items only.
     * Also loads custom recipe types from mods like CobbleFurnies.
     */
    fun getAllSimplifiedRecipes(world: ServerWorld): List<SimplifiedRecipe> {
        val recipeManager = world.server.recipeManager
        val allRecipes = recipeManager.listAllOfType(RecipeType.CRAFTING)
        val result = mutableListOf<SimplifiedRecipe>()

        // Also load modded recipe types (CobbleFurnies uses cobblefurnies:furni_crafting)
        try {
            loadModdedRecipes(world, result)
        } catch (e: Exception) {
            Cobblebase.log("[RecipeHelper] Error loading modded recipes: ${e.message}")
        }

        for (entry in allRecipes) {
            try {
                val recipe = entry.value()
                val output = recipe.getResult(world.registryManager)
                if (output.isEmpty) continue

                val materials = getRequiredMaterials(recipe)
                if (materials.isEmpty()) continue

                val outputId = Registries.ITEM.getId(output.item).toString()

                // Only include furniture, decoration, and building items
                val category = categorizeRecipe(outputId, output) ?: continue

                val inputs = materials.map { (item, count) ->
                    Registries.ITEM.getId(item).toString() to count
                }

                result.add(SimplifiedRecipe(
                    recipeId = entry.id.toString(),
                    outputItemId = outputId,
                    outputCount = output.count,
                    outputDisplayName = output.name.string,
                    inputs = inputs,
                    category = category
                ))
            } catch (_: Exception) { /* skip unparseable recipes */ }
        }

        return result.sortedWith(compareBy({ it.category }, { it.outputDisplayName }))
    }

    /**
     * Collapse a recipe's ingredient grid into a flat item-to-count map.
     * For tag-based ingredients (e.g. #minecraft:planks), picks the first matching item.
     */
    fun getRequiredMaterials(recipe: CraftingRecipe): Map<Item, Int> {
        val counts = mutableMapOf<Item, Int>()
        for (ingredient in recipe.ingredients) {
            if (ingredient.isEmpty) continue
            val stacks = ingredient.matchingStacks
            if (stacks.isEmpty()) continue
            val item = stacks[0].item
            counts[item] = (counts[item] ?: 0) + 1
        }
        return counts
    }

    /**
     * Get the raw Ingredient list for a recipe (for matching items in chests).
     * Filters out empty ingredients.
     */
    fun getIngredients(recipe: CraftingRecipe): List<Ingredient> {
        return recipe.ingredients.filter { !it.isEmpty }
    }

    /**
     * Look up a recipe by ID. Iterates all crafting recipes to find the match,
     * since RecipeManager.get(Identifier) may not resolve correctly in all MC versions.
     */
    fun getRecipeById(world: ServerWorld, recipeId: String): CraftingRecipe? {
        return try {
            val allRecipes = world.server.recipeManager.listAllOfType(RecipeType.CRAFTING)
            val entry = allRecipes.find { it.id.toString() == recipeId }
            entry?.value()
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[RecipeHelper] Failed to find recipe '$recipeId': ${e.message}")
            null
        }
    }

    /**
     * Load CobbleFurnies recipes by reading the mod's recipe JSON data directly.
     * CobbleFurnies uses a custom recipe type (cobblefurnies:furni_crafting) that
     * can't be accessed via RecipeType.CRAFTING, so we read the JSON resources.
     */
    private fun loadModdedRecipes(world: ServerWorld, result: MutableList<SimplifiedRecipe>) {
        // Try to find all cobblefurnies items that exist in the item registry
        // and create SimplifiedRecipes for them using reasonable material guesses
        val gson = com.google.gson.Gson()
        var count = 0

        // Scan the recipe manager for all recipes whose ID starts with cobblefurnies:
        // Even custom recipe types are registered in the recipe manager
        try {
            val allRecipes = world.server.recipeManager.values()
            for (entry in allRecipes) {
                try {
                    val recipeId = entry.id.toString()
                    if (!recipeId.startsWith("cobblefurnies:")) continue

                    val recipe = entry.value()
                    val output = recipe.getResult(world.registryManager)
                    if (output.isEmpty) continue

                    val outputId = Registries.ITEM.getId(output.item).toString()

                    // Get ingredients if available
                    val ingredients = recipe.ingredients
                    val materials = mutableMapOf<Item, Int>()
                    for (ing in ingredients) {
                        if (ing.isEmpty) continue
                        val stacks = ing.matchingStacks
                        if (stacks.isEmpty()) continue
                        val item = stacks[0].item
                        materials[item] = (materials[item] ?: 0) + 1
                    }

                    if (materials.isEmpty()) continue

                    val inputs = materials.map { (item, c) ->
                        Registries.ITEM.getId(item).toString() to c
                    }

                    result.add(SimplifiedRecipe(
                        recipeId = recipeId,
                        outputItemId = outputId,
                        outputCount = output.count,
                        outputDisplayName = output.name.string,
                        inputs = inputs,
                        category = "Mod Furniture"
                    ))
                    count++
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        Cobblebase.log("[RecipeHelper] Loaded $count CobbleFurnies recipes")
    }

    /**
     * Categorize a recipe output for the Workshop GUI browser.
     * Returns null for items that shouldn't be craftable (weapons, tools, armor, food, etc.)
     * Only allows furniture, decoration, and building items.
     */
    private fun categorizeRecipe(outputId: String, output: ItemStack): String? {
        val id = outputId.lowercase()

        // Cobble Furniture mod — any non-vanilla, non-cobblemon mod items
        // These are modded furniture items from cobblefurniture, MrCrayfish, etc.
        if (!id.startsWith("minecraft:") && !id.startsWith("cobblemon:")) return "Mod Furniture"

        // Cobblemon-specific furniture/decoration
        if (id.startsWith("cobblemon:") && (id.contains("table") || id.contains("chair") || id.contains("shelf")
                    || id.contains("desk") || id.contains("counter") || id.contains("bench")
                    || id.contains("cabinet") || id.contains("lamp"))) return "Cobblemon"

        // Vanilla decoration items
        if (id.contains("lantern") || id.contains("candle") || id.contains("torch")) return "Lighting"
        if (id.contains("flower_pot") || id.contains("painting") || id.contains("item_frame")) return "Decoration"
        if (id.contains("banner")) return "Decoration"
        if (id.contains("sign") && !id.contains("design")) return "Decoration"
        if (id.contains("chain") || id.contains("ladder")) return "Decoration"
        if (id.contains("_bed")) return "Furniture"
        if (id.contains("carpet")) return "Furniture"
        if (id.contains("bookshelf") || id.contains("chiseled_bookshelf")) return "Furniture"

        // Building blocks (stairs, slabs, fences, doors)
        if (id.contains("stairs")) return "Building"
        if (id.contains("slab")) return "Building"
        if (id.contains("wall") && !id.contains("banner")) return "Building"
        if (id.contains("fence")) return "Building"
        if (id.contains("door")) return "Building"
        if (id.contains("trapdoor")) return "Building"
        if (id.contains("glass_pane") || id.contains("stained_glass")) return "Building"
        if (id.contains("iron_bars")) return "Building"

        // Everything else is excluded (tools, weapons, armor, food, redstone, etc.)
        return null
    }
}

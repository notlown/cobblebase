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
     */
    fun getAllSimplifiedRecipes(world: ServerWorld): List<SimplifiedRecipe> {
        val recipeManager = world.server.recipeManager
        val allRecipes = recipeManager.listAllOfType(RecipeType.CRAFTING)
        val result = mutableListOf<SimplifiedRecipe>()

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
     * Categorize a recipe output for the Workshop GUI browser.
     * Returns null for items that shouldn't be craftable (weapons, tools, armor, food, etc.)
     * Only allows furniture, decoration, and building items.
     */
    private fun categorizeRecipe(outputId: String, output: ItemStack): String? {
        val id = outputId.lowercase()

        // Always include Cobble Furniture and Cobblemon mod items
        // Check multiple possible mod IDs for cobble furniture
        if (id.startsWith("cobblefurniture:") || id.startsWith("cobble_furniture:") ||
            id.startsWith("cobblemon_furniture:") || id.startsWith("cobblemon:furniture_")) return "Cobblefurniture"
        // Any non-minecraft/cobblemon mod item that looks like furniture
        if (!id.startsWith("minecraft:") && !id.startsWith("cobblemon:") &&
            (id.contains("chair") || id.contains("table") || id.contains("shelf") || id.contains("desk") ||
             id.contains("counter") || id.contains("bench") || id.contains("cabinet") || id.contains("lamp") ||
             id.contains("couch") || id.contains("sofa") || id.contains("stool") || id.contains("drawer"))) return "Cobblefurniture"
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

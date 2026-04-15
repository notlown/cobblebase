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
     * Get all crafting recipes from the server, simplified for the Workshop GUI.
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
                val inputs = materials.map { (item, count) ->
                    Registries.ITEM.getId(item).toString() to count
                }

                val category = categorizeRecipe(outputId, output)

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
     * Look up a recipe by ID.
     */
    fun getRecipeById(world: ServerWorld, recipeId: String): CraftingRecipe? {
        return try {
            val id = Identifier.of(recipeId)
            val entry = world.server.recipeManager.get(id)
            entry?.orElse(null)?.value() as? CraftingRecipe
        } catch (_: Exception) { null }
    }

    /**
     * Categorize a recipe output for the Workshop GUI browser.
     */
    private fun categorizeRecipe(outputId: String, output: ItemStack): String {
        val id = outputId.lowercase()
        return when {
            id.contains("stairs") -> "Building"
            id.contains("slab") -> "Building"
            id.contains("wall") && !id.contains("banner") -> "Building"
            id.contains("fence") -> "Building"
            id.contains("door") -> "Building"
            id.contains("trapdoor") -> "Building"
            id.contains("planks") -> "Building"
            id.contains("bricks") -> "Building"
            id.contains("_block") && !id.contains("command") -> "Building"
            id.contains("glass") -> "Building"
            id.contains("concrete") -> "Building"
            id.contains("wool") -> "Building"
            id.contains("carpet") -> "Building"

            id.contains("sword") -> "Weapons"
            id.contains("bow") && !id.contains("bowl") -> "Weapons"
            id.contains("crossbow") -> "Weapons"
            id.contains("arrow") -> "Weapons"

            id.contains("pickaxe") || id.contains("axe") || id.contains("shovel") || id.contains("hoe") -> "Tools"
            id.contains("shears") -> "Tools"
            id.contains("bucket") -> "Tools"
            id.contains("compass") -> "Tools"
            id.contains("clock") -> "Tools"
            id.contains("fishing_rod") -> "Tools"
            id.contains("lead") -> "Tools"

            id.contains("helmet") || id.contains("chestplate") || id.contains("leggings") || id.contains("boots") -> "Armor"
            id.contains("shield") -> "Armor"

            id.contains("piston") || id.contains("hopper") || id.contains("dropper") || id.contains("dispenser") -> "Redstone"
            id.contains("repeater") || id.contains("comparator") || id.contains("observer") -> "Redstone"
            id.contains("redstone") && !id.contains("ore") -> "Redstone"
            id.contains("lever") || id.contains("button") || id.contains("pressure_plate") -> "Redstone"
            id.contains("rail") -> "Redstone"

            id.contains("lantern") || id.contains("candle") || id.contains("torch") -> "Decoration"
            id.contains("flower_pot") || id.contains("painting") || id.contains("item_frame") -> "Decoration"
            id.contains("banner") || id.contains("sign") || id.contains("bed") -> "Decoration"
            id.contains("chain") || id.contains("ladder") -> "Decoration"

            id.contains("bread") || id.contains("cake") || id.contains("cookie") || id.contains("pie") -> "Food"
            id.contains("stew") || id.contains("soup") || id.contains("sugar") -> "Food"

            id.contains("cobblemon:") -> "Cobblemon"

            else -> "Miscellaneous"
        }
    }
}

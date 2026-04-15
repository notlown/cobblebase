package notlown.cobblebase.core

import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import java.util.UUID

/**
 * Filters item drops for Craftsman supplier Mons.
 * When a Mon is in "craftsman_supply:" mode, only items the Craftsman
 * needs for its active project are kept. Everything else is discarded.
 */
object CraftsmanSupplyFilter {

    /**
     * Filters items for a supplier Mon. Returns only items the Craftsman needs.
     * Returns the original list if the Mon is not a supplier.
     */
    fun filterDrops(pokemonId: UUID, items: List<ItemStack>): List<ItemStack> {
        val assignment = BaseManager.getAssignment(pokemonId) ?: return items
        if (!assignment.startsWith(BaseManager.SUPPLIER_PREFIX)) return items

        // Find which Craftsman this supplier is helping
        // Look for any active Craftsman project that needs materials
        val neededItems = getNeededItemIds()
        if (neededItems.isEmpty()) return items // No active projects, keep everything

        val filtered = items.filter { stack ->
            if (stack.isEmpty) return@filter false
            val itemId = Registries.ITEM.getId(stack.item).toString()
            neededItems.contains(itemId)
        }
        // Log when items are filtered out so we can debug mismatches
        if (filtered.isEmpty() && items.isNotEmpty()) {
            val droppedIds = items.map { Registries.ITEM.getId(it.item).toString() }
            Cobblebase.log("[SupplyFilter] Discarded ${items.size} items (${droppedIds}) — needed: $neededItems")
        }
        return filtered
    }

    /**
     * Get all item IDs that any active Craftsman project currently needs.
     * Includes ALL variant items for tag-based ingredients (e.g. any plank type for #planks).
     */
    private fun getNeededItemIds(): Set<String> {
        val needed = mutableSetOf<String>()
        for ((_, project) in WorkshopManager.getAllProjects()) {
            if (project.phase != WorkshopManager.Phase.GATHERING) continue
            for ((itemId, required) in getProjectRequiredItems(project)) {
                val gathered = project.gatheredItems[itemId] ?: 0
                if (gathered < required) {
                    needed.add(itemId)
                    // Also add related variants (e.g. all plank types if one plank type is needed)
                    needed.addAll(getRelatedItems(itemId))
                }
            }
        }
        return needed
    }

    /**
     * For tag-based recipe ingredients, return all items in the same "family".
     * E.g. if minecraft:acacia_planks is needed, also accept oak_planks, spruce_planks etc.
     */
    fun getRelatedItems(itemId: String): Set<String> {
        val id = itemId.lowercase()
        val related = mutableSetOf<String>()

        // Planks — any plank type works for plank recipes
        if (id.contains("_planks")) {
            for (regId in Registries.ITEM.ids) {
                if (regId.path.endsWith("_planks")) related.add(regId.toString())
            }
        }
        // Logs
        if (id.contains("_log")) {
            for (regId in Registries.ITEM.ids) {
                if (regId.path.endsWith("_log") || regId.path.endsWith("_wood")) related.add(regId.toString())
            }
        }
        // Wool
        if (id.contains("_wool")) {
            for (regId in Registries.ITEM.ids) {
                if (regId.path.endsWith("_wool")) related.add(regId.toString())
            }
        }
        // Cobblestone variants
        if (id == "minecraft:cobblestone" || id == "minecraft:cobbled_deepslate") {
            related.add("minecraft:cobblestone")
            related.add("minecraft:cobbled_deepslate")
        }

        return related
    }

    /**
     * Get required items for a project. Uses a cached map since we can't
     * access RecipeHelper from here without a ServerWorld.
     * WorkshopManager stores the recipe ID, but we need the actual materials.
     * This is computed when the project is set and stored alongside.
     */
    private fun getProjectRequiredItems(project: WorkshopManager.WorkshopProject): Map<String, Int> {
        // The required items can be computed from the recipe, but we need ServerWorld for that.
        // Instead, use the gatheredItems keys as a hint — if gathering has started, those keys
        // represent what's needed. For new projects, we need a different approach.
        // Best approach: store required items in the WorkshopProject itself.
        return project.requiredItems
    }
}

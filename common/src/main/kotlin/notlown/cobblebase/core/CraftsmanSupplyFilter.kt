package notlown.cobblebase.core

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

        return items.filter { stack ->
            if (stack.isEmpty) return@filter false
            val itemId = Registries.ITEM.getId(stack.item).toString()
            neededItems.contains(itemId)
        }
    }

    /**
     * Get all item IDs that any active Craftsman project currently needs.
     */
    private fun getNeededItemIds(): Set<String> {
        val needed = mutableSetOf<String>()
        for ((_, project) in WorkshopManager.getAllProjects()) {
            if (project.phase != WorkshopManager.Phase.GATHERING) continue
            for ((itemId, required) in getProjectRequiredItems(project)) {
                val gathered = project.gatheredItems[itemId] ?: 0
                if (gathered < required) {
                    needed.add(itemId)
                }
            }
        }
        return needed
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

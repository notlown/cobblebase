package notlown.cobblebase.core

import notlown.cobblebase.core.net.AdminRecipesSyncS2CPacket

/**
 * Client cache for the Admin → Jobs → Craftsman → Recipes tab. Populated when the
 * server replies to an [notlown.cobblebase.core.net.AdminRecipesRequestC2SPacket].
 * Stores the **full** recipe list (including admin-disabled ones) plus the current
 * disabled set so the panel can render row state in one pass.
 */
object AdminRecipesCache {

    private var recipes: List<AdminRecipesSyncS2CPacket.Entry> = emptyList()
    private var disabledIds: MutableSet<String> = mutableSetOf()

    /** Replace cache from a server snapshot. */
    fun set(snapshot: AdminRecipesSyncS2CPacket) {
        recipes = snapshot.recipes
        disabledIds = snapshot.disabledIds.toMutableSet()
    }

    fun getAll(): List<AdminRecipesSyncS2CPacket.Entry> = recipes

    /** Every distinct category that appears in the recipe list, in insertion order. */
    fun getCategories(): List<String> = recipes.map { it.category }.distinct()

    fun byCategory(category: String): List<AdminRecipesSyncS2CPacket.Entry> =
        recipes.filter { it.category == category }

    fun isEnabled(recipeId: String): Boolean = recipeId !in disabledIds

    /** Optimistic local update — overwritten by the next server sync. */
    fun setLocal(recipeId: String, enabled: Boolean) {
        if (enabled) disabledIds.remove(recipeId) else disabledIds.add(recipeId)
    }

    /** Bulk local update for category-wide toggles. */
    fun setLocalMany(recipeIds: Collection<String>, enabled: Boolean) {
        if (enabled) disabledIds.removeAll(recipeIds.toSet())
        else disabledIds.addAll(recipeIds)
    }
}

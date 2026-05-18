package notlown.cobblebase.core

/**
 * Client-side mirror of [RecipeOverrides] populated by
 * [notlown.cobblebase.core.net.RecipeOverridesSyncS2CPacket]. The Admin GUI reads
 * this when rendering the Recipes sub-tab and writes optimistically before the
 * server's sync packet arrives back.
 */
object RecipeOverridesCache {

    private val disabled: MutableSet<String> = mutableSetOf()

    fun isEnabled(recipeId: String): Boolean = recipeId !in disabled

    fun setSnapshot(snapshot: Set<String>) {
        disabled.clear()
        disabled.addAll(snapshot)
    }

    /** Optimistic local update — server snapshot will arrive shortly and overwrite. */
    fun setLocal(recipeId: String, enabled: Boolean) {
        if (enabled) disabled.remove(recipeId) else disabled.add(recipeId)
    }

    fun getDisabledSnapshot(): Set<String> = disabled.toSet()
}

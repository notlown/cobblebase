package notlown.cobblebase.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.WorldSavePath
import java.io.File

/**
 * Per-recipe enable/disable overrides for the Craftsman job.
 *
 * Admins disable recipes from the in-game Admin → Jobs → Craftsman → Recipes tab.
 * Default is "enabled" — only recipes explicitly disabled by the admin appear in this
 * map. That keeps the JSON file small on fresh worlds and means a new recipe added by
 * a mod update is allowed by default.
 *
 * Stored per-world in `cobblebase_recipe_overrides.json`. Synced to clients on join
 * + on every admin update via [notlown.cobblebase.core.net.RecipeOverridesSyncS2CPacket].
 */
object RecipeOverrides {

    /** Map of recipeId (e.g. "minecraft:crafting_table") → enabled boolean. */
    private val disabled: MutableSet<String> = mutableSetOf()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /** True when the recipe is enabled (default). False only if the admin disabled it. */
    fun isEnabled(recipeId: String): Boolean = recipeId !in disabled

    /** Apply an enable/disable change. */
    fun set(recipeId: String, enabled: Boolean) {
        if (enabled) disabled.remove(recipeId) else disabled.add(recipeId)
    }

    /** Apply a snapshot from a sync packet (replaces local state). */
    fun setDisabledSnapshot(snapshot: Set<String>) {
        disabled.clear()
        disabled.addAll(snapshot)
    }

    /** Disable every recipe in [recipeIds] at once. Used by the "disable category" button. */
    fun disableAll(recipeIds: Collection<String>) {
        disabled.addAll(recipeIds)
    }

    /** Enable every recipe in [recipeIds] at once. Used by the "enable category" button. */
    fun enableAll(recipeIds: Collection<String>) {
        disabled.removeAll(recipeIds.toSet())
    }

    /** Snapshot of disabled IDs for sync packets. */
    fun getDisabledSnapshot(): Set<String> = disabled.toSet()

    // -- Persistence ----------------------------------------------------------------

    private fun getSaveFile(world: ServerWorld): File {
        val saveDir = world.server.getSavePath(WorldSavePath.ROOT).toFile()
        return File(saveDir, "cobblebase_recipe_overrides.json")
    }

    fun save(world: ServerWorld) {
        try {
            val file = getSaveFile(world)
            // Serialize as a plain list of disabled IDs — simpler than a map of booleans
            // and reflects the "default true, only disabled IDs tracked" model.
            file.writeText(gson.toJson(disabled.sorted()))
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[RecipeOverrides] Failed to save: ${e.message}")
        }
    }

    fun load(world: ServerWorld) {
        try {
            val file = getSaveFile(world)
            if (!file.exists()) {
                disabled.clear()
                return
            }
            val type = object : TypeToken<List<String>>() {}.type
            val loaded: List<String>? = gson.fromJson(file.readText(), type)
            disabled.clear()
            if (loaded != null) disabled.addAll(loaded)
            Cobblebase.LOGGER.debug("[RecipeOverrides] Loaded ${disabled.size} disabled recipe IDs")
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[RecipeOverrides] Failed to load: ${e.message}")
        }
    }
}

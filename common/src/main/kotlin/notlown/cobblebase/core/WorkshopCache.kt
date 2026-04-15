package notlown.cobblebase.core

import notlown.cobblebase.core.net.RecipeListSyncS2CPacket
import java.util.UUID

/**
 * Client-side cache for workshop data received from the server.
 */
object WorkshopCache {

    /** All craftable recipes (received from server) */
    var recipes: List<RecipeListSyncS2CPacket.RecipeDTO> = emptyList()
        private set

    /** Active projects per Pokemon (pokemonId -> project info) */
    data class ProjectState(
        val recipeId: String,
        val gatheredItems: Map<String, Int>,
        val phase: String,
        val requiredItems: Map<String, Int>
    )

    var projects: Map<UUID, ProjectState> = emptyMap()
        private set

    var recipesLoaded = false
        private set

    fun updateRecipes(list: List<RecipeListSyncS2CPacket.RecipeDTO>) {
        recipes = list
        recipesLoaded = true
    }

    fun updateProjects(map: Map<UUID, ProjectState>) {
        projects = map
    }

    fun clear() {
        recipes = emptyList()
        projects = emptyMap()
        recipesLoaded = false
    }
}

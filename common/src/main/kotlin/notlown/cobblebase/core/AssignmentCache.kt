package notlown.cobblebase.core

import java.util.UUID

/**
 * Client-side cache for skill assignments received from the server.
 * The GUI reads from this cache instead of BaseManager (which is server-only).
 */
object AssignmentCache {

    private val assignments = mutableMapOf<UUID, String?>()

    fun update(data: Map<UUID, String?>) {
        assignments.clear()
        assignments.putAll(data)
    }

    fun getAssignment(pokemonId: UUID): String? = assignments[pokemonId]

    fun isCraftsmanSupplier(pokemonId: UUID): Boolean {
        val assignment = assignments[pokemonId] ?: return false
        return assignment.startsWith("craftsman_supply:")
    }

    fun setAssignment(pokemonId: UUID, skillId: String?) {
        if (skillId == null) assignments.remove(pokemonId) else assignments[pokemonId] = skillId
    }

    fun clear() {
        assignments.clear()
    }
}

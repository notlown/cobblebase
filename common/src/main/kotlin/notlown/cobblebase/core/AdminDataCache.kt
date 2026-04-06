package notlown.cobblebase.core

/**
 * Client-side cache for admin species data received from the server.
 * Species names + override flags are loaded upfront.
 * Skills are loaded lazily on demand when a species is selected.
 */
object AdminDataCache {

    /** All species names from Cobblemon (lowercase) */
    var allSpecies: List<String> = emptyList()

    /** Lazy-loaded cache: species -> skills (populated as user selects species) */
    var speciesSkills: MutableMap<String, List<SkillEntry>> = mutableMapOf()
        private set

    /** Set of species names that have admin overrides */
    var overriddenSpecies: Set<String> = emptySet()
        private set

    /** Species currently being requested from server (avoid duplicate requests) */
    private val pendingRequests = mutableSetOf<String>()

    /** Update species list and overrides (initial sync) */
    fun update(species: List<String>, overridden: Set<String>) {
        allSpecies = species
        overriddenSpecies = overridden
    }

    /** Store skills for a single species (response from server) */
    fun setSpeciesSkills(species: String, skills: List<SkillEntry>) {
        speciesSkills[species] = skills
        pendingRequests.remove(species)
    }

    /** Mark species as having a request in flight */
    fun markPending(species: String): Boolean {
        return pendingRequests.add(species)
    }

    fun isPending(species: String): Boolean = pendingRequests.contains(species)

    /** Update single species after admin save (without re-requesting) */
    fun updateLocalSkills(species: String, skills: List<SkillEntry>) {
        speciesSkills[species] = skills
        val newOverridden = overriddenSpecies.toMutableSet()
        newOverridden.add(species)
        overriddenSpecies = newOverridden
    }

    fun clear() {
        allSpecies = emptyList()
        speciesSkills = mutableMapOf()
        overriddenSpecies = emptySet()
        pendingRequests.clear()
    }
}

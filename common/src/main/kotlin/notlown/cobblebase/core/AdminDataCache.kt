package notlown.cobblebase.core

/**
 * Client-side cache for admin species data received from the server.
 * Updated when AdminSpeciesSyncS2CPacket is received.
 */
object AdminDataCache {

    /** All species names from Cobblemon (lowercase) */
    var allSpecies: List<String> = emptyList()
        private set

    /** Species -> list of SkillEntry for those that have assignments */
    var speciesSkills: Map<String, List<SkillEntry>> = emptyMap()
        private set

    /** Set of species names that have admin overrides (not just built-in) */
    var overriddenSpecies: Set<String> = emptySet()
        private set

    fun update(
        species: List<String>,
        skills: Map<String, List<SkillEntry>>,
        overridden: Set<String>
    ) {
        allSpecies = species
        speciesSkills = skills
        overriddenSpecies = overridden
    }

    fun clear() {
        allSpecies = emptyList()
        speciesSkills = emptyMap()
        overriddenSpecies = emptySet()
    }
}

package notlown.cobblebase.core

import com.google.gson.Gson
import java.io.InputStreamReader

/**
 * Registry mapping Pokemon species to their available skills.
 * Species skill JSON files are located in data/cobblebase/species_skills/ and indexed by _index.txt.
 * Can be extended/overridden via datapacks.
 */
object SpeciesSkillRegistry {
    private val gson = Gson()
    private val speciesMap = mutableMapOf<String, SpeciesSkills>()
    private val builtInMap = mutableMapOf<String, SpeciesSkills>()

    fun init() {
        loadFromResources()
        Cobblebase.LOGGER.info("SpeciesSkillRegistry: ${speciesMap.size} species registered")
    }

    // Regional/alternate forms that get their own species_skills file (e.g. "vulpix_alolan")
    private val FORM_ASPECTS = setOf("alolan", "galarian", "hisuian", "paldean", "valencian", "chest", "roaming")

    fun getSkills(species: String): SpeciesSkills? = speciesMap[species.lowercase()]
    fun getBuiltInSkills(species: String): SpeciesSkills? = builtInMap[species.lowercase()]
    fun getAllAssigned(): Map<String, SpeciesSkills> = speciesMap.toMap()
    fun register(speciesSkills: SpeciesSkills) { speciesMap[speciesSkills.species.lowercase()] = speciesSkills }

    /**
     * Resolves a form-aware species name from a base species name and a set of aspects.
     * If a regional form aspect is present and a form-specific entry is registered, returns "species_form".
     * Otherwise falls back to the base species name.
     */
    fun resolveFormName(baseName: String, aspects: Set<String>): String {
        val lower = baseName.lowercase()
        val formAspect = aspects.firstOrNull { it.lowercase() in FORM_ASPECTS }
        if (formAspect != null) {
            val formName = "${lower}_${formAspect.lowercase()}"
            if (speciesMap.containsKey(formName)) return formName
        }
        return lower
    }

    private fun loadFromResources() {
        val indexPath = "/data/cobblebase/species_skills/_index.txt"
        val indexStream = Cobblebase::class.java.getResourceAsStream(indexPath)
        if (indexStream == null) {
            Cobblebase.LOGGER.warn("[Cobblebase] Species skills index not found at $indexPath")
            return
        }

        val fileNames = indexStream.bufferedReader().use { it.readLines() }
            .map { it.trim() }
            .filter { it.endsWith(".json") }

        for (fileName in fileNames) {
            try {
                val resourcePath = "/data/cobblebase/species_skills/$fileName"
                val stream = Cobblebase::class.java.getResourceAsStream(resourcePath)
                if (stream == null) {
                    Cobblebase.LOGGER.warn("[Cobblebase] Species skill file not found: $resourcePath")
                    continue
                }
                val speciesSkills = stream.use { s ->
                    InputStreamReader(s, Charsets.UTF_8).use { reader ->
                        gson.fromJson(reader, SpeciesSkills::class.java)
                    }
                }
                if (speciesSkills != null) {
                    register(speciesSkills)
                    builtInMap[speciesSkills.species.lowercase()] = speciesSkills
                } else {
                    Cobblebase.LOGGER.warn("[Cobblebase] Failed to parse species skills from $fileName")
                }
            } catch (e: Exception) {
                Cobblebase.LOGGER.error("[Cobblebase] Error loading species skills $fileName: ${e.message}")
            }
        }
    }
}

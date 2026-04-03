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

    fun getSkills(species: String): SpeciesSkills? = speciesMap[species.lowercase()]
    fun getBuiltInSkills(species: String): SpeciesSkills? = builtInMap[species.lowercase()]
    fun getAllAssigned(): Map<String, SpeciesSkills> = speciesMap.toMap()
    fun register(speciesSkills: SpeciesSkills) { speciesMap[speciesSkills.species.lowercase()] = speciesSkills }

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

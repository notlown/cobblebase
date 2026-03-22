package notlown.cobblebase.core

import com.google.gson.Gson
import java.io.InputStreamReader

/**
 * Registry that loads and holds all skill definitions from JSON resources.
 * Skill JSON files are located in data/cobblebase/skills/ and indexed by _index.txt.
 */
object SkillRegistry {
    private val gson = Gson()
    private val skills = mutableMapOf<String, SkillDef>()

    fun init() {
        loadFromResources()
        Cobblebase.LOGGER.info("SkillRegistry: ${skills.size} skills registered")
    }

    fun get(id: String): SkillDef? = skills[id]
    fun getAll(): Map<String, SkillDef> = skills.toMap()
    fun register(skill: SkillDef) { skills[skill.id] = skill }

    private fun loadFromResources() {
        val indexPath = "/data/cobblebase/skills/_index.txt"
        val indexStream = Cobblebase::class.java.getResourceAsStream(indexPath)
        if (indexStream == null) {
            Cobblebase.LOGGER.warn("[Cobblebase] Skill index not found at $indexPath")
            return
        }

        val fileNames = indexStream.bufferedReader().use { it.readLines() }
            .map { it.trim() }
            .filter { it.endsWith(".json") }

        for (fileName in fileNames) {
            try {
                val resourcePath = "/data/cobblebase/skills/$fileName"
                val stream = Cobblebase::class.java.getResourceAsStream(resourcePath)
                if (stream == null) {
                    Cobblebase.LOGGER.warn("[Cobblebase] Skill file not found: $resourcePath")
                    continue
                }
                val skillDef = stream.use { s ->
                    InputStreamReader(s, Charsets.UTF_8).use { reader ->
                        gson.fromJson(reader, SkillDef::class.java)
                    }
                }
                if (skillDef != null) {
                    register(skillDef)
                } else {
                    Cobblebase.LOGGER.warn("[Cobblebase] Failed to parse skill from $fileName")
                }
            } catch (e: Exception) {
                Cobblebase.LOGGER.error("[Cobblebase] Error loading skill $fileName: ${e.message}")
            }
        }
    }
}

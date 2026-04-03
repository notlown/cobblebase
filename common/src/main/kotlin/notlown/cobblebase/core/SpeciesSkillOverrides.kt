package notlown.cobblebase.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.WorldSavePath
import java.io.File

/**
 * Stores custom skill assignments as overrides on top of built-in JSON data.
 * When an override exists for a species, it FULLY REPLACES the built-in data.
 */
object SpeciesSkillOverrides {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val overrides = mutableMapOf<String, List<SkillEntry>>()

    fun getOverride(species: String): List<SkillEntry>? = overrides[species.lowercase()]

    fun getAllOverriddenSpecies(): Set<String> = overrides.keys.toSet()

    fun hasOverride(species: String): Boolean = overrides.containsKey(species.lowercase())

    /**
     * Sets an override for a species. Saves to disk and hot-reloads into SpeciesSkillRegistry.
     */
    fun setOverride(species: String, skills: List<SkillEntry>, world: ServerWorld) {
        val key = species.lowercase()
        overrides[key] = skills
        // Hot-reload into SpeciesSkillRegistry
        SpeciesSkillRegistry.register(SpeciesSkills(key, skills))
        save(world)
        Cobblebase.LOGGER.info("[Cobblebase] Override set for species '$key' with ${skills.size} skills")
    }

    /**
     * Removes an override for a species, reverting to built-in data.
     */
    fun removeOverride(species: String, world: ServerWorld) {
        val key = species.lowercase()
        overrides.remove(key)
        // Revert to built-in data
        val builtIn = SpeciesSkillRegistry.getBuiltInSkills(key)
        if (builtIn != null) {
            SpeciesSkillRegistry.register(builtIn)
        }
        save(world)
        Cobblebase.LOGGER.info("[Cobblebase] Override removed for species '$key', reverted to built-in")
    }

    private fun getSaveFile(world: ServerWorld): File {
        val saveDir = world.server.getSavePath(WorldSavePath.ROOT).toFile()
        return File(saveDir, "cobblebase_species_overrides.json")
    }

    fun save(world: ServerWorld) {
        try {
            val file = getSaveFile(world)
            val data = overrides.mapValues { (_, skills) ->
                skills.map { mapOf("skillId" to it.skillId, "proficiency" to it.proficiency) }
            }
            file.writeText(gson.toJson(data))
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to save species overrides: ${e.message}")
        }
    }

    fun load(world: ServerWorld) {
        try {
            val file = getSaveFile(world)
            if (!file.exists()) return
            val type = object : TypeToken<Map<String, List<Map<String, Any>>>>() {}.type
            val data: Map<String, List<Map<String, Any>>> = gson.fromJson(file.readText(), type) ?: return
            overrides.clear()
            for ((species, skillMaps) in data) {
                val skills = skillMaps.map { m ->
                    SkillEntry(
                        skillId = m["skillId"] as String,
                        proficiency = (m["proficiency"] as Double).toInt()
                    )
                }
                overrides[species.lowercase()] = skills
                // Apply override into SpeciesSkillRegistry
                SpeciesSkillRegistry.register(SpeciesSkills(species.lowercase(), skills))
            }
            Cobblebase.LOGGER.info("[Cobblebase] Loaded ${overrides.size} species skill overrides")
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to load species overrides: ${e.message}")
        }
    }
}

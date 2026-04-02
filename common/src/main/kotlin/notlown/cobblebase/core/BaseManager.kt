package notlown.cobblebase.core

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.WorldSavePath
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.io.File
import java.util.UUID

object BaseManager {

    private val gson = Gson()
    private val assignments = mutableMapOf<UUID, String?>()
    private var dirty = false
    private var lastSaveTick = 0L
    private val SAVE_INTERVAL = 600L // save every 30 seconds

    fun tickPokemon(world: World, pastureOrigin: BlockPos, pokemonEntity: PokemonEntity) {
        val pokemonId: UUID = pokemonEntity.pokemon.uuid
        val speciesName: String = pokemonEntity.pokemon.species.name.lowercase()
        val now = world.time

        // Auto-save periodically
        if (dirty && world is ServerWorld && now - lastSaveTick > SAVE_INTERVAL) {
            save(world)
        }

        val speciesData: SpeciesSkills? = SpeciesSkillRegistry.getSkills(speciesName)
        if (speciesData == null) {
            if (now % 100 == 0L) Cobblebase.LOGGER.info("[BaseManager] $speciesName has NO species skills registered")
            return
        }

        val assignedSkillId: String? = assignments[pokemonId]

        if (assignedSkillId != null) {
            val entry: SkillEntry? = speciesData.skills.find { e -> e.skillId == assignedSkillId }
            if (entry == null) {
                if (now % 100 == 0L) Cobblebase.LOGGER.info("[BaseManager] $speciesName assigned=$assignedSkillId but skill NOT FOUND in species skills: ${speciesData.skills.map { it.skillId }}")
                return
            }
            executeSkill(world, pastureOrigin, pokemonEntity, entry)
        } else {
            if (now % 100 == 0L) Cobblebase.LOGGER.info("[BaseManager] $speciesName has no assignment, running all ${speciesData.skills.size} skills")
            for (entry: SkillEntry in speciesData.skills) {
                executeSkill(world, pastureOrigin, pokemonEntity, entry)
            }
        }
    }

    private fun executeSkill(world: World, origin: BlockPos, pokemonEntity: PokemonEntity, entry: SkillEntry) {
        val skillDef: SkillDef? = SkillRegistry.get(entry.skillId)
        if (skillDef == null) {
            if (world.time % 100 == 0L) Cobblebase.LOGGER.info("[BaseManager] SkillDef NOT FOUND for ${entry.skillId}")
            return
        }
        val exec: SkillExecutor? = ExecutorRegistry.get(skillDef.executor)
        if (exec == null) {
            if (world.time % 100 == 0L) Cobblebase.LOGGER.info("[BaseManager] Executor NOT FOUND for ${skillDef.executor}")
            return
        }
        exec.tick(world, origin, pokemonEntity, skillDef, entry)
    }

    fun assignSkill(pokemonId: UUID, skillId: String?) {
        if (skillId == null) assignments.remove(pokemonId) else assignments[pokemonId] = skillId
        dirty = true
    }

    fun getAssignment(pokemonId: UUID): String? = assignments[pokemonId]

    fun getAvailableSkills(pokemonEntity: PokemonEntity): List<SkillEntry> {
        val speciesName: String = pokemonEntity.pokemon.species.name.lowercase()
        return SpeciesSkillRegistry.getSkills(speciesName)?.skills ?: emptyList()
    }

    // -- Persistence --

    private fun getSaveFile(world: ServerWorld): File {
        val saveDir = world.server.getSavePath(WorldSavePath.ROOT).toFile()
        return File(saveDir, "cobblebase_assignments.json")
    }

    fun save(world: World) {
        if (world !is ServerWorld) return
        try {
            val file = getSaveFile(world)
            val data = assignments.mapKeys { it.key.toString() }
            file.writeText(gson.toJson(data))
            dirty = false
            lastSaveTick = world.time
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to save assignments: ${e.message}")
        }
    }

    fun load(world: World) {
        if (world !is ServerWorld) return
        try {
            val file = getSaveFile(world)
            if (!file.exists()) return
            val type = object : TypeToken<Map<String, String?>>() {}.type
            val data: Map<String, String?> = gson.fromJson(file.readText(), type)
            assignments.clear()
            for ((key, value) in data) {
                assignments[UUID.fromString(key)] = value
            }
            Cobblebase.LOGGER.info("[Cobblebase] Loaded ${assignments.size} skill assignments")
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to load assignments: ${e.message}")
        }
    }
}

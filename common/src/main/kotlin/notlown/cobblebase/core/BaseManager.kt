package notlown.cobblebase.core

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.executor.ExecutorRegistry
import java.util.UUID

/**
 * Manages active bases (Pasture Blocks) and dispatches skill execution.
 * This replaces Cobbleworkers' WorkerDispatcher with a data-driven approach.
 */
object BaseManager {

    // Manual job assignments: pokemonUUID -> skill ID (null = auto, uses species defaults)
    private val assignments = mutableMapOf<UUID, String?>()

    /**
     * Tick all skills for a Pokemon in a base.
     * Called from the Pasture Block Entity mixin.
     */
    fun tickPokemon(world: World, pastureOrigin: BlockPos, pokemonEntity: PokemonEntity) {
        val pokemonId = pokemonEntity.pokemon.uuid
        val speciesName = pokemonEntity.pokemon.species.name.lowercase()

        // Get this species' available skills
        val speciesSkills = SpeciesSkillRegistry.getSkills(speciesName) ?: return

        val assignedSkillId = assignments[pokemonId]

        if (assignedSkillId != null) {
            // Manual assignment: run only the assigned skill
            val entry = speciesSkills.skills.find { it.skill == assignedSkillId }
                ?: speciesSkills.skills.firstOrNull() ?: return
            val skill = SkillRegistry.get(entry.skill) ?: return
            val executor = ExecutorRegistry.get(skill.executor) ?: return
            executor.tick(world, pastureOrigin, pokemonEntity, skill, entry)
        } else {
            // Auto mode: run ALL available skills for this species
            for (entry in speciesSkills.skills) {
                val skill = SkillRegistry.get(entry.skill) ?: continue
                val executor = ExecutorRegistry.get(skill.executor) ?: continue
                executor.tick(world, pastureOrigin, pokemonEntity, skill, entry)
            }
        }
    }

    fun assignSkill(pokemonId: UUID, skillId: String?) {
        if (skillId == null) {
            assignments.remove(pokemonId)
        } else {
            assignments[pokemonId] = skillId
        }
    }

    fun getAssignment(pokemonId: UUID): String? = assignments[pokemonId]

    fun getAvailableSkills(pokemonEntity: PokemonEntity): List<notlown.cobblebase.skill.SkillEntry> {
        val speciesName = pokemonEntity.pokemon.species.name.lowercase()
        return SpeciesSkillRegistry.getSkills(speciesName)?.skills ?: emptyList()
    }
}

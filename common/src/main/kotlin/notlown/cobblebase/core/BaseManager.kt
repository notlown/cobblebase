package notlown.cobblebase.core

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID

object BaseManager {

    private val assignments = mutableMapOf<UUID, String?>()

    fun tickPokemon(world: World, pastureOrigin: BlockPos, pokemonEntity: PokemonEntity) {
        val pokemonId: UUID = pokemonEntity.pokemon.uuid
        val speciesName: String = pokemonEntity.pokemon.species.name.lowercase()

        val speciesData: SpeciesSkills = SpeciesSkillRegistry.getSkills(speciesName) ?: return
        val assignedSkillId: String? = assignments[pokemonId]

        if (assignedSkillId != null) {
            val entry: SkillEntry = speciesData.skills.find { e -> e.skillId == assignedSkillId } ?: return
            executeSkill(world, pastureOrigin, pokemonEntity, entry)
        } else {
            for (entry: SkillEntry in speciesData.skills) {
                executeSkill(world, pastureOrigin, pokemonEntity, entry)
            }
        }
    }

    private fun executeSkill(world: World, origin: BlockPos, pokemonEntity: PokemonEntity, entry: SkillEntry) {
        val skillDef: SkillDef = SkillRegistry.get(entry.skillId) ?: return
        val exec: SkillExecutor = ExecutorRegistry.get(skillDef.executor) ?: return
        exec.tick(world, origin, pokemonEntity, skillDef, entry)
    }

    fun assignSkill(pokemonId: UUID, skillId: String?) {
        if (skillId == null) assignments.remove(pokemonId) else assignments[pokemonId] = skillId
    }

    fun getAssignment(pokemonId: UUID): String? = assignments[pokemonId]

    fun getAvailableSkills(pokemonEntity: PokemonEntity): List<SkillEntry> {
        val speciesName: String = pokemonEntity.pokemon.species.name.lowercase()
        return SpeciesSkillRegistry.getSkills(speciesName)?.skills ?: emptyList()
    }
}

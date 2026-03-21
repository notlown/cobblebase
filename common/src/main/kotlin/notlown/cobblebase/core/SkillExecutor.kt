package notlown.cobblebase.core

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Executes a skill for a Pokemon. Each executor type handles a different mechanic.
 * The executor name in the Skill JSON maps to a registered executor implementation.
 *
 * This is the core of the ECS-like system: SkillDefs are data, executors are behavior.
 */
interface SkillExecutor {
    /**
     * Execute one tick of this skill for the given Pokemon.
     * @param world The server world
     * @param origin The pasture block position
     * @param pokemonEntity The Pokemon entity performing the skill
     * @param skill The skill definition (contains cooldown, radius, etc.)
     * @param skillEntry The species-specific entry (contains proficiency)
     */
    fun tick(
        world: World,
        origin: BlockPos,
        pokemonEntity: PokemonEntity,
        skill: SkillDef,
        skillEntry: SkillEntry
    )
}

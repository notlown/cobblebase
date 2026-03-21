package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Blocks
import net.minecraft.block.LeveledCauldronBlock
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.effects.SkillEffects
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import java.util.UUID

/**
 * Cauldron fill executor for lava, water, and powder snow fill skills.
 * Finds nearby empty cauldrons, navigates to them, and fills them based on the skill's effectType.
 */
object CauldronFillExecutor : SkillExecutor {

    private val lastFillTime = mutableMapOf<UUID, Long>()
    private val cauldronTarget = mutableMapOf<UUID, BlockPos>()

    override fun tick(
        world: World,
        origin: BlockPos,
        pokemonEntity: PokemonEntity,
        skill: SkillDef,
        skillEntry: SkillEntry
    ) {
        val pokemonId = pokemonEntity.pokemon.uuid
        val now = world.time
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(skill.cooldownSeconds, skillEntry.proficiency)

        // Cooldown check
        val lastTime = lastFillTime[pokemonId] ?: 0L
        if (now - lastTime < cooldownTicks) return

        // Find or navigate to cauldron
        val target = cauldronTarget[pokemonId] ?: findEmptyCauldron(world, origin, skill.searchRadius)
        if (target == null) return

        cauldronTarget[pokemonId] = target

        // Verify the cauldron is still empty
        if (!isCauldronEmpty(world, target)) {
            cauldronTarget.remove(pokemonId)
            return
        }

        navigateTo(pokemonEntity, target)
        if (isNearPosition(pokemonEntity, target)) {
            fillCauldron(world, target, skill)
            lastFillTime[pokemonId] = now
            cauldronTarget.remove(pokemonId)
            SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
        }
    }

    private fun findEmptyCauldron(world: World, origin: BlockPos, radius: Int): BlockPos? {
        var best: BlockPos? = null
        var bestDist = Double.MAX_VALUE

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = origin.add(x, y, z)
                    if (isCauldronEmpty(world, pos)) {
                        val dist = pos.getSquaredDistance(origin)
                        if (dist < bestDist) {
                            bestDist = dist
                            best = pos.toImmutable()
                        }
                    }
                }
            }
        }
        return best
    }

    private fun isCauldronEmpty(world: World, pos: BlockPos): Boolean {
        return world.getBlockState(pos).isOf(Blocks.CAULDRON)
    }

    private fun fillCauldron(world: World, pos: BlockPos, skill: SkillDef) {
        // Determine fluid type based on the skill ID
        val skillId = skill.id
        when {
            skillId.contains("lava") -> {
                world.setBlockState(pos, Blocks.LAVA_CAULDRON.defaultState, 3)
            }
            skillId.contains("water") -> {
                world.setBlockState(
                    pos,
                    Blocks.WATER_CAULDRON.defaultState.with(LeveledCauldronBlock.LEVEL, 3),
                    3
                )
            }
            skillId.contains("snow") -> {
                world.setBlockState(
                    pos,
                    Blocks.POWDER_SNOW_CAULDRON.defaultState.with(LeveledCauldronBlock.LEVEL, 3),
                    3
                )
            }
        }
    }


    private fun navigateTo(pokemonEntity: PokemonEntity, target: BlockPos) {
        pokemonEntity.navigation.startMovingTo(target.x + 0.5, target.y.toDouble(), target.z + 0.5, 1.0)
    }

    private fun isNearPosition(pokemonEntity: PokemonEntity, pos: BlockPos): Boolean {
        return pokemonEntity.blockPos.getSquaredDistance(pos) <= 4.0
    }
}

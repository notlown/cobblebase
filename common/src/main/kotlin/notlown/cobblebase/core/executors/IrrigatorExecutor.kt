package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Blocks
import net.minecraft.block.FarmlandBlock
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.NavigationHelper
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import notlown.cobblebase.core.effects.SkillEffects
import java.util.UUID

/**
 * Irrigator: walks over dry farmland and hydrates it.
 * Plays watergun animation when irrigating.
 * Hydrated farmland makes crops grow faster.
 */
object IrrigatorExecutor : SkillExecutor {

    private val lastIrrigateTime = mutableMapOf<UUID, Long>()
    private val targetBlock = mutableMapOf<UUID, BlockPos>()
    private val targetSetTime = mutableMapOf<UUID, Long>()
    private val NAV_TIMEOUT_TICKS = 100L

    override fun tick(
        world: World,
        origin: BlockPos,
        pokemonEntity: PokemonEntity,
        skill: SkillDef,
        skillEntry: SkillEntry
    ) {
        if (world !is ServerWorld) return
        val pokemonId = pokemonEntity.pokemon.uuid
        val now = world.time

        if (!CobblebaseConfig.irrigatorEnabled) return

        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(CobblebaseConfig.irrigatorCooldownSeconds, skillEntry.proficiency)
        val lastTime = lastIrrigateTime[pokemonId] ?: 0L
        if (now - lastTime < cooldownTicks) return

        // Find or navigate to dry farmland
        val target = targetBlock[pokemonId]
        if (target != null) {
            NavigationHelper.navigateTo(pokemonEntity, target)
            val navStarted = targetSetTime[pokemonId] ?: now
            val timedOut = now - navStarted >= NAV_TIMEOUT_TICKS

            if (NavigationHelper.isPokemonAtPosition(pokemonEntity, target, 2.0) || timedOut) {
                irrigate(world, target, pokemonEntity, skill)
                targetBlock.remove(pokemonId)
                targetSetTime.remove(pokemonId)
                lastIrrigateTime[pokemonId] = now
            }
        } else {
            val found = findDryFarmland(world, origin, skill.searchRadius)
            if (found != null) {
                targetBlock[pokemonId] = found
                targetSetTime[pokemonId] = now
            }
        }
    }

    private fun findDryFarmland(world: World, origin: BlockPos, radius: Int): BlockPos? {
        var best: BlockPos? = null
        var bestDist = Double.MAX_VALUE

        for (x in -radius..radius) {
            for (y in -3..3) {
                for (z in -radius..radius) {
                    val pos = origin.add(x, y, z)
                    val state = world.getBlockState(pos)
                    if (state.block is FarmlandBlock) {
                        val moisture = state.get(FarmlandBlock.MOISTURE)
                        if (moisture < FarmlandBlock.MAX_MOISTURE) {
                            val dist = pos.getSquaredDistance(origin)
                            if (dist < bestDist) {
                                bestDist = dist
                                best = pos.toImmutable()
                            }
                        }
                    }
                }
            }
        }
        return best
    }

    private fun irrigate(world: ServerWorld, pos: BlockPos, pokemonEntity: PokemonEntity, skill: SkillDef) {
        val state = world.getBlockState(pos)
        if (state.block !is FarmlandBlock) return

        // Set moisture to max
        world.setBlockState(pos, state.with(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE), 3)

        // Also irrigate nearby farmland (configurable area)
        val r = CobblebaseConfig.irrigatorRadius
        for (dx in -r..r) {
            for (dz in -r..r) {
                val nearby = pos.add(dx, 0, dz)
                val nearbyState = world.getBlockState(nearby)
                if (nearbyState.block is FarmlandBlock) {
                    val moisture = nearbyState.get(FarmlandBlock.MOISTURE)
                    if (moisture < FarmlandBlock.MAX_MOISTURE) {
                        world.setBlockState(nearby, nearbyState.with(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE), 3)
                    }
                }
            }
        }

        // Watergun animation + splash particles
        SkillEffects.sendAnimationPublic(world, pokemonEntity, "watergun", "bubble", "spray", "special")

        val x = pos.x + 0.5
        val y = pos.y + 1.0
        val z = pos.z + 0.5
        world.spawnParticles(ParticleTypes.SPLASH, x, y, z, 20, 0.5, 0.2, 0.5, 0.1)
        world.spawnParticles(ParticleTypes.DRIPPING_WATER, x, y + 0.5, z, 8, 0.4, 0.3, 0.4, 0.0)
    }
}

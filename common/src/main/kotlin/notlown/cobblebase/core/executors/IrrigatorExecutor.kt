package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.block.ApricornBlock
import com.cobblemon.mod.common.block.BerryBlock
import com.cobblemon.mod.common.block.MintBlock
import com.cobblemon.mod.common.block.MedicinalLeekBlock
import com.cobblemon.mod.common.block.RevivalHerbBlock
import com.cobblemon.mod.common.block.HeartyGrainsBlock
import com.cobblemon.mod.common.block.NutBushBlock
import com.cobblemon.mod.common.block.BugwortBlock
import com.cobblemon.mod.common.block.VivichokeBlock
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.CropBlock
import net.minecraft.block.FarmlandBlock
import net.minecraft.block.SweetBerryBushBlock
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
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
 * Irrigator: walks to growing plants and boosts their growth (like bone meal lite).
 * Works on ALL growable blocks: apricorns, berries, nuts, mints, crops, etc.
 * Also hydrates dry farmland. Searches from Pokemon position.
 * Proficiency determines speed and growth ticks per visit.
 */
object IrrigatorExecutor : SkillExecutor {

    private val APRICORNS_TAG = TagKey.of(RegistryKeys.BLOCK, Identifier.of("cobblemon", "apricorns"))

    private val targetBlock = mutableMapOf<UUID, BlockPos>()
    private val targetSetTime = mutableMapOf<UUID, Long>()
    private val lastSearchTime = mutableMapOf<UUID, Long>()
    private val NAV_TIMEOUT_TICKS = 100L
    private val SEARCH_INTERVAL_TICKS = 40L

    private fun getSpeedForProficiency(proficiency: Int): Double {
        return 0.15 + (proficiency * 0.1)
    }

    override fun tick(
        world: World,
        origin: BlockPos,
        pokemonEntity: PokemonEntity,
        skill: SkillDef,
        skillEntry: SkillEntry
    ) {
        if (world !is ServerWorld) return
        if (!CobblebaseConfig.irrigatorEnabled) return

        val pokemonId = pokemonEntity.pokemon.uuid
        val now = world.time
        val speed = getSpeedForProficiency(skillEntry.proficiency)
        // Search from Pokemon position, not pasture origin
        val searchOrigin = pokemonEntity.blockPos

        // Navigate to target or boost
        val target = targetBlock[pokemonId]
        if (target != null) {
            NavigationHelper.navigateTo(pokemonEntity, target, speed)
            val navStarted = targetSetTime[pokemonId] ?: now
            val timedOut = now - navStarted >= NAV_TIMEOUT_TICKS
            val atPos = NavigationHelper.isPokemonAtPosition(pokemonEntity, target, 2.0)

            if (atPos || timedOut) {
                boostGrowth(world, target, pokemonEntity, skill, skillEntry.proficiency)
                targetBlock.remove(pokemonId)
                targetSetTime.remove(pokemonId)
            }
            return
        }

        // Throttled search
        val lastSearch = lastSearchTime[pokemonId] ?: 0L
        if (now - lastSearch < SEARCH_INTERVAL_TICKS) return
        lastSearchTime[pokemonId] = now

        val found = findGrowingBlock(world, searchOrigin, skill.searchRadius)
        if (found != null) {
            targetBlock[pokemonId] = found
            targetSetTime[pokemonId] = now
        } else {
            // Nothing growing - wander randomly in radius
            val wanderPos = origin.add(
                world.random.nextInt(skill.searchRadius * 2) - skill.searchRadius,
                0,
                world.random.nextInt(skill.searchRadius * 2) - skill.searchRadius
            )
            NavigationHelper.navigateTo(pokemonEntity, wanderPos, speed * 0.4)
        }
    }

    /**
     * Finds any block that is growing but not yet mature.
     * Includes: apricorns, berries, crops, nuts, mints, herbs, etc.
     */
    private fun findGrowingBlock(world: World, origin: BlockPos, radius: Int): BlockPos? {
        val candidates = mutableListOf<BlockPos>()

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = origin.add(x, y, z)
                    val state = world.getBlockState(pos)
                    if (isGrowing(world, pos, state)) {
                        candidates.add(pos.toImmutable())
                    }
                }
            }
        }
        return candidates.randomOrNull()
    }

    /**
     * Checks if a block is a growable type that hasn't reached max age yet.
     */
    private fun isGrowing(world: World, pos: BlockPos, state: BlockState): Boolean {
        val block = state.block

        // Apricorns
        if (state.isIn(APRICORNS_TAG)) {
            return try { state.get(ApricornBlock.AGE) < ApricornBlock.MAX_AGE } catch (_: Exception) { false }
        }

        // Cobblemon berries
        if (block is BerryBlock) {
            return try { state.get(BerryBlock.AGE) < BerryBlock.FRUIT_AGE } catch (_: Exception) { false }
        }

        // Vanilla crops
        if (block is CropBlock) return !block.isMature(state)

        // Sweet berry bush
        if (block is SweetBerryBushBlock) {
            return try { state.get(SweetBerryBushBlock.AGE) < 3 } catch (_: Exception) { false }
        }

        // Cobblemon mints
        if (block is MintBlock) {
            return try { state.get(MintBlock.AGE) < MintBlock.MATURE_AGE } catch (_: Exception) { false }
        }

        // Cobblemon medicinal leek
        if (block is MedicinalLeekBlock) {
            return try { state.get(MedicinalLeekBlock.AGE) < MedicinalLeekBlock.MATURE_AGE } catch (_: Exception) { false }
        }

        // Cobblemon revival herb
        if (block is RevivalHerbBlock) {
            return try { state.get(RevivalHerbBlock.AGE) < RevivalHerbBlock.MAX_AGE } catch (_: Exception) { false }
        }

        // Cobblemon hearty grains
        if (block is HeartyGrainsBlock) {
            return try { state.get(HeartyGrainsBlock.AGE) < HeartyGrainsBlock.MATURE_AGE } catch (_: Exception) { false }
        }

        // Cobblemon nut bush (Galarica Nuts etc.)
        if (block is NutBushBlock) {
            return try { state.get(NutBushBlock.AGE) < NutBushBlock.MAX_AGE } catch (_: Exception) { false }
        }

        // Cobblemon bugwort
        if (block is BugwortBlock) {
            return try { state.get(BugwortBlock.AGE) < BugwortBlock.MATURE_AGE } catch (_: Exception) { false }
        }

        // Cobblemon vivichoke
        if (block is VivichokeBlock) {
            return try { state.get(net.minecraft.state.property.Properties.AGE_4) < 4 } catch (_: Exception) { false }
        }

        // Dry farmland (hydrate it)
        if (block is FarmlandBlock) {
            return try { state.get(FarmlandBlock.MOISTURE) < FarmlandBlock.MAX_MOISTURE } catch (_: Exception) { false }
        }

        return false
    }

    private fun boostGrowth(world: ServerWorld, pos: BlockPos, pokemonEntity: PokemonEntity, skill: SkillDef, proficiency: Int) {
        val state = world.getBlockState(pos)
        val block = state.block

        // If it's dry farmland, hydrate it + nearby
        if (block is FarmlandBlock) {
            val moisture = state.get(FarmlandBlock.MOISTURE)
            if (moisture < FarmlandBlock.MAX_MOISTURE) {
                world.setBlockState(pos, state.with(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE), 3)
            }
            val r = CobblebaseConfig.irrigatorRadius
            for (dx in -r..r) {
                for (dz in -r..r) {
                    val nearby = pos.add(dx, 0, dz)
                    val nearbyState = world.getBlockState(nearby)
                    if (nearbyState.block is FarmlandBlock) {
                        val m = nearbyState.get(FarmlandBlock.MOISTURE)
                        if (m < FarmlandBlock.MAX_MOISTURE) {
                            world.setBlockState(nearby, nearbyState.with(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE), 3)
                        }
                    }
                }
            }
        }

        // Apply growth boost - single randomTick with a chance based on proficiency
        // Prof 1 = 10% chance, Prof 2 = 20%, Prof 3 = 30%, Prof 4 = 40%, Prof 5 = 50%
        // This is much gentler than the old system (which applied 1-5 ticks every visit)
        val boostChance = proficiency * 10
        if (world.random.nextInt(100) < boostChance) {
            try {
                state.randomTick(world, pos, world.random)
            } catch (e: Exception) {
                Cobblebase.LOGGER.warn("[Irrigator] randomTick failed at $pos: ${e.message}")
            }
        }

        // Watergun animation + cry + splash particles
        SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)

        // Big visible water particles at the plant position
        val x = pos.x + 0.5
        val y = pos.y + 1.5
        val z = pos.z + 0.5
        world.spawnParticles(ParticleTypes.SPLASH, x, y + 1.0, z, 40, 0.8, 1.0, 0.8, 0.2)
        world.spawnParticles(ParticleTypes.DRIPPING_WATER, x, y + 2.0, z, 20, 0.6, 1.5, 0.6, 0.0)
        world.spawnParticles(ParticleTypes.FALLING_WATER, x, y + 2.5, z, 15, 0.5, 1.0, 0.5, 0.0)
    }
}

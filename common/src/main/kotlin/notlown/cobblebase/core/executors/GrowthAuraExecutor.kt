package notlown.cobblebase.core.executors

import notlown.cobblebase.core.effectiveRadius

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.CropBlock
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import java.util.UUID

/**
 * Passively accelerates crop growth near the pasture.
 * Scans for CropBlock instances within range and randomly ticks them
 * to accelerate growth (like bone meal but passive and area-based).
 *
 * Range and number of crops ticked per cycle scale with proficiency.
 * Green sparkle particles (HAPPY_VILLAGER) appear on affected crops.
 */
object GrowthAuraExecutor : SkillExecutor {

    private val lastTickTime = mutableMapOf<UUID, Long>()
    /** Per-Pokemon heartbeat for [cleanupStale]; updated every tick so the 30s pulse
     *  interval can never be wiped mid-flight by the 60s cleanup sweep. */
    private val lastScanTime = mutableMapOf<UUID, Long>()
    private const val STALE_TTL_TICKS = 1200L

    fun cleanupStale(now: Long) {
        val stale = lastScanTime.entries.filter { now - it.value > STALE_TTL_TICKS }.map { it.key }
        for (id in stale) {
            lastScanTime.remove(id)
            lastTickTime.remove(id)
        }
    }

    // How often the aura pulses (in ticks). Every 30 seconds.
    private const val PULSE_INTERVAL = 600L

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
        lastScanTime[pokemonId] = now
        val prof = skillEntry.proficiency.coerceIn(1, 5)

        // Pulse interval check
        val lastTime = lastTickTime[pokemonId] ?: 0L
        if (now - lastTime < PULSE_INTERVAL) return

        val radius = getRadius(prof, skill.effectiveRadius)
        // Per-job tuning: admin can scale the number of crops ticked per pulse.
        val growthMult = notlown.cobblebase.core.SkillRegistry
            .getEffectiveTuning(skill.id, "growthMultiplier", 1.0)
        val maxCropsPerPulse = (getCropsPerPulse(prof) * growthMult).toInt().coerceAtLeast(1)

        // Scan for crop blocks within range
        val candidates = mutableListOf<BlockPos>()
        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = origin.add(x, y, z)
                    val state = world.getBlockState(pos)
                    if (state.block is CropBlock) {
                        val crop = state.block as CropBlock
                        // Only tick crops that aren't fully grown
                        if (!crop.isMature(state)) {
                            candidates.add(pos.toImmutable())
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            lastTickTime[pokemonId] = now
            return
        }

        // Pick random crops to tick
        val toTick = candidates.shuffled().take(maxCropsPerPulse)
        var tickedCount = 0

        for (pos in toTick) {
            val state = world.getBlockState(pos)
            try {
                state.randomTick(world, pos, world.random)
                tickedCount++
            } catch (e: Exception) {
                Cobblebase.LOGGER.warn("[GrowthAura] randomTick failed at $pos: ${e.message}")
            }
        }

        lastTickTime[pokemonId] = now
    }

    /**
     * Range scales with proficiency. Base radius from skill JSON is the Prof 1 value.
     * Prof 1: base, Prof 2: base*1.5, Prof 3: base*2, Prof 4: base*3, Prof 5: base*5
     */
    private fun getRadius(prof: Int, baseRadius: Int): Int {
        val multiplier = when (prof) {
            1 -> 1.0
            2 -> 1.5
            3 -> 2.0
            4 -> 3.0
            5 -> 5.0
            else -> 1.0
        }
        return (baseRadius * multiplier).toInt()
    }

    /**
     * Number of crops ticked per pulse, scaling with proficiency.
     * Prof 1: 2, Prof 2: 4, Prof 3: 6, Prof 4: 10, Prof 5: 16
     */
    private fun getCropsPerPulse(prof: Int): Int {
        return when (prof) {
            1 -> 1
            2 -> 1
            3 -> 2
            4 -> 2
            5 -> 3
            else -> 1
        }
    }
}

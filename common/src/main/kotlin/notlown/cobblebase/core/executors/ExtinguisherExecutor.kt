package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Blocks
import net.minecraft.block.CampfireBlock
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.LogManager
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import notlown.cobblebase.core.effects.SkillEffects
import java.util.UUID

/**
 * Scans for fire blocks and lit campfires within range of the pasture origin
 * and extinguishes them. Plays steam particles where fire was removed.
 * Range scales with proficiency.
 */
object ExtinguisherExecutor : SkillExecutor {

    private val lastScanTime = mutableMapOf<UUID, Long>()
    private const val STALE_TTL_TICKS = 1200L

    fun cleanupStale(now: Long) {
        val stale = lastScanTime.entries.filter { now - it.value > STALE_TTL_TICKS }.map { it.key }
        for (id in stale) lastScanTime.remove(id)
    }

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
        val prof = skillEntry.proficiency.coerceIn(1, 5)

        // Cooldown check
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(skill.cooldownSeconds, prof, skill.id)
        val lastTime = lastScanTime[pokemonId] ?: 0L
        if (now - lastTime < cooldownTicks) {
            if (now % 60 == 0L) SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
            return
        }

        // Range scales with proficiency
        val radius = getRadius(prof, skill.searchRadius)
        var extinguishedCount = 0

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = origin.add(x, y, z)
                    val state = world.getBlockState(pos)
                    val block = state.block

                    // Remove fire blocks
                    if (block == Blocks.FIRE || block == Blocks.SOUL_FIRE) {
                        world.removeBlock(pos, false)
                        extinguishedCount++
                    }

                    // Extinguish lit campfires
                    if ((block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE)
                        && state.contains(CampfireBlock.LIT)
                        && state.get(CampfireBlock.LIT)
                    ) {
                        world.setBlockState(pos, state.with(CampfireBlock.LIT, false))
                        extinguishedCount++
                    }
                }
            }
        }

        lastScanTime[pokemonId] = now

        if (extinguishedCount > 0) {
            SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
            LogManager.log(
                origin, now,
                pokemonEntity.pokemon.species.name,
                "Extinguisher",
                "Extinguished $extinguishedCount fire(s)",
                LogManager.Rarity.COMMON
            )
            Cobblebase.log("[Extinguisher] ${pokemonEntity.pokemon.species.name} extinguished $extinguishedCount fire(s)")
        }
    }

    /**
     * Range scales with proficiency. Base radius from skill JSON is the Prof 1 value.
     * Prof 1: base, Prof 2: base*1.5, Prof 3: base*2, Prof 4: base*3, Prof 5: base*4
     */
    private fun getRadius(prof: Int, baseRadius: Int): Int {
        val multiplier = when (prof) {
            1 -> 1.0
            2 -> 1.5
            3 -> 2.0
            4 -> 3.0
            5 -> 4.0
            else -> 1.0
        }
        return (baseRadius * multiplier).toInt()
    }
}

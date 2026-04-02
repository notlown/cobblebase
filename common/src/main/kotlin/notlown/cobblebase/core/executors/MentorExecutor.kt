package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor

/**
 * Mentor: passive XP boost for all Pokemon in the same Pasture Block.
 *
 * This executor does NOT actively produce items or heal. Instead it registers
 * its proficiency so that PassiveXp can apply an XP multiplier.
 *
 * Proficiency determines the boost (scaled by mentorMaxBoost config):
 *   Prof 1 = +20%, Prof 2 = +40%, Prof 3 = +60%, Prof 4 = +80%, Prof 5 = +100%
 *
 * Multiple mentors do NOT stack -- only the highest proficiency is used.
 * Visual: occasional enchant/xp orb particles around the mentor Pokemon.
 */
object MentorExecutor : SkillExecutor {

    /**
     * Map of pasture BlockPos -> highest mentor proficiency active this tick window.
     * Refreshed each tick by every active mentor; stale entries expire after 5 ticks.
     */
    private data class MentorEntry(val proficiency: Int, val lastTick: Long)

    private val activeMentors = mutableMapOf<BlockPos, MentorEntry>()
    private const val EXPIRY_TICKS = 5L

    override fun tick(
        world: World,
        origin: BlockPos,
        pokemonEntity: PokemonEntity,
        skill: SkillDef,
        skillEntry: SkillEntry
    ) {
        if (!CobblebaseConfig.mentorEnabled) return

        val now = world.time
        val proficiency = skillEntry.proficiency.coerceIn(1, 5)

        // Register or update this mentor's proficiency for the pasture
        val existing = activeMentors[origin]
        if (existing == null || proficiency > existing.proficiency || now - existing.lastTick > EXPIRY_TICKS) {
            activeMentors[origin] = MentorEntry(proficiency, now)
        } else if (proficiency == existing.proficiency) {
            // Same proficiency, refresh timestamp
            activeMentors[origin] = existing.copy(lastTick = now)
        }

        // Visual: enchant + xp orb particles every 60 ticks (~3 seconds)
        if (now % 60 == 0L && world is ServerWorld) {
            val x = pokemonEntity.x
            val y = pokemonEntity.y
            val h = pokemonEntity.height.toDouble()
            val z = pokemonEntity.z
            world.spawnParticles(
                net.minecraft.particle.ParticleTypes.ENCHANT,
                x, y + h + 0.5, z, 8, 0.4, 0.3, 0.4, 0.5
            )
            world.spawnParticles(
                net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                x, y + h, z, 4, 0.3, 0.2, 0.3, 0.02
            )
        }

        if (now % 200 == 0L) {
            Cobblebase.LOGGER.info(
                "[Mentor] ${pokemonEntity.pokemon.species.name} active at $origin (prof=$proficiency, boost=${getXpMultiplier(origin)}x)"
            )
        }
    }

    /**
     * Returns the XP multiplier for a given pasture position.
     * 1.0 = no boost, 2.0 = double XP (at prof 5 with default config).
     * Called by PassiveXp to scale XP gains.
     */
    fun getXpMultiplier(pasturePos: BlockPos): Double {
        val entry = activeMentors[pasturePos] ?: return 1.0
        // Expire stale entries (mentor may have been unassigned or despawned)
        // We can't check world.time here, so we rely on tick() refreshing within EXPIRY_TICKS
        val proficiency = entry.proficiency.coerceIn(1, 5)
        val maxBoost = CobblebaseConfig.mentorMaxBoost
        // Prof 1 = 20% of maxBoost, Prof 5 = 100% of maxBoost
        val boost = maxBoost * proficiency / 5.0
        return 1.0 + boost
    }

    /**
     * Clean up stale entries. Called periodically to prevent memory leaks.
     */
    fun cleanupStale(currentTick: Long) {
        activeMentors.entries.removeIf { (_, entry) -> currentTick - entry.lastTick > 100L }
    }
}

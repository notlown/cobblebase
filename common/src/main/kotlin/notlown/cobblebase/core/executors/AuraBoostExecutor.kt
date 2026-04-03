package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import java.util.UUID

/**
 * Gives all nearby players the Luck status effect, increasing loot quality.
 * Functions like BuffExecutor but with proficiency-scaling amplifier:
 * - Prof 1-2: Luck I (amplifier 0)
 * - Prof 3-4: Luck II (amplifier 1)
 * - Prof 5:   Luck III (amplifier 2)
 *
 * Range and duration scale with proficiency like other buff executors.
 */
object AuraBoostExecutor : SkillExecutor {

    private val lastBuffTime = mutableMapOf<UUID, Long>()
    private val activeBuffPlayers = mutableMapOf<UUID, MutableSet<UUID>>()

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

        // No cooldown -- reapply constantly like other buffs
        val lastTime = lastBuffTime[pokemonId] ?: 0L
        if (now - lastTime < 0L) return

        // Find players based on proficiency range
        val players: List<ServerPlayerEntity>
        if (prof >= 5) {
            // Prof 5: GLOBAL -- owner only
            val ownerUuid = pokemonEntity.pokemon.getOwnerUUID()
            players = if (ownerUuid != null) {
                world.players.filter { it.uuid == ownerUuid }
            } else {
                val range = getBuffRange(prof)
                world.getEntitiesByClass(ServerPlayerEntity::class.java,
                    Box.of(origin.toCenterPos(), range * 2, range * 2, range * 2)) { true }
            }
        } else {
            val range = getBuffRange(prof)
            val searchBox = Box.of(origin.toCenterPos(), range * 2, range * 2, range * 2)
            players = world.getEntitiesByClass(ServerPlayerEntity::class.java, searchBox) { true }
        }
        if (players.isEmpty()) return

        // Amplifier scales with proficiency
        val amplifier = when {
            prof >= 5 -> 2  // Luck III
            prof >= 3 -> 1  // Luck II
            else -> 0       // Luck I
        }

        val durationTicks = getDurationTicks(prof)
        val trackedPlayers = activeBuffPlayers.getOrPut(pokemonId) { mutableSetOf() }
        var appliedCount = 0

        for (player in players) {
            val effectInstance = StatusEffectInstance(
                StatusEffects.LUCK,
                durationTicks,
                amplifier,
                true,   // ambient (subtle particles)
                false,  // showParticles — disabled to avoid annoying player particles
                true    // showIcon
            )
            player.addStatusEffect(effectInstance)
            appliedCount++
            trackedPlayers.add(player.uuid)
        }

        // Clean up tracked players who are no longer in range
        val playerUuids = players.map { it.uuid }.toSet()
        trackedPlayers.removeAll { it !in playerUuids }

        lastBuffTime[pokemonId] = now

        // Subtle particles at the pasture origin every 5 seconds
        if (appliedCount > 0 && now % 100 == 0L) {
            val x = origin.x + 0.5
            val y = origin.y + 1.0
            val z = origin.z + 0.5
            world.spawnParticles(ParticleTypes.ENCHANT, x, y, z, 3, 0.4, 0.3, 0.4, 0.1)
        }
    }

    /**
     * Duration in ticks based on proficiency.
     * Prof 1: 300 (15s), Prof 2: 500 (25s), Prof 3: 700 (35s),
     * Prof 4: 1000 (50s), Prof 5: 1400 (70s)
     */
    private fun getDurationTicks(prof: Int): Int {
        if (prof == 1) return 300
        if (prof == 2) return 500
        if (prof == 3) return 700
        if (prof == 4) return 1000
        return 1400 // prof 5
    }

    /**
     * Buff range in blocks based on proficiency.
     * Prof 1: 30, Prof 2: 50, Prof 3: 100, Prof 4: 200
     * Prof 5: global (handled separately -- owner only)
     */
    private fun getBuffRange(prof: Int): Double {
        if (prof == 1) return 30.0
        if (prof == 2) return 50.0
        if (prof == 3) return 100.0
        if (prof == 4) return 200.0
        return 200.0 // fallback for prof 5
    }
}

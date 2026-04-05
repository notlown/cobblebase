package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.effect.StatusEffect
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.entry.RegistryEntry
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
 * Applies a Minecraft status effect to all players within range of the pasture origin.
 * Each buff type is a separate instance with its own effect, amplifier, and tracking.
 *
 * Duration and cooldown scale with proficiency:
 * - Prof 5 with 0s cooldown = effectively permanent buff while near the base.
 */
class BuffExecutor(
    private val buffType: String,
    private val statusEffect: RegistryEntry<StatusEffect>,
    private val baseAmplifier: Int = 0  // 0 = level I, 1 = level II
) : SkillExecutor {

    private val lastBuffTime = mutableMapOf<UUID, Long>()
    // Track which players already received the buff this cycle (to log only on first apply)
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

        // Cooldown check
        val cooldownTicks = getCooldownTicks(prof)
        val lastTime = lastBuffTime[pokemonId] ?: 0L
        if (now - lastTime < cooldownTicks) return

        // Find players based on proficiency range
        // Within 40 blocks: ALL players get the buff
        // Beyond 40 blocks: OWNER only
        val ownerUuid = pokemonEntity.pokemon.getOwnerUUID()
        val players: List<ServerPlayerEntity>

        if (prof >= 5) {
            // Prof 5: GLOBAL — owner only (anywhere in world)
            players = if (ownerUuid != null) {
                world.players.filter { it.uuid == ownerUuid }
            } else {
                // Fallback: nearby players if no owner
                val range = 40.0
                world.getEntitiesByClass(ServerPlayerEntity::class.java,
                    Box.of(origin.toCenterPos(), range * 2, range * 2, range * 2)) { true }
            }
        } else {
            // Prof 1-4: all players within 40 blocks + owner within full range
            val baseRange = 40.0
            val fullRange = getBuffRange(prof)
            val baseBox = Box.of(origin.toCenterPos(), baseRange * 2, baseRange * 2, baseRange * 2)
            val nearbyPlayers = world.getEntitiesByClass(ServerPlayerEntity::class.java, baseBox) { true }

            if (fullRange > baseRange && ownerUuid != null) {
                // Owner gets buff within full range even beyond 40 blocks
                val fullBox = Box.of(origin.toCenterPos(), fullRange * 2, fullRange * 2, fullRange * 2)
                val ownerInRange = world.getEntitiesByClass(ServerPlayerEntity::class.java, fullBox) { it.uuid == ownerUuid }
                val combined = nearbyPlayers.toMutableList()
                for (owner in ownerInRange) {
                    if (combined.none { it.uuid == owner.uuid }) combined.add(owner)
                }
                players = combined
            } else {
                players = nearbyPlayers
            }
        }
        if (players.isEmpty()) return

        // Apply buff to all players in range
        val durationTicks = getDurationTicks(prof)
        val trackedPlayers = activeBuffPlayers.getOrPut(pokemonId) { mutableSetOf() }
        var appliedCount = 0

        for (player in players) {
            val effectInstance = StatusEffectInstance(
                statusEffect,
                durationTicks,
                baseAmplifier,
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

        // Very subtle particles at the pasture origin — only every 5 seconds
        if (appliedCount > 0 && now % 100 == 0L) {
            playBuffParticles(world, origin)
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
     * No cooldown for any proficiency — buffs reapply constantly.
     * Proficiency only affects range.
     */
    private fun getCooldownTicks(prof: Int): Long {
        return 0L
    }

    /**
     * Buff range in blocks based on proficiency.
     * Prof 1: 30 (small base), Prof 2: 50, Prof 3: 100, Prof 4: 200
     * Prof 5: global (handled separately — owner only)
     */
    private fun getBuffRange(prof: Int): Double {
        if (prof == 1) return 30.0
        if (prof == 2) return 50.0
        if (prof == 3) return 100.0
        if (prof == 4) return 200.0
        return 200.0 // fallback for prof 5 (shouldn't be used — global is handled above)
    }

    private fun getBuffDisplayName(): String {
        if (buffType == "speed_boost") return "Speed III"
        if (buffType == "strength_boost") return "Strength I"
        if (buffType == "resistance_boost") return "Resistance I"
        if (buffType == "night_vision") return "Night Vision"
        if (buffType == "water_breathing") return "Water Breathing"
        if (buffType == "jump_boost") return "Jump Boost I"
        if (buffType == "haste_boost") return "Haste I"
        if (buffType == "saturation_boost") return "Saturation"
        return buffType
    }

    /**
     * Plays subtle particles at the pasture origin when a buff is applied.
     * Different particle type per buff for visual variety.
     */
    private fun playBuffParticles(world: ServerWorld, origin: BlockPos) {
        val x = origin.x + 0.5
        val y = origin.y + 1.0
        val z = origin.z + 0.5

        // Very minimal particles — just a hint that a buff is active
        if (buffType == "speed_boost") {
            world.spawnParticles(ParticleTypes.CLOUD, x, y, z, 1, 0.3, 0.2, 0.3, 0.005)
        } else if (buffType == "strength_boost") {
            world.spawnParticles(ParticleTypes.CRIT, x, y, z, 2, 0.3, 0.2, 0.3, 0.01)
        } else if (buffType == "resistance_boost") {
            world.spawnParticles(ParticleTypes.ENCHANT, x, y, z, 2, 0.3, 0.3, 0.3, 0.1)
        } else if (buffType == "night_vision") {
            world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.3, 0.2, 0.3, 0.005)
        } else if (buffType == "water_breathing") {
            world.spawnParticles(ParticleTypes.BUBBLE_POP, x, y, z, 2, 0.3, 0.2, 0.3, 0.01)
        } else if (buffType == "jump_boost") {
            world.spawnParticles(ParticleTypes.FIREWORK, x, y, z, 1, 0.3, 0.2, 0.3, 0.005)
        } else if (buffType == "haste_boost") {
            world.spawnParticles(ParticleTypes.INSTANT_EFFECT, x, y, z, 1, 0.3, 0.2, 0.3, 0.05)
        } else if (buffType == "saturation_boost") {
            world.spawnParticles(ParticleTypes.HEART, x, y, z, 1, 0.3, 0.2, 0.3, 0.005)
        } else {
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 1, 0.3, 0.2, 0.3, 0.005)
        }
    }

    /**
     * Tick buff without a PokemonEntity (owner offline, entity despawned).
     * Uses pasture block position for range. Applies the same buff logic.
     */
    fun tickWithoutEntity(
        world: World,
        origin: BlockPos,
        pokemonId: UUID,
        ownerUuid: UUID?,
        skill: SkillDef,
        skillEntry: SkillEntry
    ) {
        if (world !is ServerWorld) return
        val now = world.time
        val prof = skillEntry.proficiency.coerceIn(1, 5)

        val cooldownTicks = getCooldownTicks(prof)
        val lastTime = lastBuffTime[pokemonId] ?: 0L
        if (now - lastTime < cooldownTicks) return

        // Find players near pasture block
        val players: List<ServerPlayerEntity> = if (prof >= 5 && ownerUuid != null) {
            world.players.filter { it.uuid == ownerUuid }
        } else {
            val range = getBuffRange(prof)
            world.getEntitiesByClass(ServerPlayerEntity::class.java,
                Box.of(origin.toCenterPos(), range * 2, range * 2, range * 2)) { true }
        }
        if (players.isEmpty()) return

        val durationTicks = getDurationTicks(prof)
        for (player in players) {
            player.addStatusEffect(StatusEffectInstance(
                statusEffect, durationTicks, baseAmplifier,
                true, false, true
            ))
        }

        lastBuffTime[pokemonId] = now
    }

    companion object {
        val SpeedBoost = BuffExecutor("speed_boost", StatusEffects.SPEED, 2)
        val StrengthBoost = BuffExecutor("strength_boost", StatusEffects.STRENGTH, 0)
        val ResistanceBoost = BuffExecutor("resistance_boost", StatusEffects.RESISTANCE, 0)
        val NightVision = BuffExecutor("night_vision", StatusEffects.NIGHT_VISION, 0)
        val WaterBreathing = BuffExecutor("water_breathing", StatusEffects.WATER_BREATHING, 0)
        val JumpBoost = BuffExecutor("jump_boost", StatusEffects.JUMP_BOOST, 0)
        val HasteBoost = BuffExecutor("haste_boost", StatusEffects.HASTE, 0)
        val SaturationBoost = BuffExecutor("saturation_boost", StatusEffects.SATURATION, 0)
    }
}

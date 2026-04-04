package notlown.cobblebase.core

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import notlown.cobblebase.core.effects.SkillEffects
import java.util.UUID

/**
 * Ambient behavior system for pasture Pokemon.
 * Makes Pokemon feel alive with idle animations, sleep cycles, and social interactions.
 *
 * Behaviors only trigger when the Pokemon has no active job (Idle mode)
 * or is waiting on cooldown between job cycles.
 */
object AmbientBehavior {

    // State tracking
    private val currentState = mutableMapOf<UUID, BehaviorState>()
    private val stateStartTime = mutableMapOf<UUID, Long>()
    private val lastInteractionPartner = mutableMapOf<UUID, UUID>()

    // Timing constants
    private const val IDLE_STAND_MIN = 100L   // 5 seconds minimum standing still
    private const val IDLE_STAND_MAX = 400L   // 20 seconds max before next behavior
    private const val SLEEP_CHECK_INTERVAL = 200L
    private const val SOCIAL_RANGE = 6.0      // blocks — how close mons need to be to interact
    private const val SOCIAL_CHANCE = 15       // percent chance per check to start social interaction
    private const val SIT_CHANCE = 20          // percent chance to sit instead of wander
    private const val LOOK_AROUND_CHANCE = 30  // percent chance to play look-around animation

    enum class BehaviorState {
        WANDERING,      // normal wandering (existing behavior)
        STANDING,       // standing still, looking around
        SLEEPING,       // lying down at night
        SOCIALIZING,    // interacting with nearby mon
        SITTING         // sitting/resting in place
    }

    /**
     * Called from the mixin tick loop when a Pokemon's navigation is idle.
     * Returns true if ambient behavior took over (don't wander), false to allow normal wandering.
     */
    fun tickIdle(world: World, pokemonEntity: PokemonEntity, origin: BlockPos): Boolean {
        if (world !is ServerWorld) return false
        val id = pokemonEntity.pokemon.uuid
        val now = world.time

        val state = currentState[id] ?: BehaviorState.WANDERING
        val stateStart = stateStartTime[id] ?: now

        return when (state) {
            BehaviorState.SLEEPING -> tickSleeping(world, pokemonEntity, id, now, origin)
            BehaviorState.STANDING -> tickStanding(world, pokemonEntity, id, now, stateStart, origin)
            BehaviorState.SITTING -> tickSitting(world, pokemonEntity, id, now, stateStart, origin)
            BehaviorState.SOCIALIZING -> tickSocializing(world, pokemonEntity, id, now, stateStart)
            BehaviorState.WANDERING -> pickNextBehavior(world, pokemonEntity, id, now, origin)
        }
    }

    /**
     * Night time = sleep. Pokemon play sleep animation and stay still.
     */
    private fun tickSleeping(world: ServerWorld, entity: PokemonEntity, id: UUID, now: Long, origin: BlockPos): Boolean {
        // Wake up at dawn (time 0-1000 = morning)
        val dayTime = world.timeOfDay % 24000
        if (dayTime in 0..12500) {
            // Morning — wake up
            setState(id, BehaviorState.WANDERING, now)
            // Play wake-up cry
            if (now % 200 == 0L) {
                SkillEffects.sendAnimationPublic(world, entity, "cry")
            }
            return false
        }

        // Stay sleeping — play sleep animation periodically to maintain pose
        if (now % 100 == 0L) {
            SkillEffects.sendAnimationPublic(world, entity, "sleep", "water_sleep", "battle_sleep")
        }

        // Don't move while sleeping
        NavigationHelper.clearTargets(entity)
        return true
    }

    /**
     * Standing still — looking around with occasional idle animations.
     */
    private fun tickStanding(world: ServerWorld, entity: PokemonEntity, id: UUID, now: Long, stateStart: Long, origin: BlockPos): Boolean {
        val elapsed = now - stateStart

        // Play look-around animation occasionally (compatible with CobbleMotion)
        if (now % 80 == 0L && world.random.nextInt(100) < LOOK_AROUND_CHANCE) {
            val lookAnims = arrayOf("happy", "pose", "blink", "look_quirk", "sniff_quirk", "cry")
            val pick = lookAnims[world.random.nextInt(lookAnims.size)]
            SkillEffects.sendAnimationPublic(world, entity, pick)
        }

        // After standing for a while, transition to next behavior
        val standDuration = IDLE_STAND_MIN + (world.random.nextInt((IDLE_STAND_MAX - IDLE_STAND_MIN).toInt())).toLong()
        if (elapsed >= standDuration) {
            setState(id, BehaviorState.WANDERING, now)
            return false
        }

        NavigationHelper.clearTargets(entity)
        return true
    }

    /**
     * Sitting/resting — Pokemon stays in place with a relaxed pose.
     */
    private fun tickSitting(world: ServerWorld, entity: PokemonEntity, id: UUID, now: Long, stateStart: Long, origin: BlockPos): Boolean {
        val elapsed = now - stateStart

        // Play sit/rest animation
        if (now % 120 == 0L) {
            SkillEffects.sendAnimationPublic(world, entity, "sleep", "pose", "ground_idle")
        }

        // Sit for 10-30 seconds then get up
        val sitDuration = 200L + world.random.nextInt(400).toLong()
        if (elapsed >= sitDuration) {
            setState(id, BehaviorState.WANDERING, now)
            return false
        }

        NavigationHelper.clearTargets(entity)
        return true
    }

    /**
     * Social interaction — two mons face each other, play cry animations.
     */
    private fun tickSocializing(world: ServerWorld, entity: PokemonEntity, id: UUID, now: Long, stateStart: Long): Boolean {
        val elapsed = now - stateStart

        // Social interaction lasts 3-5 seconds
        if (elapsed >= 100L) {
            setState(id, BehaviorState.WANDERING, now)
            return false
        }

        // Play cry/call animations alternating
        if (elapsed % 40 == 0L) {
            SkillEffects.sendAnimationPublic(world, entity, "cry")
        }

        // Face towards partner
        val partnerId = lastInteractionPartner[id]
        if (partnerId != null) {
            val searchBox = Box.of(entity.pos, 12.0, 6.0, 12.0)
            val partner = world.getEntitiesByClass(PokemonEntity::class.java, searchBox) { it.pokemon.uuid == partnerId }
                .firstOrNull()
            if (partner != null) {
                entity.lookAtEntity(partner, 30f, 30f)
            }
        }

        NavigationHelper.clearTargets(entity)
        return true
    }

    /**
     * Decides what to do next when in WANDERING state.
     * Returns false to allow normal wandering most of the time.
     */
    private fun pickNextBehavior(world: ServerWorld, entity: PokemonEntity, id: UUID, now: Long, origin: BlockPos): Boolean {
        // Only pick new behavior every few seconds
        val lastPick = stateStartTime[id] ?: 0L
        if (now - lastPick < 60L) return false // check every 3 seconds

        stateStartTime[id] = now

        // Night time — go to sleep
        val dayTime = world.timeOfDay % 24000
        if (dayTime in 12500..23999) {
            // Check periodically, not every tick
            if (now % SLEEP_CHECK_INTERVAL == 0L && world.random.nextInt(100) < 40) {
                setState(id, BehaviorState.SLEEPING, now)
                return true
            }
        }

        // Check for nearby mons to socialize with
        if (world.random.nextInt(100) < SOCIAL_CHANCE) {
            val searchBox = Box.of(entity.pos, SOCIAL_RANGE * 2, 4.0, SOCIAL_RANGE * 2)
            val nearbyMons = world.getEntitiesByClass(PokemonEntity::class.java, searchBox) { other ->
                other != entity && other.isAlive && !other.pokemon.isWild()
            }
            if (nearbyMons.isNotEmpty()) {
                val partner = nearbyMons[world.random.nextInt(nearbyMons.size)]
                lastInteractionPartner[id] = partner.pokemon.uuid
                lastInteractionPartner[partner.pokemon.uuid] = id
                setState(id, BehaviorState.SOCIALIZING, now)
                setState(partner.pokemon.uuid, BehaviorState.SOCIALIZING, now)
                return true
            }
        }

        // Random chance to sit down
        if (world.random.nextInt(100) < SIT_CHANCE) {
            setState(id, BehaviorState.SITTING, now)
            return true
        }

        // Random chance to just stand and look around
        if (world.random.nextInt(100) < LOOK_AROUND_CHANCE) {
            setState(id, BehaviorState.STANDING, now)
            return true
        }

        // Default: continue wandering (return false)
        return false
    }

    private fun setState(id: UUID, state: BehaviorState, now: Long) {
        currentState[id] = state
        stateStartTime[id] = now
    }

    /**
     * Clear state for a Pokemon (e.g., when it starts a job).
     */
    fun clearState(id: UUID) {
        currentState.remove(id)
        stateStartTime.remove(id)
        lastInteractionPartner.remove(id)
    }

    /**
     * Check if a Pokemon is currently sleeping (for GUI display).
     */
    fun isSleeping(id: UUID): Boolean {
        return currentState[id] == BehaviorState.SLEEPING
    }
}

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
    private const val SOCIAL_RANGE = 6.0      // blocks — how close mons need to be to interact
    private const val SOCIAL_CHANCE = 15       // percent chance per check to start social interaction
    private const val SIT_CHANCE = 20          // percent chance to sit instead of wander
    private const val LOOK_AROUND_CHANCE = 30  // percent chance to play look-around animation
    private const val SPECIAL_ANIM_CHANCE = 25 // percent chance to play a special animation (attack, emote)

    // CobbleMotion top-level animations (triggerable via PlayPosableAnimationPacket)
    // Only includes animations registered in the poser's "animations" section, NOT namedAnimations
    private val SPECIES_ANIMATIONS = mapOf(
        "grimmsnarl" to listOf("physical", "special", "status", "taunt"),
        "serperior" to listOf("physical", "special", "leafstorm"),
        "gengar" to listOf("physical", "special", "status"),
        "lucario" to listOf("physical", "special", "status"),
        "scizor" to listOf("status"),
        "incineroar" to listOf("darkestlariat"),
        "emboar" to listOf("heatcrash"),
        "meowscarada" to listOf("flowertrick"),
        "greninja" to listOf("physical", "special", "status"),
        "typhlosion" to listOf("eruption", "physical", "special", "status"),
        "kleavor" to listOf("physical", "special", "status"),
        "primarina" to listOf("status", "sparklingaria"),
        "decidueye" to listOf("physical", "special", "spiritshackle", "status"),
        "samurott" to listOf("physical", "special", "status", "aquajet"),
        "tsareena" to listOf("physical", "special", "status"),
        "quaquaval" to listOf("status", "aquastep"),
        "totodile" to listOf("physical", "status"),
        "cyndaquil" to listOf("physical", "special", "status"),
        "meganium" to listOf("physical", "special", "status"),
        "chikorita" to listOf("physical", "special"),
        "luxray" to listOf("physical", "special", "status"),
        "perrserker" to listOf("physical", "special", "status"),
        "magmortar" to listOf("physical", "special"),
        "electivire" to listOf("status"),
        "accelgor" to listOf("status"),
        "escavalier" to listOf("physical", "status"),
        "politoed" to listOf("physical", "special", "status"),
        "dewott" to listOf("status", "physical", "special"),
        "tyranitar" to listOf("physical", "special"),
        "sceptile" to listOf("physical", "special", "status"),
        "arboliva" to listOf("status"),
        "clodsire" to listOf("physical", "status"),
        "kommo-o" to listOf("status"),
        "combusken" to listOf("physical", "special", "status"),
        "steenee" to listOf("status"),
        "zoroark_hisuian" to listOf("physical", "special", "status"),
        "typhlosion_hisuian" to listOf("physical", "special", "infernalparade", "status"),
        "decidueye_hisuian" to listOf("physical", "special", "triplearrows", "status"),
        "samurott_hisuian" to listOf("physical", "special", "status", "aquajet"),
        "lilligant_hisuian" to listOf("physical", "special", "status")
    )

    enum class BehaviorState {
        WANDERING,      // normal wandering (existing behavior)
        STANDING,       // standing still, looking around
        SLEEPING,       // lying down at night
        SOCIALIZING,    // interacting with nearby mon
        SITTING         // sitting/resting in place
    }

    // Track last special animation time per Pokemon
    private val lastSpecialAnim = mutableMapOf<UUID, Long>()
    private const val SPECIAL_ANIM_INTERVAL = 300L // 15 seconds between special animations

    /**
     * Plays species-specific animations periodically (every ~15 seconds) for idle Pokemon.
     * Called every tick from the mixin, independent of behavior state.
     * Only runs for Pokemon with no active job (Idle mode).
     */
    fun tickSpecialAnimations(world: World, pokemonEntity: PokemonEntity) {
        if (world !is ServerWorld) return
        val id = pokemonEntity.pokemon.uuid
        val now = world.time

        // Don't play attack animations while sleeping
        val state = currentState[id]
        if (state == BehaviorState.SLEEPING) return

        val lastAnim = lastSpecialAnim[id] ?: 0L
        if (now - lastAnim < SPECIAL_ANIM_INTERVAL) return

        val speciesName = pokemonEntity.pokemon.species.name.lowercase()
        val anims = SPECIES_ANIMATIONS[speciesName] ?: return

        // Play a random species animation
        val pick = anims[world.random.nextInt(anims.size)]
        SkillEffects.sendAnimationPublic(world, pokemonEntity, pick)
        lastSpecialAnim[id] = now
        Cobblebase.LOGGER.info("[Ambient] $speciesName playing special animation: $pick")
    }

    /**
     * Check if a Pokemon should be held still (sleeping/sitting/socializing).
     * Called every tick from the mixin — prevents navigation even when not idle.
     */
    fun shouldPreventMovement(pokemonId: UUID): Boolean {
        val state = currentState[pokemonId] ?: return false
        return state == BehaviorState.SLEEPING || state == BehaviorState.SITTING || state == BehaviorState.SOCIALIZING || state == BehaviorState.STANDING
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

        // Debug logging every 5 seconds
        if (now % 100 == 0L) {
            val speciesName = pokemonEntity.pokemon.species.name
            val dayTime = (world as? ServerWorld)?.timeOfDay?.rem(24000) ?: -1
            if (state != BehaviorState.WANDERING) {
                Cobblebase.LOGGER.info("[Ambient] $speciesName: state=$state, elapsed=${now - stateStart}t, dayTime=$dayTime")
            } else if (now % 500 == 0L) {
                // Log wandering state less frequently
                Cobblebase.LOGGER.info("[Ambient] $speciesName: WANDERING, dayTime=$dayTime")
            }
        }

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
        // Wake up at dawn (Minecraft: 23000-24000 + 0-1000 = sunrise)
        val dayTime = world.timeOfDay % 24000
        val isDaytime = dayTime in 0..12999 || dayTime in 23000..24000
        if (isDaytime) {
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

        // Play species-specific special animation (attack, emote, taunt)
        val speciesName = entity.pokemon.species.name.lowercase()
        val specAnims = SPECIES_ANIMATIONS[speciesName]
        if (now % 80 == 0L && specAnims != null && world.random.nextInt(100) < SPECIAL_ANIM_CHANCE) {
            val pick = specAnims[world.random.nextInt(specAnims.size)]
            SkillEffects.sendAnimationPublic(world, entity, pick)
        }
        // Generic look-around animation for all mons
        else if (now % 80 == 0L && world.random.nextInt(100) < LOOK_AROUND_CHANCE) {
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

        // Night time — go to sleep (Minecraft: 13000 = sunset, 23000 = sunrise)
        val dayTime = world.timeOfDay % 24000
        val isNight = dayTime in 13000..22999
        if (isNight) {
            // High chance to sleep at night
            if (world.random.nextInt(100) < 70) {
                Cobblebase.LOGGER.info("[Ambient] ${entity.pokemon.species.name} going to sleep (dayTime=$dayTime)")
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
        val prev = currentState[id]
        if (prev != state) {
            Cobblebase.LOGGER.info("[Ambient] State change: $prev -> $state")
        }
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

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
    private val speciesNames = mutableMapOf<UUID, String>()

    // Timing constants
    private const val IDLE_STAND_MIN = 100L   // 5 seconds minimum standing still
    private const val IDLE_STAND_MAX = 400L   // 20 seconds max before next behavior
    private const val SOCIAL_RANGE = 15.0     // blocks — how close mons need to be to interact
    private const val SOCIAL_CHANCE = 50       // percent chance per check to start social interaction
    private const val SIT_CHANCE = 15          // percent chance to sit instead of wander
    private const val LOOK_AROUND_CHANCE = 20  // percent chance to play look-around animation
    private const val SPECIAL_ANIM_CHANCE = 25 // percent chance to play a special animation (attack, emote)

    // Signature moves for CobbleMotion species (preferred over generic animations)
    private val SIGNATURE_ANIMATIONS = mapOf(
        "grimmsnarl" to listOf("taunt"),
        "incineroar" to listOf("darkestlariat"),
        "emboar" to listOf("heatcrash"),
        "meowscarada" to listOf("flowertrick"),
        "typhlosion" to listOf("eruption"),
        "primarina" to listOf("sparklingaria"),
        "decidueye" to listOf("spiritshackle"),
        "serperior" to listOf("leafstorm"),
        "quaquaval" to listOf("aquastep"),
        "samurott" to listOf("aquajet"),
        "samurott_hisuian" to listOf("aquajet"),
        "typhlosion_hisuian" to listOf("infernalparade"),
        "decidueye_hisuian" to listOf("triplearrows")
    )

    // Generic attack animations available for ALL Cobblemon species
    private val GENERIC_ANIMATIONS = listOf("physical", "special")

    enum class BehaviorState {
        WANDERING,      // normal wandering (existing behavior)
        STANDING,       // standing still, looking around
        SLEEPING,       // lying down at night
        SOCIALIZING,    // interacting with nearby mon (facing each other, cries)
        SITTING,        // sitting/resting in place
        FOLLOWING,      // following another mon around together
        CHILLING,       // sitting together with another mon
        CHASING,        // one mon runs away, another chases it
        FLEEING,        // the mon being chased (runs away fast)
        WALKING_TOGETHER // two mons walk side by side
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

        // Play a subtle idle animation (no attack moves — those are for socializing)
        val idleAnims = listOf("pose", "blink", "happy", "look_quirk")
        val pick = idleAnims[world.random.nextInt(idleAnims.size)]
        SkillEffects.sendAnimationPublic(world, pokemonEntity, pick)
        lastSpecialAnim[id] = now
    }

    /**
     * Check if a Pokemon should be held still (sleeping/sitting/socializing).
     * Called every tick from the mixin — prevents navigation even when not idle.
     */
    fun shouldPreventMovement(pokemonId: UUID): Boolean {
        val state = currentState[pokemonId] ?: return false
        // Only stop movement for stationary states (not chasing/fleeing/following)
        return state == BehaviorState.SLEEPING || state == BehaviorState.SITTING ||
               state == BehaviorState.SOCIALIZING || state == BehaviorState.STANDING ||
               state == BehaviorState.CHILLING
    }

    /**
     * Check if a Pokemon is in any active behavior (prevents state override).
     */
    fun isInActiveBehavior(pokemonId: UUID): Boolean {
        val state = currentState[pokemonId] ?: return false
        return state != BehaviorState.WANDERING
    }

    /**
     * Called from the mixin tick loop when a Pokemon's navigation is idle.
     * Returns true if ambient behavior took over (don't wander), false to allow normal wandering.
     */
    fun tickIdle(world: World, pokemonEntity: PokemonEntity, origin: BlockPos): Boolean {
        if (world !is ServerWorld) return false
        lastWorld = world
        val id = pokemonEntity.pokemon.uuid
        speciesNames[id] = pokemonEntity.pokemon.species.name
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
            BehaviorState.FOLLOWING -> tickFollowing(world, pokemonEntity, id, now, stateStart)
            BehaviorState.CHILLING -> tickChilling(world, pokemonEntity, id, now, stateStart)
            BehaviorState.CHASING -> tickChasing(world, pokemonEntity, id, now, stateStart)
            BehaviorState.FLEEING -> tickFleeing(world, pokemonEntity, id, now, stateStart, origin)
            BehaviorState.WALKING_TOGETHER -> tickWalkingTogether(world, pokemonEntity, id, now, stateStart, origin)
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
            // Wake-up stretch animation
            SkillEffects.sendAnimationPublic(world, entity, "pose", "happy")
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

        // Play look-around animation for all mons
        if (now % 80 == 0L && world.random.nextInt(100) < LOOK_AROUND_CHANCE) {
            val lookAnims = arrayOf("happy", "pose", "blink", "look_quirk", "sniff_quirk")
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

        // Social interaction lasts 8-15 seconds
        if (elapsed >= 160L + world.random.nextInt(140).toLong()) {
            setState(id, BehaviorState.WANDERING, now)
            return false
        }

        // Find partner
        val partnerId = lastInteractionPartner[id]
        val partner = if (partnerId != null) {
            val searchBox = Box.of(entity.pos, 20.0, 6.0, 20.0)
            world.getEntitiesByClass(PokemonEntity::class.java, searchBox) { it.pokemon.uuid == partnerId }.firstOrNull()
        } else null

        if (partner == null) {
            setState(id, BehaviorState.WANDERING, now)
            return false
        }

        val dist = entity.distanceTo(partner)
        if (dist > 3.0 && elapsed < 80L) {
            // First phase: walk towards partner
            NavigationHelper.navigateTo(entity, partner.blockPos, 0.4)
        } else {
            // Second phase: face each other and show off moves
            NavigationHelper.clearTargets(entity)
            entity.lookAtEntity(partner, 60f, 30f)

            // Play signature or attack animation while facing partner
            if (elapsed % 80 == 0L) {
                val speciesName = entity.pokemon.species.name.lowercase()
                val signature = SIGNATURE_ANIMATIONS[speciesName]
                val anims = if (signature != null) signature + GENERIC_ANIMATIONS else GENERIC_ANIMATIONS
                val pick = anims[world.random.nextInt(anims.size)]
                SkillEffects.sendAnimationPublic(world, entity, pick)
            }
        }

        return true
    }

    /**
     * Following — one mon walks towards another and follows it around.
     */
    private fun tickFollowing(world: ServerWorld, entity: PokemonEntity, id: UUID, now: Long, stateStart: Long): Boolean {
        val elapsed = now - stateStart

        // Follow for 20-30 seconds then stop
        if (elapsed >= 400L + world.random.nextInt(200).toLong()) {
            setState(id, BehaviorState.WANDERING, now)
            return false
        }

        val partnerId = lastInteractionPartner[id] ?: run {
            setState(id, BehaviorState.WANDERING, now)
            return false
        }

        val searchBox = Box.of(entity.pos, 20.0, 6.0, 20.0)
        val partner = (world as ServerWorld).getEntitiesByClass(PokemonEntity::class.java, searchBox) { it.pokemon.uuid == partnerId }
            .firstOrNull()

        if (partner == null) {
            setState(id, BehaviorState.WANDERING, now)
            return false
        }

        // Walk slowly towards partner (stay ~2 blocks behind)
        val dist = entity.squaredDistanceTo(partner)
        if (dist > 4.0) { // more than 2 blocks away
            NavigationHelper.navigateTo(entity, partner.blockPos, 0.3) // slow follow speed
        } else {
            // Close enough — look at partner
            entity.lookAtEntity(partner, 30f, 30f)
            NavigationHelper.clearTargets(entity)
        }

        // Occasional happy animation while following
        if (now % 120 == 0L && world.random.nextInt(100) < 20) {
            SkillEffects.sendAnimationPublic(world, entity, "happy", "pose")
        }

        return true // don't wander
    }

    /**
     * Chilling together — two mons sit next to each other and relax.
     */
    private fun tickChilling(world: ServerWorld, entity: PokemonEntity, id: UUID, now: Long, stateStart: Long): Boolean {
        val elapsed = now - stateStart

        // Chill together for 15-30 seconds
        if (elapsed >= 300L + world.random.nextInt(300).toLong()) {
            setState(id, BehaviorState.WANDERING, now)
            return false
        }

        // Find partner
        val partnerId = lastInteractionPartner[id]
        val partner = if (partnerId != null) {
            val searchBox = Box.of(entity.pos, 20.0, 6.0, 20.0)
            world.getEntitiesByClass(PokemonEntity::class.java, searchBox) { it.pokemon.uuid == partnerId }.firstOrNull()
        } else null

        if (partner == null) {
            setState(id, BehaviorState.WANDERING, now)
            return false
        }

        val dist = entity.distanceTo(partner)
        if (dist > 3.0 && elapsed < 100L) {
            // First phase: walk to partner
            NavigationHelper.navigateTo(entity, partner.blockPos, 0.3)
        } else {
            // Second phase: sit together
            NavigationHelper.clearTargets(entity)
            entity.lookAtEntity(partner, 30f, 30f)

            // Play rest animation
            if (now % 100 == 0L) {
                SkillEffects.sendAnimationPublic(world, entity, "sleep", "pose", "ground_idle")
            }

            // Occasional happy animation
            if (now % 200 == 0L && world.random.nextInt(100) < 30) {
                SkillEffects.sendAnimationPublic(world, entity, "happy", "pose")
            }
        }

        return true
    }

    /**
     * Chasing — run after the fleeing mon at high speed.
     */
    private fun tickChasing(world: ServerWorld, entity: PokemonEntity, id: UUID, now: Long, stateStart: Long): Boolean {
        val elapsed = now - stateStart

        // Chase lasts 30 seconds
        if (elapsed >= 600L) {
            setState(id, BehaviorState.WANDERING, now)
            return false
        }

        val partnerId = lastInteractionPartner[id] ?: run {
            setState(id, BehaviorState.WANDERING, now)
            return false
        }

        val searchBox = Box.of(entity.pos, 30.0, 6.0, 30.0)
        val target = world.getEntitiesByClass(PokemonEntity::class.java, searchBox) { it.pokemon.uuid == partnerId }
            .firstOrNull()

        if (target == null) {
            setState(id, BehaviorState.WANDERING, now)
            return false
        }

        // Run towards the fleeing mon
        NavigationHelper.navigateTo(entity, target.blockPos, 0.9)

        return false // don't prevent wandering-level movement
    }

    /**
     * Fleeing — run away from the chasing mon at high speed in a random direction.
     */
    private fun tickFleeing(world: ServerWorld, entity: PokemonEntity, id: UUID, now: Long, stateStart: Long, origin: BlockPos): Boolean {
        val elapsed = now - stateStart

        // Flee lasts 30 seconds (same as chaser)
        if (elapsed >= 600L) {
            setState(id, BehaviorState.WANDERING, now)
            return false
        }

        val chaserId = lastInteractionPartner[id]
        val chaser = if (chaserId != null) {
            val searchBox = Box.of(entity.pos, 30.0, 6.0, 30.0)
            world.getEntitiesByClass(PokemonEntity::class.java, searchBox) { it.pokemon.uuid == chaserId }.firstOrNull()
        } else null

        // Run away — try multiple flee points, pick one with a valid path
        val PASTURE_RADIUS = 20
        val isStuck = !entity.navigation.isFollowingPath
        if (now % 30 == 0L || isStuck) { // recalculate every 1.5s or when stuck
            val distFromOriginX = entity.blockPos.x - origin.x
            val distFromOriginZ = entity.blockPos.z - origin.z
            val nearEdge = Math.abs(distFromOriginX) > PASTURE_RADIUS - 5 || Math.abs(distFromOriginZ) > PASTURE_RADIUS - 5

            // Try up to 5 flee points and pick the first one with a valid path
            for (attempt in 0..4) {
                val fleeX: Int
                val fleeZ: Int
                if (nearEdge || attempt > 2) {
                    // Near edge or failed attempts — run towards center area
                    fleeX = origin.x + world.random.nextInt(16) - 8
                    fleeZ = origin.z + world.random.nextInt(16) - 8
                } else if (chaser != null) {
                    // Run opposite direction from chaser with random spread
                    val dx = entity.blockPos.x - chaser.blockPos.x
                    val dz = entity.blockPos.z - chaser.blockPos.z
                    val angle = world.random.nextInt(90) - 45 // spread the flee direction
                    fleeX = entity.blockPos.x + (if (dx >= 0) 6 + attempt * 2 else -(6 + attempt * 2))  + world.random.nextInt(5) - 2
                    fleeZ = entity.blockPos.z + (if (dz >= 0) 6 + attempt * 2 else -(6 + attempt * 2)) + world.random.nextInt(5) - 2
                } else {
                    fleeX = origin.x + world.random.nextInt(PASTURE_RADIUS * 2) - PASTURE_RADIUS
                    fleeZ = origin.z + world.random.nextInt(PASTURE_RADIUS * 2) - PASTURE_RADIUS
                }
                val clampedX = fleeX.coerceIn(origin.x - PASTURE_RADIUS, origin.x + PASTURE_RADIUS)
                val clampedZ = fleeZ.coerceIn(origin.z - PASTURE_RADIUS, origin.z + PASTURE_RADIUS)

                // Try to navigate — if successful (returns true), stop trying
                val success = entity.navigation.startMovingTo(clampedX + 0.5, origin.y.toDouble(), clampedZ + 0.5, 0.9)
                if (success) break
            }
        }

        return false // don't prevent movement
    }

    // Shared walk destination for mons walking together
    private val walkDestination = mutableMapOf<UUID, BlockPos>()

    /**
     * Walking together — two mons walk side by side to a shared destination.
     */
    private fun tickWalkingTogether(world: ServerWorld, entity: PokemonEntity, id: UUID, now: Long, stateStart: Long, origin: BlockPos): Boolean {
        val elapsed = now - stateStart

        // Walk together for 20-30 seconds
        if (elapsed >= 400L + world.random.nextInt(200).toLong()) {
            setState(id, BehaviorState.WANDERING, now)
            walkDestination.remove(id)
            return false
        }

        val partnerId = lastInteractionPartner[id] ?: run {
            setState(id, BehaviorState.WANDERING, now)
            return false
        }

        val searchBox = Box.of(entity.pos, 25.0, 6.0, 25.0)
        val partner = world.getEntitiesByClass(PokemonEntity::class.java, searchBox) { it.pokemon.uuid == partnerId }
            .firstOrNull()

        if (partner == null) {
            setState(id, BehaviorState.WANDERING, now)
            walkDestination.remove(id)
            return false
        }

        // Pick a shared destination (or reuse existing one)
        val dest = walkDestination.getOrPut(id) {
            val PASTURE_RADIUS = 15
            val dx = world.random.nextInt(PASTURE_RADIUS * 2) - PASTURE_RADIUS
            val dz = world.random.nextInt(PASTURE_RADIUS * 2) - PASTURE_RADIUS
            BlockPos(origin.x + dx, origin.y, origin.z + dz)
        }
        // Partner shares the same destination (offset by 1 block to walk side by side)
        val partnerDest = walkDestination.getOrPut(partnerId) {
            BlockPos(dest.x + 1, dest.y, dest.z + 1)
        }

        // Check if arrived at destination — pick a new one
        if (NavigationHelper.isPokemonAtPosition(entity, dest, 2.0)) {
            walkDestination.remove(id)
            walkDestination.remove(partnerId)
            // Will pick new destination next tick
            return true
        }

        // Walk slowly to shared destination
        NavigationHelper.navigateTo(entity, dest, 0.3)

        return false // allow navigation
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

        // Check for nearby mons to interact with
        if (world.random.nextInt(100) < SOCIAL_CHANCE) {
            val searchBox = Box.of(entity.pos, SOCIAL_RANGE * 2, 4.0, SOCIAL_RANGE * 2)
            val nearbyMons = world.getEntitiesByClass(PokemonEntity::class.java, searchBox) { other ->
                other != entity && other.isAlive && !other.pokemon.isWild()
            }
            // Filter: only interact with mons that are also explicitly Idle (no job) AND not in active behavior
            val availableMons = nearbyMons.filter { other ->
                !isInActiveBehavior(other.pokemon.uuid) &&
                BaseManager.getAssignment(other.pokemon.uuid) == null
            }
            if (availableMons.isNotEmpty()) {
                val partner = availableMons[world.random.nextInt(availableMons.size)]
                // Register partner's species name so chat messages work
                speciesNames[partner.pokemon.uuid] = partner.pokemon.species.name
                lastInteractionPartner[id] = partner.pokemon.uuid
                lastInteractionPartner[partner.pokemon.uuid] = id

                // Pick a random social behavior
                val roll = world.random.nextInt(100)
                when {
                    roll < 20 -> {
                        // Face each other and show moves
                        setState(id, BehaviorState.SOCIALIZING, now)
                        setState(partner.pokemon.uuid, BehaviorState.SOCIALIZING, now)
                    }
                    roll < 35 -> {
                        // Follow the partner around
                        setState(id, BehaviorState.FOLLOWING, now)
                    }
                    roll < 50 -> {
                        // Sit down and chill together
                        setState(id, BehaviorState.CHILLING, now)
                        setState(partner.pokemon.uuid, BehaviorState.CHILLING, now)
                    }
                    roll < 75 -> {
                        // Walk together side by side
                        Cobblebase.LOGGER.info("[Ambient] WALK TOGETHER triggered for ${speciesNames[id]} + ${partner.pokemon.species.name}")
                        setState(id, BehaviorState.WALKING_TOGETHER, now)
                        setState(partner.pokemon.uuid, BehaviorState.WALKING_TOGETHER, now)
                        lastInteractionPartner[id] = partner.pokemon.uuid
                        lastInteractionPartner[partner.pokemon.uuid] = id
                    }
                    else -> {
                        // Chase! One runs away, the other chases
                        setState(id, BehaviorState.CHASING, now)
                        setState(partner.pokemon.uuid, BehaviorState.FLEEING, now)
                        lastInteractionPartner[id] = partner.pokemon.uuid
                        lastInteractionPartner[partner.pokemon.uuid] = id
                    }
                }
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
            val name = speciesNames[id] ?: "Unknown"
            Cobblebase.LOGGER.info("[Ambient] $name: $prev -> $state")
            if (state != BehaviorState.WANDERING) {
                broadcastAmbientState(name, state)
            }
        }
        currentState[id] = state
        stateStartTime[id] = now
    }

    // Store last broadcast world for chat messages
    private var lastWorld: ServerWorld? = null

    private fun broadcastAmbientState(speciesName: String, state: BehaviorState) {
        val stateText = when (state) {
            BehaviorState.SITTING -> "\u00A77$speciesName is sitting down"
            BehaviorState.STANDING -> "\u00A77$speciesName is looking around"
            BehaviorState.SLEEPING -> "\u00A77$speciesName fell asleep"
            BehaviorState.SOCIALIZING -> "\u00A7e$speciesName is showing off moves"
            BehaviorState.FOLLOWING -> "\u00A7b$speciesName is following a friend"
            BehaviorState.CHILLING -> "\u00A7a$speciesName is chilling with a friend"
            BehaviorState.CHASING -> "\u00A7c$speciesName is chasing!"
            BehaviorState.FLEEING -> "\u00A7c$speciesName is running away!"
            else -> return
        }
        try {
            val world = lastWorld ?: return
            world.server.playerManager.playerList.forEach { player ->
                player.sendMessage(net.minecraft.text.Text.literal(stateText), false)
            }
        } catch (_: Exception) {}
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

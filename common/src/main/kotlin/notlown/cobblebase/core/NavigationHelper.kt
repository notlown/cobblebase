package notlown.cobblebase.core

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.CobblebaseConfig
import java.util.UUID

/**
 * Navigation utilities ported from Cobbleworkers' CobbleworkersNavigationUtils.
 * Uses bounding box intersection for position checks and throttled pathfinding.
 */
object NavigationHelper {

    private val lastPathfindTick = mutableMapOf<UUID, Long>()
    private const val PATHFIND_INTERVAL_TICKS = 5L

    /**
     * Checks if the Pokemon's bounding box intersects with the target block area.
     * Same logic as Cobbleworkers' isPokemonAtPosition.
     */
    fun isPokemonAtPosition(pokemonEntity: PokemonEntity, targetPos: BlockPos, offset: Double = 2.0): Boolean {
        val interactionHitbox = Box(targetPos).expand(offset)
        return pokemonEntity.boundingBox.intersects(interactionHitbox)
    }

    /**
     * Checks if the Pokemon is near enough to a player.
     */
    fun isPokemonNearPlayer(pokemonEntity: PokemonEntity, player: PlayerEntity, offset: Double = 2.0): Boolean {
        val interactionHitbox = player.boundingBox.expand(offset)
        return pokemonEntity.boundingBox.intersects(interactionHitbox)
    }

    /**
     * Commands the Pokemon to move towards the target.
     * Throttled to once per second to avoid pathfinding spam.
     * Same logic as Cobbleworkers' navigateTo.
     */
    private const val MAX_SPEED = 0.4

    /**
     * Gets the Pokemon's natural walkSpeed from its species definition, capped at MAX_SPEED.
     */
    fun getSpeciesSpeed(pokemonEntity: PokemonEntity): Double {
        return try {
            val walkBehaviour = pokemonEntity.behaviour.moving.walk
            val speedField = walkBehaviour.javaClass.getDeclaredField("walkSpeed")
            speedField.isAccessible = true
            val expr = speedField.get(walkBehaviour)
            // walkSpeed is a MoLang Expression — try to evaluate it
            val resolveMethod = expr.javaClass.getMethod("get")
            val value = (resolveMethod.invoke(expr) as? Number)?.toDouble() ?: 0.2
            value.coerceAtMost(MAX_SPEED)
        } catch (e: Exception) {
            0.2 // Default fallback
        }
    }

    fun navigateTo(pokemonEntity: PokemonEntity, targetPos: BlockPos, speed: Double = -1.0) {
        val actualSpeed = if (speed < 0) 0.7 else speed.coerceAtLeast(0.5)
        val world = pokemonEntity.world
        val now = world.time
        val id = pokemonEntity.pokemon.uuid
        val last = lastPathfindTick[id] ?: 0L

        if (now - last < PATHFIND_INTERVAL_TICKS) return
        lastPathfindTick[id] = now

        var result = pokemonEntity.navigation.startMovingTo(
            targetPos.x + 0.5,
            targetPos.y.toDouble(),
            targetPos.z + 0.5,
            actualSpeed
        )

        // If direct path fails, try intermediate positions to unstick
        if (!result) {
            // Try navigating to a position between current pos and target
            val midX = (pokemonEntity.x + targetPos.x) / 2.0
            val midZ = (pokemonEntity.z + targetPos.z) / 2.0
            // Try at same Y level as mon first
            result = pokemonEntity.navigation.startMovingTo(midX, pokemonEntity.y, midZ, actualSpeed)

            if (!result) {
                // Try a small random offset from current position to unstick
                val rand = java.util.concurrent.ThreadLocalRandom.current()
                val offX = pokemonEntity.x + (rand.nextDouble() * 4 - 2)
                val offZ = pokemonEntity.z + (rand.nextDouble() * 4 - 2)
                result = pokemonEntity.navigation.startMovingTo(offX, pokemonEntity.y, offZ, actualSpeed)
            }

            // Don't teleport on nav failure — let stuck detection handle it
        }
    }

    /**
     * Clears navigation targets and stops the Pokemon's current pathfinding.
     */
    fun clearTargets(pokemonEntity: PokemonEntity) {
        pokemonEntity.navigation.stop()
        lastPathfindTick.remove(pokemonEntity.pokemon.uuid)
    }

    /**
     * If the mon is inside leaves, push it down to ground level.
     * Call this every tick to prevent mons getting trapped in tree canopies.
     */
    fun escapeLeaves(pokemonEntity: PokemonEntity) {
        val world = pokemonEntity.world
        val pos = pokemonEntity.blockPos
        val block = world.getBlockState(pos).block
        // Check if standing in or on leaves
        if (block is net.minecraft.block.LeavesBlock || world.getBlockState(pos.down()).block is net.minecraft.block.LeavesBlock) {
            // Find ground below
            var groundY = pos.y
            for (y in pos.y downTo pos.y - 20) {
                val checkBlock = world.getBlockState(BlockPos(pos.x, y, pos.z)).block
                if (checkBlock !is net.minecraft.block.LeavesBlock && checkBlock !is net.minecraft.block.AirBlock) {
                    groundY = y + 1
                    break
                }
            }
            if (groundY < pos.y) {
                pokemonEntity.refreshPositionAndAngles(
                    pokemonEntity.x, groundY.toDouble(), pokemonEntity.z,
                    pokemonEntity.yaw, pokemonEntity.pitch
                )
            }
        }
    }

    /**
     * Checks if a Pokemon has been stuck (same position for 15+ seconds).
     * If stuck (same BlockPos for 7+ seconds), stops navigation and tries a random
     * direction nearby. No teleporting — safe for enclosed builds like pens and aquariums.
     * Call this every tick from the pasture mixin.
     */
    fun checkAndUnstick(pokemonEntity: PokemonEntity, origin: BlockPos) {
        val world = pokemonEntity.world
        if (world.isClient) return
        val id = pokemonEntity.pokemon.uuid
        val now = world.time
        val currentPos = pokemonEntity.blockPos

        val lastPos = lastPositions[id]
        if (lastPos != null && lastPos == currentPos) {
            // Same BlockPos — check if stuck long enough
            val since = stuckSince.getOrPut(id) { now }
            val stuckDuration = now - since
            if (stuckDuration >= STUCK_THRESHOLD_TICKS) {
                pokemonEntity.navigation.stop()
                val rand = java.util.concurrent.ThreadLocalRandom.current()
                val canFly = try { pokemonEntity.pokemon.species.behaviour.moving.fly.canFly } catch (_: Exception) { false }

                // Escalating unstick: longer stuck = more aggressive attempts
                val intensity = (stuckDuration / STUCK_THRESHOLD_TICKS).coerceIn(1, 5).toInt()
                val dist = (1.5 + intensity * 1.5) // 3 to 9 blocks
                val speed = (0.4 + intensity * 0.15).coerceAtMost(1.0) // 0.55 to 1.0

                val angle = rand.nextDouble() * Math.PI * 2
                val targetX = currentPos.x + Math.cos(angle) * dist + 0.5
                val targetZ = currentPos.z + Math.sin(angle) * dist + 0.5

                val targetY = if (canFly) {
                    // Flying: diagonal movement with increasing Y variation
                    currentPos.y + (rand.nextInt(intensity * 2 + 1) - intensity).toDouble()
                } else {
                    currentPos.y.toDouble()
                }

                pokemonEntity.navigation.startMovingTo(targetX, targetY, targetZ, speed)
                // Don't reset stuckSince — let intensity escalate on next check
                stuckSince[id] = since // keep original stuck time for escalation
            }
        } else {
            // Moved — reset stuck tracking
            lastPositions[id] = currentPos.toImmutable()
            stuckSince.remove(id)
        }
    }

    private val lastWanderTick = mutableMapOf<UUID, Long>()
    private const val WANDER_INTERVAL_TICKS = 80L // Wander every 4 seconds

    // Stuck detection: track positions to detect mons that haven't moved
    private val lastPositions = mutableMapOf<UUID, BlockPos>()
    private val stuckSince = mutableMapOf<UUID, Long>()
    private const val STUCK_THRESHOLD_TICKS = 140L // 7 seconds

    /**
     * Makes a Pokemon wander randomly near the pasture origin.
     * Called when the Pokemon is idle (between jobs) to keep it moving naturally.
     * Radius is small (3-6 blocks) to stay near the base.
     */
    fun wanderNearOrigin(pokemonEntity: PokemonEntity, origin: BlockPos, radius: Int = 5) {
        val world = pokemonEntity.world
        val now = world.time
        val id = pokemonEntity.pokemon.uuid
        val last = lastWanderTick[id] ?: 0L

        if (now - last < WANDER_INTERVAL_TICKS) return
        lastWanderTick[id] = now

        // Pick a random position near the origin (use thread-safe Random to avoid C2ME crash)
        val random = java.util.concurrent.ThreadLocalRandom.current()
        val dx = random.nextInt(radius * 2 + 1) - radius
        val dz = random.nextInt(radius * 2 + 1) - radius
        val targetX = origin.x + dx
        val targetZ = origin.z + dz
        val targetY = origin.y

        pokemonEntity.navigation.startMovingTo(
            targetX + 0.5,
            targetY.toDouble(),
            targetZ + 0.5,
            0.3  // slow stroll speed for idle wandering
        )
    }
}

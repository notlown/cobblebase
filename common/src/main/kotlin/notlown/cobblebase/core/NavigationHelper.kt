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
     * If the mon is inside or under a tree canopy, push it down to ground level.
     * Detects: standing in leaves, standing on leaves, OR flying under leaves.
     * Only targets natural trees (LeavesBlock) — does NOT affect enclosed rooms
     * built from solid blocks (stone, planks, etc.), so pasture box bases are safe.
     */
    fun escapeLeaves(pokemonEntity: PokemonEntity) {
        val world = pokemonEntity.world
        val pos = pokemonEntity.blockPos
        val block = world.getBlockState(pos).block
        val blockBelow = world.getBlockState(pos.down()).block

        // Check if IN leaves, ON leaves, or trapped under a leaf canopy
        val inLeaves = block is net.minecraft.block.LeavesBlock
        val onLeaves = blockBelow is net.minecraft.block.LeavesBlock
        val underCanopy = !inLeaves && !onLeaves && hasLeavesAbove(world, pos, 3)

        if (inLeaves || onLeaves || underCanopy) {
            // Find ground below (skip leaves and air only — fences are valid ground)
            var groundY = pos.y
            for (y in pos.y downTo pos.y - 20) {
                val checkBlock = world.getBlockState(BlockPos(pos.x, y, pos.z)).block
                if (checkBlock !is net.minecraft.block.LeavesBlock
                    && checkBlock !is net.minecraft.block.AirBlock) {
                    groundY = y + 1
                    break
                }
            }
            if (groundY < pos.y) {
                pokemonEntity.refreshPositionAndAngles(
                    pokemonEntity.x, groundY.toDouble(), pokemonEntity.z,
                    pokemonEntity.yaw, pokemonEntity.pitch
                )
                pokemonEntity.navigation.stop()
            }
        }
    }

    /**
     * Checks if there are leaves within [range] blocks above the position.
     * Used to detect if a flying mon is trapped under a tree canopy.
     */
    private fun hasLeavesAbove(world: World, pos: BlockPos, range: Int): Boolean {
        for (y in 1..range) {
            if (world.getBlockState(pos.up(y)).block is net.minecraft.block.LeavesBlock) {
                return true
            }
        }
        return false
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

        // Tolerance: consider mons "stuck" even if blockPos oscillates by 1 block
        // (handles small flying mons hovering in glass that bob between y=64 and y=65)
        val lastPos = lastPositions[id]
        val moved = lastPos == null || Math.abs(lastPos.x - currentPos.x) > 1
            || Math.abs(lastPos.y - currentPos.y) > 1
            || Math.abs(lastPos.z - currentPos.z) > 1
        if (!moved) {
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

                // At higher intensity, ALSO apply direct velocity impulse (physics push)
                // This helps when pathfinding can't find a path (e.g. 2-block flying mons in tight spots)
                // Not a teleport — just a velocity nudge that respects block collisions
                if (intensity >= 3) {
                    val dx = (targetX - pokemonEntity.x) * 0.3
                    val dz = (targetZ - pokemonEntity.z) * 0.3
                    val dy = if (canFly) 0.4 else 0.3 // small hop
                    pokemonEntity.setVelocity(dx, dy, dz)
                    pokemonEntity.velocityDirty = true
                }

                // Escape solid block: at intensity 1+ (7s), check if entity is clipped inside a solid block
                // First try vertical escape (push up), then horizontal if no safe Y
                if (intensity >= 1 && isClippedInSolid(pokemonEntity)) {
                    val escape = findEscapePosition(pokemonEntity)
                    if (escape != null) {
                        pokemonEntity.refreshPositionAndAngles(
                            escape.first, escape.second, escape.third,
                            pokemonEntity.yaw, pokemonEntity.pitch
                        )
                        pokemonEntity.setVelocity(0.0, 0.0, 0.0)
                        pokemonEntity.velocityDirty = true
                        notlown.cobblebase.core.Cobblebase.LOGGER.info(
                            "[Unstick] Escaped clipped ${pokemonEntity.pokemon.species.name} to ${escape.first},${escape.second},${escape.third}"
                        )
                    }
                }

                // Don't reset stuckSince — let intensity escalate on next check
                stuckSince[id] = since // keep original stuck time for escalation
            }
        } else {
            // Moved — reset stuck tracking
            lastPositions[id] = currentPos.toImmutable()
            stuckSince.remove(id)
        }
    }

    /**
     * Checks if a Pokemon's bounding box intersects any solid block collision shape.
     * Uses the actual entity bounding box (not contracted) to catch edge cases like
     * small flying mons clipped into ceiling glass.
     */
    private fun isClippedInSolid(pokemonEntity: PokemonEntity): Boolean {
        val world = pokemonEntity.world
        val box = pokemonEntity.boundingBox
        // Use ceil for max so we check ALL blocks the box touches (even partially)
        val minX = Math.floor(box.minX + 0.001).toInt()
        val minY = Math.floor(box.minY + 0.001).toInt()
        val minZ = Math.floor(box.minZ + 0.001).toInt()
        val maxX = Math.floor(box.maxX - 0.001).toInt()
        val maxY = Math.floor(box.maxY - 0.001).toInt()
        val maxZ = Math.floor(box.maxZ - 0.001).toInt()

        val pos = BlockPos.Mutable()
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    pos.set(x, y, z)
                    val state = world.getBlockState(pos)
                    if (state.isAir) continue
                    val shape = state.getCollisionShape(world, pos)
                    if (shape.isEmpty) continue
                    // Check if any of the shape's voxel boxes actually overlap the entity box
                    val shapeBoxes = shape.boundingBoxes
                    for (sb in shapeBoxes) {
                        val worldBox = sb.offset(x.toDouble(), y.toDouble(), z.toDouble())
                        if (box.intersects(worldBox)) return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Find a safe escape position. Tries vertical (up) first, then horizontal (8 directions),
     * then upward+horizontal combos. Returns (x, y, z) of a safe air pocket.
     */
    private fun findEscapePosition(pokemonEntity: PokemonEntity): Triple<Double, Double, Double>? {
        val world = pokemonEntity.world
        val startX = pokemonEntity.blockX
        val startY = pokemonEntity.blockY
        val startZ = pokemonEntity.blockZ
        val pos = BlockPos.Mutable()

        fun isSafe(x: Int, y: Int, z: Int): Boolean {
            pos.set(x, y, z)
            if (!world.getBlockState(pos).isAir) return false
            pos.set(x, y + 1, z)
            return world.getBlockState(pos).isAir
        }

        // Try vertical escape first (1-6 blocks up)
        for (offset in 1..6) {
            if (isSafe(startX, startY + offset, startZ)) {
                return Triple(startX + 0.5, (startY + offset).toDouble(), startZ + 0.5)
            }
        }
        // Try horizontal escape — 8 directions × 1-3 block distances
        val dirs = listOf(
            1 to 0, -1 to 0, 0 to 1, 0 to -1,
            1 to 1, 1 to -1, -1 to 1, -1 to -1
        )
        for (dist in 1..3) {
            for ((dx, dz) in dirs) {
                val x = startX + dx * dist
                val z = startZ + dz * dist
                // Check at current Y, also Y+1 (over a 1-block obstacle)
                for (dy in 0..2) {
                    if (isSafe(x, startY + dy, z)) {
                        return Triple(x + 0.5, (startY + dy).toDouble(), z + 0.5)
                    }
                }
            }
        }
        return null
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

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
    /**
     * Ticks between consecutive pathfind requests per Pokemon. 10 ticks (0.5 s) is the
     * sweet spot: 5 ticks (the previous value) caused expensive A* recalcs every quarter
     * second, while 20 ticks felt sluggish for time-sensitive jobs like Recruiter/Guard
     * where the target moves. 10 keeps response time imperceptible to humans while
     * halving the pathfinding cost.
     */
    private const val PATHFIND_INTERVAL_TICKS = 10L

    /**
     * Returns true if this Pokemon's species can fly. Used to gate the y-clamping logic
     * below — without grounding, flying mons drift upward on every pathfind tick and end
     * up 20+ blocks above their crops, never reaching them.
     */
    private fun canFly(pokemonEntity: PokemonEntity): Boolean {
        return try { pokemonEntity.pokemon.species.behaviour.moving.fly.canFly } catch (_: Exception) { false }
    }

    /**
     * Snaps a flying Pokemon down to roughly target y if it has drifted more than 1.5
     * blocks above. Sets a slight downward velocity so it doesn't immediately float back up.
     */
    private fun groundFlyingPokemon(pokemonEntity: PokemonEntity, targetY: Double) {
        if (pokemonEntity.y > targetY + 1.5) {
            pokemonEntity.refreshPositionAndAngles(
                pokemonEntity.x, targetY + 1.0, pokemonEntity.z,
                pokemonEntity.yaw, pokemonEntity.pitch
            )
            pokemonEntity.setVelocity(pokemonEntity.velocity.x, -0.1, pokemonEntity.velocity.z)
            pokemonEntity.velocityDirty = true
        }
    }

    /**
     * Clamps a navigation target to within the Pokemon's tethering roam box. Without this,
     * a target picked just outside the pasture range would pull the mon over the edge and
     * Cobblemon's tether system would yank it back — feedback loop, jittery movement.
     */
    private fun clampToRoamBox(pokemonEntity: PokemonEntity, targetPos: BlockPos): BlockPos {
        val tethering = pokemonEntity.tethering ?: return targetPos
        val min = tethering.minRoamPos
        val max = tethering.maxRoamPos
        val clampedX = targetPos.x.coerceIn(min.x, max.x)
        val clampedY = targetPos.y.coerceIn(min.y, max.y)
        val clampedZ = targetPos.z.coerceIn(min.z, max.z)
        return if (clampedX == targetPos.x && clampedY == targetPos.y && clampedZ == targetPos.z) targetPos
        else BlockPos(clampedX, clampedY, clampedZ)
    }

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

        // Clamp the target to within the tethering roam box so navigation never aims
        // outside the pasture's working radius (Cobblemon would otherwise yank the
        // mon back, creating a jitter loop).
        val clampedTarget = clampToRoamBox(pokemonEntity, targetPos)

        // Flying mons drift upward on every pathfind unless we snap them back down.
        if (canFly(pokemonEntity)) {
            groundFlyingPokemon(pokemonEntity, clampedTarget.y.toDouble())
        }

        var result = pokemonEntity.navigation.startMovingTo(
            clampedTarget.x + 0.5,
            clampedTarget.y.toDouble(),
            clampedTarget.z + 0.5,
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
     * Null-safe so the pasture mixin can call it even when the entity has already been
     * unloaded (e.g. during chunk transition + release in the same tick).
     */
    fun clearTargets(pokemonEntity: PokemonEntity?) {
        pokemonEntity?.navigation?.stop()
        pokemonEntity?.pokemon?.uuid?.let { lastPathfindTick.remove(it) }
    }

    /**
     * Drops every per-Pokemon map entry for a UUID. Called immediately from the pasture
     * mixin's `releasePokemon` hook so memory is freed the moment a mon leaves the pasture
     * (recall, withdraw, base disassemble), instead of waiting for the 60s periodic sweep.
     */
    fun cleanupPokemon(id: UUID) {
        lastPathfindTick.remove(id)
        lastEscapeLeavesTick.remove(id)
        waterTypeCache.remove(id)
        waterCache.remove(id)
        lastPositions.remove(id)
        stuckSince.remove(id)
        lastWanderTick.remove(id)
    }

    /**
     * If the mon is inside or under a tree canopy, push it down to ground level.
     * Detects: standing in leaves, standing on leaves, OR flying under leaves.
     * Only targets natural trees (LeavesBlock) — does NOT affect enclosed rooms
     * built from solid blocks (stone, planks, etc.), so pasture box bases are safe.
     */
    // Throttle escapeLeaves to once per second per Pokemon. The check reads 2-5 block states
    // every tick for every pastured Pokemon, even though "trapped in leaves" is a rare event
    // that can wait up to 1s to be detected.
    private val lastEscapeLeavesTick = mutableMapOf<UUID, Long>()
    private const val ESCAPE_LEAVES_INTERVAL = 20L
    private const val NAV_STATE_STALE_TICKS = 1200L

    /**
     * Per-Pokemon cache of "is this a water-type Pokemon". Pokemon types are effectively
     * immutable in the pasture context (form changes are very rare); the previous code
     * iterated the type list and string-compared "water" every single tick for every
     * pastured Pokemon, in both the pasture mixin and `BaseManager.tickPokemon`'s
     * safety-teleport branch.
     */
    private val waterTypeCache = mutableMapOf<UUID, Boolean>()

    fun isWaterType(pokemon: com.cobblemon.mod.common.pokemon.Pokemon): Boolean {
        val id = pokemon.uuid
        waterTypeCache[id]?.let { return it }
        val result = pokemon.types.any { it.name.equals("water", ignoreCase = true) }
        waterTypeCache[id] = result
        return result
    }

    /** Called by BaseManager periodic sweep — drops per-Pokemon throttling state for
     *  Pokemon that haven't been ticked in 60s (recalled, despawned, chunk unloaded). */
    fun cleanupStale(now: Long) {
        val stale = lastEscapeLeavesTick.entries.filter { now - it.value > NAV_STATE_STALE_TICKS }.map { it.key }
        for (id in stale) {
            lastEscapeLeavesTick.remove(id)
            lastPathfindTick.remove(id)
            waterCache.remove(id)
            waterTypeCache.remove(id)
        }
    }

    fun escapeLeaves(pokemonEntity: PokemonEntity) {
        val world = pokemonEntity.world
        val now = world.time
        val pokemonId = pokemonEntity.pokemon.uuid

        // Cheap path runs EVERY tick (2 block reads): detect in/on leaves and continuously
        // clear the brain's movement memories so flying mons (Butterfree, Combee, Ninjask)
        // can't immediately re-issue a "fly up" path between the throttled ground-scan
        // resets below. Previously the whole function was throttled to once per second,
        // so a flying mon would visibly twitch in-tree for the full 1 s gap because its
        // brain re-engaged the climb path as soon as the reposition landed.
        val pos = pokemonEntity.blockPos
        val block = world.getBlockState(pos).block
        val blockBelow = world.getBlockState(pos.down()).block
        val inLeaves = block is net.minecraft.block.LeavesBlock
        val onLeaves = blockBelow is net.minecraft.block.LeavesBlock

        if (inLeaves || onLeaves) {
            try {
                val brain = pokemonEntity.brain
                brain.forget(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET)
                brain.forget(net.minecraft.entity.ai.brain.MemoryModuleType.PATH)
            } catch (_: Exception) {}
            try { pokemonEntity.navigation.stop() } catch (_: Exception) {}
        }

        // Expensive path (canopy check + downward ground scan + reposition) stays
        // throttled to once per second per Pokemon — block reads here can total up to
        // ~23 per call, way too costly to run every tick across every pastured mon.
        val lastCheck = lastEscapeLeavesTick[pokemonId] ?: 0L
        if (now - lastCheck < ESCAPE_LEAVES_INTERVAL) return
        lastEscapeLeavesTick[pokemonId] = now

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

        // Water-type Pokemon in/near water are swimming, not stuck — skip entirely
        if (pokemonEntity.isTouchingWater || pokemonEntity.isSubmergedInWater) {
            val isWaterType = pokemonEntity.pokemon.types.any { it.name.equals("water", ignoreCase = true) }
            if (isWaterType) {
                // Clear any stuck tracking so they start fresh if they leave water
                stuckSince.remove(pokemonEntity.pokemon.uuid)
                lastPositions.remove(pokemonEntity.pokemon.uuid)
                return
            }
        }
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
                        notlown.cobblebase.core.Cobblebase.log(
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
     * Find a safe escape position. Tries vertical (up) first, then horizontal (8 directions).
     * Returns (x, y, z) of a position where the entity's bounding box would NOT clip into any solid.
     */
    private fun findEscapePosition(pokemonEntity: PokemonEntity): Triple<Double, Double, Double>? {
        val world = pokemonEntity.world
        val startX = pokemonEntity.blockX
        val startY = pokemonEntity.blockY
        val startZ = pokemonEntity.blockZ
        val entityBox = pokemonEntity.boundingBox
        val width = entityBox.maxX - entityBox.minX
        val height = entityBox.maxY - entityBox.minY
        val depth = entityBox.maxZ - entityBox.minZ

        // Test if a candidate (cx, cy, cz) — block-center coordinates — is safe.
        // Builds a virtual bounding box at the destination and checks for any solid block intersection.
        fun isSafePosition(cx: Double, cy: Double, cz: Double): Boolean {
            val testBox = net.minecraft.util.math.Box(
                cx - width / 2, cy, cz - depth / 2,
                cx + width / 2, cy + height, cz + depth / 2
            )
            val minX = Math.floor(testBox.minX + 0.001).toInt()
            val minY = Math.floor(testBox.minY + 0.001).toInt()
            val minZ = Math.floor(testBox.minZ + 0.001).toInt()
            val maxX = Math.floor(testBox.maxX - 0.001).toInt()
            val maxY = Math.floor(testBox.maxY - 0.001).toInt()
            val maxZ = Math.floor(testBox.maxZ - 0.001).toInt()
            val pos = BlockPos.Mutable()
            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    for (z in minZ..maxZ) {
                        pos.set(x, y, z)
                        val state = world.getBlockState(pos)
                        if (state.isAir) continue
                        val shape = state.getCollisionShape(world, pos)
                        if (shape.isEmpty) continue
                        for (sb in shape.boundingBoxes) {
                            val worldBox = sb.offset(x.toDouble(), y.toDouble(), z.toDouble())
                            if (testBox.intersects(worldBox)) return false
                        }
                    }
                }
            }
            return true
        }

        // Try vertical escape first (1-8 blocks up)
        for (offset in 1..8) {
            val cx = startX + 0.5
            val cy = (startY + offset).toDouble()
            val cz = startZ + 0.5
            if (isSafePosition(cx, cy, cz)) {
                return Triple(cx, cy, cz)
            }
        }
        // Try horizontal escape — 8 directions × 1-4 block distances × 0-3 height steps
        val dirs = listOf(
            1 to 0, -1 to 0, 0 to 1, 0 to -1,
            1 to 1, 1 to -1, -1 to 1, -1 to -1
        )
        for (dist in 1..4) {
            for ((dx, dz) in dirs) {
                for (dy in 0..3) {
                    val cx = startX + 0.5 + dx * dist
                    val cy = (startY + dy).toDouble()
                    val cz = startZ + 0.5 + dz * dist
                    if (isSafePosition(cx, cy, cz)) {
                        return Triple(cx, cy, cz)
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

        val isWaterType = pokemonEntity.pokemon.types.any { it.name.equals("water", ignoreCase = true) }

        if (isWaterType) {
            if (pokemonEntity.isTouchingWater || pokemonEntity.isSubmergedInWater) {
                // In water — check if too far from pasture, swim back toward nearby water closer to origin
                // Squared-distance comparison avoids the sqrt; 20 blocks → 400 squared.
                val distSqFromOrigin = pokemonEntity.squaredDistanceTo(
                    origin.x + 0.5, origin.y.toDouble(), origin.z + 0.5
                )
                if (distSqFromOrigin > 400.0) {
                    // Swim back toward pasture — find water block closer to origin
                    val dirX = (origin.x - pokemonEntity.blockX).coerceIn(-5, 5)
                    val dirZ = (origin.z - pokemonEntity.blockZ).coerceIn(-5, 5)
                    val targetPos = pokemonEntity.blockPos.add(dirX, 0, dirZ)
                    pokemonEntity.navigation.startMovingTo(
                        targetPos.x + 0.5, pokemonEntity.y, targetPos.z + 0.5, 0.4
                    )
                }
                return
            }
            // On land: navigate toward nearest water block so they can swim
            val waterPos = findNearbyWater(pokemonEntity.world, origin, radius)
            if (waterPos != null) {
                pokemonEntity.navigation.startMovingTo(
                    waterPos.x + 0.5, waterPos.y.toDouble(), waterPos.z + 0.5, 0.4
                )
                return
            }
            // No water found — just wander normally
        }

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

    // Cache for findNearbyWaterCached — keyed by Pokemon UUID, holds last result + tick.
    // Without this, the pasture mixin scans up to 3087 block positions every tick for any
    // water-type Pokemon that's on land (e.g. spawned on grass next to its pasture).
    private data class WaterCacheEntry(val pos: BlockPos?, val tick: Long)
    private val waterCache = mutableMapOf<UUID, WaterCacheEntry>()
    private const val WATER_CACHE_TICKS = 40L  // re-scan at most every 2 seconds

    /**
     * Throttled version of [findNearbyWater]. Returns the cached result if it was computed
     * within the last 2 seconds; otherwise re-scans. Callers that hit this every tick (the
     * pasture mixin) get a ~40× reduction in block-state reads at the cost of up to 2 seconds
     * of staleness — acceptable since the water-mon-to-water teleport is a quality-of-life
     * helper, not a correctness-critical operation.
     */
    fun findNearbyWaterCached(
        world: net.minecraft.world.World,
        origin: BlockPos,
        radius: Int,
        pokemonId: UUID,
        now: Long
    ): BlockPos? {
        val cached = waterCache[pokemonId]
        if (cached != null && now - cached.tick < WATER_CACHE_TICKS) {
            return cached.pos
        }
        val found = findNearbyWater(world, origin, radius)
        waterCache[pokemonId] = WaterCacheEntry(found, now)
        return found
    }

    /**
     * Finds the nearest water block within radius of the origin.
     * Used to navigate water-type Pokemon toward water.
     */
    fun findNearbyWater(world: net.minecraft.world.World, origin: BlockPos, radius: Int): BlockPos? {
        var closest: BlockPos? = null
        var closestDist = Double.MAX_VALUE
        for (x in -radius..radius step 2) {
            for (z in -radius..radius step 2) {
                for (y in -3..3) {
                    val pos = origin.add(x, y, z)
                    if (world.getBlockState(pos).fluidState.isStill) {
                        val dist = origin.getSquaredDistance(pos)
                        if (dist < closestDist) {
                            closestDist = dist
                            closest = pos
                        }
                    }
                }
            }
        }
        return closest
    }
}

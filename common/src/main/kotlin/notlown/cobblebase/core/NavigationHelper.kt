package notlown.cobblebase.core

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
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
        val actualSpeed = if (speed < 0) getSpeciesSpeed(pokemonEntity) else speed.coerceAtMost(MAX_SPEED)
        val world = pokemonEntity.world
        val now = world.time
        val id = pokemonEntity.pokemon.uuid
        val last = lastPathfindTick[id] ?: 0L

        if (now - last < PATHFIND_INTERVAL_TICKS) return
        lastPathfindTick[id] = now

        pokemonEntity.navigation.startMovingTo(
            targetPos.x + 0.5,
            targetPos.y.toDouble(),
            targetPos.z + 0.5,
            actualSpeed
        )
    }

    /**
     * Clears navigation targets and stops the Pokemon's current pathfinding.
     * Used by the unstuck detector when a Pokemon hasn't moved for too long.
     */
    fun clearTargets(pokemonEntity: PokemonEntity) {
        pokemonEntity.navigation.stop()
        lastPathfindTick.remove(pokemonEntity.pokemon.uuid)
    }

    private val lastWanderTick = mutableMapOf<UUID, Long>()
    private const val WANDER_INTERVAL_TICKS = 80L // Wander every 4 seconds

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

        // Pick a random position near the origin
        val random = world.random
        val dx = random.nextInt(radius * 2 + 1) - radius
        val dz = random.nextInt(radius * 2 + 1) - radius
        val targetX = origin.x + dx
        val targetZ = origin.z + dz
        val targetY = origin.y

        val wanderSpeed = getSpeciesSpeed(pokemonEntity)
        pokemonEntity.navigation.startMovingTo(
            targetX + 0.5,
            targetY.toDouble(),
            targetZ + 0.5,
            wanderSpeed
        )
    }
}

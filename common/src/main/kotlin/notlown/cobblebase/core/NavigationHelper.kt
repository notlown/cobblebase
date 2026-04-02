package notlown.cobblebase.core

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import java.util.UUID

/**
 * Navigation utilities ported from Cobbleworkers' CobbleworkersNavigationUtils.
 * Uses bounding box intersection for position checks and throttled pathfinding.
 */
object NavigationHelper {

    private val lastPathfindTick = mutableMapOf<UUID, Long>()
    private const val PATHFIND_INTERVAL_TICKS = 20L

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
    fun navigateTo(pokemonEntity: PokemonEntity, targetPos: BlockPos, speed: Double = 1.0) {
        val world = pokemonEntity.world
        val now = world.time
        val id = pokemonEntity.pokemon.uuid
        val last = lastPathfindTick[id] ?: 0L

        if (now - last < PATHFIND_INTERVAL_TICKS) return
        lastPathfindTick[id] = now

        // Flying Pokemon: velocity-based movement with smart altitude handling
        val canFly = try { pokemonEntity.pokemon.species.behaviour.moving.fly.canFly } catch (_: Exception) { false }
        if (canFly) {
            val dx = (targetPos.x + 0.5) - pokemonEntity.x
            val dz = (targetPos.z + 0.5) - pokemonEntity.z
            val horizontalDist = Math.sqrt(dx * dx + dz * dz)
            val moveSpeed = speed * 0.15

            if (horizontalDist > 2.5) {
                // Phase 1: Far away — fly horizontally towards target at current altitude
                pokemonEntity.setVelocity(
                    dx / horizontalDist * moveSpeed,
                    0.0,
                    dz / horizontalDist * moveSpeed
                )
            } else {
                // Phase 2: Close enough horizontally — descend to target Y to interact
                val dy = (targetPos.y + 1.0) - pokemonEntity.y
                pokemonEntity.setVelocity(
                    dx / (horizontalDist + 0.1) * moveSpeed * 0.5,
                    (dy * 0.1).coerceIn(-0.15, 0.15), // gentle descent/ascent
                    dz / (horizontalDist + 0.1) * moveSpeed * 0.5
                )
            }
            pokemonEntity.velocityModified = true
            return
        }

        // Ground navigation for non-flying Pokemon
        if (pokemonEntity.navigation.isIdle) {
            pokemonEntity.navigation.stop()
        }

        pokemonEntity.navigation.startMovingTo(
            targetPos.x + 0.5,
            targetPos.y.toDouble(),
            targetPos.z + 0.5,
            speed
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
}

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

        // Flying Pokemon: always noClip (fly through leaves, branches, fences, anything)
        val canFly = try { pokemonEntity.pokemon.species.behaviour.moving.fly.canFly } catch (_: Exception) { false }
        if (canFly) {
            pokemonEntity.noClip = true

            // Safety: prevent going underground — push back to surface if below ground level
            val groundY = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, pokemonEntity.blockX, pokemonEntity.blockZ).toDouble()
            if (pokemonEntity.y < groundY - 1) {
                pokemonEntity.setPosition(pokemonEntity.x, groundY + 1.0, pokemonEntity.z)
            }
        }

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

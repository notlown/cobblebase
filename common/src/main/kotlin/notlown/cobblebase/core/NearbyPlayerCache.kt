package notlown.cobblebase.core

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

/**
 * Per-pasture cache of nearby ServerPlayerEntity references. Refreshed on the pasture
 * tick (every CACHE_TTL_TICKS) so animation broadcasts and cry packets reuse the same
 * list instead of running a 128³ `getEntitiesByClass` query per call — which was the
 * single hottest non-pathfinding cost in profiling on bases with multiple working mons.
 *
 * 5-second staleness is fine for these use cases: a player walking into range waits at
 * most one cache refresh before seeing animations / hearing cries. A player walking out
 * of range continues to receive packets for at most 5s — wasted bandwidth, but cheap.
 */
object NearbyPlayerCache {

    private const val CACHE_TTL_TICKS = 100L
    private const val PLAYER_RANGE = 128.0

    private data class CacheEntry(
        val players: List<ServerPlayerEntity>,
        val timestamp: Long
    )

    private val cache = mutableMapOf<BlockPos, CacheEntry>()

    /** Called from the pasture mixin tick — re-scans once the entry is older than the TTL. */
    fun update(world: ServerWorld, pasturePos: BlockPos, now: Long) {
        val existing = cache[pasturePos]
        if (existing != null && now - existing.timestamp < CACHE_TTL_TICKS) return
        val box = Box.of(pasturePos.toCenterPos(), PLAYER_RANGE, PLAYER_RANGE, PLAYER_RANGE)
        val players = world.getEntitiesByClass(ServerPlayerEntity::class.java, box) { true }
        cache[pasturePos] = CacheEntry(players.toList(), now)
    }

    /** Returns the cached list. May be up to 5s stale; empty when never populated. */
    fun getPlayers(pasturePos: BlockPos): List<ServerPlayerEntity> {
        return cache[pasturePos]?.players ?: emptyList()
    }

    /** Drop the cache entry when a pasture is broken / chunk-unloaded. */
    fun invalidate(pasturePos: BlockPos) {
        cache.remove(pasturePos)
    }
}

package notlown.cobblebase.core

import net.minecraft.block.LeavesBlock
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks leaf blocks near active Pasture Blocks.
 * Used by LeavesBlockMixin to selectively disable collision only for leaves
 * within a pasture's working area — Pokemon can fly through these leaves,
 * while leaves elsewhere in the world retain normal collision.
 *
 * Uses a thread-safe set since the mixin's getCollisionShape is called
 * from the main server thread but may also be queried during rendering.
 */
object PastureLeavesTracker {

    // Set of leaf block positions that are within working range of a pasture
    // Using long-packed BlockPos for memory efficiency (1 long vs 16 bytes per BlockPos)
    private val pastureLeaves: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    // Was 20 (= 41×41×41 = 68 921 blocks per scan). At that radius every pasture re-scan
    // costs nearly 70k block-state reads on the main thread. With multiple pastures all
    // firing their first scan on the same tick after a world load, this caused 5-10 seconds
    // of visible lag. 10 reduces it to 21³ = 9261 blocks per scan, ~7× cheaper and still
    // covers the typical Pokemon working radius.
    private const val SCAN_RADIUS = 10
    private const val SCAN_INTERVAL_TICKS = 100L // rescan every 5 seconds

    // Per-pasture last scan time + cached leaf positions (for incremental cleanup)
    private val lastScanTime = ConcurrentHashMap<Long, Long>()
    private val pastureLeafMap = ConcurrentHashMap<Long, Set<Long>>()

    /**
     * Called from the pasture tick mixin. Periodically scans for leaves
     * within SCAN_RADIUS of this pasture and tracks them.
     */
    fun updatePasture(world: ServerWorld, pasturePos: BlockPos) {
        val pastureKey = pasturePos.asLong()
        val now = world.time
        val lastScan = lastScanTime[pastureKey]
        if (lastScan == null) {
            // First time we see this pasture — stagger the initial scan by 1-5 seconds based
            // on a hash of the pasture position so multiple pastures don't all scan together
            // on the first tick after a world load.
            val stagger = (pasturePos.hashCode() and 0x7F).toLong() + 20L  // 20-147 ticks (1-7s)
            lastScanTime[pastureKey] = now - SCAN_INTERVAL_TICKS + stagger
            return
        }
        if (now - lastScan < SCAN_INTERVAL_TICKS) return
        lastScanTime[pastureKey] = now

        val scanStart = System.nanoTime()

        // Remove old entries for this pasture
        val oldLeaves = pastureLeafMap[pastureKey]
        if (oldLeaves != null) {
            pastureLeaves.removeAll(oldLeaves)
        }

        // Scan new leaves around the pasture
        val newLeaves = HashSet<Long>()
        val pos = BlockPos.Mutable()
        for (dx in -SCAN_RADIUS..SCAN_RADIUS) {
            for (dy in -SCAN_RADIUS..SCAN_RADIUS) {
                for (dz in -SCAN_RADIUS..SCAN_RADIUS) {
                    pos.set(pasturePos.x + dx, pasturePos.y + dy, pasturePos.z + dz)
                    val block = world.getBlockState(pos).block
                    if (block is LeavesBlock) {
                        newLeaves.add(pos.asLong())
                    }
                }
            }
        }
        pastureLeafMap[pastureKey] = newLeaves
        pastureLeaves.addAll(newLeaves)

        val scanMs = (System.nanoTime() - scanStart) / 1_000_000
        if (scanMs > 15) {
            Cobblebase.LOGGER.warn("[Cobblebase] PERF leaves-scan @ ${pasturePos.toShortString()} took ${scanMs}ms (${newLeaves.size} leaves)")
        }
    }

    /**
     * Called when a pasture block is removed/broken.
     */
    fun removePasture(pasturePos: BlockPos) {
        val pastureKey = pasturePos.asLong()
        val oldLeaves = pastureLeafMap.remove(pastureKey)
        if (oldLeaves != null) {
            pastureLeaves.removeAll(oldLeaves)
        }
        lastScanTime.remove(pastureKey)
    }

    /**
     * Fast lookup: is this leaf block part of any pasture's working area?
     * Called from the block collision mixin — must be O(1).
     */
    fun isPastureLeaf(pos: BlockPos): Boolean {
        return pastureLeaves.contains(pos.asLong())
    }

    /**
     * Clears all tracked leaves (e.g., on server stop).
     */
    fun clear() {
        pastureLeaves.clear()
        pastureLeafMap.clear()
        lastScanTime.clear()
    }

    /**
     * Verifies that every tracked pasture block still exists in the world and removes
     * tracking for any that don't. Called periodically by BaseManager — without this,
     * breaking a pasture block leaves its leaf-position cache in `pastureLeafMap` /
     * `pastureLeaves` for the entire server lifetime, slowing every leaf-collision check.
     */
    fun cleanupOrphanedPastures(world: ServerWorld) {
        val toRemove = mutableListOf<Long>()
        for ((key, _) in pastureLeafMap) {
            val pos = BlockPos.fromLong(key)
            val be = world.getBlockEntity(pos)
            if (be !is com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity) {
                toRemove.add(key)
            }
        }
        if (toRemove.isEmpty()) return
        for (key in toRemove) {
            val oldLeaves = pastureLeafMap.remove(key)
            if (oldLeaves != null) pastureLeaves.removeAll(oldLeaves)
            lastScanTime.remove(key)
        }
        Cobblebase.log("[PastureLeaves] Cleaned up ${toRemove.size} orphaned pasture(s)")
    }
}

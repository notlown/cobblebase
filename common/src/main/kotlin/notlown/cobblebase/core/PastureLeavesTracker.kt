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

    private const val SCAN_RADIUS = 20
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
        val lastScan = lastScanTime[pastureKey] ?: 0L
        if (now - lastScan < SCAN_INTERVAL_TICKS) return
        lastScanTime[pastureKey] = now

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
        if (newLeaves.isNotEmpty()) {
            Cobblebase.LOGGER.info("[PastureLeaves] Tracked ${newLeaves.size} leaves near pasture at ${pasturePos.x},${pasturePos.y},${pasturePos.z}")
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
}

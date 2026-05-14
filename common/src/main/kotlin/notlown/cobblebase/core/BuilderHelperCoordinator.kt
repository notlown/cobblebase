package notlown.cobblebase.core

import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import java.util.UUID

/**
 * Per-pasture claim ledger for Builder-Helper Pokemon.
 *
 * When a helper Pokemon's tick fires it asks the coordinator "what should I work on?" —
 * the coordinator looks at the active Builder job's still-needed blocks, filters to ones
 * this helper's species can supply via [BlockSupplyMap], and returns a [Claim] (target
 * block + role). Once a claim is held, other helpers see it as unavailable and pick
 * something different. Claims expire after [CLAIM_TTL_TICKS] of inactivity.
 *
 * This is the "Auto-Target" piece of the Builder-Helper system: helpers don't get told
 * what to do explicitly — they figure it out by querying the coordinator each tick.
 */
object BuilderHelperCoordinator {

    data class Claim(val blockId: String, val role: BlockSupplyMap.Role, val lastSeenTick: Long)

    private val claims = mutableMapOf<UUID, Claim>()
    private const val CLAIM_TTL_TICKS = 600L  // 30 seconds without refresh = drop the claim

    /**
     * Returns the current claim for [helperId], or assigns a fresh one. Returns null when
     * there's no active Builder job at this pasture, or no remaining block this helper
     * can supply.
     *
     * [supportedRoles] is the set of roles this helper can fulfil (e.g. wooloo can do
     * PRODUCER(wooloo); any species can do MINING/HARVESTER if it has those species skills).
     */
    fun getOrAssignClaim(
        world: ServerWorld,
        pasture: BlockPos,
        helperId: UUID,
        supportedRoles: Set<BlockSupplyMap.Role>,
        now: Long
    ): Claim? {
        // Garbage-collect stale claims first so other helpers can pick those targets up.
        claims.entries.removeAll { (_, c) -> now - c.lastSeenTick > CLAIM_TTL_TICKS }

        // Existing claim still valid?
        val existing = claims[helperId]
        if (existing != null) {
            // Refresh timestamp and check the claim is still useful (block still needed).
            if (isStillNeeded(world, pasture, existing.blockId)) {
                claims[helperId] = existing.copy(lastSeenTick = now)
                return existing
            }
            // Stale claim — drop it and look for a new one.
            claims.remove(helperId)
        }

        // Pick a new target from the needed-blocks list, skipping anything other helpers
        // are already chasing.
        val needed = notlown.cobblebase.core.executors.BuilderExecutor
            .getNeededBlocks(world, pasture) ?: return null
        val otherClaims = claims.filterKeys { it != helperId }.values.map { it.blockId }.toSet()
        for (blockId in needed) {
            if (blockId in otherClaims) continue
            val matchingRole = BlockSupplyMap.rolesFor(blockId).firstOrNull { it in supportedRoles }
                ?: continue
            val claim = Claim(blockId, matchingRole, now)
            claims[helperId] = claim
            return claim
        }
        return null
    }

    /** Drops the claim for a helper that just succeeded so the next tick re-picks. */
    fun releaseClaim(helperId: UUID) {
        claims.remove(helperId)
    }

    private fun isStillNeeded(world: ServerWorld, pasture: BlockPos, blockId: String): Boolean {
        val needed = notlown.cobblebase.core.executors.BuilderExecutor
            .getNeededBlocks(world, pasture) ?: return false
        return blockId in needed
    }

    /** Called by BaseManager periodic sweep. */
    fun cleanupStale(now: Long) {
        claims.entries.removeAll { (_, c) -> now - c.lastSeenTick > CLAIM_TTL_TICKS }
    }
}

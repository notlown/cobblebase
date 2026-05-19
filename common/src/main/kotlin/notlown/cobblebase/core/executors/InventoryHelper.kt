package notlown.cobblebase.core.executors

import notlown.cobblebase.core.effectiveRadius

import net.minecraft.entity.ItemEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import notlown.cobblebase.core.ContainerHelperRegistry
import notlown.cobblebase.core.ItemOriginHelper

/**
 * Shared helper for finding and inserting items into nearby containers.
 * Prioritizes containers that already hold the same item type (auto-sorting).
 */
object InventoryHelper {

    /**
     * Finds the best container for the given items:
     * 1. Nearest container that can fit the ENTIRE incoming stack and already contains the
     *    same item type (auto-sort).
     * 2. If no matching chest has enough room, nearest container with any free space.
     *
     * The capacity check is what prevents the "drops items because the matching chest is
     * almost full" bug — previously the matching chest was picked the moment it had ANY
     * matching slot with room, even if that room was just a few items while the empty
     * chest next door could fit the whole stack.
     */
    fun findBestContainer(world: World, pokemonPos: BlockPos, radius: Int, items: List<ItemStack>): BlockPos? {
        val cappedRadius = radius.coerceAtMost(CHEST_SCAN_RADIUS_CAP)
        val containers = findAllContainers(world, pokemonPos, cappedRadius)
        if (containers.isEmpty()) return null

        // Phase 1: nearest matching chest that can fit ALL incoming items.
        val matchingItem = items.firstOrNull { !it.isEmpty }
        if (matchingItem != null) {
            val totalIncoming = items.filter { ItemStack.areItemsAndComponentsEqual(it, matchingItem) }
                .sumOf { it.count }
            val matchingContainer = containers
                .filter { pos -> containerCapacityFor(world, pos, matchingItem) >= totalIncoming }
                .minByOrNull { it.getSquaredDistance(pokemonPos) }

            if (matchingContainer != null) return matchingContainer
        }

        // Phase 2: nearest container with free space (any kind).
        return containers
            .filter { pos -> containerHasSpace(world, pos) }
            .minByOrNull { it.getSquaredDistance(pokemonPos) }
    }

    /**
     * Returns the maximum count of `itemToMatch` that could still be added to this container,
     * summing up (a) free room in existing matching slots and (b) empty slots × maxStackSize.
     * Used by [findBestContainer] to skip "smart sort" chests that don't have enough room for
     * the whole incoming stack.
     */
    private fun containerCapacityFor(world: World, pos: BlockPos, itemToMatch: ItemStack): Int {
        val blockEntity = world.getBlockEntity(pos) as? Inventory ?: return 0
        var capacity = 0
        val maxStack = itemToMatch.maxCount
        for (i in 0 until blockEntity.size()) {
            val slot = blockEntity.getStack(i)
            if (slot.isEmpty) {
                capacity += maxStack
            } else if (ItemStack.areItemsAndComponentsEqual(slot, itemToMatch) && slot.count < slot.maxCount) {
                capacity += slot.maxCount - slot.count
            }
        }
        return capacity
    }

    /**
     * Legacy method - finds nearest container by distance only.
     */
    fun findNearestContainer(world: World, origin: BlockPos, radius: Int): BlockPos? {
        return findAllContainers(world, origin, radius.coerceAtMost(CHEST_SCAN_RADIUS_CAP))
            .minByOrNull { it.getSquaredDistance(origin) }
    }

    /**
     * Inserts items into the container. Returns leftover items that didn't fit.
     * Delegates to platform-specific ContainerHelper when available (supports modded containers
     * like Sophisticated Storage that don't implement vanilla Inventory).
     */
    fun insertItems(world: World, containerPos: BlockPos, items: List<ItemStack>): List<ItemStack> {
        // Platform-specific path (Fabric Transfer API / NeoForge Capabilities)
        val helper = ContainerHelperRegistry.instance
        if (helper != null) {
            return helper.insertItems(world, containerPos, items)
        }

        // Fallback: vanilla Inventory only
        val blockEntity = world.getBlockEntity(containerPos)
        val inventory: Inventory = when (blockEntity) {
            is Inventory -> blockEntity
            else -> return items
        }

        val leftovers = mutableListOf<ItemStack>()
        for (stack in items) {
            if (stack.isEmpty) continue
            val remaining = insertStack(inventory, stack)
            if (!remaining.isEmpty) {
                leftovers.add(remaining)
            }
        }
        blockEntity.markDirty()
        return leftovers
    }

    private fun insertStack(inventory: Inventory, stack: ItemStack): ItemStack {
        // First try to merge with existing stacks of same type
        for (i in 0 until inventory.size()) {
            val slot = inventory.getStack(i)
            if (!slot.isEmpty && ItemStack.areItemsAndComponentsEqual(slot, stack)) {
                val space = slot.maxCount - slot.count
                val transfer = minOf(space, stack.count)
                if (transfer > 0) {
                    slot.increment(transfer)
                    stack.decrement(transfer)
                    if (stack.isEmpty) return ItemStack.EMPTY
                }
            }
        }
        // Then try empty slots
        for (i in 0 until inventory.size()) {
            if (inventory.getStack(i).isEmpty) {
                inventory.setStack(i, stack.copy())
                return ItemStack.EMPTY
            }
        }
        // Didn't fit
        return stack
    }

    /**
     * Drops items on the ground at the Pokemon's position.
     * Used by all executors except Gatherer (which sorts into chests).
     *
     * When [pastureOrigin] is provided, items are tagged with the Pasture Block
     * position so the Gatherer only picks up items from its own base.
     */
    fun dropItems(world: World, pos: BlockPos, items: List<ItemStack>, pastureOrigin: BlockPos? = null) {
        for (stack in items) {
            if (stack.isEmpty) continue
            val entity = ItemEntity(
                world,
                pos.x + 0.5,
                pos.y + 0.25, // slight offset above ground, not a full block
                pos.z + 0.5,
                stack.copy()
            )
            entity.setVelocity(0.0, 0.0, 0.0) // no spread — items stay where dropped
            entity.setPickupDelay(20) // 1 second before pickup
            if (pastureOrigin != null) {
                ItemOriginHelper.tagEntity(entity, pastureOrigin)
            }
            world.spawnEntity(entity)
        }
    }

    /**
     * Extracts up to [maxCount] of the specified item from a container.
     * Returns the actual extracted ItemStack (may be empty if item not found).
     * Works with vanilla and most modded containers (Sophisticated Storage implements Inventory).
     */
    fun extractItem(world: World, containerPos: BlockPos, itemId: String, maxCount: Int): ItemStack {
        val targetId = net.minecraft.util.Identifier.of(
            if (itemId.contains(":")) itemId.substringBefore(":") else "minecraft",
            if (itemId.contains(":")) itemId.substringAfter(":") else itemId
        )
        val targetItem = net.minecraft.registry.Registries.ITEM.get(targetId)
        if (targetItem == net.minecraft.item.Items.AIR) return ItemStack.EMPTY

        // Use platform helper (Fabric Transfer API / NeoForge Capabilities) for modded containers
        val helper = ContainerHelperRegistry.instance
        if (helper != null) {
            return helper.extractItem(world, containerPos, ItemStack(targetItem), maxCount)
        }

        // Fallback: vanilla Inventory
        val blockEntity = world.getBlockEntity(containerPos) ?: return ItemStack.EMPTY
        val inventory = blockEntity as? Inventory ?: return ItemStack.EMPTY
        var remaining = maxCount
        for (i in 0 until inventory.size()) {
            val slot = inventory.getStack(i)
            if (slot.isEmpty || slot.item != targetItem) continue
            val take = minOf(remaining, slot.count)
            slot.decrement(take)
            remaining -= take
            if (remaining <= 0) break
        }
        val taken = maxCount - remaining
        blockEntity.markDirty()
        return if (taken > 0) ItemStack(targetItem, taken) else ItemStack.EMPTY
    }

    /**
     * Counts how many of a specific item exist across all containers in radius.
     */
    fun countItemInContainers(world: World, origin: BlockPos, radius: Int, itemId: String): Int {
        val targetId = net.minecraft.util.Identifier.of(
            if (itemId.contains(":")) itemId.substringBefore(":") else "minecraft",
            if (itemId.contains(":")) itemId.substringAfter(":") else itemId
        )
        val targetItem = net.minecraft.registry.Registries.ITEM.get(targetId)
        if (targetItem == net.minecraft.item.Items.AIR) return 0

        var total = 0
        for (pos in findAllContainers(world, origin, radius.coerceAtMost(CHEST_SCAN_RADIUS_CAP))) {
            val inv = world.getBlockEntity(pos) as? Inventory ?: continue
            for (i in 0 until inv.size()) {
                val slot = inv.getStack(i)
                if (!slot.isEmpty && slot.item == targetItem) {
                    total += slot.count
                }
            }
        }
        return total
    }

    /**
     * Finds the nearest container that has a specific item.
     * Uses platform ContainerHelper for modded container detection.
     */
    fun findContainerWithItem(world: World, origin: BlockPos, radius: Int, itemId: String): BlockPos? {
        val targetId = net.minecraft.util.Identifier.of(
            if (itemId.contains(":")) itemId.substringBefore(":") else "minecraft",
            if (itemId.contains(":")) itemId.substringAfter(":") else itemId
        )
        val targetItem = net.minecraft.registry.Registries.ITEM.get(targetId)
        if (targetItem == net.minecraft.item.Items.AIR) return null

        val matchStack = ItemStack(targetItem)
        val helper = ContainerHelperRegistry.instance

        return findAllContainers(world, origin, radius.coerceAtMost(CHEST_SCAN_RADIUS_CAP))
            .filter { pos ->
                // Check if container CONTAINS the item (any amount, no stack space requirement)
                val blockEntity = world.getBlockEntity(pos)
                if (blockEntity is Inventory) {
                    return@filter (0 until blockEntity.size()).any { i ->
                        val s = blockEntity.getStack(i)
                        !s.isEmpty && s.item == targetItem
                    }
                }
                // Platform helper fallback — hasItem checks for room, so also try extraction test
                if (helper != null) {
                    return@filter helper.hasItem(world, pos, matchStack)
                }
                false
            }
            .minByOrNull { it.getSquaredDistance(origin) }
    }

    fun findAllContainersPublic(world: World, origin: BlockPos, radius: Int): List<BlockPos> =
        findAllContainers(world, origin, radius)

    // Per-origin container-position cache. Without this, every `findBestContainer` call
    // scans (2*radius+1)³ positions — at radius 24 that's 117k getBlockEntity calls *per tick*
    // *per Gatherer/Craftsman/etc Pokemon*, which caused 600-1000ms tick spikes on the server.
    // We refresh the cache every 10 seconds per origin; container layout in a base doesn't
    // change often enough to matter.
    private data class ContainerCacheEntry(val positions: List<BlockPos>, val tick: Long, val radius: Int)
    private val containerCache = java.util.concurrent.ConcurrentHashMap<BlockPos, ContainerCacheEntry>()
    private const val CONTAINER_CACHE_TTL_TICKS = 1200L  // 60 seconds — container layout
    // doesn't change rapidly in normal play, and the per-Pokemon performance cost of cache
    // misses (was 600-1400ms per tick at radius 24) makes a longer TTL essential.

    /**
     * Maximum radius we'll EVER scan for containers, regardless of what `skill.effectiveRadius`
     * declares. The skill's search radius is for OTHER purposes (e.g. Gatherer item-pickup
     * range = 24 blocks), but for deposit-chest detection a 12-block cap covers any sensible
     * base layout while keeping per-scan cost bounded (25³ = 15625 positions).
     */
    private const val CHEST_SCAN_RADIUS_CAP = 12

    private fun findAllContainers(world: World, origin: BlockPos, radius: Int): List<BlockPos> {
        val serverWorld = world as? net.minecraft.server.world.ServerWorld
        if (serverWorld != null) {
            val now = serverWorld.time
            val cached = containerCache[origin]
            // Reuse the cache if the radius asked for is <= cached radius and not stale.
            if (cached != null && cached.radius >= radius && now - cached.tick < CONTAINER_CACHE_TTL_TICKS) {
                // Filter to within the requested radius (cache may have been built for a wider one).
                if (cached.radius == radius) return cached.positions
                return cached.positions.filter {
                    kotlin.math.abs(it.x - origin.x) <= radius &&
                    kotlin.math.abs(it.y - origin.y) <= radius &&
                    kotlin.math.abs(it.z - origin.z) <= radius
                }
            }
            val fresh = scanAllContainers(world, origin, radius)
            containerCache[origin] = ContainerCacheEntry(fresh, now, radius)
            return fresh
        }
        return scanAllContainers(world, origin, radius)
    }

    /** Periodic cleanup hook called from BaseManager's sweep. Drops cache entries older than ~5 min. */
    fun cleanupStale(now: Long) {
        containerCache.entries.removeAll { (_, entry) -> now - entry.tick > 6000L }
    }

    private fun scanAllContainers(world: World, origin: BlockPos, radius: Int): List<BlockPos> {
        // Fast path: ServerWorld → iterate chunk.getBlockEntities() for chunks intersecting
        // the radius cube. A 25-block radius touches ~4 chunks × ~20 block entities = ~80
        // lookups, compared to 15625 position-by-position getBlockEntity calls in the old
        // cube scan. Same candidate set thanks to the L∞ distance filter below.
        val serverWorld = world as? net.minecraft.server.world.ServerWorld
        if (serverWorld != null) {
            return scanAllContainersChunked(serverWorld, origin, radius)
        }
        // Fallback path for non-ServerWorld callers (mostly test harnesses).
        return scanAllContainersFallback(world, origin, radius)
    }

    private fun scanAllContainersChunked(
        world: net.minecraft.server.world.ServerWorld,
        origin: BlockPos,
        radius: Int
    ): List<BlockPos> {
        val helper = ContainerHelperRegistry.instance
        val result = mutableListOf<BlockPos>()
        val cxMin = (origin.x - radius) shr 4
        val cxMax = (origin.x + radius) shr 4
        val czMin = (origin.z - radius) shr 4
        val czMax = (origin.z + radius) shr 4
        for (cx in cxMin..cxMax) {
            for (cz in czMin..czMax) {
                // create=false: never trigger synchronous chunk loads on the main thread.
                val chunk = world.getChunk(cx, cz, net.minecraft.world.chunk.ChunkStatus.FULL, false) ?: continue
                // Chunk.blockEntities is protected; use the public position-set + getBlockEntity
                // lookup. Still vastly cheaper than the old per-position cube scan because
                // we only touch positions that actually have block entities.
                for (pos in chunk.blockEntityPositions) {
                    // L∞ (cube) distance filter so the candidate set matches the old code.
                    if (kotlin.math.abs(pos.x - origin.x) > radius) continue
                    if (kotlin.math.abs(pos.y - origin.y) > radius) continue
                    if (kotlin.math.abs(pos.z - origin.z) > radius) continue
                    if (helper != null) {
                        if (helper.isContainer(world, pos)) {
                            result.add(pos.toImmutable())
                        }
                    } else {
                        val be = chunk.getBlockEntity(pos)
                        if (be is net.minecraft.block.entity.LockableContainerBlockEntity) {
                            result.add(pos.toImmutable())
                        }
                    }
                }
            }
        }
        return result
    }

    private fun scanAllContainersFallback(world: World, origin: BlockPos, radius: Int): List<BlockPos> {
        val helper = ContainerHelperRegistry.instance
        val result = mutableListOf<BlockPos>()
        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = origin.add(x, y, z)
                    if (helper != null) {
                        if (helper.isContainer(world, pos)) {
                            result.add(pos.toImmutable())
                        }
                    } else {
                        val blockEntity = world.getBlockEntity(pos)
                        if (blockEntity is net.minecraft.block.entity.LockableContainerBlockEntity) {
                            result.add(pos.toImmutable())
                        }
                    }
                }
            }
        }
        return result
    }

    /**
     * Checks if a container already holds items of the same type.
     * Delegates to platform-specific ContainerHelper when available.
     */
    private fun containerHasItem(world: World, pos: BlockPos, itemToMatch: ItemStack): Boolean {
        val helper = ContainerHelperRegistry.instance
        if (helper != null) {
            return helper.hasItem(world, pos, itemToMatch)
        }

        // Fallback: vanilla Inventory only
        val blockEntity = world.getBlockEntity(pos) as? Inventory ?: return false
        for (i in 0 until blockEntity.size()) {
            val slot = blockEntity.getStack(i)
            if (!slot.isEmpty && ItemStack.areItemsAndComponentsEqual(slot, itemToMatch)) {
                if (slot.count < slot.maxCount) return true
            }
        }
        return false
    }

    /**
     * Checks if a container has at least one empty or partially-filled slot.
     * Delegates to platform-specific ContainerHelper when available.
     */
    private fun containerHasSpace(world: World, pos: BlockPos): Boolean {
        val helper = ContainerHelperRegistry.instance
        if (helper != null) {
            return helper.hasSpace(world, pos)
        }

        // Fallback: vanilla Inventory only
        val blockEntity = world.getBlockEntity(pos) as? Inventory ?: return false
        for (i in 0 until blockEntity.size()) {
            if (blockEntity.getStack(i).isEmpty) return true
        }
        return false
    }
}

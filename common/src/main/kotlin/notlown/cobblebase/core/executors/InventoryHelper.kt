package notlown.cobblebase.core.executors

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
     * 1. Nearest container that already contains the same item (auto-sort)
     * 2. If no match, nearest container with free space
     *
     * Sorted by distance to pokemonPos (not origin) so each Mon goes to its own chest.
     */
    fun findBestContainer(world: World, pokemonPos: BlockPos, radius: Int, items: List<ItemStack>): BlockPos? {
        val containers = findAllContainers(world, pokemonPos, radius)
        if (containers.isEmpty()) return null

        // Phase 1: Find nearest container that already has this item type
        val matchingItem = items.firstOrNull { !it.isEmpty }
        if (matchingItem != null) {
            val matchingContainer = containers
                .filter { pos -> containerHasItem(world, pos, matchingItem) }
                .minByOrNull { it.getSquaredDistance(pokemonPos) }

            if (matchingContainer != null) return matchingContainer
        }

        // Phase 2: Nearest container with free space
        return containers
            .filter { pos -> containerHasSpace(world, pos) }
            .minByOrNull { it.getSquaredDistance(pokemonPos) }
    }

    /**
     * Legacy method - finds nearest container by distance only.
     */
    fun findNearestContainer(world: World, origin: BlockPos, radius: Int): BlockPos? {
        return findAllContainers(world, origin, radius)
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
            val cleanStack = stack.copy()
            ItemOriginHelper.removeTag(cleanStack) // Strip origin tag so items stack normally
            val remaining = insertStack(inventory, cleanStack)
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
            val taggedStack = stack.copy()
            if (pastureOrigin != null) {
                ItemOriginHelper.tagItem(taggedStack, pastureOrigin)
            }
            val entity = ItemEntity(
                world,
                pos.x + 0.5,
                pos.y + 0.25, // slight offset above ground, not a full block
                pos.z + 0.5,
                taggedStack
            )
            entity.setVelocity(0.0, 0.0, 0.0) // no spread — items stay where dropped
            entity.setPickupDelay(20) // 1 second before pickup
            world.spawnEntity(entity)
        }
    }

    private fun findAllContainers(world: World, origin: BlockPos, radius: Int): List<BlockPos> {
        val helper = ContainerHelperRegistry.instance
        val result = mutableListOf<BlockPos>()
        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = origin.add(x, y, z)
                    if (helper != null) {
                        // Platform-specific detection (vanilla Inventory + Fabric Transfer API / NeoForge Capabilities)
                        if (helper.isContainer(world, pos)) {
                            result.add(pos.toImmutable())
                        }
                    } else {
                        // Fallback: vanilla Inventory only
                        val blockEntity = world.getBlockEntity(pos)
                        if (blockEntity is Inventory && blockEntity !is PokemonPastureBlockEntity) {
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

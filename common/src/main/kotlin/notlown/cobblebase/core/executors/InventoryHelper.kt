package notlown.cobblebase.core.executors

import net.minecraft.block.BarrelBlock
import net.minecraft.block.ChestBlock
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.entity.BarrelBlockEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Shared helper for finding and inserting items into nearby containers (chests, barrels).
 */
object InventoryHelper {

    /**
     * Finds the nearest chest or barrel within the given radius of origin.
     */
    fun findNearestContainer(world: World, origin: BlockPos, radius: Int): BlockPos? {
        var best: BlockPos? = null
        var bestDist = Double.MAX_VALUE

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = origin.add(x, y, z)
                    val block = world.getBlockState(pos).block
                    if (block is ChestBlock || block is BarrelBlock) {
                        val dist = pos.getSquaredDistance(origin)
                        if (dist < bestDist) {
                            bestDist = dist
                            best = pos.toImmutable()
                        }
                    }
                }
            }
        }
        return best
    }

    /**
     * Inserts a list of item stacks into the container at the given position.
     * Leftover items that don't fit are silently discarded (could be improved to drop them).
     */
    fun insertItems(world: World, containerPos: BlockPos, items: List<ItemStack>) {
        val blockEntity = world.getBlockEntity(containerPos)
        val inventory: Inventory = when (blockEntity) {
            is Inventory -> blockEntity
            else -> return
        }

        for (stack in items) {
            if (stack.isEmpty) continue
            insertStack(inventory, stack.copy())
        }
        blockEntity.markDirty()
    }

    private fun insertStack(inventory: Inventory, stack: ItemStack) {
        // First try to merge with existing stacks
        for (i in 0 until inventory.size()) {
            val slot = inventory.getStack(i)
            if (!slot.isEmpty && ItemStack.areItemsAndComponentsEqual(slot, stack)) {
                val space = slot.maxCount - slot.count
                val transfer = minOf(space, stack.count)
                if (transfer > 0) {
                    slot.increment(transfer)
                    stack.decrement(transfer)
                    if (stack.isEmpty) return
                }
            }
        }
        // Then try empty slots
        for (i in 0 until inventory.size()) {
            if (inventory.getStack(i).isEmpty) {
                inventory.setStack(i, stack.copy())
                stack.count = 0
                return
            }
        }
    }
}

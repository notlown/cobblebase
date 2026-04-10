package notlown.cobblebase.neoforge

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.items.IItemHandler
import notlown.cobblebase.core.ContainerHelper
import notlown.cobblebase.core.ItemOriginHelper

/**
 * NeoForge container helper using the Capabilities system (IItemHandler).
 *
 * Detection order:
 *   1. Exclude PokemonPastureBlockEntity
 *   2. Check vanilla Inventory interface
 *   3. Check NeoForge IItemHandler capability
 *
 * Insertion order:
 *   1. Prefer IItemHandler when available (handles both vanilla and modded)
 *   2. Fall back to vanilla Inventory for edge cases
 */
class NeoForgeContainerHelper : ContainerHelper {

    override fun isContainer(world: World, pos: BlockPos): Boolean {
        val blockEntity = world.getBlockEntity(pos) ?: return false
        if (blockEntity is PokemonPastureBlockEntity) return false

        // Vanilla storage containers (chests, barrels, hoppers, dispensers, shulker boxes, furnaces).
        // Excludes non-storage blocks that happen to implement Inventory (waystones, modded
        // crafting tables, etc.) — those don't extend LockableContainerBlockEntity.
        if (blockEntity is net.minecraft.block.entity.LockableContainerBlockEntity) return true

        // NeoForge IItemHandler capability (modded storage containers)
        val handler = world.getCapability(Capabilities.ItemHandler.BLOCK, pos, null)
        return handler != null
    }

    override fun insertItems(world: World, pos: BlockPos, items: List<ItemStack>): List<ItemStack> {
        // Try NeoForge capability first (covers both vanilla wrappers and modded)
        val handler = world.getCapability(Capabilities.ItemHandler.BLOCK, pos, null)
        if (handler != null) {
            return insertIntoHandler(handler, items, world, pos)
        }

        // Fallback to vanilla Inventory
        val blockEntity = world.getBlockEntity(pos)
        if (blockEntity is Inventory) {
            return insertIntoVanillaInventory(blockEntity, items)
        }

        return items
    }

    override fun hasSpace(world: World, pos: BlockPos): Boolean {
        val handler = world.getCapability(Capabilities.ItemHandler.BLOCK, pos, null)
        if (handler != null) {
            for (i in 0 until handler.slots) {
                val slot = handler.getStackInSlot(i)
                if (slot.isEmpty || slot.count < handler.getSlotLimit(i).coerceAtMost(slot.maxCount)) {
                    return true
                }
            }
            return false
        }

        // Vanilla fallback
        val blockEntity = world.getBlockEntity(pos) as? Inventory ?: return false
        for (i in 0 until blockEntity.size()) {
            if (blockEntity.getStack(i).isEmpty) return true
        }
        return false
    }

    override fun hasItem(world: World, pos: BlockPos, itemToMatch: ItemStack): Boolean {
        val handler = world.getCapability(Capabilities.ItemHandler.BLOCK, pos, null)
        if (handler != null) {
            for (i in 0 until handler.slots) {
                val slot = handler.getStackInSlot(i)
                if (!slot.isEmpty && ItemStack.areItemsAndComponentsEqual(slot, itemToMatch)) {
                    if (slot.count < handler.getSlotLimit(i).coerceAtMost(slot.maxCount)) return true
                }
            }
            return false
        }

        // Vanilla fallback
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
     * Inserts items via NeoForge IItemHandler. Uses insertItem with simulate=false.
     */
    private fun insertIntoHandler(handler: IItemHandler, items: List<ItemStack>, world: World, pos: BlockPos): List<ItemStack> {
        val leftovers = mutableListOf<ItemStack>()
        for (stack in items) {
            if (stack.isEmpty) continue
            val cleanStack = stack.copy()
            // Origin tags now live on entities, not stacks — no cleanup needed

            var remaining = cleanStack
            // Try each slot
            for (i in 0 until handler.slots) {
                remaining = handler.insertItem(i, remaining, false)
                if (remaining.isEmpty) break
            }
            if (!remaining.isEmpty) {
                leftovers.add(remaining)
            }
        }
        world.getBlockEntity(pos)?.markDirty()
        return leftovers
    }

    /**
     * Fallback: inserts into vanilla Inventory directly.
     */
    private fun insertIntoVanillaInventory(inventory: Inventory, items: List<ItemStack>): List<ItemStack> {
        val leftovers = mutableListOf<ItemStack>()
        for (stack in items) {
            if (stack.isEmpty) continue
            val cleanStack = stack.copy()
            // Origin tags now live on entities, not stacks — no cleanup needed
            val remaining = insertStack(inventory, cleanStack)
            if (!remaining.isEmpty) {
                leftovers.add(remaining)
            }
        }
        (inventory as? net.minecraft.block.entity.BlockEntity)?.markDirty()
        return leftovers
    }

    private fun insertStack(inventory: Inventory, stack: ItemStack): ItemStack {
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
        for (i in 0 until inventory.size()) {
            if (inventory.getStack(i).isEmpty) {
                inventory.setStack(i, stack.copy())
                return ItemStack.EMPTY
            }
        }
        return stack
    }
}

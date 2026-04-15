package notlown.cobblebase.fabric

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.ContainerHelper
import notlown.cobblebase.core.ItemOriginHelper

/**
 * Fabric container helper using the Transfer API.
 *
 * Detection order:
 *   1. Exclude PokemonPastureBlockEntity (CobBreeding adds Inventory to it)
 *   2. Check vanilla Inventory interface
 *   3. Check Fabric Transfer API via ItemStorage.SIDED (Sophisticated Storage, etc.)
 *
 * Insertion order:
 *   1. Prefer vanilla Inventory path (direct slot access, proven logic)
 *   2. Fall back to Transfer API for modded containers
 */
@Suppress("UnstableApiUsage")
class FabricContainerHelper : ContainerHelper {

    override fun isContainer(world: World, pos: BlockPos): Boolean {
        val blockEntity = world.getBlockEntity(pos) ?: return false
        if (blockEntity is PokemonPastureBlockEntity) return false

        // Vanilla storage containers (chests, barrels, hoppers, dispensers, shulker boxes, furnaces).
        // Excludes non-storage blocks that happen to implement Inventory (waystones, modded
        // crafting tables, etc.) — those don't extend LockableContainerBlockEntity.
        if (blockEntity is net.minecraft.block.entity.LockableContainerBlockEntity) return true

        // Fabric Transfer API (Sophisticated Storage, modded containers that explicitly
        // expose item storage). Filter out small inventories like crafting tables that
        // expose Transfer API but only have a few slots.
        val storage = ItemStorage.SIDED.find(world, pos, null) ?: return false
        var slotCount = 0L
        for (view in storage) { slotCount++ }
        return slotCount >= 9
    }

    override fun insertItems(world: World, pos: BlockPos, items: List<ItemStack>): List<ItemStack> {
        val blockEntity = world.getBlockEntity(pos)

        // Prefer vanilla Inventory path when available
        if (blockEntity is Inventory) {
            return insertIntoVanillaInventory(blockEntity, items)
        }

        // Fabric Transfer API path
        val storage = ItemStorage.SIDED.find(world, pos, null) ?: return items
        val leftovers = mutableListOf<ItemStack>()

        Transaction.openOuter().use { transaction ->
            for (stack in items) {
                if (stack.isEmpty) continue
                val cleanStack = stack.copy()
                // Origin tags now live on entities, not stacks — no cleanup needed
                val variant = ItemVariant.of(cleanStack)
                val inserted = storage.insert(variant, cleanStack.count.toLong(), transaction)
                val remaining = cleanStack.count - inserted.toInt()
                if (remaining > 0) {
                    leftovers.add(cleanStack.copyWithCount(remaining))
                }
            }
            transaction.commit()
        }

        blockEntity?.markDirty()
        return leftovers
    }

    override fun hasSpace(world: World, pos: BlockPos): Boolean {
        val blockEntity = world.getBlockEntity(pos)

        // Vanilla path
        if (blockEntity is Inventory) {
            for (i in 0 until blockEntity.size()) {
                val slot = blockEntity.getStack(i)
                if (slot.isEmpty || slot.count < slot.maxCount) return true
            }
            return false
        }

        // Transfer API path: try inserting 1 of a common item in a simulated transaction
        val storage = ItemStorage.SIDED.find(world, pos, null) ?: return false
        // Check all resource views for available capacity
        for (view in storage) {
            if (view.isResourceBlank || view.amount < view.capacity) return true
        }
        return false
    }

    override fun hasItem(world: World, pos: BlockPos, itemToMatch: ItemStack): Boolean {
        val blockEntity = world.getBlockEntity(pos)

        // Vanilla path
        if (blockEntity is Inventory) {
            for (i in 0 until blockEntity.size()) {
                val slot = blockEntity.getStack(i)
                if (!slot.isEmpty && ItemStack.areItemsAndComponentsEqual(slot, itemToMatch)) {
                    if (slot.count < slot.maxCount) return true
                }
            }
            return false
        }

        // Transfer API path
        val storage = ItemStorage.SIDED.find(world, pos, null) ?: return false
        val targetVariant = ItemVariant.of(itemToMatch)
        for (view in storage) {
            if (!view.isResourceBlank && view.resource == targetVariant && view.amount < view.capacity) {
                return true
            }
        }
        return false
    }

    override fun extractItem(world: World, pos: BlockPos, itemToExtract: ItemStack, maxCount: Int): ItemStack {
        val blockEntity = world.getBlockEntity(pos)

        // Vanilla path
        if (blockEntity is Inventory) {
            var remaining = maxCount
            for (i in 0 until blockEntity.size()) {
                val slot = blockEntity.getStack(i)
                if (slot.isEmpty || slot.item != itemToExtract.item) continue
                val take = minOf(remaining, slot.count)
                slot.decrement(take)
                remaining -= take
                if (remaining <= 0) break
            }
            (blockEntity as? net.minecraft.block.entity.BlockEntity)?.markDirty()
            val taken = maxCount - remaining
            return if (taken > 0) ItemStack(itemToExtract.item, taken) else ItemStack.EMPTY
        }

        // Fabric Transfer API path
        val storage = ItemStorage.SIDED.find(world, pos, null) ?: return ItemStack.EMPTY
        val variant = ItemVariant.of(itemToExtract)
        Transaction.openOuter().use { transaction ->
            val extracted = storage.extract(variant, maxCount.toLong(), transaction)
            if (extracted > 0) {
                transaction.commit()
                blockEntity?.markDirty()
                return ItemStack(itemToExtract.item, extracted.toInt())
            }
        }
        return ItemStack.EMPTY
    }

    /**
     * Inserts items into a vanilla Inventory. Mirrors the logic from InventoryHelper.insertStack
     * but strips origin tags first.
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

    /**
     * Inserts a single stack into an Inventory.
     * First merges with existing matching stacks, then fills empty slots.
     */
    private fun insertStack(inventory: Inventory, stack: ItemStack): ItemStack {
        // Merge with existing stacks of the same type
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
        // Fill first available empty slot
        for (i in 0 until inventory.size()) {
            if (inventory.getStack(i).isEmpty) {
                inventory.setStack(i, stack.copy())
                return ItemStack.EMPTY
            }
        }
        return stack
    }
}

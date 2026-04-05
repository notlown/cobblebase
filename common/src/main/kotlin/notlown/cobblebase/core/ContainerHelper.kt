package notlown.cobblebase.core

import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Platform-specific container detection and item insertion.
 * Fabric implementation uses Transfer API (supports Sophisticated Storage, etc.),
 * NeoForge implementation uses Capabilities (IItemHandler).
 *
 * Common code falls back to vanilla Inventory checks if no platform helper is registered.
 */
interface ContainerHelper {
    /** Returns true if the block at [pos] is a storage container (vanilla or modded). */
    fun isContainer(world: World, pos: BlockPos): Boolean

    /** Inserts [items] into the container at [pos]. Returns leftover items that didn't fit. */
    fun insertItems(world: World, pos: BlockPos, items: List<ItemStack>): List<ItemStack>

    /** Returns true if the container at [pos] has at least one empty/partially-filled slot. */
    fun hasSpace(world: World, pos: BlockPos): Boolean

    /** Returns true if the container at [pos] already holds an item matching [itemToMatch] with room for more. */
    fun hasItem(world: World, pos: BlockPos, itemToMatch: ItemStack): Boolean
}

object ContainerHelperRegistry {
    var instance: ContainerHelper? = null
}

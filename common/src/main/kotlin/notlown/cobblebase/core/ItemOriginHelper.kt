package notlown.cobblebase.core

import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.math.BlockPos

/**
 * Tags ItemStacks with the Pasture Block origin that produced them.
 * Used by the Gatherer to only pick up items from its own Pasture Block,
 * preventing item stealing between bases in multiplayer.
 *
 * Untagged items (player drops, mob drops) are treated as "ownerless"
 * and can be picked up by any Gatherer.
 */
object ItemOriginHelper {

    private const val TAG_KEY = "cobblebase_origin"

    /**
     * Tags an ItemStack with the pasture block position that produced it.
     */
    fun tagItem(stack: ItemStack, pastureOrigin: BlockPos) {
        if (stack.isEmpty) return
        val existing = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
        val nbt = existing.copyNbt()
        nbt.putLong(TAG_KEY, pastureOrigin.asLong())
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt))
    }

    /**
     * Tags all ItemStacks in a list with the pasture block position.
     */
    fun tagItems(stacks: List<ItemStack>, pastureOrigin: BlockPos) {
        for (stack in stacks) {
            tagItem(stack, pastureOrigin)
        }
    }

    /**
     * Reads the pasture origin from an ItemStack's custom data.
     * Returns null if no origin tag is present.
     */
    fun getOrigin(stack: ItemStack): BlockPos? {
        val customData = stack.get(DataComponentTypes.CUSTOM_DATA) ?: return null
        val nbt = customData.copyNbt()
        if (!nbt.contains(TAG_KEY)) return null
        return BlockPos.fromLong(nbt.getLong(TAG_KEY))
    }

    /**
     * Removes the origin tag from an ItemStack.
     * Call this when items are deposited into containers so they stack normally
     * with vanilla items of the same type.
     */
    fun removeTag(stack: ItemStack) {
        if (stack.isEmpty) return
        val customData = stack.get(DataComponentTypes.CUSTOM_DATA) ?: return
        val nbt = customData.copyNbt()
        if (nbt.contains(TAG_KEY)) {
            nbt.remove(TAG_KEY)
            if (nbt.isEmpty) {
                stack.remove(DataComponentTypes.CUSTOM_DATA)
            } else {
                stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt))
            }
        }
    }

    /**
     * Checks if an item belongs to the given pasture block.
     * Untagged items (player drops, mob drops) return true — any Gatherer can pick them up.
     * Tagged items only return true if the origin matches.
     */
    fun belongsTo(stack: ItemStack, pastureOrigin: BlockPos): Boolean {
        val origin = getOrigin(stack) ?: return true // untagged = pick up by any Gatherer
        return origin == pastureOrigin
    }
}

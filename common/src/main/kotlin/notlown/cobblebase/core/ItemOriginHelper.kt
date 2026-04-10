package notlown.cobblebase.core

import net.minecraft.entity.ItemEntity
import net.minecraft.util.math.BlockPos

/**
 * Tags ItemEntities (NOT ItemStacks) with the Pasture Block origin that produced them.
 * Used by the Gatherer to only pick up items from its own Pasture Block,
 * preventing item stealing between bases in multiplayer.
 *
 * Tagging lives on the entity via command tags so the underlying ItemStack
 * stays clean — players who pick items up directly get normal, stackable items.
 * Only the Gatherer needs to inspect the entity tag before pickup.
 *
 * Untagged item entities (player drops, mob drops) are treated as "ownerless"
 * and can be picked up by any Gatherer.
 */
object ItemOriginHelper {

    private const val TAG_PREFIX = "cobblebase_origin_"

    /**
     * Tags an ItemEntity with the pasture block position that produced it.
     */
    fun tagEntity(entity: ItemEntity, pastureOrigin: BlockPos) {
        entity.addCommandTag("$TAG_PREFIX${pastureOrigin.asLong()}")
    }

    /**
     * Reads the pasture origin from an ItemEntity's command tags.
     * Returns null if no origin tag is present.
     */
    fun getOrigin(entity: ItemEntity): BlockPos? {
        for (tag in entity.commandTags) {
            if (tag.startsWith(TAG_PREFIX)) {
                val long = tag.removePrefix(TAG_PREFIX).toLongOrNull() ?: continue
                return BlockPos.fromLong(long)
            }
        }
        return null
    }

    /**
     * Checks if an item entity belongs to the given pasture block.
     * Untagged entities (player drops, mob drops) return true — any Gatherer can pick them up.
     * Tagged entities only return true if the origin matches.
     */
    fun belongsTo(entity: ItemEntity, pastureOrigin: BlockPos): Boolean {
        val origin = getOrigin(entity) ?: return true // untagged = pick up by any Gatherer
        return origin == pastureOrigin
    }
}

package notlown.cobblebase.core

/**
 * Maps block IDs to the supplier roles that can produce them, used by the Builder-Helper
 * Coordinator to decide which helper Pokemon can supply which needed block.
 *
 * Three role categories:
 *   - MINING: stones, ores, dirt-family, sands, gravels — any direct-mineable raw block
 *   - HARVESTER: crops, berries, plants
 *   - PRODUCER(species): species-specific items (e.g. white_wool ← wooloo/dubwool/mareep)
 *
 * Crafted blocks (planks, doors, glass, smooth_stone, ...) are NOT in this map because no
 * helper can directly produce them in Cobblemon. The user has to provide them, or chain
 * a Workshop-Craftsman to craft from raw materials a helper supplies.
 */
object BlockSupplyMap {

    sealed class Role {
        object MINING : Role()
        object HARVESTER : Role()
        data class PRODUCER(val species: String) : Role()
    }

    /** Quick lookup: which roles can supply this block? Empty = no helper can do it. */
    fun rolesFor(blockId: String): List<Role> {
        val out = mutableListOf<Role>()
        if (blockId in miningBlocks) out.add(Role.MINING)
        if (blockId in harvestableBlocks) out.add(Role.HARVESTER)
        producerSpeciesFor(blockId).forEach { out.add(Role.PRODUCER(it)) }
        return out
    }

    /**
     * Returns the list of Cobblemon species whose ProducerExecutor entry produces this block
     * as an item. Computed once on first call from notlown.cobblebase.core.executors.ProducerExecutor.
     */
    private val producerCache by lazy { buildProducerCache() }

    private fun producerSpeciesFor(blockId: String): List<String> {
        return producerCache[blockId] ?: emptyList()
    }

    private fun buildProducerCache(): Map<String, List<String>> {
        val cache = mutableMapOf<String, MutableList<String>>()
        // We can't access ProducerExecutor's private map directly — use the public accessors.
        for (species in notlown.cobblebase.core.executors.ProducerExecutor::class.java.declaredFields
            .filter { it.name == "produceMap" }
            .mapNotNull { field ->
                try {
                    field.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    field.get(notlown.cobblebase.core.executors.ProducerExecutor) as? Map<String, *>
                } catch (_: Exception) { null }
            }.flatMap { it.entries }) {
            val itemEntry = species.value ?: continue
            val itemId = try {
                val field = itemEntry.javaClass.getDeclaredField("itemId")
                field.isAccessible = true
                field.get(itemEntry) as? String
            } catch (_: Exception) { null } ?: continue
            // Normalize to "minecraft:X" or "namespace:X"
            val normalized = if (itemId.contains(":")) itemId else "minecraft:$itemId"
            cache.getOrPut(normalized) { mutableListOf() }.add(species.key)
        }
        return cache
    }

    // Hardcoded sets — items helpers can DIRECTLY produce (no crafting required).
    // Planks, slabs, stairs, glass, smooth_stone etc. are crafted from raws → not listed here.
    // Player or a Workshop-Craftsman chain handles those.

    private val miningBlocks: Set<String> = setOf(
        // Stone family
        "minecraft:stone", "minecraft:cobblestone", "minecraft:granite", "minecraft:diorite",
        "minecraft:andesite", "minecraft:deepslate", "minecraft:cobbled_deepslate", "minecraft:tuff",
        "minecraft:calcite", "minecraft:dripstone_block", "minecraft:basalt", "minecraft:blackstone",
        "minecraft:netherrack", "minecraft:end_stone",
        // Sand/gravel/dirt
        "minecraft:sand", "minecraft:red_sand", "minecraft:gravel", "minecraft:dirt",
        "minecraft:coarse_dirt", "minecraft:rooted_dirt", "minecraft:grass_block", "minecraft:podzol",
        "minecraft:mycelium", "minecraft:clay",
        // Ores (raw drops)
        "minecraft:coal", "minecraft:raw_iron", "minecraft:raw_copper", "minecraft:raw_gold",
        "minecraft:diamond", "minecraft:emerald", "minecraft:redstone", "minecraft:lapis_lazuli",
        "minecraft:quartz", "minecraft:amethyst_shard", "minecraft:ancient_debris", "minecraft:netherite_scrap"
    )

    private val harvestableBlocks: Set<String> = setOf(
        // Vanilla crops + plants
        "minecraft:wheat", "minecraft:carrot", "minecraft:potato", "minecraft:beetroot",
        "minecraft:melon", "minecraft:melon_slice", "minecraft:pumpkin", "minecraft:sugar_cane",
        "minecraft:kelp", "minecraft:bamboo", "minecraft:sweet_berries", "minecraft:glow_berries",
        "minecraft:cocoa_beans", "minecraft:nether_wart", "minecraft:cactus",
        // Mushrooms
        "minecraft:brown_mushroom", "minecraft:red_mushroom",
        // Wood/leaves (apricorns are Cobblemon; logs are tree breaks not really harvester)
        // Cobblemon apricorns are added by datapack so we skip them in the static map
    )
}

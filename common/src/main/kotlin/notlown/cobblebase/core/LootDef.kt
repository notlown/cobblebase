package notlown.cobblebase.core

/**
 * Editable, serializable representation of a loot table entry.
 * Mirrors the relevant fields of a vanilla `minecraft:item` loot entry,
 * collapsed to the bits the admin GUI can edit.
 *
 * - [itemId] is a registry id like `cobblemon:poke_ball` or `minecraft:diamond`.
 * - [weight] is the random selection weight within its pool.
 * - [minCount] / [maxCount] cap the stack size produced by `set_count`.
 *
 * One [LootEntry] always corresponds to "one possible item drop". Loot tables
 * with multiple pools or non-item entries collapse into a single flat list of
 * entries when imported, to keep the editor simple.
 */
data class LootEntry(
    val itemId: String,
    val weight: Int,
    val minCount: Int,
    val maxCount: Int
)

/**
 * A loot table that can be evaluated by [LootHelper.generateLoot].
 *
 * - [id] is the registry id (e.g. `cobblebase:finder_bal_common`).
 * - [rolls] is how many entries are picked per generation (weighted random).
 * - [entries] is the flat list of possible drops.
 *
 * This intentionally drops the vanilla pool/conditions/functions structure;
 * everything we need for our jobs is "pick N items from a weighted list".
 */
data class LootTableDef(
    val id: String,
    val rolls: Int,
    val entries: List<LootEntry>
)

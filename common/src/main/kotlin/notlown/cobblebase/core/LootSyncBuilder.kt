package notlown.cobblebase.core

/**
 * Builds the snapshot the admin GUI consumes:
 *
 *  - For each known loot table, the *editable* form is the override (if any)
 *    merged with the bundled default — every default item is guaranteed to be
 *    present so the GUI can show an On/Off toggle even when no override exists
 *    yet. Override-only state for default entries (custom weight/count/disabled)
 *    wins over the default; entries from the override that are not in the
 *    default are appended at the end and shown as "custom" (deletable).
 *
 *  - The `defaultItemIds` map tells the GUI which item ids in each editable
 *    table came from the bundled default, so it can show the right action
 *    button (toggle vs delete).
 */
object LootSyncBuilder {

    data class Snapshot(
        val tables: List<LootTableDef>,
        val defaultItemIds: Map<String, List<String>>
    )

    fun buildSnapshot(): Snapshot {
        val out = mutableListOf<LootTableDef>()
        val defaults = mutableMapOf<String, List<String>>()

        for (id in CobblebaseLootRegistry.getAllIds()) {
            val def = CobblebaseLootRegistry.get(id) ?: continue
            val override = LootOverrides.getOverride(id)
            val merged = mergeDefaultsWithOverride(def, override)
            out.add(merged)
            defaults[id] = def.entries.map { it.itemId }
        }
        return Snapshot(out, defaults)
    }

    /**
     * Returns a [LootTableDef] that contains every default entry first (in
     * default order), each carrying any override-set values, followed by any
     * custom entries the override added on top.
     */
    private fun mergeDefaultsWithOverride(def: LootTableDef, override: LootTableDef?): LootTableDef {
        if (override == null) return def

        val out = mutableListOf<LootEntry>()
        val claimed = HashSet<Int>() // indices in override.entries we've already used

        for (defaultEntry in def.entries) {
            // Find the first override entry with a matching itemId whose index isn't claimed.
            var matchedIdx = -1
            for ((i, oe) in override.entries.withIndex()) {
                if (i in claimed) continue
                if (oe.itemId == defaultEntry.itemId) { matchedIdx = i; break }
            }
            if (matchedIdx >= 0) {
                claimed.add(matchedIdx)
                out.add(override.entries[matchedIdx])
            } else {
                out.add(defaultEntry)
            }
        }
        // Append any override entries not matched to a default (custom items)
        for ((i, oe) in override.entries.withIndex()) {
            if (i !in claimed) out.add(oe)
        }
        return LootTableDef(def.id, override.rolls, out)
    }
}

package notlown.cobblebase.core

/**
 * Client-side cache of the loot table snapshot most recently received from
 * the server (see `AdminLootSyncS2CPacket`). The admin Loot tab reads from
 * this cache.
 */
object AdminLootDataCache {

    var tables: List<LootTableDef> = emptyList()
        private set

    var overriddenIds: Set<String> = emptySet()
        private set

    /** For each table id, the set of item ids that exist in the bundled default. */
    var defaultItemIds: Map<String, Set<String>> = emptyMap()
        private set

    fun update(
        newTables: List<LootTableDef>,
        newOverridden: Set<String>,
        newDefaults: Map<String, List<String>> = emptyMap()
    ) {
        tables = newTables.sortedBy { it.id }
        overriddenIds = newOverridden
        defaultItemIds = newDefaults.mapValues { it.value.toSet() }
    }

    fun get(id: String): LootTableDef? = tables.firstOrNull { it.id == id }

    fun isOverridden(id: String): Boolean = id in overriddenIds

    fun isDefaultEntry(tableId: String, itemId: String): Boolean =
        defaultItemIds[tableId]?.contains(itemId) == true
}

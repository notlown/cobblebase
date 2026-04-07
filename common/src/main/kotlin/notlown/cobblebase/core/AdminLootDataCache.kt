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

    fun update(newTables: List<LootTableDef>, newOverridden: Set<String>) {
        tables = newTables.sortedBy { it.id }
        overriddenIds = newOverridden
    }

    fun get(id: String): LootTableDef? = tables.firstOrNull { it.id == id }

    fun isOverridden(id: String): Boolean = id in overriddenIds
}

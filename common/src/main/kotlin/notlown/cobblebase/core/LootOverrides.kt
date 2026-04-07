package notlown.cobblebase.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.WorldSavePath
import java.io.File

/**
 * Per-loot-table override store. When an override exists for a given loot
 * table id, [LootHelper.generateLoot] uses it instead of the bundled default
 * from [CobblebaseLootRegistry] (and instead of the vanilla loot table from
 * the datapack registry).
 *
 * Persisted to `<world>/cobblebase_loot_overrides.json`.
 */
object LootOverrides {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val overrides = mutableMapOf<String, LootTableDef>()

    fun getOverride(id: String): LootTableDef? = overrides[normalize(id)]
    fun hasOverride(id: String): Boolean = overrides.containsKey(normalize(id))
    fun getAll(): Map<String, LootTableDef> = overrides.toMap()

    fun setOverride(def: LootTableDef) {
        overrides[normalize(def.id)] = def
    }

    fun removeOverride(id: String) {
        overrides.remove(normalize(id))
    }

    private fun normalize(id: String): String =
        if (id.contains(":")) id else "cobblebase:$id"

    private fun getSaveFile(world: ServerWorld): File {
        val saveDir = world.server.getSavePath(WorldSavePath.ROOT).toFile()
        return File(saveDir, "cobblebase_loot_overrides.json")
    }

    fun save(world: ServerWorld) {
        try {
            val file = getSaveFile(world)
            val data = overrides.mapValues { (_, def) ->
                mapOf(
                    "rolls" to def.rolls,
                    "entries" to def.entries.map { e ->
                        mapOf(
                            "itemId" to e.itemId,
                            "weight" to e.weight,
                            "minCount" to e.minCount,
                            "maxCount" to e.maxCount
                        )
                    }
                )
            }
            file.writeText(gson.toJson(data))
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to save loot overrides: ${e.message}")
        }
    }

    fun load(world: ServerWorld) {
        try {
            val file = getSaveFile(world)
            if (!file.exists()) return
            val type = object : TypeToken<Map<String, Map<String, Any?>>>() {}.type
            val data: Map<String, Map<String, Any?>> = gson.fromJson(file.readText(), type) ?: return
            overrides.clear()
            for ((id, fields) in data) {
                val rolls = (fields["rolls"] as? Double)?.toInt() ?: 1
                @Suppress("UNCHECKED_CAST")
                val rawEntries = fields["entries"] as? List<Map<String, Any?>> ?: emptyList()
                val entries = rawEntries.mapNotNull { e ->
                    val itemId = e["itemId"] as? String ?: return@mapNotNull null
                    val weight = (e["weight"] as? Double)?.toInt() ?: 1
                    val minCount = (e["minCount"] as? Double)?.toInt() ?: 1
                    val maxCount = (e["maxCount"] as? Double)?.toInt() ?: minCount
                    LootEntry(itemId, weight, minCount, maxCount)
                }
                overrides[normalize(id)] = LootTableDef(normalize(id), rolls, entries)
            }
            Cobblebase.LOGGER.info("[Cobblebase] Loaded ${overrides.size} loot overrides")
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to load loot overrides: ${e.message}")
        }
    }
}

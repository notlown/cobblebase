package notlown.cobblebase.core

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader

/**
 * Loads all bundled loot tables under `data/cobblebase/loot_table/` into
 * an in-memory map of [LootTableDef] instances.
 *
 * The registry is the source of truth for "default" loot. Admin overrides
 * (see [LootOverrides]) are layered on top of this at generation time by
 * [LootHelper].
 *
 * Vanilla loot table JSONs use a nested pool/entries/functions schema; we
 * collapse that into a single flat list of [LootEntry]s per table:
 *  - We sum the rolls across pools (since pools are all rolled).
 *  - Non-`minecraft:item` entries are skipped (we don't yet support nested
 *    loot tables, tags, or empty entries from the GUI).
 *  - The `set_count` function is honored when present, defaulting to 1..1.
 *
 * Anything that does not fit cleanly is silently dropped — the original JSON
 * still ships in the jar, so vanilla fallback in [LootHelper.generateLoot]
 * keeps working for tables we couldn't import.
 */
object CobblebaseLootRegistry {
    private val gson = Gson()
    private val tables = mutableMapOf<String, LootTableDef>()

    fun init() {
        loadFromResources()
        Cobblebase.LOGGER.debug("[Cobblebase] CobblebaseLootRegistry: ${tables.size} loot tables loaded")
    }

    fun get(id: String): LootTableDef? = tables[normalizeId(id)]
    fun getAllIds(): List<String> = tables.keys.sorted()
    fun getAll(): Map<String, LootTableDef> = tables.toMap()

    private fun normalizeId(id: String): String =
        if (id.contains(":")) id else "cobblebase:$id"

    private fun loadFromResources() {
        val indexPath = "/data/cobblebase/loot_table/_index.txt"
        val indexStream = Cobblebase::class.java.getResourceAsStream(indexPath)
        if (indexStream == null) {
            Cobblebase.LOGGER.warn("[Cobblebase] Loot table index not found at $indexPath")
            return
        }
        val fileNames = indexStream.bufferedReader().use { it.readLines() }
            .map { it.trim() }
            .filter { it.endsWith(".json") }

        for (fileName in fileNames) {
            try {
                val resourcePath = "/data/cobblebase/loot_table/$fileName"
                val stream = Cobblebase::class.java.getResourceAsStream(resourcePath) ?: continue
                val parsed = stream.use { s ->
                    JsonParser.parseReader(InputStreamReader(s, Charsets.UTF_8))
                }
                if (!parsed.isJsonObject) continue
                val tableDef = parseTable(fileName.removeSuffix(".json"), parsed.asJsonObject)
                tables[tableDef.id] = tableDef
            } catch (e: Exception) {
                Cobblebase.LOGGER.error("[Cobblebase] Failed to parse loot table $fileName: ${e.message}")
            }
        }
    }

    /**
     * Flattens a vanilla-style loot table JSON object into a [LootTableDef].
     * Only `minecraft:item` entries with optional `set_count` functions are kept.
     */
    private fun parseTable(baseName: String, json: JsonObject): LootTableDef {
        val id = "cobblebase:$baseName"
        val entries = mutableListOf<LootEntry>()
        var rollsSum = 0

        val pools = json.getAsJsonArray("pools") ?: return LootTableDef(id, 1, emptyList())
        for (poolEl in pools) {
            if (!poolEl.isJsonObject) continue
            val pool = poolEl.asJsonObject

            val rolls = when {
                pool.has("rolls") && pool.get("rolls").isJsonPrimitive ->
                    pool.get("rolls").asInt
                pool.has("rolls") && pool.get("rolls").isJsonObject -> {
                    val r = pool.getAsJsonObject("rolls")
                    if (r.has("min") && r.has("max")) (r.get("min").asInt + r.get("max").asInt) / 2 else 1
                }
                else -> 1
            }
            rollsSum += rolls

            val poolEntries = pool.getAsJsonArray("entries") ?: continue
            for (entryEl in poolEntries) {
                if (!entryEl.isJsonObject) continue
                val entry = entryEl.asJsonObject
                val type = entry.get("type")?.asString ?: continue
                if (type != "minecraft:item") continue
                val name = entry.get("name")?.asString ?: continue
                val weight = entry.get("weight")?.asInt ?: 1

                var minCount = 1
                var maxCount = 1
                val functions = entry.getAsJsonArray("functions")
                if (functions != null) {
                    for (fnEl in functions) {
                        if (!fnEl.isJsonObject) continue
                        val fn = fnEl.asJsonObject
                        val fnType = fn.get("function")?.asString
                        if (fnType == "set_count" || fnType == "minecraft:set_count") {
                            val count = fn.get("count")
                            if (count != null && count.isJsonObject) {
                                val c = count.asJsonObject
                                minCount = c.get("min")?.asInt ?: 1
                                maxCount = c.get("max")?.asInt ?: minCount
                            } else if (count != null && count.isJsonPrimitive) {
                                minCount = count.asInt
                                maxCount = minCount
                            }
                        }
                    }
                }
                entries.add(LootEntry(name, weight, minCount, maxCount))
            }
        }

        return LootTableDef(id, rollsSum.coerceAtLeast(1), entries)
    }
}

package notlown.cobblebase.core

import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtSizeTracker
import net.minecraft.server.MinecraftServer
import net.minecraft.structure.StructureTemplate
import net.minecraft.util.Identifier

/**
 * Registry of building templates available to the Builder job.
 *
 * Templates come from two places:
 *  1. Vanilla path — files ending in `.nbt` under `data/<ns>/structures/`. These are
 *     indexed and loaded by Minecraft's own StructureTemplateManager, and we enumerate
 *     them via its `streamTemplates()` method.
 *  2. Non-vanilla path — files ending in `.nbt` under `data/<ns>/structure/` (singular).
 *     Cobblemon ships ~540 structures here (fishing boats, fossils, apricorn trees, etc.)
 *     that the vanilla manager never sees. We scan the resource manager directly, read
 *     the raw NBT, and build [StructureTemplate] instances with `createTemplate()` —
 *     then stash them in a side cache so [resolve] can return them.
 *
 * ID convention mirrors Minecraft's — a file at `data/cobblemon/structure/fossils/birch_tree1.nbt`
 * becomes `cobblemon:fossils/birch_tree1`.
 */
object StructureTemplateRegistry {

    // Volatile reference + concurrent inner map: refresh() is now async, so getAll/resolve
    // can be called from server threads while the background scanner is still populating.
    @Volatile private var cache: Map<Identifier, StructureTemplateRef>? = null
    @Volatile private var refreshInFlight = false

    /** Templates loaded from non-vanilla `structure/` paths — keyed so [resolve] finds them. */
    private val customTemplates = java.util.concurrent.ConcurrentHashMap<Identifier, StructureTemplate>()

    data class StructureTemplateRef(
        val id: Identifier,
        val displayName: String,
        val sizeX: Int,
        val sizeY: Int,
        val sizeZ: Int,
        /**
         * Top-N block types by count for this template. Used by the Builder GUI to render
         * a "what's in this template" preview without sending the full block list to clients.
         * Format: `[block_id to count]`, sorted descending by count. Air is excluded.
         * Capped to [TOP_BLOCKS_LIMIT] entries.
         */
        val topBlocks: List<Pair<String, Int>> = emptyList(),
        /** Total non-air block count (sum of every histogram entry, even ones past the cap). */
        val totalBlockCount: Int = 0
    ) {
        val volume: Int get() = sizeX * sizeY * sizeZ
    }

    private const val TOP_BLOCKS_LIMIT = 8
    @Volatile private var histogramSuccessCount = 0
    @Volatile private var histogramFailureCount = 0

    /**
     * Re-scan server resources for every discoverable structure. Runs the entire scan on a
     * background daemon thread so world load isn't blocked — with 5000+ NBT files this
     * scan previously took 5+ seconds of main-thread time. Builder GUI gets an empty list
     * until the scan finishes (~5-10s after world load).
     */
    fun refresh(server: MinecraftServer) {
        if (refreshInFlight) return
        refreshInFlight = true
        Thread({
            try {
                refreshSync(server)
            } catch (e: Exception) {
                Cobblebase.LOGGER.error("[Cobblebase] StructureTemplateRegistry: refresh thread crashed: ${e.message}")
            } finally {
                refreshInFlight = false
            }
        }, "Cobblebase-template-scan").apply {
            isDaemon = true
            start()
        }
    }

    private fun refreshSync(server: MinecraftServer) {
        val found = mutableMapOf<Identifier, StructureTemplateRef>()
        customTemplates.clear()
        histogramSuccessCount = 0
        histogramFailureCount = 0
        val startNanos = System.nanoTime()

        // (1) Vanilla `structures/` path — use StructureTemplateManager's own discovery.
        var vanillaCount = 0
        try {
            server.structureTemplateManager.streamTemplates().forEach { id ->
                val template = server.structureTemplateManager.getTemplate(id).orElse(null) ?: return@forEach
                val ref = toRef(id, template) ?: return@forEach
                found[id] = ref
                vanillaCount++
            }
        } catch (e: Exception) {
            Cobblebase.LOGGER.warn("[Cobblebase] StructureTemplateRegistry: vanilla scan failed: ${e.message}")
        }

        // (2) Non-vanilla `structure/` path (singular) — Cobblemon and similar.
        var customCount = 0
        try {
            val resources = server.resourceManager.findResources("structure") { id ->
                id.path.endsWith(".nbt") && id.path.startsWith("structure/")
            }
            for ((fullId, resource) in resources) {
                val relativePath = fullId.path.removePrefix("structure/").removeSuffix(".nbt")
                val structureId = try {
                    Identifier.of(fullId.namespace, relativePath)
                } catch (e: Exception) {
                    continue
                }
                if (found.containsKey(structureId)) continue  // vanilla version wins

                val template = try {
                    resource.inputStream.use { stream ->
                        val nbt = NbtIo.readCompressed(stream, NbtSizeTracker.ofUnlimitedBytes())
                        server.structureTemplateManager.createTemplate(nbt)
                    }
                } catch (e: Exception) {
                    Cobblebase.LOGGER.warn("[Cobblebase] Failed to load structure $fullId: ${e.message}")
                    continue
                }

                val ref = toRef(structureId, template) ?: continue
                customTemplates[structureId] = template
                found[structureId] = ref
                customCount++
            }
        } catch (e: Exception) {
            Cobblebase.LOGGER.warn("[Cobblebase] StructureTemplateRegistry: custom-path scan failed: ${e.message}")
        }

        cache = found
        if (found.isEmpty()) {
            Cobblebase.LOGGER.info(
                "[Cobblebase] StructureTemplateRegistry: no .nbt structure files found. " +
                "Install a Pokemon structure datapack (e.g. CobbleTowns, Radical Gyms) " +
                "or place .nbt files under data/<namespace>/structures/ to give Builder Pokemon something to build."
            )
        } else {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
            Cobblebase.LOGGER.info(
                "[Cobblebase] StructureTemplateRegistry: indexed ${found.size} templates " +
                "(vanilla=$vanillaCount, custom=$customCount) in ${elapsedMs}ms — histogram enrichment running in background"
            )
            // Kick off histogram enrichment off the server thread so world load isn't blocked.
            enrichHistogramsAsync(server)
        }
    }

    private fun toRef(id: Identifier, template: StructureTemplate): StructureTemplateRef? {
        val size = template.size
        if (size.x <= 0 || size.y <= 0 || size.z <= 0) return null
        // Vanilla returns 1×1×1 for a missing/empty template — skip those.
        if (size.x == 1 && size.y == 1 && size.z == 1) return null
        // Histogram is computed asynchronously by [enrichHistogramsAsync] after refresh
        // returns. The main thread doesn't pay the per-template block-iteration cost on
        // world load anymore (was responsible for multi-second hitches with large packs).
        return StructureTemplateRef(
            id = id,
            displayName = formatDisplayName(id),
            sizeX = size.x,
            sizeY = size.y,
            sizeZ = size.z,
            topBlocks = emptyList(),
            totalBlockCount = 0
        )
    }

    /**
     * Runs on a background thread after [refresh] returns. Iterates each loaded template's
     * blocks via [TemplateBlockReader], builds the top-N histogram, and atomically swaps
     * the ref's cached entry. The client-side preview just shows "no block preview available"
     * for templates whose histogram hasn't been computed yet.
     */
    private fun enrichHistogramsAsync(server: MinecraftServer) {
        val snapshot = cache?.toMap() ?: return
        Thread({
            try {
                val updated = HashMap<Identifier, StructureTemplateRef>(snapshot.size)
                var ok = 0; var failed = 0
                for ((id, ref) in snapshot) {
                    val template = resolve(server, id)
                    if (template == null) {
                        updated[id] = ref
                        continue
                    }
                    val (top, total) = computeHistogram(template)
                    if (top.isEmpty()) failed++ else ok++
                    updated[id] = ref.copy(topBlocks = top, totalBlockCount = total)
                }
                // Atomic swap. Other threads either see the old or the new map — no torn read.
                cache = updated
                histogramSuccessCount = ok
                histogramFailureCount = failed
                Cobblebase.LOGGER.info(
                    "[Cobblebase] StructureTemplateRegistry: async histogram enrichment done " +
                    "(OK=$ok, failed=$failed)"
                )
            } catch (e: Exception) {
                Cobblebase.LOGGER.warn("[Cobblebase] StructureTemplateRegistry: enrichment thread crashed: ${e.message}")
            }
        }, "Cobblebase-template-histogram").apply {
            isDaemon = true
            start()
        }
    }

    private fun computeHistogram(template: StructureTemplate): Pair<List<Pair<String, Int>>, Int> {
        val histogram = HashMap<String, Int>()
        try {
            val blocks = notlown.cobblebase.core.executors.TemplateBlockReader.getAllBlocks(template)
            for (info in blocks) {
                val block = info.state.block
                if (block == net.minecraft.block.Blocks.AIR ||
                    block == net.minecraft.block.Blocks.CAVE_AIR ||
                    block == net.minecraft.block.Blocks.VOID_AIR) continue
                val blockId = net.minecraft.registry.Registries.BLOCK.getId(block).toString()
                histogram.merge(blockId, 1) { a, b -> a + b }
            }
        } catch (_: Exception) { return emptyList<Pair<String, Int>>() to 0 }
        val total = histogram.values.sum()
        val top = histogram.entries.sortedByDescending { it.value }
            .take(TOP_BLOCKS_LIMIT).map { it.key to it.value }
        return top to total
    }

    /**
     * Lightweight list of all known templates, sorted by namespace then path.
     * Auto-refreshes on first access if the cache is empty.
     */
    fun getAll(server: MinecraftServer): List<StructureTemplateRef> {
        if (cache == null) refresh(server)
        return cache?.values?.sortedWith(
            compareBy({ it.id.namespace }, { it.id.path })
        ) ?: emptyList()
    }

    fun getRef(server: MinecraftServer, id: Identifier): StructureTemplateRef? {
        if (cache == null) refresh(server)
        return cache?.get(id)
    }

    /**
     * Resolve the full [StructureTemplate] for a given ID. Prefers the custom-loaded cache
     * (Cobblemon's `structure/` path) before falling back to the vanilla manager.
     */
    fun resolve(server: MinecraftServer, id: Identifier): StructureTemplate? {
        customTemplates[id]?.let { return it }
        return server.structureTemplateManager.getTemplate(id).orElse(null)
    }

    /** Clear the cache — mainly for testing and `/reload` handlers. */
    fun invalidate() {
        cache = null
        customTemplates.clear()
    }

    private fun formatDisplayName(id: Identifier): String {
        // "cobblebase:small_house" → "Small House"
        // "cobblemon:fossils/birch_tree1" → "Birch Tree1"
        val last = id.path.substringAfterLast('/')
        return last.split('_', '-')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
}

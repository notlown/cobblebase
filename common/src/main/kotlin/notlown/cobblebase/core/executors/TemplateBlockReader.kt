package notlown.cobblebase.core.executors

import net.minecraft.structure.StructureTemplate
import notlown.cobblebase.core.Cobblebase

/**
 * Reflection helper that pulls every [StructureTemplate.StructureBlockInfo] out of a
 * loaded structure template.
 *
 * Vanilla keeps `blockInfoLists` (the per-palette block list) private with no public
 * iterator — the only exposed read is [StructureTemplate.getInfosForBlock] which filters
 * to a single block type. We need *all* blocks for the Builder, so we crack the field
 * open via reflection. The field name matches Yarn mappings used by this mod.
 *
 * Result is cached per template instance to avoid repeated reflection on every tick.
 */
object TemplateBlockReader {

    private val blockInfoListsField by lazy {
        // Try several possible field names — Yarn ("blockInfoLists"), Mojmap-style ("palettes"),
        // and a heuristic scan for the first `List` field. Whichever matches at runtime wins.
        // Without this fallback the histogram preview (and Builder enumeration) silently fails
        // on environments where the field name differs from the compile-time Yarn mapping.
        val candidates = listOf("blockInfoLists", "palettes")
        for (name in candidates) {
            try {
                return@lazy StructureTemplate::class.java.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) { /* try next */ }
        }
        // Heuristic: first declared field of type List
        try {
            val listField = StructureTemplate::class.java.declaredFields.firstOrNull {
                java.util.List::class.java.isAssignableFrom(it.type)
            }
            if (listField != null) {
                Cobblebase.LOGGER.info(
                    "[Cobblebase] TemplateBlockReader: using heuristic field '${listField.name}' " +
                    "for block enumeration (none of the known names matched)."
                )
                listField.isAccessible = true
                return@lazy listField
            }
        } catch (_: Exception) { }
        Cobblebase.LOGGER.error(
            "[Cobblebase] TemplateBlockReader: cannot find any usable field on StructureTemplate. " +
            "Available fields: " +
            StructureTemplate::class.java.declaredFields.joinToString { "${it.type.simpleName} ${it.name}" }
        )
        null
    }

    private val cache = java.util.WeakHashMap<StructureTemplate, List<StructureTemplate.StructureBlockInfo>>()
    @Volatile private var heuristicMethodLogged = false

    fun getAllBlocks(template: StructureTemplate): List<StructureTemplate.StructureBlockInfo> {
        cache[template]?.let { return it }
        val field = blockInfoListsField ?: return emptyList()

        val all = try {
            val palettes = field.get(template) as? List<*> ?: return emptyList()
            if (palettes.isEmpty()) return emptyList()
            val firstPalette = palettes[0] ?: return emptyList()
            // Try known method names — Yarn ("getAll"), Mojmap ("blocks"), and fall back to
            // any zero-arg method on the palette returning a List.
            val methodNames = listOf("getAll", "blocks")
            var methodResult: Any? = null
            for (mn in methodNames) {
                try {
                    methodResult = firstPalette.javaClass.getMethod(mn).invoke(firstPalette)
                    break
                } catch (_: NoSuchMethodException) { /* try next */ }
            }
            if (methodResult == null) {
                val heuristic = firstPalette.javaClass.methods.firstOrNull {
                    it.parameterCount == 0 &&
                        java.util.List::class.java.isAssignableFrom(it.returnType)
                }
                if (heuristic != null) {
                    if (!heuristicMethodLogged) {
                        heuristicMethodLogged = true
                        Cobblebase.LOGGER.info(
                            "[Cobblebase] TemplateBlockReader: using heuristic palette method '${heuristic.name}'."
                        )
                    }
                    methodResult = heuristic.invoke(firstPalette)
                }
            }
            @Suppress("UNCHECKED_CAST")
            methodResult as? List<StructureTemplate.StructureBlockInfo> ?: emptyList()
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] TemplateBlockReader: extraction failed: ${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        }

        cache[template] = all
        return all
    }
}

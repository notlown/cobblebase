package notlown.cobblebase.neoforge.client.gui

import net.neoforged.neoforge.network.PacketDistributor
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import notlown.cobblebase.core.AdminLootDataCache
import notlown.cobblebase.core.LootEntry
import notlown.cobblebase.core.LootTableDef
import notlown.cobblebase.core.net.AdminLootRequestC2SPacket
import notlown.cobblebase.core.net.AdminLootUpdateC2SPacket
import java.util.function.Function

/**
 * Admin GUI "Loot" tab — visually consistent with the Jobs tab.
 *
 * The previous version used native [net.minecraft.client.gui.widget.TextFieldWidget]s
 * for the editable cells, which forced their text to render at the
 * native Minecraft font size and made the rows visibly larger than the
 * Jobs tab. This rewrite drops the TextFieldWidget pool entirely and
 * draws every cell manually with [drawScaled] / [renderField], using
 * exactly the same SCALE/ROW_H constants as AdminJobsPanel. Click,
 * charTyped, and keyPressed are now routed by the panel itself to the
 * currently-active cell, so 7-8 rows fit on screen at once.
 *
 * Sidebar groups loot tables by job (e.g. "Mining") with a rarity tab
 * bar in the right pane (Common / Uncommon / Rare / Ultra Rare). Default
 * items get an On/Off toggle (kept across overrides); custom items get
 * a delete button.
 */
class AdminLootPanel(
    private val x: Int,
    private val y: Int,
    private val w: Int,
    private val h: Int,
    private val textRenderer: TextRenderer
) {
    companion object {
        private const val PADDING = 4
        private const val SIDEBAR_W = 88
        private const val ROW_H = 14
        private const val FOOTER_H = 18
        private const val HEADER_H = 14
        private const val SCALE = 0.7f

        // Job base name → human-readable label. These match the Skills tab.
        private val PRETTY_NAMES = linkedMapOf(
            "mining" to "Mining",
            "finder_bal" to "Collector",
            "finder_evo" to "Alchemist",
            "finder_exp" to "Scholar",
            "finder_food" to "Chef",
            "finder_hea" to "Pharmacist",
            "finder_held" to "Armorer",
            "finder_ore" to "Excavator",
            "finder_see" to "Botanist",
            "finder_smith" to "Smith",
            "finder_stat" to "Trainer",
            "finder_treasure" to "Prospector",
            "finder_bui" to "Architect",
            "honey_collect" to "Honey Collect",
            "dive_treasure" to "Dive Treasure"
        )
        private val RARITY_ORDER = listOf("common", "uncommon", "rare", "ultra_rare")
        private val RARITY_MATCH_ORDER = listOf("ultra_rare", "uncommon", "rare", "common")
        private val RARITY_LABELS = mapOf(
            "common" to "Common",
            "uncommon" to "Uncommon",
            "rare" to "Rare",
            "ultra_rare" to "Ultra Rare"
        )
        private val RARITY_COLORS = mapOf(
            "common" to 0xFFFFFFFF.toInt(),
            "uncommon" to 0xFF55FF55.toInt(),
            "rare" to 0xFF5555FF.toInt(),
            "ultra_rare" to 0xFFFFAA00.toInt()
        )

        // Theme colors copied from AdminJobsPanel for visual consistency.
        private const val FIELD_BG = 0xFF2A2A3E.toInt()
        private const val FIELD_BORDER = 0xFF4A4A6E.toInt()
        private const val FIELD_ACTIVE = 0xFF7A7ABE.toInt()
        private const val SIDEBAR_BG = 0xCC15152A.toInt()
        private const val ROW_HOVER = 0x22FFFFFF
        private const val SEPARATOR = 0xFF3A3A5C.toInt()
        private const val CHECKBOX_ON = 0xFF4CAF50.toInt()
        private const val CHECKBOX_OFF = 0xFFD32F2F.toInt()
        private const val DELETE_BG = 0xFFD32F2F.toInt()
    }

    /** A logical "job" in the sidebar. Holds 1..4 loot table ids. */
    private data class JobGroup(
        val baseName: String,
        val displayName: String,
        /** rarityKey ("common"/"uncommon"/"rare"/"ultra_rare"/"") → table id */
        val rarities: LinkedHashMap<String, String>
    )

    enum class FieldType { NONE, ITEM, WEIGHT, MIN, MAX, ROLLS }

    // -------- state --------
    private val jobs = mutableListOf<JobGroup>()
    private var selectedJob: JobGroup? = null
    private var selectedRarity: String = ""
    private var sidebarScroll = 0
    private var listScroll = 0

    private val editEntries = mutableListOf<LootEntry>()
    private var editRolls = 1
    private var dirty = false

    private var activeFieldRow: Int = -1
    private var activeFieldType: FieldType = FieldType.NONE
    private var fieldText = ""
    private var cursorBlink = 0

    /** Which scrollbar is currently being dragged (null = none). */
    private enum class DragTarget { SIDEBAR, LIST }
    private var dragging: DragTarget? = null

    // Autocomplete state for the active item id field.
    private var suggestions: List<String> = emptyList()
    private var selectedSuggestion = -1
    private var allItemIdsCache: List<String>? = null
    private var allItemTagsCache: List<String>? = null

    private fun allItemIds(): List<String> {
        var c = allItemIdsCache
        if (c == null) {
            c = try {
                Registries.ITEM.ids.map { it.toString() }.sorted()
            } catch (_: Throwable) {
                emptyList()
            }
            allItemIdsCache = c
        }
        return c
    }

    private fun allItemTags(): List<String> {
        var c = allItemTagsCache
        if (c == null) {
            c = try {
                // Registry.streamTags() yields Stream<TagKey<T>> which has an `id` field directly.
                val out = mutableListOf<String>()
                Registries.ITEM.streamTags().forEach { tag ->
                    out.add("#" + tag.id.toString())
                }
                out.sorted()
            } catch (_: Throwable) {
                emptyList()
            }
            allItemTagsCache = c
        }
        return c
    }

    private fun recomputeSuggestions() {
        if (activeFieldType != FieldType.ITEM || fieldText.isEmpty()) {
            suggestions = emptyList()
            selectedSuggestion = -1
            return
        }
        val q = fieldText.lowercase()
        val source = if (q.startsWith("#")) allItemTags() else allItemIds()
        val (prefixMatches, containsMatches) = source.asSequence()
            .filter { it.contains(q) }
            .partition { it.startsWith(q) }
        suggestions = (prefixMatches + containsMatches).take(8).toList()
        selectedSuggestion = if (suggestions.isNotEmpty()) 0 else -1
    }

    var pendingTooltip: List<String> = emptyList()
        private set
    var tooltipX: Int = 0
        private set
    var tooltipY: Int = 0
        private set

    private var saveBtn: ButtonWidget? = null
    private var resetBtn: ButtonWidget? = null
    private var refreshBtn: ButtonWidget? = null
    private var addRowBtn: ButtonWidget? = null

    fun init(addWidget: Function<ClickableWidget, ClickableWidget>) {
        refreshBtn = ButtonWidget.builder(Text.literal("\u00A7bRefresh")) {
            PacketDistributor.sendToServer(AdminLootRequestC2SPacket())
        }.dimensions(x + PADDING, y + h - FOOTER_H + 2, 50, 12).build()
        addWidget.apply(refreshBtn!!)

        addRowBtn = ButtonWidget.builder(Text.literal("\u00A7a+ Add Item")) {
            // Insert at the top so the user immediately sees the new row.
            editEntries.add(0, LootEntry("", 1, 1, 1))
            dirty = true
            listScroll = 0
            // Activate the new item id field for immediate typing.
            activeFieldRow = 0
            activeFieldType = FieldType.ITEM
            fieldText = ""
            suggestions = emptyList()
            selectedSuggestion = -1
        }.dimensions(x + SIDEBAR_W + PADDING + 4, y + h - FOOTER_H + 2, 60, 12).build()
        addWidget.apply(addRowBtn!!)

        saveBtn = ButtonWidget.builder(Text.literal("\u00A72Save")) { commitActiveField(); saveCurrent() }
            .dimensions(x + w - 88, y + h - FOOTER_H + 2, 40, 12).build()
        addWidget.apply(saveBtn!!)

        resetBtn = ButtonWidget.builder(Text.literal("\u00A7cReset")) { resetCurrent() }
            .dimensions(x + w - 46, y + h - FOOTER_H + 2, 42, 12).build()
        addWidget.apply(resetBtn!!)

        PacketDistributor.sendToServer(AdminLootRequestC2SPacket())
    }

    /** Recomputes [jobs] from the current cache. */
    private fun rebuildJobs() {
        jobs.clear()
        val ids = AdminLootDataCache.tables.map { it.id.removePrefix("cobblebase:") }
        val grouped = LinkedHashMap<String, LinkedHashMap<String, String>>()

        for (id in ids) {
            val rarity = RARITY_MATCH_ORDER.firstOrNull { id.endsWith("_$it") } ?: ""
            val base = if (rarity.isEmpty()) id else id.removeSuffix("_$rarity")
            val map = grouped.getOrPut(base) { LinkedHashMap() }
            map[rarity] = "cobblebase:$id"
        }

        val orderedKeys = PRETTY_NAMES.keys.filter { it in grouped }.toMutableList()
        orderedKeys.addAll(grouped.keys.filter { it !in PRETTY_NAMES }.sorted())

        for (base in orderedKeys) {
            val sorted = LinkedHashMap<String, String>()
            for (r in (RARITY_ORDER + listOf(""))) {
                grouped[base]?.get(r)?.let { sorted[r] = it }
            }
            jobs.add(JobGroup(base, PRETTY_NAMES[base] ?: base.replaceFirstChar { it.uppercase() }, sorted))
        }
    }

    private fun loadCurrentTable() {
        commitActiveField()
        val job = selectedJob ?: return
        val tableId = job.rarities[selectedRarity] ?: job.rarities.values.firstOrNull() ?: return
        val def = AdminLootDataCache.get(tableId)
        editEntries.clear()
        if (def != null) {
            editRolls = def.rolls
            editEntries.addAll(def.entries)
        } else {
            editRolls = 1
        }
        dirty = false
        listScroll = 0
        activeFieldRow = -1
        activeFieldType = FieldType.NONE
    }

    private fun currentTableId(): String? {
        val job = selectedJob ?: return null
        return job.rarities[selectedRarity] ?: job.rarities.values.firstOrNull()
    }

    private fun saveCurrent() {
        val id = currentTableId() ?: return
        PacketDistributor.sendToServer(AdminLootUpdateC2SPacket(id, false, editRolls, editEntries.toList()))
        dirty = false
    }

    private fun resetCurrent() {
        val id = currentTableId() ?: return
        PacketDistributor.sendToServer(AdminLootUpdateC2SPacket(id, true, 1, emptyList()))
        dirty = false
    }

    private fun distinctJobCount(): Int {
        val ids = AdminLootDataCache.tables.map { it.id.removePrefix("cobblebase:") }
        val bases = HashSet<String>()
        for (id in ids) {
            val rarity = RARITY_MATCH_ORDER.firstOrNull { id.endsWith("_$it") } ?: ""
            bases.add(if (rarity.isEmpty()) id else id.removeSuffix("_$rarity"))
        }
        return bases.size
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        pendingTooltip = emptyList()
        cursorBlink++

        if (jobs.size != distinctJobCount()) rebuildJobs()
        if (selectedJob == null && jobs.isNotEmpty()) {
            selectedJob = jobs.first()
            selectedRarity = jobs.first().rarities.keys.first()
            loadCurrentTable()
        }

        // Background
        context.fill(x, y, x + w, y + h, 0xCC1E1E2E.toInt())

        // Sidebar
        context.fill(x, y, x + SIDEBAR_W, y + h, SIDEBAR_BG)
        context.fill(x + SIDEBAR_W, y, x + SIDEBAR_W + 1, y + h, SEPARATOR)
        drawScaled(context, "\u00A7f\u00A7lJobs", x + PADDING, y + PADDING, 0xFFFFFF, 0.85f)

        if (jobs.isEmpty()) {
            drawScaled(context, "\u00A77Loading...", x + PADDING, y + 14, 0xAAAAAA, SCALE)
        }

        val sidebarListY = y + 14
        val sidebarListH = h - 14 - FOOTER_H
        val maxSidebarRows = sidebarListH / ROW_H
        sidebarScroll = sidebarScroll.coerceIn(0, (jobs.size - maxSidebarRows).coerceAtLeast(0))

        context.enableScissor(x, sidebarListY, x + SIDEBAR_W, sidebarListY + sidebarListH)
        for (i in 0 until maxSidebarRows + 1) {
            val idx = i + sidebarScroll
            if (idx >= jobs.size) break
            val job = jobs[idx]
            val rowY = sidebarListY + i * ROW_H
            val isSelected = job === selectedJob
            val isHovered = mouseX in x..(x + SIDEBAR_W) && mouseY in rowY..(rowY + ROW_H)
            if (isSelected) context.fill(x + 1, rowY, x + SIDEBAR_W, rowY + ROW_H, 0x442196F3)
            else if (isHovered) context.fill(x + 1, rowY, x + SIDEBAR_W, rowY + ROW_H, ROW_HOVER)
            val anyOverridden = job.rarities.values.any { AdminLootDataCache.isOverridden(it) }
            val color = if (isSelected) 0xFFFFFF else 0xCCCCCC
            drawScaled(context, job.displayName, x + PADDING + 4, rowY + 3, color, SCALE)
            if (anyOverridden) {
                drawScaled(context, "\u00A76\u25CF", x + SIDEBAR_W - 9, rowY + 3, 0xFF9800, 0.7f)
            }
        }
        context.disableScissor()

        if (jobs.size > maxSidebarRows) {
            val trackX = x + SIDEBAR_W - 3
            val trackH = sidebarListH
            val maxScroll = (jobs.size - maxSidebarRows).coerceAtLeast(1)
            val thumbH = ((maxSidebarRows.toFloat() / jobs.size) * trackH).toInt().coerceAtLeast(8)
            val thumbY = sidebarListY + ((sidebarScroll.toFloat() / maxScroll) * (trackH - thumbH)).toInt()
            context.fill(trackX, sidebarListY, trackX + 2, sidebarListY + trackH, 0x33FFFFFF)
            context.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xAAFFFFFF.toInt())
        }

        // Right pane
        val rightX = x + SIDEBAR_W + 2
        val rightW = w - SIDEBAR_W - 2
        val job = selectedJob ?: return

        drawScaled(context, "\u00A7f\u00A7l${job.displayName}", rightX + PADDING, y + PADDING, 0xFFFFFF, 0.85f)
        if (dirty) drawScaled(context, "\u00A7e*unsaved", rightX + rightW - 50, y + PADDING + 1, 0xFFFF00, SCALE)

        // Rarity tabs
        val tabY = y + 12
        val tabH = 11
        val tabW = 48
        val rarityKeys = job.rarities.keys.toList()
        var tx = rightX + PADDING
        for (rk in rarityKeys) {
            val tableId = job.rarities[rk]!!
            val isOverridden = AdminLootDataCache.isOverridden(tableId)
            val isActive = rk == selectedRarity
            val isHov = mouseX in tx..(tx + tabW) && mouseY in tabY..(tabY + tabH)
            val bg = when {
                isActive -> 0xFF3A3A6C.toInt()
                isHov -> 0xFF2E2E55.toInt()
                else -> 0xFF252545.toInt()
            }
            context.fill(tx, tabY, tx + tabW, tabY + tabH, bg)
            if (isActive) context.fill(tx, tabY + tabH - 1, tx + tabW, tabY + tabH, 0xFF6A6AFF.toInt())
            val label = if (rk.isEmpty()) "Default" else (RARITY_LABELS[rk] ?: rk)
            val labelColor = if (isActive) (RARITY_COLORS[rk] ?: 0xFFFFFF) else 0xFFAAAAAA.toInt()
            drawScaled(context, label, tx + 4, tabY + 3, labelColor, SCALE)
            if (isOverridden) {
                drawScaled(context, "\u00A76\u25CF", tx + tabW - 8, tabY + 2, 0xFF9800, 0.65f)
            }
            tx += tabW + 2
        }

        // Rolls field
        val rollsLabelY = y + 26
        drawScaled(context, "\u00A77Rolls per generation:", rightX + PADDING, rollsLabelY + 2, 0xAAAAAA, SCALE)
        val rollsFieldX = rightX + PADDING + 76
        val rollsFieldW = 26
        val rollsActive = activeFieldRow == -1 && activeFieldType == FieldType.ROLLS
        renderField(
            context, rollsFieldX, rollsLabelY, rollsFieldW, 11,
            if (rollsActive) fieldText else editRolls.toString(),
            isActive = rollsActive,
            isOverride = false
        )
        if (mouseX in (rightX + PADDING)..(rightX + PADDING + 100) && mouseY in rollsLabelY..(rollsLabelY + 11)) {
            setTip(mouseX, mouseY,
                "\u00A7f\u00A7lRolls per generation",
                "\u00A77How many items the job produces each time it ticks.",
                "\u00A77Each roll picks one item using the weight values below.",
                "\u00A78Example: Rolls = 3 -> the job spits out 3 items per tick."
            )
        }

        // Column headers
        val headerY = y + 41
        val colItemX = rightX + PADDING + 14
        val colWeightX = rightX + PADDING + 154
        val colMinX = rightX + PADDING + 184
        val colMaxX = rightX + PADDING + 214
        val colActionX = rightX + PADDING + 246
        drawScaled(context, "\u00A77Item", colItemX, headerY, 0xAAAAAA, SCALE)
        drawScaled(context, "\u00A77Weight", colWeightX - 2, headerY, 0xAAAAAA, SCALE)
        drawScaled(context, "\u00A77Min", colMinX - 2, headerY, 0xAAAAAA, SCALE)
        drawScaled(context, "\u00A77Max", colMaxX - 2, headerY, 0xAAAAAA, SCALE)
        drawScaled(context, "\u00A77Action", colActionX - 2, headerY, 0xAAAAAA, SCALE)

        // Header tooltips
        if (mouseY in headerY..(headerY + 8)) {
            when {
                mouseX in colItemX..(colItemX + 30) -> setTip(mouseX, mouseY,
                    "\u00A7f\u00A7lItem ID",
                    "\u00A77The Minecraft item identifier including the mod namespace.",
                    "\u00A78Example: \u00A7fminecraft:diamond\u00A78, \u00A7fcobblemon:rare_candy",
                    "",
                    "\u00A7e\u00A7lBulk add via item tag",
                    "\u00A77Type \u00A7f#namespace:tag\u00A77 to expand all items in a tag",
                    "\u00A77into one entry each (weight/min/max copied from this row).",
                    "\u00A78Examples: \u00A7f#minecraft:logs\u00A78, \u00A7f#c:ores\u00A78, \u00A7f#minecraft:flowers"
                )
                mouseX in colWeightX..(colWeightX + 28) -> setTip(mouseX, mouseY,
                    "\u00A7f\u00A7lWeight",
                    "\u00A77How likely this item is to be picked on a roll.",
                    "\u00A77Relative to the sum of all weights in this table.",
                    "\u00A78Example: weights 10/5/1 -> 62.5% / 31.3% / 6.3%."
                )
                mouseX in colMinX..(colMinX + 28) -> setTip(mouseX, mouseY,
                    "\u00A7f\u00A7lMin Count",
                    "\u00A77Smallest stack size produced when this item is picked.",
                    "\u00A78Example: Min 1 Max 3 -> 1, 2 or 3 of this item."
                )
                mouseX in colMaxX..(colMaxX + 28) -> setTip(mouseX, mouseY,
                    "\u00A7f\u00A7lMax Count",
                    "\u00A77Largest stack size produced when this item is picked.",
                    "\u00A78If Min == Max the count is always exactly that value."
                )
                mouseX in colActionX..(colActionX + 32) -> setTip(mouseX, mouseY,
                    "\u00A7f\u00A7lAction",
                    "\u00A7aon/off\u00A77 toggle for default items from the bundled loot pool.",
                    "\u00A77Disabled items stay in the table but are never picked,",
                    "\u00A77so you can flip them back on later.",
                    "\u00A77Custom items (added by you) show \u00A7c\u00d7\u00A77 to remove them entirely."
                )
            }
        }

        context.fill(rightX + 2, headerY + 9, rightX + rightW - 4, headerY + 10, SEPARATOR)

        // Rows
        val listY = headerY + 12
        val listH = y + h - FOOTER_H - listY - 2
        val maxRows = listH / ROW_H
        listScroll = listScroll.coerceIn(0, (editEntries.size - maxRows).coerceAtLeast(0))
        val tableId = currentTableId()

        context.enableScissor(rightX, listY, rightX + rightW, listY + listH)
        for (i in 0 until maxRows + 1) {
            val rowIdx = i + listScroll
            if (rowIdx >= editEntries.size) break
            val entry = editEntries[rowIdx]
            val rowY = listY + i * ROW_H
            val isDefault = tableId != null && AdminLootDataCache.isDefaultEntry(tableId, entry.itemId)

            // Hover background
            val isHovered = mouseX in rightX..(rightX + rightW - 4) && mouseY in rowY..(rowY + ROW_H)
            if (isHovered) context.fill(rightX + 2, rowY, rightX + rightW - 4, rowY + ROW_H, ROW_HOVER)

            // Dim disabled rows
            if (entry.disabled) {
                context.fill(rightX + 2, rowY, rightX + rightW - 4, rowY + ROW_H, 0x66000000)
            }

            // Item icon — scaled to ~11x11 to match the row height. While the
            // user is editing this row's item id, preview the live typed value
            // instead of the committed one so the icon updates as you type.
            val isItemActiveForIcon = activeFieldRow == rowIdx && activeFieldType == FieldType.ITEM
            val previewId = if (isItemActiveForIcon) fieldText else entry.itemId
            val stack = makeStack(previewId)
            val iconScale = (ROW_H - 3).toFloat() / 16f
            context.matrices.push()
            context.matrices.translate((rightX + PADDING).toFloat(), (rowY + 1).toFloat(), 0f)
            context.matrices.scale(iconScale, iconScale, 1f)
            if (!stack.isEmpty) {
                context.drawItem(stack, 0, 0)
            } else {
                context.fill(0, 0, 16, 16, 0xFFFF00FF.toInt())
            }
            context.matrices.pop()

            // Item ID field
            val isItemActive = activeFieldRow == rowIdx && activeFieldType == FieldType.ITEM
            val itemDisplay = when {
                isItemActive -> fieldText
                entry.itemId.isEmpty() -> "id or #c:ores"
                else -> entry.itemId
            }
            renderField(
                context,
                colItemX, rowY + 1, 136, ROW_H - 2,
                itemDisplay,
                isActive = isItemActive,
                isOverride = false,
                grayWhenEmpty = entry.itemId.isEmpty() && !isItemActive
            )

            // Weight
            val isWActive = activeFieldRow == rowIdx && activeFieldType == FieldType.WEIGHT
            renderField(
                context,
                colWeightX, rowY + 1, 26, ROW_H - 2,
                if (isWActive) fieldText else entry.weight.toString(),
                isActive = isWActive,
                isOverride = false
            )

            // Min
            val isMinActive = activeFieldRow == rowIdx && activeFieldType == FieldType.MIN
            renderField(
                context,
                colMinX, rowY + 1, 26, ROW_H - 2,
                if (isMinActive) fieldText else entry.minCount.toString(),
                isActive = isMinActive,
                isOverride = false
            )

            // Max
            val isMaxActive = activeFieldRow == rowIdx && activeFieldType == FieldType.MAX
            renderField(
                context,
                colMaxX, rowY + 1, 26, ROW_H - 2,
                if (isMaxActive) fieldText else entry.maxCount.toString(),
                isActive = isMaxActive,
                isOverride = false
            )

            // Action: toggle for default-origin, delete (X) for custom
            val actX = colActionX
            val actY = rowY + 2
            val actW = 22
            val actH = ROW_H - 4
            if (isDefault) {
                val on = !entry.disabled
                val bg = if (on) CHECKBOX_ON else CHECKBOX_OFF
                context.fill(actX, actY, actX + actW, actY + actH, bg)
                drawScaled(context, if (on) "\u00A7fon" else "\u00A7foff", actX + 5, actY + 2, 0xFFFFFF, SCALE)
            } else {
                context.fill(actX, actY, actX + actW, actY + actH, DELETE_BG)
                drawScaled(context, "\u00A7f\u00d7", actX + 9, actY + 2, 0xFFFFFF, SCALE)
            }
        }
        context.disableScissor()

        // List scrollbar
        if (editEntries.size > maxRows) {
            val trackX = rightX + rightW - 3
            val trackH = listH
            val maxScroll = (editEntries.size - maxRows).coerceAtLeast(1)
            val thumbH = ((maxRows.toFloat() / editEntries.size) * trackH).toInt().coerceAtLeast(8)
            val thumbY = listY + ((listScroll.toFloat() / maxScroll) * (trackH - thumbH)).toInt()
            context.fill(trackX, listY, trackX + 2, listY + trackH, 0x33FFFFFF)
            context.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xAAFFFFFF.toInt())
        }

        // Autocomplete popup — drawn after the rows so it overlays subsequent
        // rows. Anchored under the active item field.
        if (activeFieldType == FieldType.ITEM && suggestions.isNotEmpty() && activeFieldRow >= 0) {
            val visualIdx = activeFieldRow - listScroll
            if (visualIdx in 0 until maxRows) {
                val activeRowY = listY + visualIdx * ROW_H
                renderSuggestions(context, mouseX, mouseY, colItemX, activeRowY + ROW_H + 1, 156)
            }
        }
    }

    private fun renderSuggestions(context: DrawContext, mouseX: Int, mouseY: Int, px: Int, py: Int, pw: Int) {
        val itemH = 11
        val ph = suggestions.size * itemH + 2

        // Flush pending draws so everything rendered earlier in this frame
        // (rows, text, item icons) is committed to the screen *before* we
        // draw the popup. Without this flush the text geometry from the rows
        // bleeds through the popup background even though the fill is fully
        // opaque, because all DrawContext.fill / drawTextWithShadow calls in
        // a frame share a single buffer that flushes at the end.
        context.draw()

        // Push a high Z too so anything that *does* batch with the popup ends
        // up behind it.
        context.matrices.push()
        context.matrices.translate(0f, 0f, 400f)

        // Drop shadow
        context.fill(px + 1, py + 1, px + pw + 1, py + ph + 1, 0xC0000000.toInt())
        // Border + bg (fully opaque so nothing bleeds through)
        context.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF7A7ABE.toInt())
        context.fill(px, py, px + pw, py + ph, 0xFF15152A.toInt())

        for ((i, s) in suggestions.withIndex()) {
            val ry = py + 1 + i * itemH
            val isHover = mouseX in px..(px + pw) && mouseY in ry..(ry + itemH)
            val isSel = i == selectedSuggestion
            val bg = when {
                isSel -> 0xFF3A3A6C.toInt()
                isHover -> 0xFF2E2E55.toInt()
                else -> 0
            }
            if (bg != 0) context.fill(px + 1, ry, px + pw - 1, ry + itemH, bg)

            // Icon for item suggestions (skip for tag suggestions starting with #)
            if (!s.startsWith("#")) {
                val stack = makeStack(s)
                if (!stack.isEmpty) {
                    context.matrices.push()
                    context.matrices.translate((px + 2).toFloat(), (ry + 1).toFloat(), 0f)
                    context.matrices.scale(0.55f, 0.55f, 1f)
                    context.drawItem(stack, 0, 0)
                    context.matrices.pop()
                }
            }
            val textX = if (s.startsWith("#")) px + 4 else px + 13
            drawScaled(context, s, textX, ry + 2, if (isSel) 0xFFFFFF else 0xCCCCCC, SCALE)
        }

        // Flush the popup's own draws while still translated, then pop.
        context.draw()
        context.matrices.pop()
    }

    private fun renderField(
        context: DrawContext,
        fx: Int, fy: Int, fw: Int, fh: Int,
        text: String,
        isActive: Boolean,
        isOverride: Boolean,
        grayWhenEmpty: Boolean = false
    ) {
        val border = if (isActive) FIELD_ACTIVE else FIELD_BORDER
        context.fill(fx - 1, fy - 1, fx + fw + 1, fy + fh + 1, border)
        context.fill(fx, fy, fx + fw, fy + fh, FIELD_BG)
        val color = when {
            grayWhenEmpty -> 0x666666
            isOverride -> 0xFFFF00
            else -> 0xCCCCCC
        }
        // Clip text inside the field via scissor
        context.enableScissor(fx + 1, fy, fx + fw - 1, fy + fh)
        drawScaled(context, text, fx + 2, fy + 2, color, SCALE)
        if (isActive && (cursorBlink / 20) % 2 == 0) {
            val cursorX = fx + 2 + (textRenderer.getWidth(text) * SCALE).toInt()
            drawScaled(context, "|", cursorX, fy + 2, 0xFFFFFF, SCALE)
        }
        context.disableScissor()
    }

    private fun setTip(mx: Int, my: Int, vararg lines: String) {
        pendingTooltip = lines.toList()
        tooltipX = mx
        tooltipY = my
    }

    private fun drawScaled(context: DrawContext, text: String, px: Int, py: Int, color: Int, scale: Float) {
        context.matrices.push()
        context.matrices.translate(px.toFloat(), py.toFloat(), 0f)
        context.matrices.scale(scale, scale, 1f)
        context.drawTextWithShadow(textRenderer, text, 0, 0, color)
        context.matrices.pop()
    }

    private fun makeStack(itemId: String): ItemStack {
        return try {
            val parts = itemId.split(":", limit = 2)
            val id = if (parts.size == 2) Identifier.of(parts[0], parts[1]) else Identifier.of("minecraft", itemId)
            val item = Registries.ITEM.get(id)
            if (item == net.minecraft.item.Items.AIR) ItemStack.EMPTY else ItemStack(item)
        } catch (_: Exception) {
            ItemStack.EMPTY
        }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Suggestion popup click — must run before commitActiveField since it
        // would clear the active state and the suggestions.
        if (activeFieldType == FieldType.ITEM && suggestions.isNotEmpty() && activeFieldRow >= 0) {
            val rightX = x + SIDEBAR_W + 2
            val colItemX = rightX + PADDING + 14
            val headerY = y + 41
            val rightListY = headerY + 12
            val rightListH = y + h - FOOTER_H - rightListY - 2
            val maxRows = rightListH / ROW_H
            val visualIdx = activeFieldRow - listScroll
            if (visualIdx in 0 until maxRows) {
                val popupX = colItemX
                val popupY = rightListY + visualIdx * ROW_H + ROW_H + 1
                val popupW = 156
                val itemH = 11
                val popupH = suggestions.size * itemH + 2
                if (mouseX in popupX.toDouble()..(popupX + popupW).toDouble() &&
                    mouseY in popupY.toDouble()..(popupY + popupH).toDouble()
                ) {
                    val rel = ((mouseY - popupY - 1) / itemH.toDouble()).toInt()
                    if (rel in suggestions.indices) {
                        fieldText = suggestions[rel]
                        commitActiveField()
                    }
                    return true
                }
            }
        }

        commitActiveField()

        // Sidebar scrollbar drag start
        val sidebarListY = y + 14
        val sidebarListH = h - 14 - FOOTER_H
        val sidebarMaxRows = sidebarListH / ROW_H
        val sidebarMaxScroll = (jobs.size - sidebarMaxRows).coerceAtLeast(0)
        val sidebarTrackX = x + SIDEBAR_W - 3
        if (sidebarMaxScroll > 0 &&
            mouseX in (sidebarTrackX - 3).toDouble()..(sidebarTrackX + 5).toDouble() &&
            mouseY in sidebarListY.toDouble()..(sidebarListY + sidebarListH).toDouble()
        ) {
            dragging = DragTarget.SIDEBAR
            val rel = ((mouseY - sidebarListY) / sidebarListH.toDouble()).coerceIn(0.0, 1.0)
            sidebarScroll = (rel * sidebarMaxScroll).toInt()
            return true
        }

        // Right list scrollbar drag start
        val rightX = x + SIDEBAR_W + 2
        val rightW = w - SIDEBAR_W - 2
        val headerY = y + 41
        val rightListY = headerY + 12
        val rightListH = y + h - FOOTER_H - rightListY - 2
        val rightMaxRows = rightListH / ROW_H
        val rightMaxScroll = (editEntries.size - rightMaxRows).coerceAtLeast(0)
        val rightTrackX = rightX + rightW - 3
        if (rightMaxScroll > 0 &&
            mouseX in (rightTrackX - 3).toDouble()..(rightTrackX + 5).toDouble() &&
            mouseY in rightListY.toDouble()..(rightListY + rightListH).toDouble()
        ) {
            dragging = DragTarget.LIST
            val rel = ((mouseY - rightListY) / rightListH.toDouble()).coerceIn(0.0, 1.0)
            listScroll = (rel * rightMaxScroll).toInt()
            return true
        }

        // Sidebar selection (only when not clicking the scrollbar)
        if (mouseX in x.toDouble()..(x + SIDEBAR_W - 4).toDouble() &&
            mouseY in sidebarListY.toDouble()..(y + h - FOOTER_H).toDouble()
        ) {
            val idx = ((mouseY - sidebarListY) / ROW_H).toInt() + sidebarScroll
            if (idx in jobs.indices) {
                selectedJob = jobs[idx]
                selectedRarity = jobs[idx].rarities.keys.first()
                loadCurrentTable()
                return true
            }
        }

        val job = selectedJob ?: return false

        // Rarity tab clicks
        val tabY = y + 12
        val tabH = 11
        val tabW = 48
        var tx = rightX + PADDING
        for (rk in job.rarities.keys) {
            if (mouseX in tx.toDouble()..(tx + tabW).toDouble() &&
                mouseY in tabY.toDouble()..(tabY + tabH).toDouble()
            ) {
                selectedRarity = rk
                loadCurrentTable()
                return true
            }
            tx += tabW + 2
        }

        // Rolls field click
        val rollsLabelY = y + 26
        val rollsFieldX = rightX + PADDING + 76
        val rollsFieldW = 26
        if (mouseX in rollsFieldX.toDouble()..(rollsFieldX + rollsFieldW).toDouble() &&
            mouseY in rollsLabelY.toDouble()..(rollsLabelY + 11).toDouble()
        ) {
            activeFieldRow = -1
            activeFieldType = FieldType.ROLLS
            fieldText = editRolls.toString()
            return true
        }

        // Row cells (reuse rightListY/H from the scrollbar block above)
        val listY = rightListY
        val listH = rightListH
        val maxRows = listH / ROW_H

        val colItemX = rightX + PADDING + 14
        val colWeightX = rightX + PADDING + 154
        val colMinX = rightX + PADDING + 184
        val colMaxX = rightX + PADDING + 214
        val colActionX = rightX + PADDING + 246

        if (mouseX in rightX.toDouble()..(rightX + (w - SIDEBAR_W)).toDouble() &&
            mouseY in listY.toDouble()..(listY + listH).toDouble()
        ) {
            val visRow = ((mouseY - listY) / ROW_H).toInt()
            val rowIdx = visRow + listScroll
            if (rowIdx in editEntries.indices) {
                val rowY = listY + visRow * ROW_H
                val cellY = (rowY + 1).toDouble()..(rowY + ROW_H - 1).toDouble()
                when {
                    mouseX in colItemX.toDouble()..(colItemX + 136).toDouble() && mouseY in cellY -> {
                        activeFieldRow = rowIdx
                        activeFieldType = FieldType.ITEM
                        fieldText = editEntries[rowIdx].itemId
                        recomputeSuggestions()
                        return true
                    }
                    mouseX in colWeightX.toDouble()..(colWeightX + 26).toDouble() && mouseY in cellY -> {
                        activeFieldRow = rowIdx
                        activeFieldType = FieldType.WEIGHT
                        fieldText = editEntries[rowIdx].weight.toString()
                        return true
                    }
                    mouseX in colMinX.toDouble()..(colMinX + 26).toDouble() && mouseY in cellY -> {
                        activeFieldRow = rowIdx
                        activeFieldType = FieldType.MIN
                        fieldText = editEntries[rowIdx].minCount.toString()
                        return true
                    }
                    mouseX in colMaxX.toDouble()..(colMaxX + 26).toDouble() && mouseY in cellY -> {
                        activeFieldRow = rowIdx
                        activeFieldType = FieldType.MAX
                        fieldText = editEntries[rowIdx].maxCount.toString()
                        return true
                    }
                    mouseX in colActionX.toDouble()..(colActionX + 22).toDouble() && mouseY in cellY -> {
                        val entry = editEntries[rowIdx]
                        val tableId = currentTableId()
                        val isDefault = tableId != null && AdminLootDataCache.isDefaultEntry(tableId, entry.itemId)
                        if (isDefault) {
                            editEntries[rowIdx] = entry.copy(disabled = !entry.disabled)
                        } else {
                            editEntries.removeAt(rowIdx)
                            // Adjust active field if it pointed past the removed row
                            if (activeFieldRow >= editEntries.size) activeFieldRow = -1
                        }
                        dirty = true
                        return true
                    }
                }
            }
            // Click in row area but not on a cell -> commit & deactivate
            activeFieldRow = -1
            activeFieldType = FieldType.NONE
            return true
        }

        activeFieldRow = -1
        activeFieldType = FieldType.NONE
        return false
    }

    /**
     * Expands `#namespace:path` into all item ids contained in that vanilla
     * item tag (resolved against the client-side item registry — tags sync
     * from server to client during world join). Returns null if [text] is
     * not a tag query.
     */
    private fun expandItemTag(text: String): List<String>? {
        if (!text.startsWith("#")) return null
        val raw = text.removePrefix("#").trim()
        if (raw.isEmpty()) return emptyList()
        val parts = raw.split(":", limit = 2)
        val ns = if (parts.size == 2) parts[0] else "minecraft"
        val path = if (parts.size == 2) parts[1] else parts[0]
        val tagId = try { Identifier.of(ns, path) } catch (_: Exception) { return emptyList() }
        val tagKey = TagKey.of(RegistryKeys.ITEM, tagId)
        return try {
            Registries.ITEM.iterateEntries(tagKey)
                .mapNotNull { entry -> Registries.ITEM.getId(entry.value())?.toString() }
                .distinct()
                .sorted()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun commitActiveField() {
        if (activeFieldType == FieldType.NONE) return
        when (activeFieldType) {
            FieldType.ROLLS -> fieldText.toIntOrNull()?.takeIf { it > 0 }?.let {
                editRolls = it
                dirty = true
            }
            FieldType.ITEM -> {
                if (activeFieldRow in editEntries.indices) {
                    val expanded = expandItemTag(fieldText)
                    if (expanded != null) {
                        // Tag query: replace this row with one row per matching item.
                        // Reuse the row's weight/min/max as the template for every new entry.
                        if (expanded.isNotEmpty()) {
                            val template = editEntries[activeFieldRow]
                            editEntries.removeAt(activeFieldRow)
                            for ((i, id) in expanded.withIndex()) {
                                editEntries.add(activeFieldRow + i, template.copy(itemId = id))
                            }
                            dirty = true
                        }
                    } else {
                        editEntries[activeFieldRow] = editEntries[activeFieldRow].copy(itemId = fieldText)
                        dirty = true
                    }
                }
            }
            FieldType.WEIGHT -> {
                if (activeFieldRow in editEntries.indices) {
                    fieldText.toIntOrNull()?.takeIf { it >= 0 }?.let {
                        editEntries[activeFieldRow] = editEntries[activeFieldRow].copy(weight = it.coerceAtLeast(1))
                        dirty = true
                    }
                }
            }
            FieldType.MIN -> {
                if (activeFieldRow in editEntries.indices) {
                    fieldText.toIntOrNull()?.takeIf { it >= 1 }?.let {
                        editEntries[activeFieldRow] = editEntries[activeFieldRow].copy(minCount = it)
                        dirty = true
                    }
                }
            }
            FieldType.MAX -> {
                if (activeFieldRow in editEntries.indices) {
                    fieldText.toIntOrNull()?.takeIf { it >= 1 }?.let {
                        editEntries[activeFieldRow] = editEntries[activeFieldRow].copy(maxCount = it)
                        dirty = true
                    }
                }
            }
            FieldType.NONE -> {}
        }
        activeFieldRow = -1
        activeFieldType = FieldType.NONE
        suggestions = emptyList()
        selectedSuggestion = -1
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        when (dragging) {
            DragTarget.SIDEBAR -> {
                val sidebarListY = y + 14
                val sidebarListH = h - 14 - FOOTER_H
                val maxRows = sidebarListH / ROW_H
                val maxScroll = (jobs.size - maxRows).coerceAtLeast(0)
                if (maxScroll > 0) {
                    val rel = ((mouseY - sidebarListY) / sidebarListH.toDouble()).coerceIn(0.0, 1.0)
                    sidebarScroll = (rel * maxScroll).toInt()
                }
                return true
            }
            DragTarget.LIST -> {
                val headerY = y + 41
                val rightListY = headerY + 12
                val rightListH = y + h - FOOTER_H - rightListY - 2
                val maxRows = rightListH / ROW_H
                val maxScroll = (editEntries.size - maxRows).coerceAtLeast(0)
                if (maxScroll > 0) {
                    val rel = ((mouseY - rightListY) / rightListH.toDouble()).coerceIn(0.0, 1.0)
                    listScroll = (rel * maxScroll).toInt()
                }
                return true
            }
            null -> return false
        }
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (dragging != null) {
            dragging = null
            return true
        }
        return false
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (mouseX in x.toDouble()..(x + SIDEBAR_W).toDouble() &&
            mouseY in y.toDouble()..(y + h).toDouble()
        ) {
            val sidebarListH = h - 14 - FOOTER_H
            val maxRows = sidebarListH / ROW_H
            val maxScroll = (jobs.size - maxRows).coerceAtLeast(0)
            sidebarScroll = (sidebarScroll - vertical.toInt() * 2).coerceIn(0, maxScroll)
            return true
        }
        if (mouseX in (x + SIDEBAR_W).toDouble()..(x + w).toDouble() &&
            mouseY in y.toDouble()..(y + h).toDouble()
        ) {
            val listY = y + 53
            val listH = y + h - FOOTER_H - listY - 2
            val maxRows = listH / ROW_H
            val maxScroll = (editEntries.size - maxRows).coerceAtLeast(0)
            listScroll = (listScroll - vertical.toInt() * 2).coerceIn(0, maxScroll)
            return true
        }
        return false
    }

    fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (activeFieldType == FieldType.NONE) return false
        when (activeFieldType) {
            FieldType.ITEM -> {
                // Item ids and item-tag references allow letters, digits, underscore,
                // colon, and a leading '#' (for "#minecraft:logs"-style bulk expansion).
                if ((chr.isLetterOrDigit() || chr == '_' || chr == ':' || chr == '#') && fieldText.length < 64) {
                    fieldText += chr.lowercaseChar()
                    recomputeSuggestions()
                    return true
                }
            }
            FieldType.WEIGHT, FieldType.MIN, FieldType.MAX, FieldType.ROLLS -> {
                if (chr.isDigit() && fieldText.length < 5) {
                    fieldText += chr
                    return true
                }
            }
            FieldType.NONE -> return false
        }
        return false
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (activeFieldType == FieldType.NONE) return false

        // Suggestion navigation only applies to the item field
        if (activeFieldType == FieldType.ITEM && suggestions.isNotEmpty()) {
            if (keyCode == 264) { // Down
                selectedSuggestion = (selectedSuggestion + 1).coerceAtMost(suggestions.size - 1)
                return true
            }
            if (keyCode == 265) { // Up
                selectedSuggestion = (selectedSuggestion - 1).coerceAtLeast(0)
                return true
            }
            if (keyCode == 258) { // Tab — autocomplete to the highlighted suggestion
                val pick = suggestions.getOrNull(selectedSuggestion.coerceAtLeast(0))
                if (pick != null) {
                    fieldText = pick
                    recomputeSuggestions()
                }
                return true
            }
        }

        if (keyCode == 259 && fieldText.isNotEmpty()) { // backspace
            fieldText = fieldText.dropLast(1)
            if (activeFieldType == FieldType.ITEM) recomputeSuggestions()
            return true
        }
        if (keyCode == 257 || keyCode == 335) { // enter
            // If a suggestion is highlighted, commit that text instead of the typed string
            if (activeFieldType == FieldType.ITEM && selectedSuggestion >= 0 && selectedSuggestion < suggestions.size) {
                fieldText = suggestions[selectedSuggestion]
            }
            commitActiveField()
            return true
        }
        if (keyCode == 256) { // escape
            activeFieldRow = -1
            activeFieldType = FieldType.NONE
            suggestions = emptyList()
            selectedSuggestion = -1
            return true
        }
        return false
    }
}

package notlown.cobblebase.neoforge.client.gui

import net.neoforged.neoforge.network.PacketDistributor
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import notlown.cobblebase.core.AdminLootDataCache
import notlown.cobblebase.core.LootEntry
import notlown.cobblebase.core.LootTableDef
import notlown.cobblebase.core.net.AdminLootRequestC2SPacket
import notlown.cobblebase.core.net.AdminLootUpdateC2SPacket
import java.util.function.Function

/**
 * Admin GUI "Loot" tab.
 *
 * Layout:
 *  - Sidebar (left): list of *jobs* (e.g. "Mining", "Finder: Balls"), grouped
 *    so the four `_common`/`_uncommon`/`_rare`/`_ultra_rare` tables collapse
 *    into a single sidebar entry. Tables without a rarity suffix become their
 *    own job (e.g. "Honey Collect").
 *  - Right pane: rarity tabs across the top (only the rarities that exist for
 *    the selected job), and below that an item-by-item editor with the actual
 *    Minecraft item icon, item id, weight and min/max count.
 *
 * Widget bookkeeping:
 *  - The row widgets are a *fixed pool* (POOL_SIZE rows) created once during
 *    [init]. Picking a different table or rarity just rebinds those widgets
 *    to the new entries. This avoids the bug where dynamically-added widgets
 *    accumulated in the screen's child list and re-appeared on top of other
 *    tabs after a tab switch.
 */
class AdminLootPanel(
    private val x: Int,
    private val y: Int,
    private val w: Int,
    private val h: Int,
    private val textRenderer: TextRenderer
) {
    companion object {
        private const val POOL_SIZE = 25
        private const val PADDING = 4
        private const val SIDEBAR_W = 88
        private const val ROW_H = 18
        private const val FOOTER_H = 18
        private const val SCALE = 0.65f

        // Job base name → human-readable label. These match the names used
        // in the Skills tab (loaded from the skill JSONs).
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
        // Order for *display* in the rarity tab bar (left to right).
        private val RARITY_ORDER = listOf("common", "uncommon", "rare", "ultra_rare")
        // Order for *suffix matching* — longest first so "ultra_rare" wins
        // over "rare" when both would match.
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
    }

    /** A logical "job" in the sidebar. Holds 1..4 loot table ids. */
    private data class JobGroup(
        val baseName: String,
        val displayName: String,
        /** rarityKey ("common"/"uncommon"/"rare"/"ultra_rare"/"") → table id */
        val rarities: LinkedHashMap<String, String>
    )

    // -------- state --------
    private var addWidgetFn: Function<ClickableWidget, ClickableWidget>? = null
    private val jobs = mutableListOf<JobGroup>()
    private var selectedJob: JobGroup? = null
    private var selectedRarity: String = ""
    private var sidebarScroll = 0
    private var listScroll = 0

    private val editEntries = mutableListOf<LootEntry>()
    private var editRolls = 1
    private var dirty = false

    /** Tooltip lines set during render() and drawn by AdminScreen *after* the
     *  widgets layer so they always appear on top. */
    var pendingTooltip: List<String> = emptyList()
        private set
    var tooltipX: Int = 0
        private set
    var tooltipY: Int = 0
        private set

    // -------- widgets (fixed pool) --------
    private data class RowWidgets(
        val itemField: TextFieldWidget,
        val weightField: TextFieldWidget,
        val minField: TextFieldWidget,
        val maxField: TextFieldWidget,
        val deleteBtn: ButtonWidget,
        val toggleBtn: ButtonWidget
    )
    private val rowPool = mutableListOf<RowWidgets>()
    private var rollsField: TextFieldWidget? = null
    private var addRowBtn: ButtonWidget? = null
    private var saveBtn: ButtonWidget? = null
    private var resetBtn: ButtonWidget? = null
    private var refreshBtn: ButtonWidget? = null

    fun init(addWidget: Function<ClickableWidget, ClickableWidget>) {
        addWidgetFn = addWidget

        // Static action buttons
        refreshBtn = ButtonWidget.builder(Text.literal("\u00A7bRefresh")) {
            PacketDistributor.sendToServer(AdminLootRequestC2SPacket())
        }.dimensions(x + PADDING, y + h - FOOTER_H + 2, 50, 12).build()
        addWidget.apply(refreshBtn!!)

        addRowBtn = ButtonWidget.builder(Text.literal("\u00A7a+ Add Item")) {
            if (editEntries.size < POOL_SIZE) {
                editEntries.add(LootEntry("minecraft:apple", 1, 1, 1))
                dirty = true
                bindRowWidgetsToBuffer()
            }
        }.dimensions(x + SIDEBAR_W + PADDING + 4, y + h - FOOTER_H + 2, 60, 12).build()
        addWidget.apply(addRowBtn!!)

        saveBtn = ButtonWidget.builder(Text.literal("\u00A72Save")) { saveCurrent() }
            .dimensions(x + w - 88, y + h - FOOTER_H + 2, 40, 12).build()
        addWidget.apply(saveBtn!!)

        resetBtn = ButtonWidget.builder(Text.literal("\u00A7cReset")) { resetCurrent() }
            .dimensions(x + w - 46, y + h - FOOTER_H + 2, 42, 12).build()
        addWidget.apply(resetBtn!!)

        // Rolls field
        val rf = TextFieldWidget(textRenderer, 0, 0, 26, 12, Text.literal("Rolls"))
        rf.setMaxLength(3)
        rf.setChangedListener { v ->
            v.toIntOrNull()?.let { editRolls = it.coerceAtLeast(1); dirty = true }
        }
        rollsField = rf
        addWidget.apply(rf)

        // Pre-allocate row pool
        repeat(POOL_SIZE) { idx ->
            val itemF = TextFieldWidget(textRenderer, 0, 0, 180, 12, Text.literal("Item ID"))
            itemF.setMaxLength(64)
            itemF.setChangedListener { v ->
                if (idx in editEntries.indices) {
                    editEntries[idx] = editEntries[idx].copy(itemId = v)
                    dirty = true
                }
            }
            val weightF = numberField { v ->
                if (idx in editEntries.indices) editEntries[idx] = editEntries[idx].copy(weight = v.coerceAtLeast(1))
            }
            val minF = numberField { v ->
                if (idx in editEntries.indices) editEntries[idx] = editEntries[idx].copy(minCount = v.coerceAtLeast(1))
            }
            val maxF = numberField { v ->
                if (idx in editEntries.indices) editEntries[idx] = editEntries[idx].copy(maxCount = v.coerceAtLeast(1))
            }
            val delBtn = ButtonWidget.builder(Text.literal("\u00A7c\u00d7")) {
                if (idx in editEntries.indices) {
                    editEntries.removeAt(idx)
                    dirty = true
                    bindRowWidgetsToBuffer()
                }
            }.dimensions(0, 0, 14, 12).build()
            val toggleBtn = ButtonWidget.builder(Text.literal("on")) {
                if (idx in editEntries.indices) {
                    val cur = editEntries[idx]
                    editEntries[idx] = cur.copy(disabled = !cur.disabled)
                    dirty = true
                    bindRowWidgetsToBuffer()
                }
            }.dimensions(0, 0, 22, 12).build()

            addWidget.apply(itemF)
            addWidget.apply(weightF)
            addWidget.apply(minF)
            addWidget.apply(maxF)
            addWidget.apply(delBtn)
            addWidget.apply(toggleBtn)
            rowPool.add(RowWidgets(itemF, weightF, minF, maxF, delBtn, toggleBtn))
        }

        // Initial sync
        PacketDistributor.sendToServer(AdminLootRequestC2SPacket())
    }

    private fun numberField(onChange: (Int) -> Unit): TextFieldWidget {
        val f = TextFieldWidget(textRenderer, 0, 0, 22, 12, Text.literal(""))
        f.setMaxLength(5)
        f.setChangedListener { v -> v.toIntOrNull()?.let { onChange(it); dirty = true } }
        return f
    }

    /** Recomputes [jobs] from the current cache. */
    private fun rebuildJobs() {
        jobs.clear()
        val ids = AdminLootDataCache.tables.map { it.id.removePrefix("cobblebase:") }
        val grouped = LinkedHashMap<String, LinkedHashMap<String, String>>()

        for (id in ids) {
            // Detect "_common", "_uncommon", "_rare", "_ultra_rare" suffix.
            // Check longest suffix first so "ultra_rare" wins over "rare".
            val rarity = RARITY_MATCH_ORDER.firstOrNull { id.endsWith("_$it") } ?: ""
            val base = if (rarity.isEmpty()) id else id.removeSuffix("_$rarity")
            val map = grouped.getOrPut(base) { LinkedHashMap() }
            map[rarity] = "cobblebase:$id"
        }

        // Stable order: PRETTY_NAMES order first, then any unknowns alphabetically
        val orderedKeys = PRETTY_NAMES.keys.filter { it in grouped }.toMutableList()
        orderedKeys.addAll(grouped.keys.filter { it !in PRETTY_NAMES }.sorted())

        for (base in orderedKeys) {
            // Sort rarities by canonical order
            val sorted = LinkedHashMap<String, String>()
            for (r in (RARITY_ORDER + listOf(""))) {
                grouped[base]?.get(r)?.let { sorted[r] = it }
            }
            jobs.add(JobGroup(base, PRETTY_NAMES[base] ?: base.replaceFirstChar { it.uppercase() }, sorted))
        }
    }

    private fun loadCurrentTable() {
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
        rollsField?.text = editRolls.toString()
        bindRowWidgetsToBuffer()
    }

    /** Push entry buffer values into the fixed row widget pool. */
    private fun bindRowWidgetsToBuffer() {
        for ((i, rw) in rowPool.withIndex()) {
            if (i < editEntries.size) {
                val e = editEntries[i]
                if (rw.itemField.text != e.itemId) {
                    rw.itemField.text = e.itemId
                    // Reset cursor + scroll to the start so the field shows the
                    // beginning of the id (e.g. "cobblemon:") instead of the
                    // tail end the user can't read.
                    rw.itemField.setCursor(0, false)
                }
                if (rw.weightField.text != e.weight.toString()) {
                    rw.weightField.text = e.weight.toString()
                    rw.weightField.setCursor(0, false)
                }
                if (rw.minField.text != e.minCount.toString()) {
                    rw.minField.text = e.minCount.toString()
                    rw.minField.setCursor(0, false)
                }
                if (rw.maxField.text != e.maxCount.toString()) {
                    rw.maxField.text = e.maxCount.toString()
                    rw.maxField.setCursor(0, false)
                }
            } else {
                rw.itemField.text = ""
                rw.weightField.text = ""
                rw.minField.text = ""
                rw.maxField.text = ""
            }
        }
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

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        pendingTooltip = emptyList()

        // Refresh job list if cache changed
        if (jobs.size != distinctJobCount()) rebuildJobs()
        if (selectedJob == null && jobs.isNotEmpty()) {
            selectedJob = jobs.first()
            selectedRarity = jobs.first().rarities.keys.first()
            loadCurrentTable()
        }

        // Background
        context.fill(x, y, x + w, y + h, 0xCC1E1E2E.toInt())

        // Sidebar bg
        context.fill(x, y, x + SIDEBAR_W, y + h, 0xCC15152A.toInt())
        context.fill(x + SIDEBAR_W, y, x + SIDEBAR_W + 1, y + h, 0xFF3A3A5C.toInt())

        // Sidebar header
        drawScaled(context, "\u00A7f\u00A7lJobs", x + PADDING, y + PADDING, 0xFFFFFF, 0.85f)

        if (jobs.isEmpty()) {
            drawScaled(context, "\u00A77Loading…", x + PADDING, y + 14, 0xAAAAAA, SCALE)
        }

        // Sidebar list
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
            else if (isHovered) context.fill(x + 1, rowY, x + SIDEBAR_W, rowY + ROW_H, 0x22FFFFFF)

            // Indicator if any rarity for this job is overridden
            val anyOverridden = job.rarities.values.any { AdminLootDataCache.isOverridden(it) }
            val color = if (isSelected) 0xFFFFFF else 0xCCCCCC
            drawScaled(context, job.displayName, x + PADDING + 4, rowY + 5, color, SCALE)
            if (anyOverridden) {
                drawScaled(context, "\u00A76\u25CF", x + SIDEBAR_W - 9, rowY + 4, 0xFF9800, 0.7f)
            }
        }
        context.disableScissor()

        // Sidebar scrollbar
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

        val job = selectedJob
        if (job == null) {
            // Hide everything
            hideEditorWidgets()
            return
        }

        // Job title
        drawScaled(context, "\u00A7f\u00A7l${job.displayName}", rightX + PADDING, y + PADDING, 0xFFFFFF, 0.9f)
        if (dirty) drawScaled(context, "\u00A7e*unsaved", rightX + rightW - 50, y + PADDING + 1, 0xFFFF00, SCALE)

        // Rarity tabs
        val tabY = y + 12
        val tabH = 11
        val tabW = 56
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

        // Rolls + label
        val rollsLabelY = y + 26
        drawScaled(context, "\u00A77Rolls per generation:", rightX + PADDING, rollsLabelY + 2, 0xAAAAAA, SCALE)
        rollsField?.let {
            it.x = rightX + PADDING + 76
            it.y = rollsLabelY
            it.visible = true
        }
        // Hover tooltip for rolls label
        if (mouseX in (rightX + PADDING)..(rightX + PADDING + 100) && mouseY in rollsLabelY..(rollsLabelY + 10)) {
            pendingTooltip = listOf(
                "\u00A7f\u00A7lRolls per generation",
                "\u00A77Wie viele Items der Job pro Cooldown ausspuckt.",
                "\u00A77Pro Roll wird genau ein Item nach Weight gewuerfelt.",
                "\u00A78Beispiel: Rolls = 3 -> es kommen 3 Items raus pro Tick."
            )
            tooltipX = mouseX
            tooltipY = mouseY
        }

        // Column headers — column X positions also drive the row widget layout below
        val headerY = y + 40
        val colItemX = rightX + PADDING + 18
        val colWeightX = rightX + PADDING + 200
        val colMinX = rightX + PADDING + 226
        val colMaxX = rightX + PADDING + 252
        val colActionX = rightX + PADDING + 280
        drawScaled(context, "\u00A77Item", colItemX, headerY, 0xAAAAAA, SCALE)
        drawScaled(context, "\u00A77W", colWeightX, headerY, 0xAAAAAA, SCALE)
        drawScaled(context, "\u00A77Min", colMinX, headerY, 0xAAAAAA, SCALE)
        drawScaled(context, "\u00A77Max", colMaxX, headerY, 0xAAAAAA, SCALE)
        drawScaled(context, "\u00A77Action", colActionX, headerY, 0xAAAAAA, SCALE)
        context.fill(rightX + 2, headerY + 8, rightX + rightW - 4, headerY + 9, 0xFF3A3A5C.toInt())

        // Column header tooltips
        if (mouseY in headerY..(headerY + 8)) {
            when {
                mouseX in colItemX..(colItemX + 30) -> setTip(mouseX, mouseY,
                    "\u00A7f\u00A7lItem ID",
                    "\u00A77Minecraft Item Identifier mit Mod-Namespace.",
                    "\u00A78Beispiele: minecraft:diamond, cobblemon:rare_candy"
                )
                mouseX in colWeightX..(colWeightX + 26) -> setTip(mouseX, mouseY,
                    "\u00A7f\u00A7lWeight",
                    "\u00A77Wahrscheinlichkeit dass dieses Item gewaehlt wird.",
                    "\u00A77Verhaeltnis zur Summe aller Weights in der Tabelle.",
                    "\u00A78Beispiel: Weights 10/5/1 -> 62.5%/31.3%/6.3%."
                )
                mouseX in colMinX..(colMinX + 26) -> setTip(mouseX, mouseY,
                    "\u00A7f\u00A7lMin Count",
                    "\u00A77Minimale Stack-Groesse wenn dieses Item gewaehlt wird.",
                    "\u00A78Beispiel: Min 1 Max 3 -> 1, 2 oder 3 Stueck."
                )
                mouseX in colMaxX..(colMaxX + 26) -> setTip(mouseX, mouseY,
                    "\u00A7f\u00A7lMax Count",
                    "\u00A77Maximale Stack-Groesse wenn dieses Item gewaehlt wird.",
                    "\u00A78Wenn Min == Max gibts immer genau diese Anzahl."
                )
                mouseX in colActionX..(colActionX + 32) -> setTip(mouseX, mouseY,
                    "\u00A7f\u00A7lAction",
                    "\u00A7aon/off\u00A77 Toggle fuer Default-Items aus dem Loot Pool.",
                    "\u00A77Disabled Items bleiben in der Tabelle aber werden nie",
                    "\u00A77ausgewaehlt - kannst du jederzeit wieder aktivieren.",
                    "\u00A77Custom Items (selber hinzugefuegt) zeigen \u00A7c\u00d7\u00A77 zum Loeschen."
                )
            }
        }

        // Rows
        val listY = headerY + 11
        val listH = y + h - FOOTER_H - listY - 2
        val maxRows = listH / ROW_H
        listScroll = listScroll.coerceIn(0, (editEntries.size - maxRows).coerceAtLeast(0))

        // Position pool widgets and draw icons
        val tableId = currentTableId()
        for (i in 0 until POOL_SIZE) {
            val rw = rowPool[i]
            val visualIdx = i - listScroll
            val visible = i < editEntries.size && visualIdx in 0 until maxRows
            if (!visible) {
                rw.itemField.visible = false
                rw.weightField.visible = false
                rw.minField.visible = false
                rw.maxField.visible = false
                rw.deleteBtn.visible = false
                rw.toggleBtn.visible = false
                continue
            }

            val entry = editEntries[i]
            val rowY = listY + visualIdx * ROW_H
            val isDefault = tableId != null && AdminLootDataCache.isDefaultEntry(tableId, entry.itemId)

            // Dim the row when disabled
            if (entry.disabled) {
                context.fill(rightX + 2, rowY, rightX + rightW - 4, rowY + ROW_H - 1, 0x44000000)
            }

            // Item icon
            val stack = makeStack(entry.itemId)
            if (!stack.isEmpty) {
                context.drawItem(stack, rightX + PADDING, rowY)
            } else {
                context.fill(rightX + PADDING, rowY, rightX + PADDING + 16, rowY + 16, 0xFFFF00FF.toInt())
            }

            rw.itemField.visible = true
            rw.weightField.visible = true
            rw.minField.visible = true
            rw.maxField.visible = true
            rw.itemField.x = colItemX
            rw.itemField.y = rowY + 2
            rw.weightField.x = colWeightX - 2
            rw.weightField.y = rowY + 2
            rw.minField.x = colMinX - 2
            rw.minField.y = rowY + 2
            rw.maxField.x = colMaxX - 2
            rw.maxField.y = rowY + 2

            // Action: toggle for default-origin entries, delete for custom
            if (isDefault) {
                rw.toggleBtn.visible = true
                rw.deleteBtn.visible = false
                rw.toggleBtn.x = colActionX - 2
                rw.toggleBtn.y = rowY + 2
                rw.toggleBtn.message = Text.literal(
                    if (entry.disabled) "\u00A7coff" else "\u00A7aon"
                )
            } else {
                rw.toggleBtn.visible = false
                rw.deleteBtn.visible = true
                rw.deleteBtn.x = colActionX - 2
                rw.deleteBtn.y = rowY + 2
            }
        }

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
    }

    private fun hideEditorWidgets() {
        rollsField?.visible = false
        for (rw in rowPool) {
            rw.itemField.visible = false
            rw.weightField.visible = false
            rw.minField.visible = false
            rw.maxField.visible = false
            rw.deleteBtn.visible = false
            rw.toggleBtn.visible = false
        }
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

    private fun distinctJobCount(): Int {
        val ids = AdminLootDataCache.tables.map { it.id.removePrefix("cobblebase:") }
        val bases = HashSet<String>()
        for (id in ids) {
            val rarity = RARITY_MATCH_ORDER.firstOrNull { id.endsWith("_$it") } ?: ""
            bases.add(if (rarity.isEmpty()) id else id.removeSuffix("_$rarity"))
        }
        return bases.size
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

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Sidebar selection
        val sidebarListY = y + 14
        if (mouseX in x.toDouble()..(x + SIDEBAR_W).toDouble() &&
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

        // Rarity tab click
        val job = selectedJob
        if (job != null) {
            val rightX = x + SIDEBAR_W + 2
            val tabY = y + 12
            val tabH = 11
            val tabW = 56
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
        }
        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean = false
    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean = false

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
            val listY = y + 51
            val listH = y + h - FOOTER_H - listY - 2
            val maxRows = listH / ROW_H
            val maxScroll = (editEntries.size - maxRows).coerceAtLeast(0)
            listScroll = (listScroll - vertical.toInt() * 2).coerceIn(0, maxScroll)
            return true
        }
        return false
    }

    fun charTyped(chr: Char, modifiers: Int): Boolean = false
    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean = false
}

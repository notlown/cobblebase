package notlown.cobblebase.neoforge.client.gui

import net.neoforged.neoforge.network.PacketDistributor
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text
import notlown.cobblebase.core.AdminLootDataCache
import notlown.cobblebase.core.LootEntry
import notlown.cobblebase.core.LootTableDef
import notlown.cobblebase.core.net.AdminLootRequestC2SPacket
import notlown.cobblebase.core.net.AdminLootUpdateC2SPacket
import java.util.function.Function

/**
 * Admin GUI "Loot" tab.
 *
 * Two-pane layout — sidebar with all bundled loot tables (sorted, with an
 * orange dot when an override is active), and an editor on the right that
 * shows the entries of the selected table. The editor uses the standard
 * Minecraft `TextFieldWidget` for both the item id and the numeric fields,
 * since each row needs three editable values.
 *
 * The whole entry list is destroyed and rebuilt every time the user picks
 * a different table or adds/removes a row — this keeps widget bookkeeping
 * simple and the row count is always small (≈10–20 entries per table).
 */
class AdminLootPanel(
    private val x: Int,
    private val y: Int,
    private val w: Int,
    private val h: Int,
    private val textRenderer: TextRenderer
) {
    private val PADDING = 4
    private val SIDEBAR_W = 130
    private val ROW_H = 16
    private val SCALE = 0.7f
    private val FOOTER_H = 18

    private var addWidgetFn: Function<ClickableWidget, ClickableWidget>? = null

    private var selectedId: String? = null
    private var sidebarScroll = 0
    private var listScroll = 0

    /** All loot table ids in sorted order. Recomputed when cache changes. */
    private fun allTables(): List<LootTableDef> = AdminLootDataCache.tables

    /** Current edit buffer for the selected table (mutable, drives the UI). */
    private val editEntries = mutableListOf<LootEntry>()
    private var editRolls = 1
    private var dirty = false

    /** Per-row widgets for the editor pane. */
    private data class RowWidgets(
        val itemField: TextFieldWidget,
        val weightField: TextFieldWidget,
        val minField: TextFieldWidget,
        val maxField: TextFieldWidget,
        val deleteBtn: ButtonWidget
    )
    private val rowWidgets = mutableListOf<RowWidgets>()
    private var rollsField: TextFieldWidget? = null
    private var addRowBtn: ButtonWidget? = null
    private var saveBtn: ButtonWidget? = null
    private var resetBtn: ButtonWidget? = null
    private var refreshBtn: ButtonWidget? = null

    fun init(addWidget: Function<ClickableWidget, ClickableWidget>) {
        addWidgetFn = addWidget

        // Bottom action buttons (always present)
        refreshBtn = ButtonWidget.builder(Text.literal("\u00A7bRefresh")) {
            PacketDistributor.sendToServer(AdminLootRequestC2SPacket())
        }.dimensions(x + PADDING, y + h - FOOTER_H + 2, 50, 12).build()
        addWidget.apply(refreshBtn!!)

        addRowBtn = ButtonWidget.builder(Text.literal("\u00A7a+ Add Entry")) {
            editEntries.add(LootEntry("minecraft:apple", 1, 1, 1))
            dirty = true
            rebuildRowWidgets()
        }.dimensions(x + SIDEBAR_W + PADDING + 4, y + h - FOOTER_H + 2, 70, 12).build()
        addWidgetFn?.apply(addRowBtn!!)

        saveBtn = ButtonWidget.builder(Text.literal("\u00A72Save")) { saveCurrent() }
            .dimensions(x + w - 88, y + h - FOOTER_H + 2, 40, 12).build()
        addWidgetFn?.apply(saveBtn!!)

        resetBtn = ButtonWidget.builder(Text.literal("\u00A7cReset")) { resetCurrent() }
            .dimensions(x + w - 46, y + h - FOOTER_H + 2, 42, 12).build()
        addWidgetFn?.apply(resetBtn!!)

        // Trigger initial sync request — server will reply with the current state
        PacketDistributor.sendToServer(AdminLootRequestC2SPacket())
    }

    /** Loads the entries of the currently selected table into the edit buffer. */
    private fun loadIntoBuffer(id: String) {
        selectedId = id
        val def = AdminLootDataCache.get(id)
        editEntries.clear()
        if (def != null) {
            editRolls = def.rolls
            editEntries.addAll(def.entries)
        } else {
            editRolls = 1
        }
        dirty = false
        listScroll = 0
        rebuildRowWidgets()
    }

    /** Wipes and recreates one set of TextFieldWidgets per row. */
    private fun rebuildRowWidgets() {
        // Remove old row widgets from the screen
        for (rw in rowWidgets) {
            rw.itemField.visible = false
            rw.weightField.visible = false
            rw.minField.visible = false
            rw.maxField.visible = false
            rw.deleteBtn.visible = false
        }
        rowWidgets.clear()

        if (rollsField == null) {
            val rf = TextFieldWidget(textRenderer, 0, 0, 30, 12, Text.literal("Rolls"))
            rf.setMaxLength(3)
            rf.text = editRolls.toString()
            rf.setChangedListener { v ->
                v.toIntOrNull()?.let { editRolls = it.coerceAtLeast(1); dirty = true }
            }
            rollsField = rf
            addWidgetFn?.apply(rf)
        } else {
            rollsField!!.text = editRolls.toString()
        }

        for ((idx, entry) in editEntries.withIndex()) {
            val itemF = TextFieldWidget(textRenderer, 0, 0, 100, 12, Text.literal("Item ID"))
            itemF.setMaxLength(64)
            itemF.text = entry.itemId
            itemF.setChangedListener { v ->
                if (idx in editEntries.indices) {
                    editEntries[idx] = editEntries[idx].copy(itemId = v)
                    dirty = true
                }
            }
            val weightF = numberField(entry.weight) { v ->
                if (idx in editEntries.indices) editEntries[idx] = editEntries[idx].copy(weight = v.coerceAtLeast(1))
            }
            val minF = numberField(entry.minCount) { v ->
                if (idx in editEntries.indices) editEntries[idx] = editEntries[idx].copy(minCount = v.coerceAtLeast(1))
            }
            val maxF = numberField(entry.maxCount) { v ->
                if (idx in editEntries.indices) editEntries[idx] = editEntries[idx].copy(maxCount = v.coerceAtLeast(1))
            }
            val capturedIdx = idx
            val delBtn = ButtonWidget.builder(Text.literal("\u00A7c\u00d7")) {
                if (capturedIdx in editEntries.indices) {
                    editEntries.removeAt(capturedIdx)
                    dirty = true
                    rebuildRowWidgets()
                }
            }.dimensions(0, 0, 12, 12).build()

            addWidgetFn?.apply(itemF)
            addWidgetFn?.apply(weightF)
            addWidgetFn?.apply(minF)
            addWidgetFn?.apply(maxF)
            addWidgetFn?.apply(delBtn)
            rowWidgets.add(RowWidgets(itemF, weightF, minF, maxF, delBtn))
        }
    }

    private fun numberField(initial: Int, onChange: (Int) -> Unit): TextFieldWidget {
        val f = TextFieldWidget(textRenderer, 0, 0, 26, 12, Text.literal(""))
        f.setMaxLength(5)
        f.text = initial.toString()
        f.setChangedListener { v ->
            v.toIntOrNull()?.let { onChange(it); dirty = true }
        }
        return f
    }

    private fun saveCurrent() {
        val id = selectedId ?: return
        PacketDistributor.sendToServer(AdminLootUpdateC2SPacket(id, false, editRolls, editEntries.toList()))
        dirty = false
    }

    private fun resetCurrent() {
        val id = selectedId ?: return
        PacketDistributor.sendToServer(AdminLootUpdateC2SPacket(id, true, 1, emptyList()))
        dirty = false
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Background
        context.fill(x, y, x + w, y + h, 0xCC1E1E2E.toInt())

        // Sidebar bg
        context.fill(x, y, x + SIDEBAR_W, y + h, 0xCC15152A.toInt())
        context.fill(x + SIDEBAR_W, y, x + SIDEBAR_W + 1, y + h, 0xFF3A3A5C.toInt())

        // Sidebar header
        drawScaled(context, "\u00A7f\u00A7lLoot Tables", x + PADDING, y + PADDING, 0xFFFFFF, 0.85f)

        val tables = allTables()
        if (tables.isEmpty()) {
            drawScaled(context, "\u00A77Loading…", x + PADDING, y + 14, 0xAAAAAA, 0.7f)
        }

        // Sidebar rows
        val sidebarListY = y + 14
        val sidebarListH = h - 14 - FOOTER_H
        val maxSidebarRows = sidebarListH / ROW_H
        sidebarScroll = sidebarScroll.coerceIn(0, (tables.size - maxSidebarRows).coerceAtLeast(0))

        context.enableScissor(x, sidebarListY, x + SIDEBAR_W, sidebarListY + sidebarListH)
        for (i in 0 until maxSidebarRows + 1) {
            val idx = i + sidebarScroll
            if (idx >= tables.size) break
            val def = tables[idx]
            val rowY = sidebarListY + i * ROW_H
            val isSelected = def.id == selectedId
            val isHovered = mouseX in x..(x + SIDEBAR_W) && mouseY in rowY..(rowY + ROW_H)
            if (isSelected) context.fill(x + 1, rowY, x + SIDEBAR_W, rowY + ROW_H, 0x442196F3)
            else if (isHovered) context.fill(x + 1, rowY, x + SIDEBAR_W, rowY + ROW_H, 0x22FFFFFF)

            val isOverridden = AdminLootDataCache.isOverridden(def.id)
            val color = if (isSelected) 0xFFFFFF else 0xCCCCCC
            // Strip "cobblebase:" prefix for display
            val display = def.id.removePrefix("cobblebase:")
            drawScaled(context, display, x + PADDING + 4, rowY + 4, color, 0.7f)
            if (isOverridden) {
                drawScaled(context, "\u00A76\u00B7", x + SIDEBAR_W - 8, rowY + 3, 0xFF9800, 0.85f)
            }
        }
        context.disableScissor()

        // Sidebar scrollbar
        if (tables.size > maxSidebarRows) {
            val trackX = x + SIDEBAR_W - 3
            val trackH = sidebarListH
            val maxScroll = (tables.size - maxSidebarRows).coerceAtLeast(1)
            val thumbH = ((maxSidebarRows.toFloat() / tables.size) * trackH).toInt().coerceAtLeast(8)
            val thumbY = sidebarListY + ((sidebarScroll.toFloat() / maxScroll) * (trackH - thumbH)).toInt()
            context.fill(trackX, sidebarListY, trackX + 2, sidebarListY + trackH, 0x33FFFFFF)
            context.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xAAFFFFFF.toInt())
        }

        // Right pane header
        val rightX = x + SIDEBAR_W + 2
        val rightW = w - SIDEBAR_W - 2
        val selId = selectedId
        if (selId == null) {
            drawScaled(context, "\u00A77Select a loot table on the left to edit it.", rightX + PADDING, y + PADDING + 4, 0xAAAAAA, 0.75f)
            // Hide row widgets
            for (rw in rowWidgets) { rw.itemField.visible = false; rw.weightField.visible = false; rw.minField.visible = false; rw.maxField.visible = false; rw.deleteBtn.visible = false }
            rollsField?.visible = false
            addRowBtn?.visible = false
            saveBtn?.visible = false
            resetBtn?.visible = false
            return
        }

        addRowBtn?.visible = true
        saveBtn?.visible = true
        resetBtn?.visible = true
        rollsField?.visible = true

        drawScaled(context, "\u00A7f\u00A7l${selId.removePrefix("cobblebase:")}", rightX + PADDING, y + PADDING, 0xFFFFFF, 0.85f)
        if (dirty) drawScaled(context, "\u00A7e*unsaved", rightX + PADDING + 100, y + PADDING + 1, 0xFFFF00, 0.7f)
        if (AdminLootDataCache.isOverridden(selId)) {
            drawScaled(context, "\u00A76[overridden]", rightX + PADDING + 100, y + PADDING + 8, 0xFF9800, 0.65f)
        }

        // Rolls field label
        drawScaled(context, "\u00A77Rolls per generation:", rightX + PADDING, y + 14, 0xAAAAAA, 0.7f)
        rollsField?.let {
            it.x = rightX + PADDING + 70
            it.y = y + 12
        }

        // Column headers
        val headerY = y + 26
        drawScaled(context, "\u00A77Item ID", rightX + PADDING, headerY, 0xAAAAAA, 0.7f)
        drawScaled(context, "\u00A77Weight", rightX + PADDING + 110, headerY, 0xAAAAAA, 0.7f)
        drawScaled(context, "\u00A77Min", rightX + PADDING + 142, headerY, 0xAAAAAA, 0.7f)
        drawScaled(context, "\u00A77Max", rightX + PADDING + 172, headerY, 0xAAAAAA, 0.7f)
        context.fill(rightX + 2, headerY + 9, rightX + rightW - 4, headerY + 10, 0xFF3A3A5C.toInt())

        val listY = headerY + 12
        val listH = y + h - FOOTER_H - listY - 2
        val maxRows = listH / ROW_H
        listScroll = listScroll.coerceIn(0, (rowWidgets.size - maxRows).coerceAtLeast(0))

        for ((i, rw) in rowWidgets.withIndex()) {
            val visualIdx = i - listScroll
            val visible = visualIdx in 0 until maxRows
            rw.itemField.visible = visible
            rw.weightField.visible = visible
            rw.minField.visible = visible
            rw.maxField.visible = visible
            rw.deleteBtn.visible = visible
            if (visible) {
                val rowY = listY + visualIdx * ROW_H
                rw.itemField.x = rightX + PADDING
                rw.itemField.y = rowY
                rw.weightField.x = rightX + PADDING + 108
                rw.weightField.y = rowY
                rw.minField.x = rightX + PADDING + 138
                rw.minField.y = rowY
                rw.maxField.x = rightX + PADDING + 168
                rw.maxField.y = rowY
                rw.deleteBtn.x = rightX + PADDING + 198
                rw.deleteBtn.y = rowY
            }
        }

        // Right scrollbar
        if (rowWidgets.size > maxRows) {
            val trackX = rightX + rightW - 3
            val trackH = listH
            val maxScroll = (rowWidgets.size - maxRows).coerceAtLeast(1)
            val thumbH = ((maxRows.toFloat() / rowWidgets.size) * trackH).toInt().coerceAtLeast(8)
            val thumbY = listY + ((listScroll.toFloat() / maxScroll) * (trackH - thumbH)).toInt()
            context.fill(trackX, listY, trackX + 2, listY + trackH, 0x33FFFFFF)
            context.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xAAFFFFFF.toInt())
        }
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
            val tables = allTables()
            if (idx in tables.indices) {
                loadIntoBuffer(tables[idx].id)
                return true
            }
        }
        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean = false
    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean = false

    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        // Sidebar
        if (mouseX in x.toDouble()..(x + SIDEBAR_W).toDouble() &&
            mouseY in y.toDouble()..(y + h).toDouble()
        ) {
            val tables = allTables()
            val sidebarListH = h - 14 - FOOTER_H
            val maxRows = sidebarListH / ROW_H
            val maxScroll = (tables.size - maxRows).coerceAtLeast(0)
            sidebarScroll = (sidebarScroll - vertical.toInt() * 2).coerceIn(0, maxScroll)
            return true
        }
        // Right list
        if (mouseX in (x + SIDEBAR_W).toDouble()..(x + w).toDouble() &&
            mouseY in y.toDouble()..(y + h).toDouble()
        ) {
            val rightX = x + SIDEBAR_W + 2
            val rightW = w - SIDEBAR_W - 2
            val listY = y + 38
            val listH = y + h - FOOTER_H - listY - 2
            val maxRows = listH / ROW_H
            val maxScroll = (rowWidgets.size - maxRows).coerceAtLeast(0)
            listScroll = (listScroll - vertical.toInt() * 2).coerceIn(0, maxScroll)
            return true
        }
        return false
    }

    fun charTyped(chr: Char, modifiers: Int): Boolean = false
    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean = false
}

package notlown.cobblebase.fabric.client.gui

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import notlown.cobblebase.core.AdminDataCache
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillRegistry
import notlown.cobblebase.core.net.AdminSpeciesUpdateC2SPacket
import java.util.function.Function

/**
 * Right pane of the Admin GUI: skill toggle + proficiency editor for selected species.
 * Producer config is embedded at the top, before the skill grid.
 */
class AdminSkillEditorPanel(
    private val x: Int,
    private val y: Int,
    private val w: Int,
    private val h: Int,
    private val textRenderer: TextRenderer,
    private val onSaved: () -> Unit
) {
    private val ROW_HEIGHT = 16
    private val PADDING = 6
    private val STAR_SIZE = 8
    private val PRODUCER_SECTION_H = 30

    private val CHECKBOX_ON = 0xFF4CAF50.toInt()
    private val CHECKBOX_OFF = 0xFF555555.toInt()
    private val STAR_FILLED = 0xFFFFD700.toInt()
    private val STAR_EMPTY = 0xFF444444.toInt()

    private val CATEGORY_COLORS = mapOf(
        "gathering" to 0xFF4CAF50.toInt(),
        "generation" to 0xFFFF9800.toInt(),
        "combat" to 0xFFF44336.toInt(),
        "support" to 0xFFE91E9E.toInt(),
        "utility" to 0xFF2196F3.toInt(),
        "legendary" to 0xFFFFD700.toInt(),
        "social" to 0xFFFF55FF.toInt()
    )

    var selectedSpecies: String? = null
        private set

    private val skillEdits = mutableListOf<AdminButtonData>()
    private var scrollOffset = 0
    private var isDraggingScrollbar = false
    private var saveButton: ButtonWidget? = null
    private var resetButton: ButtonWidget? = null
    private var dirty = false

    // Producer state
    private var producerItemField: TextFieldWidget? = null
    private var producerCount = 1
    private var producerEnabled = false       // Producer skill is assigned
    private var producerJobActive = true      // Toggle: job active or paused (item stays configured)
    private var itemSuggestions: List<String> = emptyList()
    private var showSuggestions = false
    private var selectedSuggestionIdx = -1
    private var allItemIds: List<String>? = null

    fun init(addWidget: Function<ButtonWidget, ButtonWidget>) {
        saveButton = ButtonWidget.builder(Text.literal("\u00A72Save")) { saveChanges() }
            .dimensions(x + w - 90, y + h - 18, 40, 14).build()
        addWidget.apply(saveButton!!)

        resetButton = ButtonWidget.builder(Text.literal("\u00A7cReset")) { resetToDefault() }
            .dimensions(x + w - 46, y + h - 18, 42, 14).build()
        addWidget.apply(resetButton!!)
    }

    fun initProducerField(addDrawable: (net.minecraft.client.gui.widget.ClickableWidget) -> Unit) {
        // Initial position — updated every frame in render()
        producerItemField = TextFieldWidget(textRenderer, x + PADDING + 56, y + 20, w - PADDING * 2 - 110, 12, Text.literal(""))
        producerItemField!!.setMaxLength(128)
        producerItemField!!.setChangedListener { text ->
            dirty = true
            updateSuggestions(text)
        }
        producerItemField!!.visible = false
        addDrawable(producerItemField!!)
    }

    private fun updateSuggestions(text: String) {
        if (text.isBlank()) {
            itemSuggestions = emptyList()
            showSuggestions = false
            return
        }
        if (allItemIds == null) {
            allItemIds = Registries.ITEM.ids.map { it.toString() }.sorted()
        }
        val query = text.lowercase()
        itemSuggestions = allItemIds!!.filter { it.contains(query) }.take(8)
        showSuggestions = itemSuggestions.isNotEmpty()
        selectedSuggestionIdx = -1
    }

    fun setSpecies(species: String) {
        selectedSpecies = species
        scrollOffset = 0
        dirty = false
        lastCachedSkillsRef = null
        lastCachedProducerRef = null
        showSuggestions = false
        if (!AdminDataCache.speciesSkills.containsKey(species) && AdminDataCache.markPending(species)) {
            ClientPlayNetworking.send(notlown.cobblebase.core.net.AdminSpeciesSkillsRequestC2SPacket(species))
        }
        rebuildSkillEdits()
        loadProducerData()
    }

    private fun loadProducerData() {
        val data = AdminDataCache.speciesProducer[selectedSpecies ?: return]
        if (data != null) {
            producerItemField?.text = data.itemId
            producerCount = data.count
            producerJobActive = true
        } else {
            producerItemField?.text = ""
            producerCount = 1
            producerJobActive = true
        }
        updateProducerVisibility()
    }

    private fun updateProducerVisibility() {
        producerEnabled = skillEdits.any { it.skillId == "cobblebase:producer" && it.assigned }
        producerItemField?.visible = producerEnabled
    }

    fun refreshProducerVisibility() {
        producerItemField?.visible = producerEnabled
    }

    private var lastCachedSkillsRef: List<SkillEntry>? = null
    private var lastCachedProducerRef: AdminDataCache.ProducerData? = null

    private fun refreshIfCacheUpdated() {
        val species = selectedSpecies ?: return
        val current = AdminDataCache.speciesSkills[species]
        if (current !== lastCachedSkillsRef) {
            lastCachedSkillsRef = current
            rebuildSkillEdits()
        }
        val currentProducer = AdminDataCache.speciesProducer[species]
        if (currentProducer !== lastCachedProducerRef) {
            lastCachedProducerRef = currentProducer
            loadProducerData()
        }
    }

    private fun rebuildSkillEdits() {
        skillEdits.clear()
        val species = selectedSpecies ?: return
        val currentSkills = AdminDataCache.speciesSkills[species] ?: emptyList()
        val skillMap = currentSkills.associateBy { it.skillId }

        val allSkills = SkillRegistry.getAll().values.sortedWith(
            compareBy({ it.category }, { it.name })
        )

        for (skill in allSkills) {
            val existing = skillMap[skill.id]
            skillEdits.add(AdminButtonData(
                skill.id, skill.name, skill.category,
                existing != null, existing?.proficiency ?: 3
            ))
        }
        updateProducerVisibility()
    }

    private fun saveChanges() {
        val species = selectedSpecies ?: return
        val assignedSkills = skillEdits.filter { it.assigned }.map {
            SkillEntry(it.skillId, it.proficiency)
        }

        val producerItem = if (producerEnabled && producerJobActive && !producerItemField?.text.isNullOrBlank())
            producerItemField!!.text else null
        val pCount = if (producerEnabled && producerJobActive) producerCount else 0
        // If producer is enabled but job is paused, send a reset to remove the override
        // (the skill stays assigned, but no item is produced)
        val pReset = producerEnabled && !producerJobActive

        ClientPlayNetworking.send(AdminSpeciesUpdateC2SPacket(
            species, assignedSkills, false,
            producerItem, pCount, pReset
        ))

        AdminDataCache.updateLocalSkills(species, assignedSkills)
        if (producerItem != null) {
            val displayName = producerItem.substringAfterLast(":")
                .replace("_", " ")
                .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            AdminDataCache.setSpeciesProducer(species, AdminDataCache.ProducerData(producerItem, pCount, displayName))
        } else {
            AdminDataCache.setSpeciesProducer(species, null)
        }

        dirty = false
        onSaved()
    }

    private fun resetToDefault() {
        val species = selectedSpecies ?: return
        ClientPlayNetworking.send(AdminSpeciesUpdateC2SPacket(species, emptyList(), true, null, 0, true))
        ClientPlayNetworking.send(notlown.cobblebase.core.net.AdminSpeciesRequestC2SPacket())
        dirty = false
    }

    // ========== RENDERING ==========

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        refreshIfCacheUpdated()
        context.fill(x, y, x + w, y + h, 0xCC1E1E2E.toInt())

        val species = selectedSpecies
        if (species == null) {
            context.drawTextWithShadow(textRenderer, "\u00A77Select a species from the list", x + PADDING, y + h / 2, 0x888888)
            producerItemField?.visible = false
            return
        }

        val scale = 0.75f

        // --- Header ---
        val displayName = species.replaceFirstChar { it.uppercase() }
        val isOverridden = AdminDataCache.overriddenSpecies.contains(species)
        val cachedSkills = AdminDataCache.speciesSkills[species]
        val countSuffix = when {
            cachedSkills == null && AdminDataCache.isPending(species) -> " \u00A77(loading...)"
            cachedSkills != null -> " \u00A7a(${cachedSkills.size} skills)"
            else -> ""
        }
        val headerText = if (isOverridden) "$displayName \u00A76[Override]$countSuffix" else "$displayName$countSuffix"

        context.matrices.push()
        context.matrices.translate((x + PADDING).toFloat(), (y + PADDING).toFloat(), 0f)
        context.matrices.scale(scale, scale, 1f)
        context.drawTextWithShadow(textRenderer, "\u00A7f\u00A7l$headerText", 0, 0, 0xFFFFFF)
        if (dirty) {
            context.drawTextWithShadow(textRenderer, "\u00A7e*unsaved", textRenderer.getWidth(headerText) + 10, 0, 0xFFFF00)
        }
        context.matrices.pop()

        PokemonSpriteHelper.renderSmallIconByName(
            context, textRenderer, species,
            x + w - PokemonSpriteHelper.ICON_SIZE - PADDING, y + PADDING - 2, delta
        )

        // --- Producer Section (top, before skill grid) ---
        val producerH = if (producerEnabled) PRODUCER_SECTION_H else 0
        if (producerEnabled) {
            renderProducerSection(context, mouseX, mouseY, scale)
        }

        // --- Skill Grid ---
        val COLS = 3
        val colW = (w - PADDING * 2) / COLS
        val listY = y + PADDING + 14 + producerH
        val listH = h - PADDING * 2 - 32 - producerH
        val gridRows = buildGridRows(COLS)

        val maxVisible = listH / ROW_HEIGHT
        val maxScroll = (gridRows.size - maxVisible).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        context.enableScissor(x, listY, x + w, listY + listH)

        for (i in 0 until maxVisible + 2) {
            val idx = i + scrollOffset
            if (idx >= gridRows.size) break
            val row = gridRows[idx]
            val rowY = listY + i * ROW_HEIGHT

            if (row.isHeader) {
                val catColor = CATEGORY_COLORS[row.category] ?: 0xFFAAAAAA.toInt()
                context.fill(x + 2, rowY, x + w - 2, rowY + ROW_HEIGHT, 0x22FFFFFF)
                context.fill(x + 2, rowY + ROW_HEIGHT - 1, x + w - 2, rowY + ROW_HEIGHT, catColor)
                context.matrices.push()
                context.matrices.translate((x + PADDING).toFloat(), (rowY + 4).toFloat(), 0f)
                context.matrices.scale(scale, scale, 1f)
                context.drawTextWithShadow(textRenderer, "\u00A7l${row.category.replaceFirstChar { it.uppercase() }}", 0, 0, catColor)
                context.matrices.pop()
            } else {
                for ((col, skillIdx) in row.skills.withIndex()) {
                    val skill = skillEdits[skillIdx]
                    val cellX = x + PADDING + col * colW
                    val cbX = cellX
                    val cbY = rowY + 3
                    val cbSize = 8
                    val cbColor = if (skill.assigned) CHECKBOX_ON else CHECKBOX_OFF
                    context.fill(cbX, cbY, cbX + cbSize, cbY + cbSize, cbColor)
                    if (skill.assigned) {
                        context.matrices.push()
                        context.matrices.translate((cbX + 1).toFloat(), cbY.toFloat(), 0f)
                        context.matrices.scale(scale, scale, 1f)
                        context.drawTextWithShadow(textRenderer, "\u2713", 0, 0, 0xFFFFFF)
                        context.matrices.pop()
                    }
                    val nameColor = if (skill.assigned) 0xFFFFFF else 0x888888
                    context.matrices.push()
                    context.matrices.translate((cbX + cbSize + 2).toFloat(), (rowY + 4).toFloat(), 0f)
                    context.matrices.scale(scale, scale, 1f)
                    context.drawTextWithShadow(textRenderer, skill.displayName, 0, 0, nameColor)
                    context.matrices.pop()
                    if (skill.assigned) {
                        val starsX = cellX + colW - 42
                        context.matrices.push()
                        context.matrices.translate(starsX.toFloat(), (rowY + 4).toFloat(), 0f)
                        context.matrices.scale(scale, scale, 1f)
                        for (star in 1..5) {
                            val color = if (star <= skill.proficiency) STAR_FILLED else STAR_EMPTY
                            context.drawTextWithShadow(textRenderer, "\u2605", (star - 1) * (STAR_SIZE + 1), 0, color)
                        }
                        context.matrices.pop()
                    }
                }
            }
        }

        context.disableScissor()

        // Scrollbar
        if (gridRows.size > maxVisible) {
            val trackX = x + w - 3
            val thumbH = ((maxVisible.toFloat() / gridRows.size) * listH).toInt().coerceAtLeast(10)
            val thumbY = listY + ((scrollOffset.toFloat() / maxScroll) * (listH - thumbH)).toInt()
            context.fill(trackX, listY, trackX + 2, listY + listH, 0x33FFFFFF)
            context.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xAAFFFFFF.toInt())
        }

        // --- Suggestion Dropdown (over everything) ---
        if (showSuggestions && producerEnabled && itemSuggestions.isNotEmpty()) {
            val field = producerItemField ?: return
            val dropX = field.x
            val dropY = field.y + field.height + 1
            val dropW = field.width
            val itemH = 10
            context.fill(dropX, dropY, dropX + dropW, dropY + itemSuggestions.size * itemH, 0xEE1E1E2E.toInt())
            context.fill(dropX, dropY, dropX + dropW, dropY + itemSuggestions.size * itemH, 0x44FFFFFF)
            for ((idx, suggestion) in itemSuggestions.withIndex()) {
                val sy = dropY + idx * itemH
                val isHovered = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= sy && mouseY < sy + itemH
                if (isHovered || idx == selectedSuggestionIdx) {
                    context.fill(dropX, sy, dropX + dropW, sy + itemH, 0x44FFD700)
                }
                context.matrices.push()
                context.matrices.translate((dropX + 2).toFloat(), (sy + 1).toFloat(), 0f)
                context.matrices.scale(0.7f, 0.7f, 1f)
                context.drawTextWithShadow(textRenderer, suggestion, 0, 0, 0xCCCCCC)
                context.matrices.pop()
            }
        }
    }

    private fun renderProducerSection(context: DrawContext, mouseX: Int, mouseY: Int, scale: Float) {
        val psY = y + PADDING + 14
        val psH = PRODUCER_SECTION_H

        // Background bar
        val bgColor = if (producerJobActive) 0x33FF9800 else 0x22555555
        context.fill(x + 2, psY, x + w - 2, psY + psH, bgColor)
        // Top accent line
        val accentColor = if (producerJobActive) 0xFFFF9800.toInt() else 0xFF666666.toInt()
        context.fill(x + 2, psY, x + w - 2, psY + 1, accentColor)

        // Enable/Disable toggle button (left side)
        val toggleX = x + PADDING
        val toggleY = psY + 4
        val toggleW = 8
        val toggleH = 8
        val toggleColor = if (producerJobActive) CHECKBOX_ON else 0xFFFF5555.toInt()
        context.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, toggleColor)
        context.matrices.push()
        context.matrices.translate((toggleX + 1).toFloat(), toggleY.toFloat(), 0f)
        context.matrices.scale(scale, scale, 1f)
        context.drawTextWithShadow(textRenderer, if (producerJobActive) "\u2713" else "\u2717", 0, 0, 0xFFFFFF)
        context.matrices.pop()

        // "Produces:" label
        val labelX = toggleX + toggleW + 4
        context.matrices.push()
        context.matrices.translate(labelX.toFloat(), (psY + 5).toFloat(), 0f)
        context.matrices.scale(scale, scale, 1f)
        val labelColor = if (producerJobActive) 0xFFFF9800.toInt() else 0xFF888888.toInt()
        context.drawTextWithShadow(textRenderer, "\u00A7lProduces:", 0, 0, labelColor)
        context.matrices.pop()

        // Position text field (rendered by widget system)
        producerItemField?.let { field ->
            field.x = x + PADDING + 56
            field.y = psY + 3
            field.width = w - PADDING * 2 - 110
            field.active = producerJobActive
        }

        // Count: [-] x1 [+] (right side)
        val countX = x + w - PADDING - 44
        val countY = psY + 5
        context.matrices.push()
        context.matrices.translate(countX.toFloat(), countY.toFloat(), 0f)
        context.matrices.scale(scale, scale, 1f)
        val countColor = if (producerJobActive) 0xFFFFFF else 0x666666
        val minusHover = producerJobActive && mouseX >= countX && mouseX < countX + 8 && mouseY >= countY - 1 && mouseY < countY + 10
        context.drawTextWithShadow(textRenderer, "-", 0, 0, if (minusHover) 0xFF5555 else if (producerJobActive) 0xAAAAAA else 0x555555)
        context.drawTextWithShadow(textRenderer, "x$producerCount", 12, 0, countColor)
        val plusHover = producerJobActive && mouseX >= countX + 34 && mouseX < countX + 44 && mouseY >= countY - 1 && mouseY < countY + 10
        context.drawTextWithShadow(textRenderer, "+", 38, 0, if (plusHover) 0x55FF55 else if (producerJobActive) 0xAAAAAA else 0x555555)
        context.matrices.pop()

        // Status text row
        val statusY = psY + 17
        context.matrices.push()
        context.matrices.translate((x + PADDING + 12).toFloat(), (statusY).toFloat(), 0f)
        context.matrices.scale(0.6f, 0.6f, 1f)
        if (!producerJobActive) {
            context.drawTextWithShadow(textRenderer, "Producer paused (item saved)", 0, 0, 0xFF8888)
        } else {
            val currentText = producerItemField?.text ?: ""
            if (currentText.isNotBlank()) {
                val validItem = allItemIds?.contains(currentText) ?: false
                val statusColor = if (validItem) 0xFF55FF55.toInt() else 0xFFFF5555.toInt()
                context.drawTextWithShadow(textRenderer, if (validItem) "Valid item" else "Unknown item ID", 0, 0, statusColor)
            }
        }
        context.matrices.pop()
    }

    // ========== CLICK HANDLING ==========

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val species = selectedSpecies ?: return false

        // --- Suggestion clicks ---
        if (showSuggestions && producerEnabled && itemSuggestions.isNotEmpty()) {
            val field = producerItemField ?: return false
            val dropX = field.x
            val dropY = field.y + field.height + 1
            val dropW = field.width
            val itemH = 10
            if (mouseX >= dropX && mouseX <= dropX + dropW) {
                for ((idx, suggestion) in itemSuggestions.withIndex()) {
                    val sy = dropY + idx * itemH
                    if (mouseY >= sy && mouseY < sy + itemH) {
                        producerItemField?.text = suggestion
                        showSuggestions = false
                        dirty = true
                        return true
                    }
                }
            }
            showSuggestions = false
        }

        // --- Producer section clicks ---
        if (producerEnabled) {
            val psY = y + PADDING + 14

            // Toggle button
            val toggleX = x + PADDING
            val toggleY = psY + 4
            if (mouseX >= toggleX && mouseX < toggleX + 10 && mouseY >= toggleY - 1 && mouseY < toggleY + 10) {
                producerJobActive = !producerJobActive
                dirty = true
                return true
            }

            // Count +/- (only when active)
            if (producerJobActive) {
                val countX = x + w - PADDING - 44
                val countY = psY + 5
                if (mouseY >= countY - 1 && mouseY < countY + 10) {
                    if (mouseX >= countX && mouseX < countX + 10) {
                        producerCount = (producerCount - 1).coerceAtLeast(1)
                        dirty = true
                        return true
                    }
                    if (mouseX >= countX + 28 && mouseX < countX + 44) {
                        producerCount = (producerCount + 1).coerceAtMost(64)
                        dirty = true
                        return true
                    }
                }
            }
        }

        // --- Skill grid clicks ---
        val COLS = 3
        val colW = (w - PADDING * 2) / COLS
        val producerH = if (producerEnabled) PRODUCER_SECTION_H else 0
        val listY = y + PADDING + 14 + producerH
        val listH = h - PADDING * 2 - 32 - producerH
        val gridRows = buildGridRows(COLS)

        val maxVisible = listH / ROW_HEIGHT
        val maxScroll = (gridRows.size - maxVisible).coerceAtLeast(0)

        // Scrollbar
        val trackX = x + w - 3
        if (maxScroll > 0 && mouseX >= trackX - 4 && mouseX <= trackX + 4 && mouseY >= listY && mouseY <= listY + listH) {
            isDraggingScrollbar = true
            val relativeY = ((mouseY - listY) / listH.toDouble()).coerceIn(0.0, 1.0)
            scrollOffset = (relativeY * maxScroll).toInt()
            return true
        }

        if (mouseX >= x && mouseX <= x + w && mouseY >= listY && mouseY <= listY + listH) {
            val rowVisIdx = ((mouseY - listY) / ROW_HEIGHT).toInt()
            val rowIdx = rowVisIdx + scrollOffset

            if (rowIdx in gridRows.indices) {
                val row = gridRows[rowIdx]
                if (!row.isHeader) {
                    val col = ((mouseX - x - PADDING) / colW).toInt().coerceIn(0, COLS - 1)
                    if (col < row.skills.size) {
                        val skillIdx = row.skills[col]
                        val skill = skillEdits[skillIdx]
                        val cellX = x + PADDING + col * colW

                        if (mouseX >= cellX && mouseX <= cellX + 12) {
                            skill.assigned = !skill.assigned
                            dirty = true
                            updateProducerVisibility()
                            return true
                        }

                        if (skill.assigned) {
                            val starsX = cellX + colW - 42
                            if (mouseX >= starsX && mouseX <= starsX + 5 * (STAR_SIZE + 1)) {
                                val starClicked = ((mouseX - starsX) / (STAR_SIZE + 1)).toInt() + 1
                                skill.proficiency = starClicked.coerceIn(1, 5)
                                dirty = true
                                return true
                            }
                        }

                        skill.assigned = !skill.assigned
                        dirty = true
                        updateProducerVisibility()
                        return true
                    }
                }
            }
        }
        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (isDraggingScrollbar) {
            val producerH = if (producerEnabled) PRODUCER_SECTION_H else 0
            val listY = y + PADDING + 14 + producerH
            val listH = h - PADDING * 2 - 32 - producerH
            val maxVisible = listH / ROW_HEIGHT
            val gridRows = buildGridRows(3)
            val maxScroll = (gridRows.size - maxVisible).coerceAtLeast(0)
            val relativeY = ((mouseY - listY) / listH.toDouble()).coerceIn(0.0, 1.0)
            scrollOffset = (relativeY * maxScroll).toInt()
            return true
        }
        return false
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (isDraggingScrollbar) { isDraggingScrollbar = false; return true }
        return false
    }

    private var scrollAccumulator = 0.0

    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
            scrollAccumulator -= verticalAmount
            val whole = scrollAccumulator.toInt()
            scrollAccumulator -= whole.toDouble()
            scrollOffset = (scrollOffset + whole).coerceAtLeast(0)
            val producerH = if (producerEnabled) PRODUCER_SECTION_H else 0
            val listH = h - PADDING * 2 - 32 - producerH
            val gridRows = buildGridRows(3)
            val maxScroll = (gridRows.size - (listH / ROW_HEIGHT)).coerceAtLeast(0)
            scrollOffset = scrollOffset.coerceAtMost(maxScroll)
            return true
        }
        return false
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (showSuggestions && itemSuggestions.isNotEmpty()) {
            when (keyCode) {
                264 -> { selectedSuggestionIdx = (selectedSuggestionIdx + 1) % itemSuggestions.size; return true }
                265 -> { selectedSuggestionIdx = if (selectedSuggestionIdx <= 0) itemSuggestions.size - 1 else selectedSuggestionIdx - 1; return true }
                257, 335 -> {
                    if (selectedSuggestionIdx in itemSuggestions.indices) {
                        producerItemField?.text = itemSuggestions[selectedSuggestionIdx]
                        showSuggestions = false; dirty = true; return true
                    }
                }
                256 -> { showSuggestions = false; return true }
            }
        }
        return false
    }

    // ========== HELPERS ==========

    private data class GridRow(val isHeader: Boolean, val category: String = "", val skills: List<Int> = emptyList())

    private fun buildGridRows(cols: Int): List<GridRow> {
        val rows = mutableListOf<GridRow>()
        var lastCat = ""
        var catSkills = mutableListOf<Int>()
        fun flush() {
            if (catSkills.isNotEmpty()) {
                for (c in catSkills.chunked(cols)) rows.add(GridRow(false, skills = c))
                catSkills = mutableListOf()
            }
        }
        for ((i, skill) in skillEdits.withIndex()) {
            if (skill.category != lastCat) { flush(); rows.add(GridRow(true, category = skill.category)); lastCat = skill.category }
            catSkills.add(i)
        }
        flush()
        return rows
    }
}

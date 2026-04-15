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
 * Includes Producer item configuration when the Producer skill is assigned.
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
    private val CATEGORY_HEADER_HEIGHT = 18
    private val STAR_SIZE = 8

    private val CHECKBOX_ON = 0xFF4CAF50.toInt()
    private val CHECKBOX_OFF = 0xFF555555.toInt()
    private val STAR_FILLED = 0xFFFFD700.toInt()
    private val STAR_EMPTY = 0xFF444444.toInt()
    private val SAVE_COLOR = 0xFF4CAF50.toInt()
    private val RESET_COLOR = 0xFFFF5722.toInt()

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

    // Mutable skill data for editing
    private val skillEdits = mutableListOf<AdminButtonData>()
    private var scrollOffset = 0
    private var isDraggingScrollbar = false
    private var saveButton: ButtonWidget? = null
    private var resetButton: ButtonWidget? = null
    private var dirty = false
    private var lastGridRows = listOf<Any>()

    // Producer editing state
    private var producerItemField: TextFieldWidget? = null
    private var producerCount = 1
    private var producerEnabled = false
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

    /** Must be called from AdminScreen.init() to add the TextFieldWidget */
    fun initProducerField(addDrawable: (net.minecraft.client.gui.widget.ClickableWidget) -> Unit) {
        val fieldY = y + h - 52
        producerItemField = TextFieldWidget(textRenderer, x + PADDING + 70, fieldY, w - PADDING * 2 - 120, 12, Text.literal(""))
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
        itemSuggestions = allItemIds!!
            .filter { it.contains(query) }
            .take(8)
        showSuggestions = itemSuggestions.isNotEmpty()
        selectedSuggestionIdx = -1
    }

    fun setSpecies(species: String) {
        selectedSpecies = species
        scrollOffset = 0
        dirty = false
        lastCachedSkillsRef = null
        showSuggestions = false
        // Lazy load: request skills from server if not cached yet
        if (!AdminDataCache.speciesSkills.containsKey(species) && AdminDataCache.markPending(species)) {
            ClientPlayNetworking.send(notlown.cobblebase.core.net.AdminSpeciesSkillsRequestC2SPacket(species))
        }
        rebuildSkillEdits()
        loadProducerData()
    }

    private fun loadProducerData() {
        val species = selectedSpecies ?: return
        val data = AdminDataCache.speciesProducer[species]
        if (data != null) {
            producerItemField?.text = data.itemId
            producerCount = data.count
        } else {
            producerItemField?.text = ""
            producerCount = 1
        }
        updateProducerVisibility()
    }

    private fun updateProducerVisibility() {
        producerEnabled = skillEdits.any { it.skillId == "cobblebase:producer" && it.assigned }
        producerItemField?.visible = producerEnabled
    }

    /** Called by AdminScreen after updateWidgetVisibility to ensure producer field respects its own logic */
    fun refreshProducerVisibility() {
        producerItemField?.visible = producerEnabled
    }

    // Track the last cached skills reference to detect when lazy-loaded data arrives
    private var lastCachedSkillsRef: List<SkillEntry>? = null
    private var lastCachedProducerRef: AdminDataCache.ProducerData? = null

    /** Called from render() — rebuilds if cache was updated since last render */
    private fun refreshIfCacheUpdated() {
        val species = selectedSpecies ?: return
        val current = AdminDataCache.speciesSkills[species]
        if (current !== lastCachedSkillsRef) {
            lastCachedSkillsRef = current
            rebuildSkillEdits()
        }
        // Also refresh producer when data arrives
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

        // All skills from SkillRegistry, grouped by category
        val allSkills = SkillRegistry.getAll().values.sortedWith(
            compareBy({ it.category }, { it.name })
        )

        for (skill in allSkills) {
            val existing = skillMap[skill.id]
            skillEdits.add(AdminButtonData(
                skill.id,
                skill.name,
                skill.category,
                existing != null,
                existing?.proficiency ?: 3
            ))
        }
        updateProducerVisibility()
    }

    private fun saveChanges() {
        val species = selectedSpecies ?: return
        val assignedSkills = skillEdits.filter { it.assigned }.map {
            SkillEntry(it.skillId, it.proficiency)
        }

        // Producer data
        val producerItem = if (producerEnabled && !producerItemField?.text.isNullOrBlank())
            producerItemField!!.text else null
        val pCount = if (producerEnabled) producerCount else 0

        ClientPlayNetworking.send(AdminSpeciesUpdateC2SPacket(
            species, assignedSkills, false,
            producerItem, pCount, false
        ))

        // Update local cache
        AdminDataCache.updateLocalSkills(species, assignedSkills)
        if (producerItem != null) {
            val displayName = producerItem.substringAfterLast(":")
                .replace("_", " ")
                .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            AdminDataCache.setSpeciesProducer(species, AdminDataCache.ProducerData(producerItem, pCount, displayName))
        } else if (producerEnabled) {
            AdminDataCache.setSpeciesProducer(species, null)
        }

        dirty = false
        onSaved()
    }

    private fun resetToDefault() {
        val species = selectedSpecies ?: return
        ClientPlayNetworking.send(AdminSpeciesUpdateC2SPacket(species, emptyList(), true, null, 0, true))

        // Update local cache: re-request data
        ClientPlayNetworking.send(notlown.cobblebase.core.net.AdminSpeciesRequestC2SPacket())
        dirty = false
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        refreshIfCacheUpdated()
        // Background
        context.fill(x, y, x + w, y + h, 0xCC1E1E2E.toInt())

        val species = selectedSpecies
        if (species == null) {
            context.drawTextWithShadow(textRenderer, "\u00A77Select a species from the list", x + PADDING, y + h / 2, 0x888888)
            return
        }

        // Header row with sprite on left + name + skill count
        val displayName = species.replaceFirstChar { it.uppercase() }
        val isOverridden = AdminDataCache.overriddenSpecies.contains(species)
        val cachedSkills = AdminDataCache.speciesSkills[species]
        val countSuffix = when {
            cachedSkills == null && AdminDataCache.isPending(species) -> " \u00A77(loading...)"
            cachedSkills != null -> " \u00A7a(${cachedSkills.size} skills)"
            else -> ""
        }
        val headerText = if (isOverridden) "$displayName \u00A76[Override]$countSuffix" else "$displayName$countSuffix"

        val hdrScale = 0.75f
        context.matrices.push()
        context.matrices.translate((x + PADDING).toFloat(), (y + PADDING).toFloat(), 0f)
        context.matrices.scale(hdrScale, hdrScale, 1f)
        context.drawTextWithShadow(textRenderer, "\u00A7f\u00A7l$headerText", 0, 0, 0xFFFFFF)
        if (dirty) {
            context.drawTextWithShadow(textRenderer, "\u00A7e*unsaved", textRenderer.getWidth(headerText) + 10, 0, 0xFFFF00)
        }
        context.matrices.pop()

        // Sprite top-right corner — standard small icon (same as rest of GUI)
        PokemonSpriteHelper.renderSmallIconByName(
            context, textRenderer, species,
            x + w - PokemonSpriteHelper.ICON_SIZE - PADDING,
            y + PADDING - 2,
            delta
        )

        // Calculate producer section height
        val producerSectionH = if (producerEnabled) 40 else 0

        // 3-column grid layout per category
        val COLS = 3
        val colW = (w - PADDING * 2) / COLS
        val listY = y + PADDING + 14
        val listH = h - PADDING * 2 - 32 - producerSectionH
        val scale = 0.75f

        // Build grid rows: category header (full width) + skill rows (3 per row)
        data class GridRow(val isHeader: Boolean, val category: String = "", val skills: List<Int> = emptyList())
        val gridRows = mutableListOf<GridRow>()
        var lastCategory = ""
        var currentCatSkills = mutableListOf<Int>()

        fun flushCategory() {
            if (currentCatSkills.isEmpty()) return
            for (chunk in currentCatSkills.chunked(COLS)) {
                gridRows.add(GridRow(isHeader = false, skills = chunk))
            }
            currentCatSkills = mutableListOf()
        }

        for ((i, skill) in skillEdits.withIndex()) {
            if (skill.category != lastCategory) {
                flushCategory()
                gridRows.add(GridRow(isHeader = true, category = skill.category))
                lastCategory = skill.category
            }
            currentCatSkills.add(i)
        }
        flushCategory()

        val maxVisible = listH / ROW_HEIGHT
        val maxScroll = (gridRows.size - maxVisible).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        context.enableScissor(x, listY, x + w, listY + listH)

        // Store grid rows for click handling
        lastGridRows = gridRows

        for (i in 0 until maxVisible + 2) {
            val idx = i + scrollOffset
            if (idx >= gridRows.size) break
            val row = gridRows[idx]
            val rowY = listY + i * ROW_HEIGHT

            if (row.isHeader) {
                val catColor = CATEGORY_COLORS[row.category] ?: 0xFFAAAAAA.toInt()
                context.fill(x + 2, rowY, x + w - 2, rowY + ROW_HEIGHT, 0x22FFFFFF)
                context.fill(x + 2, rowY + ROW_HEIGHT - 1, x + w - 2, rowY + ROW_HEIGHT, catColor)
                val catName = row.category.replaceFirstChar { it.uppercase() }
                context.matrices.push()
                context.matrices.translate((x + PADDING).toFloat(), (rowY + 4).toFloat(), 0f)
                context.matrices.scale(scale, scale, 1f)
                context.drawTextWithShadow(textRenderer, "\u00A7l$catName", 0, 0, catColor)
                context.matrices.pop()
            } else {
                for ((col, skillIdx) in row.skills.withIndex()) {
                    val skill = skillEdits[skillIdx]
                    val cellX = x + PADDING + col * colW

                    // Checkbox
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

                    // Skill name
                    val nameColor = if (skill.assigned) 0xFFFFFF else 0x888888
                    context.matrices.push()
                    context.matrices.translate((cbX + cbSize + 2).toFloat(), (rowY + 4).toFloat(), 0f)
                    context.matrices.scale(scale, scale, 1f)
                    context.drawTextWithShadow(textRenderer, skill.displayName, 0, 0, nameColor)
                    context.matrices.pop()

                    // Stars (if assigned)
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
            val trackH = listH
            val thumbH = ((maxVisible.toFloat() / gridRows.size) * trackH).toInt().coerceAtLeast(10)
            val thumbY = listY + ((scrollOffset.toFloat() / maxScroll) * (trackH - thumbH)).toInt()
            context.fill(trackX, listY, trackX + 2, listY + trackH, 0x33FFFFFF)
            context.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xAAFFFFFF.toInt())
        }

        // --- Producer Section ---
        if (producerEnabled) {
            val psY = listY + listH + 2
            // Background + border
            context.fill(x + PADDING, psY, x + w - PADDING, psY + 36, 0x44FF9800)
            context.fill(x + PADDING, psY, x + w - PADDING, psY + 1, 0xFFFF9800.toInt())

            // "Producer:" label
            context.matrices.push()
            context.matrices.translate((x + PADDING + 2).toFloat(), (psY + 4).toFloat(), 0f)
            context.matrices.scale(scale, scale, 1f)
            context.drawTextWithShadow(textRenderer, "\u00A76\u00A7lProduces:", 0, 0, 0xFF9800)
            context.matrices.pop()

            // Position the text field (it renders itself via Minecraft widget system)
            producerItemField?.let { field ->
                field.x = x + PADDING + 70
                field.y = psY + 2
                field.width = w - PADDING * 2 - 120
            }

            // Count display with +/- buttons
            val countX = x + w - PADDING - 44
            val countY = psY + 3
            context.matrices.push()
            context.matrices.translate(countX.toFloat(), countY.toFloat(), 0f)
            context.matrices.scale(scale, scale, 1f)
            // [-] button
            val minusHover = mouseX >= countX && mouseX < countX + 8 && mouseY >= countY && mouseY < countY + 10
            context.drawTextWithShadow(textRenderer, "-", 0, 0, if (minusHover) 0xFF5555 else 0xAAAAAA)
            // Count value
            context.drawTextWithShadow(textRenderer, "x$producerCount", 12, 0, 0xFFFFFF)
            // [+] button
            val plusHover = mouseX >= countX + 34 && mouseX < countX + 44 && mouseY >= countY && mouseY < countY + 10
            context.drawTextWithShadow(textRenderer, "+", 38, 0, if (plusHover) 0x55FF55 else 0xAAAAAA)
            context.matrices.pop()

            // Current produce info (below field)
            val infoY = psY + 16
            val currentText = producerItemField?.text ?: ""
            if (currentText.isNotBlank()) {
                context.matrices.push()
                context.matrices.translate((x + PADDING).toFloat(), (infoY + 2).toFloat(), 0f)
                context.matrices.scale(0.6f, 0.6f, 1f)
                val validItem = allItemIds?.contains(currentText) ?: false
                val statusColor = if (validItem) 0xFF55FF55.toInt() else 0xFFFF5555.toInt()
                val statusText = if (validItem) "Valid item" else "Unknown item ID"
                context.drawTextWithShadow(textRenderer, statusText, 0, 0, statusColor)
                context.matrices.pop()
            }

            // Suggestions dropdown (rendered above everything else)
            if (showSuggestions && itemSuggestions.isNotEmpty()) {
                val field = producerItemField ?: return
                val dropX = field.x
                val dropY = field.y + field.height + 1
                val dropW = field.width
                val itemH = 10

                // Background
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
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val species = selectedSpecies ?: return false

        // --- Producer suggestion clicks ---
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
            // Click outside suggestions -> close
            showSuggestions = false
        }

        // --- Producer count +/- buttons ---
        if (producerEnabled) {
            val producerSectionH = 40
            val listH = h - PADDING * 2 - 32 - producerSectionH
            val psY = y + PADDING + 14 + listH + 2
            val countX = x + w - PADDING - 44
            val countY = psY + 3
            // Scale is 0.75, but click detection uses screen coords
            if (mouseY >= countY && mouseY < countY + 10) {
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

        val COLS = 3
        val colW = (w - PADDING * 2) / COLS
        val producerSectionH = if (producerEnabled) 40 else 0
        val listY = y + PADDING + 14
        val listH = h - PADDING * 2 - 32 - producerSectionH

        // Check scrollbar click (8px wide hitbox around the 2px scrollbar track)
        val trackX = x + w - 3
        val maxVisible = listH / ROW_HEIGHT
        // Rebuild grid rows to compute maxScroll
        data class SbGridRow(val isHeader: Boolean, val skills: List<Int> = emptyList())
        val sbGridRows = mutableListOf<SbGridRow>()
        var sbLastCat = ""
        var sbCatSkills = mutableListOf<Int>()
        fun sbFlush() { if (sbCatSkills.isNotEmpty()) { for (c in sbCatSkills.chunked(COLS)) sbGridRows.add(SbGridRow(false, c)); sbCatSkills = mutableListOf() } }
        for ((i, skill) in skillEdits.withIndex()) {
            if (skill.category != sbLastCat) { sbFlush(); sbGridRows.add(SbGridRow(true)); sbLastCat = skill.category }
            sbCatSkills.add(i)
        }
        sbFlush()
        val maxScroll = (sbGridRows.size - maxVisible).coerceAtLeast(0)

        if (maxScroll > 0 && mouseX >= trackX - 4 && mouseX <= trackX + 4 && mouseY >= listY && mouseY <= listY + listH) {
            isDraggingScrollbar = true
            val trackHeight = listH
            val relativeY = ((mouseY - listY) / trackHeight.toDouble()).coerceIn(0.0, 1.0)
            scrollOffset = (relativeY * maxScroll).toInt()
            return true
        }

        if (mouseX >= x && mouseX <= x + w && mouseY >= listY && mouseY <= listY + listH) {
            val rowVisIdx = ((mouseY - listY) / ROW_HEIGHT).toInt()
            val rowIdx = rowVisIdx + scrollOffset

            // Rebuild grid rows for click handling
            data class GridRow(val isHeader: Boolean, val skills: List<Int> = emptyList())
            val gridRows = mutableListOf<GridRow>()
            var lastCat = ""
            var catSkills = mutableListOf<Int>()
            fun flush() { if (catSkills.isNotEmpty()) { for (c in catSkills.chunked(COLS)) gridRows.add(GridRow(false, c)); catSkills = mutableListOf() } }
            for ((i, skill) in skillEdits.withIndex()) {
                if (skill.category != lastCat) { flush(); gridRows.add(GridRow(true)); lastCat = skill.category }
                catSkills.add(i)
            }
            flush()

            if (rowIdx in gridRows.indices) {
                val row = gridRows[rowIdx]
                if (!row.isHeader) {
                    // Determine which column was clicked
                    val col = ((mouseX - x - PADDING) / colW).toInt().coerceIn(0, COLS - 1)
                    if (col < row.skills.size) {
                        val skillIdx = row.skills[col]
                        val skill = skillEdits[skillIdx]
                        val cellX = x + PADDING + col * colW

                        // Checkbox area
                        if (mouseX >= cellX && mouseX <= cellX + 12) {
                            skill.assigned = !skill.assigned
                            dirty = true
                            updateProducerVisibility()
                            return true
                        }

                        // Star area
                        if (skill.assigned) {
                            val starsX = cellX + colW - 42
                            if (mouseX >= starsX && mouseX <= starsX + 5 * (STAR_SIZE + 1)) {
                                val starClicked = ((mouseX - starsX) / (STAR_SIZE + 1)).toInt() + 1
                                skill.proficiency = starClicked.coerceIn(1, 5)
                                dirty = true
                                return true
                            }
                        }

                        // Clicking anywhere on the skill row toggles it
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
            val COLS = 3
            val producerSectionH = if (producerEnabled) 40 else 0
            val listY = y + PADDING + 14
            val listH = h - PADDING * 2 - 32 - producerSectionH
            val maxVisible = listH / ROW_HEIGHT

            data class DgGridRow(val isHeader: Boolean, val skills: List<Int> = emptyList())
            val dgGridRows = mutableListOf<DgGridRow>()
            var dgLastCat = ""
            var dgCatSkills = mutableListOf<Int>()
            fun dgFlush() { if (dgCatSkills.isNotEmpty()) { for (c in dgCatSkills.chunked(COLS)) dgGridRows.add(DgGridRow(false, c)); dgCatSkills = mutableListOf() } }
            for ((i, skill) in skillEdits.withIndex()) {
                if (skill.category != dgLastCat) { dgFlush(); dgGridRows.add(DgGridRow(true)); dgLastCat = skill.category }
                dgCatSkills.add(i)
            }
            dgFlush()

            val maxScroll = (dgGridRows.size - maxVisible).coerceAtLeast(0)
            val trackHeight = listH
            val relativeY = ((mouseY - listY) / trackHeight.toDouble()).coerceIn(0.0, 1.0)
            scrollOffset = (relativeY * maxScroll).toInt()
            return true
        }
        return false
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false
            return true
        }
        return false
    }

    private var scrollAccumulator = 0.0

    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
            scrollAccumulator -= verticalAmount
            val whole = scrollAccumulator.toInt()
            scrollAccumulator -= whole.toDouble()
            scrollOffset = (scrollOffset + whole).coerceAtLeast(0)
            val totalSkills = SkillRegistry.getAll().size
            val maxScroll = (totalSkills - ((h - 40) / ROW_HEIGHT)).coerceAtLeast(0)
            scrollOffset = scrollOffset.coerceAtMost(maxScroll)
            return true
        }
        return false
    }

    /** Handle keyboard input for suggestion navigation */
    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (showSuggestions && itemSuggestions.isNotEmpty()) {
            when (keyCode) {
                264 -> { // DOWN
                    selectedSuggestionIdx = ((selectedSuggestionIdx + 1) % itemSuggestions.size)
                    return true
                }
                265 -> { // UP
                    selectedSuggestionIdx = if (selectedSuggestionIdx <= 0) itemSuggestions.size - 1 else selectedSuggestionIdx - 1
                    return true
                }
                257, 335 -> { // ENTER / KP_ENTER
                    if (selectedSuggestionIdx in itemSuggestions.indices) {
                        producerItemField?.text = itemSuggestions[selectedSuggestionIdx]
                        showSuggestions = false
                        dirty = true
                        return true
                    }
                }
                256 -> { // ESCAPE
                    showSuggestions = false
                    return true
                }
            }
        }
        return false
    }
}

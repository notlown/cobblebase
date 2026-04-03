package notlown.cobblebase.fabric.client.gui

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import notlown.cobblebase.core.AdminDataCache
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillRegistry
import notlown.cobblebase.core.net.AdminSpeciesUpdateC2SPacket
import java.util.function.Function

/**
 * Right pane of the Admin GUI: skill toggle + proficiency editor for selected species.
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
        "legendary" to 0xFFFFD700.toInt()
    )

    var selectedSpecies: String? = null
        private set

    // Mutable skill data for editing
    private val skillEdits = mutableListOf<AdminButtonData>()
    private var scrollOffset = 0
    private var saveButton: ButtonWidget? = null
    private var resetButton: ButtonWidget? = null
    private var dirty = false
    private var lastGridRows = listOf<Any>()

    fun init(addWidget: Function<ButtonWidget, ButtonWidget>) {
        saveButton = ButtonWidget.builder(Text.literal("\u00A72Save")) { saveChanges() }
            .dimensions(x + w - 90, y + h - 18, 40, 14).build()
        addWidget.apply(saveButton!!)

        resetButton = ButtonWidget.builder(Text.literal("\u00A7cReset")) { resetToDefault() }
            .dimensions(x + w - 46, y + h - 18, 42, 14).build()
        addWidget.apply(resetButton!!)
    }

    fun setSpecies(species: String) {
        selectedSpecies = species
        scrollOffset = 0
        dirty = false
        rebuildSkillEdits()
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
    }

    private fun saveChanges() {
        val species = selectedSpecies ?: return
        val assignedSkills = skillEdits.filter { it.assigned }.map {
            SkillEntry(it.skillId, it.proficiency)
        }
        ClientPlayNetworking.send(AdminSpeciesUpdateC2SPacket(species, assignedSkills, false))

        // Update local cache
        val newMap = AdminDataCache.speciesSkills.toMutableMap()
        newMap[species] = assignedSkills
        val newOverridden = AdminDataCache.overriddenSpecies.toMutableSet()
        newOverridden.add(species)
        AdminDataCache.update(AdminDataCache.allSpecies, newMap, newOverridden)
        dirty = false
        onSaved()
    }

    private fun resetToDefault() {
        val species = selectedSpecies ?: return
        ClientPlayNetworking.send(AdminSpeciesUpdateC2SPacket(species, emptyList(), true))

        // Update local cache: remove override marker, revert to built-in would need server re-sync
        // For now, re-request data
        ClientPlayNetworking.send(notlown.cobblebase.core.net.AdminSpeciesRequestC2SPacket())
        dirty = false
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Background
        context.fill(x, y, x + w, y + h, 0xCC1E1E2E.toInt())

        val species = selectedSpecies
        if (species == null) {
            context.drawTextWithShadow(textRenderer, "\u00A77Select a species from the list", x + PADDING, y + h / 2, 0x888888)
            return
        }

        // Header (0.75x scaled)
        val displayName = species.replaceFirstChar { it.uppercase() }
        val isOverridden = AdminDataCache.overriddenSpecies.contains(species)
        val headerText = if (isOverridden) "$displayName \u00A76[Override]" else displayName
        val hdrScale = 0.75f
        context.matrices.push()
        context.matrices.translate((x + PADDING).toFloat(), (y + PADDING).toFloat(), 0f)
        context.matrices.scale(hdrScale, hdrScale, 1f)
        context.drawTextWithShadow(textRenderer, "\u00A7f\u00A7l$headerText", 0, 0, 0xFFFFFF)
        if (dirty) {
            context.drawTextWithShadow(textRenderer, "\u00A7e*unsaved", textRenderer.getWidth(headerText) + 10, 0, 0xFFFF00)
        }
        context.matrices.pop()

        // 3-column grid layout per category
        val COLS = 3
        val colW = (w - PADDING * 2) / COLS
        val listY = y + PADDING + 14
        val listH = h - PADDING * 2 - 32
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
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val species = selectedSpecies ?: return false

        val COLS = 3
        val colW = (w - PADDING * 2) / COLS
        val listY = y + PADDING + 14
        val listH = h - PADDING * 2 - 32

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
                        return true
                    }
                }
            }
        }
        return false
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
            scrollOffset = (scrollOffset - verticalAmount.toInt() * 3).coerceAtLeast(0)
            val totalSkills = SkillRegistry.getAll().size
            val maxScroll = (totalSkills - ((h - 40) / ROW_HEIGHT)).coerceAtLeast(0)
            scrollOffset = scrollOffset.coerceAtMost(maxScroll)
            return true
        }
        return false
    }
}

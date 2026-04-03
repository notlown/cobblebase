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

        // Skill list
        val listY = y + PADDING + 14
        val listH = h - PADDING * 2 - 32
        val maxVisible = listH / ROW_HEIGHT

        // Build render list with category headers
        data class RenderRow(val isHeader: Boolean, val category: String = "", val skillIndex: Int = -1)
        val renderRows = mutableListOf<RenderRow>()
        var lastCategory = ""
        for ((i, skill) in skillEdits.withIndex()) {
            if (skill.category != lastCategory) {
                renderRows.add(RenderRow(isHeader = true, category = skill.category))
                lastCategory = skill.category
            }
            renderRows.add(RenderRow(isHeader = false, skillIndex = i))
        }

        val maxScroll = (renderRows.size - maxVisible).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        // Enable scissor for clipping
        context.enableScissor(x, listY, x + w, listY + listH)

        for (i in 0 until maxVisible + 1) {
            val idx = i + scrollOffset
            if (idx >= renderRows.size) break
            val row = renderRows[idx]
            val rowY = listY + i * ROW_HEIGHT

            if (row.isHeader) {
                // Category header
                val catColor = CATEGORY_COLORS[row.category] ?: 0xFFAAAAAA.toInt()
                context.fill(x + 2, rowY, x + w - 2, rowY + ROW_HEIGHT, 0x22FFFFFF)
                context.fill(x + 2, rowY + ROW_HEIGHT - 1, x + w - 2, rowY + ROW_HEIGHT, catColor)
                val catName = row.category.replaceFirstChar { it.uppercase() }
                val scale = 0.75f
                context.matrices.push()
                context.matrices.translate((x + PADDING).toFloat(), (rowY + 4).toFloat(), 0f)
                context.matrices.scale(scale, scale, 1f)
                context.drawTextWithShadow(textRenderer, "\u00A7l$catName", 0, 0, catColor)
                context.matrices.pop()
            } else {
                val skill = skillEdits[row.skillIndex]
                val scale = 0.75f
                val colW = (w - PADDING * 2) / 2

                // Determine which column (odd skillIndex = right column)
                // Actually render in single column but scaled — 2-column done via render pairs below
                val cbX = x + PADDING
                val cbY = rowY + 3
                val cbSize = 8
                val cbColor = if (skill.assigned) CHECKBOX_ON else CHECKBOX_OFF
                context.fill(cbX, cbY, cbX + cbSize, cbY + cbSize, cbColor)
                if (skill.assigned) {
                    context.matrices.push()
                    context.matrices.translate((cbX + 1).toFloat(), (cbY).toFloat(), 0f)
                    context.matrices.scale(scale, scale, 1f)
                    context.drawTextWithShadow(textRenderer, "\u2713", 0, 0, 0xFFFFFF)
                    context.matrices.pop()
                }

                // Skill name (0.75x)
                val nameColor = if (skill.assigned) 0xFFFFFF else 0x888888
                context.matrices.push()
                context.matrices.translate((cbX + cbSize + 3).toFloat(), (rowY + 4).toFloat(), 0f)
                context.matrices.scale(scale, scale, 1f)
                context.drawTextWithShadow(textRenderer, skill.displayName, 0, 0, nameColor)
                context.matrices.pop()

                // Proficiency stars (only if assigned, 0.75x)
                if (skill.assigned) {
                    val starsX = x + w - 55
                    context.matrices.push()
                    context.matrices.translate(starsX.toFloat(), (rowY + 4).toFloat(), 0f)
                    context.matrices.scale(scale, scale, 1f)
                    for (star in 1..5) {
                        val starOff = (star - 1) * (STAR_SIZE + 1)
                        val color = if (star <= skill.proficiency) STAR_FILLED else STAR_EMPTY
                        context.drawTextWithShadow(textRenderer, "\u2605", starOff, 0, color)
                    }
                    context.matrices.pop()
                }
            }
        }

        context.disableScissor()

        // Scrollbar
        if (renderRows.size > maxVisible) {
            val trackX = x + w - 3
            val trackH = listH
            val thumbH = ((maxVisible.toFloat() / renderRows.size) * trackH).toInt().coerceAtLeast(10)
            val thumbY = listY + ((scrollOffset.toFloat() / maxScroll) * (trackH - thumbH)).toInt()
            context.fill(trackX, listY, trackX + 2, listY + trackH, 0x33FFFFFF)
            context.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xAAFFFFFF.toInt())
        }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val species = selectedSpecies ?: return false

        val listY = y + PADDING + 14
        val listH = h - PADDING * 2 - 32
        val maxVisible = listH / ROW_HEIGHT

        // Build render list
        data class RenderRow(val isHeader: Boolean, val category: String = "", val skillIndex: Int = -1)
        val renderRows = mutableListOf<RenderRow>()
        var lastCategory = ""
        for ((i, skill) in skillEdits.withIndex()) {
            if (skill.category != lastCategory) {
                renderRows.add(RenderRow(isHeader = true, category = skill.category))
                lastCategory = skill.category
            }
            renderRows.add(RenderRow(isHeader = false, skillIndex = i))
        }

        if (mouseX >= x && mouseX <= x + w && mouseY >= listY && mouseY <= listY + listH) {
            val rowIdx = ((mouseY - listY) / ROW_HEIGHT).toInt() + scrollOffset
            if (rowIdx in renderRows.indices) {
                val row = renderRows[rowIdx]
                if (!row.isHeader) {
                    val skill = skillEdits[row.skillIndex]

                    // Check if clicking checkbox area
                    val cbX = x + PADDING
                    val cbSize = 10
                    if (mouseX >= cbX && mouseX <= cbX + cbSize + 4) {
                        skill.assigned = !skill.assigned
                        dirty = true
                        return true
                    }

                    // Check if clicking star area
                    if (skill.assigned) {
                        val starsX = x + w - 60
                        if (mouseX >= starsX && mouseX <= starsX + 5 * (STAR_SIZE + 2)) {
                            val starClicked = ((mouseX - starsX) / (STAR_SIZE + 2)).toInt() + 1
                            skill.proficiency = starClicked.coerceIn(1, 5)
                            dirty = true
                            return true
                        }
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

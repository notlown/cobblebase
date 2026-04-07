package notlown.cobblebase.fabric.client.gui

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import notlown.cobblebase.core.AdminJobDataCache
import notlown.cobblebase.core.JobConfigOverrides
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.SkillRegistry
import notlown.cobblebase.core.net.AdminJobsUpdateC2SPacket
import java.util.function.Function

/**
 * Full-width panel for the Admin GUI "Jobs" tab.
 * Shows all jobs from SkillRegistry in a scrollable list with per-job
 * cooldown, search radius, and enable/disable controls.
 */
class AdminJobsPanel(
    private val x: Int,
    private val y: Int,
    private val w: Int,
    private val h: Int,
    private val textRenderer: TextRenderer
) {
    private val ROW_HEIGHT = 20
    private val PADDING = 6
    private val CATEGORY_HEADER_HEIGHT = 18

    private val CATEGORY_COLORS = mapOf(
        "gathering" to 0xFF4CAF50.toInt(),
        "generation" to 0xFFFF9800.toInt(),
        "combat" to 0xFFF44336.toInt(),
        "support" to 0xFFE91E9E.toInt(),
        "utility" to 0xFF2196F3.toInt(),
        "legendary" to 0xFFFFD700.toInt(),
        "social" to 0xFFFF55FF.toInt()
    )

    private val CHECKBOX_ON = 0xFF4CAF50.toInt()
    private val CHECKBOX_OFF = 0xFF555555.toInt()
    private val FIELD_BG = 0xFF2A2A3E.toInt()
    private val FIELD_BORDER = 0xFF4A4A6E.toInt()
    private val FIELD_ACTIVE = 0xFF5A5A8E.toInt()
    private val BUTTON_AREA_HEIGHT = 24

    private var saveButton: ButtonWidget? = null
    private var resetButton: ButtonWidget? = null

    private var scrollOffset = 0
    private var isDraggingScrollbar = false

    // Editable job data
    private val jobEdits = mutableListOf<JobEditData>()

    // Active text field tracking
    private var activeFieldJob: Int = -1  // index in jobEdits
    private var activeFieldType: FieldType = FieldType.NONE
    private var fieldText = ""
    private var cursorBlink = 0

    enum class FieldType { NONE, COOLDOWN, RADIUS_MIN, RADIUS_MAX }

    data class JobEditData(
        val skillId: String,
        val displayName: String,
        val category: String,
        val defaultCooldown: Long,
        val defaultRadius: Int,
        var cooldownSeconds: Long,
        var radiusMin: Int,
        var radiusMax: Int,
        var enabled: Boolean,
        var dirty: Boolean = false
    )

    fun rebuild() {
        jobEdits.clear()
        val allJobs = AdminJobDataCache.allJobs.values.sortedWith(
            compareBy({ it.category }, { it.name })
        )
        val overrides = AdminJobDataCache.jobOverrides

        for (job in allJobs) {
            val override = overrides[job.id]
            jobEdits.add(JobEditData(
                skillId = job.id,
                displayName = job.name,
                category = job.category,
                defaultCooldown = job.cooldownSeconds,
                defaultRadius = job.searchRadius,
                cooldownSeconds = override?.cooldownSeconds ?: job.cooldownSeconds,
                radiusMin = override?.radiusMin ?: 3,
                radiusMax = override?.radiusMax ?: job.searchRadius.coerceAtLeast(5),
                enabled = override?.enabled ?: true
            ))
        }
        scrollOffset = 0
    }

    fun init(addWidget: Function<ButtonWidget, ButtonWidget>) {
        saveButton = ButtonWidget.builder(Text.literal("\u00A72Save")) { commitActiveField(); saveAllChanges() }
            .dimensions(x + w - 90, y + h - 18, 40, 14).build()
        addWidget.apply(saveButton!!)

        resetButton = ButtonWidget.builder(Text.literal("\u00A7cReset")) { resetChanges() }
            .dimensions(x + w - 46, y + h - 18, 42, 14).build()
        addWidget.apply(resetButton!!)
    }

    // Build rows: category headers + job rows
    private data class GridRow(val isHeader: Boolean, val category: String = "", val jobIndex: Int = -1)

    private fun buildGridRows(): List<GridRow> {
        val rows = mutableListOf<GridRow>()
        var lastCategory = ""
        for ((i, job) in jobEdits.withIndex()) {
            if (job.category != lastCategory) {
                rows.add(GridRow(isHeader = true, category = job.category))
                lastCategory = job.category
            }
            rows.add(GridRow(isHeader = false, jobIndex = i))
        }
        return rows
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Background
        context.fill(x, y, x + w, y + h, 0xCC1E1E2E.toInt())

        // Header
        val scale = 0.75f
        context.matrices.push()
        context.matrices.translate((x + PADDING).toFloat(), (y + PADDING).toFloat(), 0f)
        context.matrices.scale(scale, scale, 1f)
        context.drawTextWithShadow(textRenderer, "\u00A7f\u00A7lJob Configuration", 0, 0, 0xFFFFFF)
        context.matrices.pop()

        // Column headers
        val colNameX = x + PADDING + 12
        val colCooldownX = x + w - 280
        val colRadiusMinX = x + w - 195
        val colRadiusMaxX = x + w - 120
        val colEnabledX = x + w - 50
        val headerY = y + PADDING + 12

        context.matrices.push()
        context.matrices.translate(0f, headerY.toFloat(), 0f)
        context.matrices.scale(scale, scale, 1f)
        context.drawTextWithShadow(textRenderer, "\u00A77Job Name", ((colNameX) / scale).toInt(), 0, 0xAAAAAA)
        context.drawTextWithShadow(textRenderer, "\u00A77Cooldown (s)", ((colCooldownX) / scale).toInt(), 0, 0xAAAAAA)
        context.drawTextWithShadow(textRenderer, "\u00A77Min", ((colRadiusMinX) / scale).toInt(), 0, 0xAAAAAA)
        context.drawTextWithShadow(textRenderer, "\u00A77Max", ((colRadiusMaxX) / scale).toInt(), 0, 0xAAAAAA)
        context.drawTextWithShadow(textRenderer, "\u00A77On", ((colEnabledX) / scale).toInt(), 0, 0xAAAAAA)
        context.matrices.pop()

        // Separator under headers
        val listY = y + PADDING + 24
        context.fill(x + 2, listY - 1, x + w - 2, listY, 0xFF3A3A5C.toInt())

        val listH = h - PADDING - 28 - BUTTON_AREA_HEIGHT
        val gridRows = buildGridRows()
        val maxVisible = listH / ROW_HEIGHT
        val maxScroll = (gridRows.size - maxVisible).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        context.enableScissor(x, listY, x + w, listY + listH)

        cursorBlink++

        for (i in 0 until maxVisible + 2) {
            val idx = i + scrollOffset
            if (idx >= gridRows.size) break
            val row = gridRows[idx]
            val rowY = listY + i * ROW_HEIGHT

            if (row.isHeader) {
                val catColor = CATEGORY_COLORS[row.category] ?: 0xFFAAAAAA.toInt()
                context.fill(x + 2, rowY, x + w - 6, rowY + ROW_HEIGHT, 0x22FFFFFF)
                context.fill(x + 2, rowY + ROW_HEIGHT - 1, x + w - 6, rowY + ROW_HEIGHT, catColor)
                val catName = row.category.replaceFirstChar { it.uppercase() }
                context.matrices.push()
                context.matrices.translate((x + PADDING).toFloat(), (rowY + 6).toFloat(), 0f)
                context.matrices.scale(scale, scale, 1f)
                context.drawTextWithShadow(textRenderer, "\u00A7l$catName", 0, 0, catColor)
                context.matrices.pop()
            } else {
                val job = jobEdits[row.jobIndex]
                val catColor = CATEGORY_COLORS[job.category] ?: 0xFFAAAAAA.toInt()
                val nameColor = if (job.enabled) 0xFFFFFF else 0x666666

                // Hover highlight
                val isHovered = mouseX in x..(x + w) && mouseY in rowY..(rowY + ROW_HEIGHT)
                if (isHovered) {
                    context.fill(x + 2, rowY, x + w - 6, rowY + ROW_HEIGHT, 0x11FFFFFF)
                }

                // Category dot
                context.fill(x + PADDING, rowY + 7, x + PADDING + 4, rowY + 11, catColor)

                // Job name (0.75x)
                context.matrices.push()
                context.matrices.translate((colNameX).toFloat(), (rowY + 6).toFloat(), 0f)
                context.matrices.scale(scale, scale, 1f)
                context.drawTextWithShadow(textRenderer, job.displayName, 0, 0, nameColor)
                // Dirty indicator
                if (job.dirty) {
                    val nameW = textRenderer.getWidth(job.displayName)
                    context.drawTextWithShadow(textRenderer, "\u00A7e*", nameW + 2, 0, 0xFFFF00)
                }
                // Show "override" tag if different from default
                val isOverridden = job.cooldownSeconds != job.defaultCooldown || job.radiusMin != 3 || job.radiusMax != job.defaultRadius.coerceAtLeast(5) || !job.enabled
                if (isOverridden && !job.dirty) {
                    val nameW = textRenderer.getWidth(job.displayName)
                    context.drawTextWithShadow(textRenderer, "\u00A76[o]", nameW + 2, 0, 0xFF9800)
                }
                context.matrices.pop()

                // Cooldown field
                val fieldW = 55
                val fieldH = 14
                val cooldownFieldX = colCooldownX
                val fieldY = rowY + 3
                val isCooldownActive = activeFieldJob == row.jobIndex && activeFieldType == FieldType.COOLDOWN
                val cooldownBorder = if (isCooldownActive) FIELD_ACTIVE else FIELD_BORDER
                context.fill(cooldownFieldX - 1, fieldY - 1, cooldownFieldX + fieldW + 1, fieldY + fieldH + 1, cooldownBorder)
                context.fill(cooldownFieldX, fieldY, cooldownFieldX + fieldW, fieldY + fieldH, FIELD_BG)

                val cooldownText = if (isCooldownActive) fieldText else job.cooldownSeconds.toString()
                val cooldownColor = if (job.cooldownSeconds != job.defaultCooldown) 0xFFFF00 else 0xCCCCCC
                context.matrices.push()
                context.matrices.translate((cooldownFieldX + 3).toFloat(), (fieldY + 3).toFloat(), 0f)
                context.matrices.scale(scale, scale, 1f)
                context.drawTextWithShadow(textRenderer, cooldownText, 0, 0, cooldownColor)
                // Cursor blink
                if (isCooldownActive && (cursorBlink / 20) % 2 == 0) {
                    val cursorX = textRenderer.getWidth(cooldownText)
                    context.drawTextWithShadow(textRenderer, "|", cursorX, 0, 0xFFFFFF)
                }
                context.matrices.pop()

                // Radius Min field
                val radiusMinFieldX = colRadiusMinX
                val isRadiusMinActive = activeFieldJob == row.jobIndex && activeFieldType == FieldType.RADIUS_MIN
                val radiusMinBorder = if (isRadiusMinActive) FIELD_ACTIVE else FIELD_BORDER
                context.fill(radiusMinFieldX - 1, fieldY - 1, radiusMinFieldX + fieldW + 1, fieldY + fieldH + 1, radiusMinBorder)
                context.fill(radiusMinFieldX, fieldY, radiusMinFieldX + fieldW, fieldY + fieldH, FIELD_BG)

                val radiusMinText = if (isRadiusMinActive) fieldText else job.radiusMin.toString()
                val radiusMinColor = if (job.radiusMin != 3) 0xFFFF00 else 0xCCCCCC
                context.matrices.push()
                context.matrices.translate((radiusMinFieldX + 3).toFloat(), (fieldY + 3).toFloat(), 0f)
                context.matrices.scale(scale, scale, 1f)
                context.drawTextWithShadow(textRenderer, radiusMinText, 0, 0, radiusMinColor)
                if (isRadiusMinActive && (cursorBlink / 20) % 2 == 0) {
                    val cursorX = textRenderer.getWidth(radiusMinText)
                    context.drawTextWithShadow(textRenderer, "|", cursorX, 0, 0xFFFFFF)
                }
                context.matrices.pop()

                // Radius Max field
                val radiusMaxFieldX = colRadiusMaxX
                val isRadiusMaxActive = activeFieldJob == row.jobIndex && activeFieldType == FieldType.RADIUS_MAX
                val radiusMaxBorder = if (isRadiusMaxActive) FIELD_ACTIVE else FIELD_BORDER
                context.fill(radiusMaxFieldX - 1, fieldY - 1, radiusMaxFieldX + fieldW + 1, fieldY + fieldH + 1, radiusMaxBorder)
                context.fill(radiusMaxFieldX, fieldY, radiusMaxFieldX + fieldW, fieldY + fieldH, FIELD_BG)

                val radiusMaxText = if (isRadiusMaxActive) fieldText else job.radiusMax.toString()
                val radiusMaxColor = if (job.radiusMax != job.defaultRadius.coerceAtLeast(5)) 0xFFFF00 else 0xCCCCCC
                context.matrices.push()
                context.matrices.translate((radiusMaxFieldX + 3).toFloat(), (fieldY + 3).toFloat(), 0f)
                context.matrices.scale(scale, scale, 1f)
                context.drawTextWithShadow(textRenderer, radiusMaxText, 0, 0, radiusMaxColor)
                if (isRadiusMaxActive && (cursorBlink / 20) % 2 == 0) {
                    val cursorX = textRenderer.getWidth(radiusMaxText)
                    context.drawTextWithShadow(textRenderer, "|", cursorX, 0, 0xFFFFFF)
                }
                context.matrices.pop()

                // Enable/disable checkbox
                val cbX = colEnabledX + 5
                val cbY = rowY + 5
                val cbSize = 10
                val cbColor = if (job.enabled) CHECKBOX_ON else CHECKBOX_OFF
                context.fill(cbX, cbY, cbX + cbSize, cbY + cbSize, cbColor)
                if (job.enabled) {
                    context.matrices.push()
                    context.matrices.translate((cbX + 1).toFloat(), (cbY + 1).toFloat(), 0f)
                    context.matrices.scale(scale, scale, 1f)
                    context.drawTextWithShadow(textRenderer, "\u2713", 0, 0, 0xFFFFFF)
                    context.matrices.pop()
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

        // Unsaved indicator (rendered next to the save button)
        val hasUnsaved = jobEdits.any { it.dirty }
        if (hasUnsaved) {
            val sb = saveButton
            if (sb != null) {
                val indicatorX = sb.x - 60
                val indicatorY = sb.y + 3
                context.matrices.push()
                context.matrices.translate(indicatorX.toFloat(), indicatorY.toFloat(), 0f)
                context.matrices.scale(scale, scale, 1f)
                context.drawTextWithShadow(textRenderer, "\u00A7e*unsaved", 0, 0, 0xFFFF00)
                context.matrices.pop()
            }
        }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val listY = y + PADDING + 24
        val listH = h - PADDING - 28 - BUTTON_AREA_HEIGHT
        val gridRows = buildGridRows()
        val maxVisible = listH / ROW_HEIGHT
        val maxScroll = (gridRows.size - maxVisible).coerceAtLeast(0)

        val colCooldownX = x + w - 280
        val colRadiusMinX = x + w - 195
        val colRadiusMaxX = x + w - 120
        val colEnabledX = x + w - 50
        val fieldW = 55

        // Check scrollbar click
        val trackX = x + w - 3
        if (maxScroll > 0 && mouseX >= trackX - 4 && mouseX <= trackX + 4 && mouseY >= listY && mouseY <= listY + listH) {
            isDraggingScrollbar = true
            val relativeY = ((mouseY - listY) / listH.toDouble()).coerceIn(0.0, 1.0)
            scrollOffset = (relativeY * maxScroll).toInt()
            return true
        }

        // Commit any active field first
        commitActiveField()

        if (mouseX >= x && mouseX <= x + w && mouseY >= listY && mouseY <= listY + listH) {
            val rowVisIdx = ((mouseY - listY) / ROW_HEIGHT).toInt()
            val rowIdx = rowVisIdx + scrollOffset

            if (rowIdx in gridRows.indices) {
                val row = gridRows[rowIdx]
                if (!row.isHeader && row.jobIndex >= 0) {
                    val job = jobEdits[row.jobIndex]
                    val fieldY = listY + rowVisIdx * ROW_HEIGHT + 3
                    val fieldH = 14

                    // Check cooldown field click
                    if (mouseX >= colCooldownX && mouseX <= colCooldownX + fieldW &&
                        mouseY >= fieldY && mouseY <= fieldY + fieldH) {
                        activeFieldJob = row.jobIndex
                        activeFieldType = FieldType.COOLDOWN
                        fieldText = job.cooldownSeconds.toString()
                        return true
                    }

                    // Check radius min field click
                    if (mouseX >= colRadiusMinX && mouseX <= colRadiusMinX + fieldW &&
                        mouseY >= fieldY && mouseY <= fieldY + fieldH) {
                        activeFieldJob = row.jobIndex
                        activeFieldType = FieldType.RADIUS_MIN
                        fieldText = job.radiusMin.toString()
                        return true
                    }

                    // Check radius max field click
                    if (mouseX >= colRadiusMaxX && mouseX <= colRadiusMaxX + fieldW &&
                        mouseY >= fieldY && mouseY <= fieldY + fieldH) {
                        activeFieldJob = row.jobIndex
                        activeFieldType = FieldType.RADIUS_MAX
                        fieldText = job.radiusMax.toString()
                        return true
                    }

                    // Check enabled checkbox click
                    val cbX = colEnabledX + 5
                    val cbY = listY + rowVisIdx * ROW_HEIGHT + 5
                    val cbSize = 10
                    if (mouseX >= cbX && mouseX <= cbX + cbSize &&
                        mouseY >= cbY && mouseY <= cbY + cbSize) {
                        job.enabled = !job.enabled
                        job.dirty = true
                        return true
                    }

                    // Clicking elsewhere in the row deactivates field
                    activeFieldJob = -1
                    activeFieldType = FieldType.NONE
                    return true
                }
            }
        }

        // Click outside rows deactivates field
        activeFieldJob = -1
        activeFieldType = FieldType.NONE
        return false
    }

    private fun commitActiveField() {
        if (activeFieldJob < 0 || activeFieldType == FieldType.NONE) return
        val job = jobEdits[activeFieldJob]

        when (activeFieldType) {
            FieldType.COOLDOWN -> {
                val newValue = fieldText.toLongOrNull()
                if (newValue != null && newValue > 0) {
                    job.cooldownSeconds = newValue
                    job.dirty = true
                }
            }
            FieldType.RADIUS_MIN -> {
                val newValue = fieldText.toIntOrNull()
                if (newValue != null && newValue > 0) {
                    job.radiusMin = newValue
                    job.dirty = true
                }
            }
            FieldType.RADIUS_MAX -> {
                val newValue = fieldText.toIntOrNull()
                if (newValue != null && newValue > 0) {
                    job.radiusMax = newValue
                    job.dirty = true
                }
            }
            FieldType.NONE -> {}
        }

        activeFieldJob = -1
        activeFieldType = FieldType.NONE
    }

    private fun saveAllChanges() {
        val newOverrides = AdminJobDataCache.jobOverrides.toMutableMap()

        for (job in jobEdits) {
            if (!job.dirty) continue

            val cooldownOverride = if (job.cooldownSeconds != job.defaultCooldown) job.cooldownSeconds else null
            val radiusMinOverride = if (job.radiusMin != 3) job.radiusMin else null
            val radiusMaxOverride = if (job.radiusMax != job.defaultRadius.coerceAtLeast(5)) job.radiusMax else null

            ClientPlayNetworking.send(AdminJobsUpdateC2SPacket(
                job.skillId,
                cooldownOverride,
                radiusMinOverride,
                radiusMaxOverride,
                job.enabled
            ))

            if (cooldownOverride == null && radiusMinOverride == null && radiusMaxOverride == null && job.enabled) {
                newOverrides.remove(job.skillId)
            } else {
                newOverrides[job.skillId] = JobConfigOverrides.JobOverride(
                    cooldownSeconds = cooldownOverride,
                    radiusMin = radiusMinOverride,
                    radiusMax = radiusMaxOverride,
                    enabled = job.enabled
                )
            }

            job.dirty = false
        }

        AdminJobDataCache.update(AdminJobDataCache.allJobs, newOverrides)
    }

    private fun resetChanges() {
        activeFieldJob = -1
        activeFieldType = FieldType.NONE
        rebuild()
    }

    fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (activeFieldJob < 0 || activeFieldType == FieldType.NONE) return false
        if (chr.isDigit() && fieldText.length < 8) {
            fieldText += chr
            return true
        }
        return false
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (activeFieldJob < 0 || activeFieldType == FieldType.NONE) return false

        // Backspace
        if (keyCode == 259 && fieldText.isNotEmpty()) {
            fieldText = fieldText.dropLast(1)
            return true
        }

        // Enter: commit
        if (keyCode == 257 || keyCode == 335) {
            commitActiveField()
            return true
        }

        // Escape: cancel
        if (keyCode == 256) {
            activeFieldJob = -1
            activeFieldType = FieldType.NONE
            return true
        }

        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (isDraggingScrollbar) {
            val listY = y + PADDING + 24
            val listH = h - PADDING - 28 - BUTTON_AREA_HEIGHT
            val gridRows = buildGridRows()
            val maxVisible = listH / ROW_HEIGHT
            val maxScroll = (gridRows.size - maxVisible).coerceAtLeast(0)
            val relativeY = ((mouseY - listY) / listH.toDouble()).coerceIn(0.0, 1.0)
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

    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
            val gridRows = buildGridRows()
            val listH = h - PADDING - 28 - BUTTON_AREA_HEIGHT
            val maxVisible = listH / ROW_HEIGHT
            val maxScroll = (gridRows.size - maxVisible).coerceAtLeast(0)
            scrollOffset = (scrollOffset - verticalAmount.toInt() * 3).coerceIn(0, maxScroll)
            return true
        }
        return false
    }
}

package notlown.cobblebase.neoforge.client.gui

import net.neoforged.neoforge.network.PacketDistributor
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import notlown.cobblebase.core.AdminJobDataCache
import notlown.cobblebase.core.JobConfigOverrides
import notlown.cobblebase.core.net.AdminJobsUpdateC2SPacket
import java.util.function.Function

/**
 * Admin GUI "Jobs" tab — compact layout with category sidebar (left) and
 * a job list (right). Each row exposes cooldown, search radius, and an
 * enable toggle. The search radius written here is the value the executors
 * actually use in-game (via SkillRegistry.getEffective).
 */
class AdminJobsPanel(
    private val x: Int,
    private val y: Int,
    private val w: Int,
    private val h: Int,
    private val textRenderer: TextRenderer
) {
    // Layout constants — kept tight on purpose so we fit a lot without scrolling
    private val PADDING = 4
    private val SIDEBAR_W = 78
    private val ROW_H = 14
    private val SCALE = 0.7f
    private val BUTTON_AREA_H = 18
    private val HEADER_H = 14

    private val CATEGORY_COLORS = mapOf(
        "gathering" to 0xFF4CAF50.toInt(),
        "generation" to 0xFFFF9800.toInt(),
        "combat" to 0xFFF44336.toInt(),
        "support" to 0xFFE91E9E.toInt(),
        "utility" to 0xFF2196F3.toInt(),
        "legendary" to 0xFFFFD700.toInt(),
        "social" to 0xFFFF55FF.toInt()
    )

    private val FIELD_BG = 0xFF2A2A3E.toInt()
    private val FIELD_BORDER = 0xFF4A4A6E.toInt()
    private val FIELD_ACTIVE = 0xFF7A7ABE.toInt()
    private val SIDEBAR_BG = 0xCC15152A.toInt()
    private val ROW_HOVER = 0x22FFFFFF
    private val SEPARATOR = 0xFF3A3A5C.toInt()
    private val CHECKBOX_ON = 0xFF4CAF50.toInt()
    private val CHECKBOX_OFF = 0xFF555555.toInt()

    private var saveButton: ButtonWidget? = null
    private var resetButton: ButtonWidget? = null

    private val jobEdits = mutableListOf<JobEditData>()
    private val categories = mutableListOf<String>()
    private var activeCategory: String = ""

    private var scrollOffset = 0
    private var isDraggingScrollbar = false

    private var activeFieldJob: Int = -1
    private var activeFieldType: FieldType = FieldType.NONE
    private var fieldText = ""
    private var cursorBlink = 0

    enum class FieldType { NONE, COOLDOWN, RADIUS }

    data class JobEditData(
        val skillId: String,
        val displayName: String,
        val category: String,
        val defaultCooldown: Long,
        val defaultRadius: Int,
        var cooldownSeconds: Long,
        var searchRadius: Int,
        var enabled: Boolean,
        var dirty: Boolean = false
    )

    fun rebuild() {
        jobEdits.clear()
        categories.clear()

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
                searchRadius = override?.searchRadius ?: job.searchRadius,
                enabled = override?.enabled ?: true
            ))
        }

        // Build the unique category list in display order
        val seen = linkedSetOf<String>()
        for (j in jobEdits) seen.add(j.category)
        categories.addAll(seen)

        // "All" pseudo-category at index 0
        categories.add(0, "all")

        if (activeCategory.isEmpty() || activeCategory !in categories) {
            activeCategory = "all"
        }
        scrollOffset = 0
    }

    fun init(addWidget: Function<ButtonWidget, ButtonWidget>) {
        saveButton = ButtonWidget.builder(Text.literal("\u00A72Save")) { commitActiveField(); saveAllChanges() }
            .dimensions(x + w - 86, y + h - 16, 40, 12).build()
        addWidget.apply(saveButton!!)

        resetButton = ButtonWidget.builder(Text.literal("\u00A7cReset")) { resetChanges() }
            .dimensions(x + w - 44, y + h - 16, 40, 12).build()
        addWidget.apply(resetButton!!)
    }

    private fun visibleJobs(): List<Int> {
        if (jobEdits.isEmpty()) return emptyList()
        return jobEdits.indices.filter { activeCategory == "all" || jobEdits[it].category == activeCategory }
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Background
        context.fill(x, y, x + w, y + h, 0xCC1E1E2E.toInt())

        // Sidebar background
        val sidebarX = x
        val sidebarY = y
        val sidebarH = h
        context.fill(sidebarX, sidebarY, sidebarX + SIDEBAR_W, sidebarY + sidebarH, SIDEBAR_BG)
        context.fill(sidebarX + SIDEBAR_W, sidebarY, sidebarX + SIDEBAR_W + 1, sidebarY + sidebarH, SEPARATOR)

        // Sidebar header
        drawScaledText(context, "\u00A7f\u00A7lCategories", sidebarX + PADDING, sidebarY + PADDING, 0xFFFFFF)

        // Sidebar entries
        var sy = sidebarY + PADDING + 11
        for (cat in categories) {
            val isActive = cat == activeCategory
            val isHovered = mouseX in sidebarX..(sidebarX + SIDEBAR_W) && mouseY in sy..(sy + ROW_H)

            if (isActive) context.fill(sidebarX + 1, sy, sidebarX + SIDEBAR_W, sy + ROW_H, 0x442196F3)
            else if (isHovered) context.fill(sidebarX + 1, sy, sidebarX + SIDEBAR_W, sy + ROW_H, ROW_HOVER)

            val color = CATEGORY_COLORS[cat] ?: 0xFFAAAAAA.toInt()
            // Color dot
            context.fill(sidebarX + 4, sy + 5, sidebarX + 8, sy + 9, color)

            val label = if (cat == "all") "All Jobs" else cat.replaceFirstChar { it.uppercase() }
            val count = if (cat == "all") jobEdits.size else jobEdits.count { it.category == cat }
            val labelColor = if (isActive) 0xFFFFFF else 0xCCCCCC

            drawScaledText(context, "$label ($count)", sidebarX + 11, sy + 3, labelColor)

            sy += ROW_H
            if (sy > sidebarY + sidebarH - ROW_H) break
        }

        // Right pane
        val rightX = sidebarX + SIDEBAR_W + 2
        val rightW = w - SIDEBAR_W - 2
        val rightY = y

        // Right pane header
        drawScaledText(context, "\u00A7f\u00A7lJob Configuration", rightX + PADDING, rightY + PADDING, 0xFFFFFF)

        // Column headers
        val colNameX = rightX + PADDING + 8
        val fieldW = 38
        val colCooldownX = rightX + rightW - 130
        val colRadiusX = rightX + rightW - 86
        val colEnabledX = rightX + rightW - 30
        val headerY = rightY + PADDING + 9

        drawScaledText(context, "\u00A77Name", colNameX, headerY, 0xAAAAAA)
        drawScaledText(context, "\u00A77Cooldown(s)", colCooldownX - 2, headerY, 0xAAAAAA)
        drawScaledText(context, "\u00A77Radius", colRadiusX - 2, headerY, 0xAAAAAA)
        drawScaledText(context, "\u00A77On", colEnabledX, headerY, 0xAAAAAA)

        val listY = rightY + PADDING + HEADER_H + 4
        context.fill(rightX + 2, listY - 1, rightX + rightW - 4, listY, SEPARATOR)

        val listH = h - (listY - y) - BUTTON_AREA_H - 2
        val visible = visibleJobs()
        val maxVisible = listH / ROW_H
        val maxScroll = (visible.size - maxVisible).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        context.enableScissor(rightX, listY, rightX + rightW, listY + listH)
        cursorBlink++

        for (i in 0 until maxVisible + 1) {
            val visIdx = i + scrollOffset
            if (visIdx >= visible.size) break
            val jobIdx = visible[visIdx]
            val job = jobEdits[jobIdx]
            val rowY = listY + i * ROW_H

            val isHovered = mouseX in rightX..(rightX + rightW - 4) && mouseY in rowY..(rowY + ROW_H)
            if (isHovered) context.fill(rightX + 2, rowY, rightX + rightW - 4, rowY + ROW_H, ROW_HOVER)

            val catColor = CATEGORY_COLORS[job.category] ?: 0xFFAAAAAA.toInt()
            // Category color stripe
            context.fill(rightX + PADDING, rowY + 4, rightX + PADDING + 3, rowY + ROW_H - 3, catColor)

            // Name
            val nameColor = if (job.enabled) 0xFFFFFF else 0x666666
            drawScaledText(context, job.displayName, colNameX, rowY + 3, nameColor)
            // Dirty / overridden marker
            val nameW = (textRenderer.getWidth(job.displayName) * SCALE).toInt() + 2
            if (job.dirty) {
                drawScaledText(context, "\u00A7e*", colNameX + nameW, rowY + 3, 0xFFFF00)
            } else if (job.cooldownSeconds != job.defaultCooldown || job.searchRadius != job.defaultRadius || !job.enabled) {
                drawScaledText(context, "\u00A76\u00B7", colNameX + nameW, rowY + 3, 0xFF9800)
            }

            // Cooldown field
            val fieldY = rowY + 1
            val fieldH = ROW_H - 2
            renderField(
                context,
                colCooldownX, fieldY, fieldW, fieldH,
                if (activeFieldJob == jobIdx && activeFieldType == FieldType.COOLDOWN) fieldText else job.cooldownSeconds.toString(),
                isActive = activeFieldJob == jobIdx && activeFieldType == FieldType.COOLDOWN,
                isOverride = job.cooldownSeconds != job.defaultCooldown
            )

            // Radius field
            renderField(
                context,
                colRadiusX, fieldY, fieldW, fieldH,
                if (activeFieldJob == jobIdx && activeFieldType == FieldType.RADIUS) fieldText else job.searchRadius.toString(),
                isActive = activeFieldJob == jobIdx && activeFieldType == FieldType.RADIUS,
                isOverride = job.searchRadius != job.defaultRadius
            )

            // Checkbox
            val cbX = colEnabledX + 2
            val cbY = rowY + 3
            val cbSize = 8
            context.fill(cbX, cbY, cbX + cbSize, cbY + cbSize, if (job.enabled) CHECKBOX_ON else CHECKBOX_OFF)
        }

        context.disableScissor()

        // Scrollbar
        if (visible.size > maxVisible) {
            val trackX = rightX + rightW - 3
            val trackH = listH
            val thumbH = ((maxVisible.toFloat() / visible.size) * trackH).toInt().coerceAtLeast(8)
            val thumbY = listY + ((scrollOffset.toFloat() / maxScroll) * (trackH - thumbH)).toInt()
            context.fill(trackX, listY, trackX + 2, listY + trackH, 0x33FFFFFF)
            context.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xAAFFFFFF.toInt())
        }

        // Unsaved indicator near save button
        if (jobEdits.any { it.dirty }) {
            saveButton?.let { sb ->
                drawScaledText(context, "\u00A7e*unsaved", sb.x - 50, sb.y + 3, 0xFFFF00)
            }
        }
    }

    private fun renderField(
        context: DrawContext,
        fx: Int, fy: Int, fw: Int, fh: Int,
        text: String,
        isActive: Boolean,
        isOverride: Boolean
    ) {
        val border = if (isActive) FIELD_ACTIVE else FIELD_BORDER
        context.fill(fx - 1, fy - 1, fx + fw + 1, fy + fh + 1, border)
        context.fill(fx, fy, fx + fw, fy + fh, FIELD_BG)
        val color = if (isOverride) 0xFFFF00 else 0xCCCCCC
        drawScaledText(context, text, fx + 2, fy + 2, color)
        if (isActive && (cursorBlink / 20) % 2 == 0) {
            val cursorX = fx + 2 + (textRenderer.getWidth(text) * SCALE).toInt()
            drawScaledText(context, "|", cursorX, fy + 2, 0xFFFFFF)
        }
    }

    private fun drawScaledText(context: DrawContext, text: String, px: Int, py: Int, color: Int) {
        context.matrices.push()
        context.matrices.translate(px.toFloat(), py.toFloat(), 0f)
        context.matrices.scale(SCALE, SCALE, 1f)
        context.drawTextWithShadow(textRenderer, text, 0, 0, color)
        context.matrices.pop()
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Sidebar click — switch category
        val sidebarX = x
        val sidebarY = y
        if (mouseX in sidebarX.toDouble()..(sidebarX + SIDEBAR_W).toDouble() &&
            mouseY in sidebarY.toDouble()..(sidebarY + h).toDouble()) {
            val relY = mouseY - (sidebarY + PADDING + 11)
            if (relY >= 0) {
                val idx = (relY / ROW_H).toInt()
                if (idx in categories.indices) {
                    commitActiveField()
                    activeCategory = categories[idx]
                    scrollOffset = 0
                    return true
                }
            }
            return true
        }

        val rightX = sidebarX + SIDEBAR_W + 2
        val rightW = w - SIDEBAR_W - 2
        val rightY = y
        val listY = rightY + PADDING + HEADER_H + 4
        val listH = h - (listY - y) - BUTTON_AREA_H - 2

        val fieldW = 38
        val colCooldownX = rightX + rightW - 130
        val colRadiusX = rightX + rightW - 86
        val colEnabledX = rightX + rightW - 30

        // Scrollbar drag start
        val visible = visibleJobs()
        val maxVisible = listH / ROW_H
        val maxScroll = (visible.size - maxVisible).coerceAtLeast(0)
        val trackX = rightX + rightW - 3
        if (maxScroll > 0 && mouseX in (trackX - 4).toDouble()..(trackX + 4).toDouble() &&
            mouseY in listY.toDouble()..(listY + listH).toDouble()) {
            isDraggingScrollbar = true
            val rel = ((mouseY - listY) / listH.toDouble()).coerceIn(0.0, 1.0)
            scrollOffset = (rel * maxScroll).toInt()
            return true
        }

        commitActiveField()

        if (mouseX in rightX.toDouble()..(rightX + rightW).toDouble() &&
            mouseY in listY.toDouble()..(listY + listH).toDouble()) {
            val visRow = ((mouseY - listY) / ROW_H).toInt()
            val visIdx = visRow + scrollOffset
            if (visIdx in visible.indices) {
                val jobIdx = visible[visIdx]
                val job = jobEdits[jobIdx]
                val fieldY = listY + visRow * ROW_H + 1
                val fieldH = ROW_H - 2

                if (mouseX in colCooldownX.toDouble()..(colCooldownX + fieldW).toDouble() &&
                    mouseY in fieldY.toDouble()..(fieldY + fieldH).toDouble()) {
                    activeFieldJob = jobIdx
                    activeFieldType = FieldType.COOLDOWN
                    fieldText = job.cooldownSeconds.toString()
                    return true
                }
                if (mouseX in colRadiusX.toDouble()..(colRadiusX + fieldW).toDouble() &&
                    mouseY in fieldY.toDouble()..(fieldY + fieldH).toDouble()) {
                    activeFieldJob = jobIdx
                    activeFieldType = FieldType.RADIUS
                    fieldText = job.searchRadius.toString()
                    return true
                }

                val cbX = colEnabledX + 2
                val cbY = listY + visRow * ROW_H + 3
                val cbSize = 8
                if (mouseX in cbX.toDouble()..(cbX + cbSize).toDouble() &&
                    mouseY in cbY.toDouble()..(cbY + cbSize).toDouble()) {
                    job.enabled = !job.enabled
                    job.dirty = true
                    return true
                }

                activeFieldJob = -1
                activeFieldType = FieldType.NONE
                return true
            }
        }

        activeFieldJob = -1
        activeFieldType = FieldType.NONE
        return false
    }

    private fun commitActiveField() {
        if (activeFieldJob < 0 || activeFieldType == FieldType.NONE) return
        val job = jobEdits[activeFieldJob]
        when (activeFieldType) {
            FieldType.COOLDOWN -> {
                fieldText.toLongOrNull()?.takeIf { it > 0 }?.let {
                    job.cooldownSeconds = it
                    job.dirty = true
                }
            }
            FieldType.RADIUS -> {
                fieldText.toIntOrNull()?.takeIf { it > 0 }?.let {
                    job.searchRadius = it
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
            val radiusOverride = if (job.searchRadius != job.defaultRadius) job.searchRadius else null
            PacketDistributor.sendToServer(AdminJobsUpdateC2SPacket(
                job.skillId, cooldownOverride, radiusOverride, job.enabled
            ))
            if (cooldownOverride == null && radiusOverride == null && job.enabled) {
                newOverrides.remove(job.skillId)
            } else {
                newOverrides[job.skillId] = JobConfigOverrides.JobOverride(
                    cooldownSeconds = cooldownOverride,
                    searchRadius = radiusOverride,
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
        if (keyCode == 259 && fieldText.isNotEmpty()) { // backspace
            fieldText = fieldText.dropLast(1)
            return true
        }
        if (keyCode == 257 || keyCode == 335) { // enter
            commitActiveField()
            return true
        }
        if (keyCode == 256) { // escape
            activeFieldJob = -1
            activeFieldType = FieldType.NONE
            return true
        }
        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (isDraggingScrollbar) {
            val rightX = x + SIDEBAR_W + 2
            val rightW = w - SIDEBAR_W - 2
            val rightY = y
            val listY = rightY + PADDING + HEADER_H + 4
            val listH = h - (listY - y) - BUTTON_AREA_H - 2
            val visible = visibleJobs()
            val maxVisible = listH / ROW_H
            val maxScroll = (visible.size - maxVisible).coerceAtLeast(0)
            val rel = ((mouseY - listY) / listH.toDouble()).coerceIn(0.0, 1.0)
            scrollOffset = (rel * maxScroll).toInt()
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
        val rightX = x + SIDEBAR_W + 2
        if (mouseX >= rightX && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
            val rightY = y
            val listY = rightY + PADDING + HEADER_H + 4
            val listH = h - (listY - y) - BUTTON_AREA_H - 2
            val visible = visibleJobs()
            val maxVisible = listH / ROW_H
            val maxScroll = (visible.size - maxVisible).coerceAtLeast(0)
            scrollOffset = (scrollOffset - verticalAmount.toInt() * 3).coerceIn(0, maxScroll)
            return true
        }
        return false
    }
}

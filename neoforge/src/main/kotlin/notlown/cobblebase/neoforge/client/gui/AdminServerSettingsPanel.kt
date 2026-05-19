package notlown.cobblebase.neoforge.client.gui

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ClickableWidget
import java.util.function.Function

/**
 * Admin GUI "Server" tab — server-wide settings the admin can toggle without leaving
 * the in-game GUI. Currently houses the WorkerWiki visibility flag; designed to grow
 * as more server-side feature flags get added.
 */
class AdminServerSettingsPanel(
    private val x: Int,
    private val y: Int,
    private val w: Int,
    private val h: Int,
    private val textRenderer: TextRenderer
) {
    private val PADDING = 6
    private val HEADER_H = 22
    private val ROW_H = 24
    private val ROW_GAP = 6

    /** Hit-boxes for each toggle row, keyed by setting id. */
    private val toggleBoxes = mutableMapOf<String, IntArray>()
    /** Hit-boxes for stepper buttons (− / +) per integer setting. */
    private val stepperBoxes = mutableMapOf<String, Pair<IntArray, IntArray>>() // id -> (minusBox, plusBox)
    /** Hit-boxes for the slider track + reset button per integer setting. */
    private val sliderTrackBoxes = mutableMapOf<String, IntArray>()  // id -> [x, y, w, h]
    private val resetBoxes = mutableMapOf<String, IntArray>()        // id -> [x, y, w, h]
    /** Which slider is currently being dragged with the mouse (null = none). */
    private var draggingSlider: String? = null
    /** Min/max bounds per integer setting. */
    private val PASTURE_RANGE_MIN = 1
    private val PASTURE_RANGE_MAX = 30
    /** Default values — displayed under the slider so admins know what "stock" is. */
    private val PASTURE_RANGE_DEFAULT = 10
    private val WORKING_CAP_DEFAULT = 0   // 0 = unlimited (pasture's own max)
    /**
     * Working-cap bounds. 0 = unlimited (use the pasture's own max — current behavior,
     * preserved as default so existing servers don't change). 1..64 reduces the number
     * of mons per pasture that can actively work simultaneously.
     */
    private val WORKING_CAP_MIN = 0
    private val WORKING_CAP_MAX = 64

    fun init(addWidget: Function<ClickableWidget, ClickableWidget>) {
        // No widgets yet — toggles are custom-drawn for full layout control.
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Background
        context.fill(x, y, x + w, y + h, 0xCC1E1E2E.toInt())

        // Header
        drawScaled(context, "§f§lServer Settings", x + PADDING, y + PADDING, 0xFFFFFF, 0.85f)
        drawScaled(context, "§7Toggle server-wide features players can see and use.",
            x + PADDING, y + PADDING + 9, 0x8B8B99, 0.65f)
        context.fill(x + PADDING, y + HEADER_H - 2, x + w - PADDING, y + HEADER_H - 1, 0xFF3A3A5C.toInt())

        toggleBoxes.clear()
        stepperBoxes.clear()
        var rowY = y + HEADER_H + ROW_GAP

        // WorkerWiki toggle
        val pwEnabled = notlown.cobblebase.core.GeneralSettingsCache.pokeWikiEnabled
        renderToggleRow(
            context, mouseX, mouseY,
            id = "pokeWiki",
            rowY = rowY,
            title = "WorkerWiki",
            description = "Lets players browse the in-game species reference. " +
                "Disable if you want them to discover skills by experimenting.",
            enabled = pwEnabled
        )
        rowY += ROW_H + ROW_GAP

        // Pasture range slider — now the global cap for every job's search radius
        // (see SkillRegistry.getEffectiveRadius).
        val effectiveRange = notlown.cobblebase.core.GeneralSettingsCache.pastureRange
            .let { if (it in PASTURE_RANGE_MIN..PASTURE_RANGE_MAX) it else PASTURE_RANGE_DEFAULT }
        renderStepperRow(
            context, mouseX, mouseY,
            id = "pastureRange",
            rowY = rowY,
            title = "Pasture Range",
            description = "Global cap on how far Pokemon scan for work — applies to every job. " +
                "The Show-Radius wireframe in the Pokemon tab draws this value.",
            value = effectiveRange,
            unit = "blocks",
            rangeMin = PASTURE_RANGE_MIN,
            rangeMax = PASTURE_RANGE_MAX,
            defaultValue = PASTURE_RANGE_DEFAULT
        )
        rowY += ROW_H + 10 + ROW_GAP

        // Working-cap slider.
        val workingCap = notlown.cobblebase.core.GeneralSettingsCache.maxWorkingPokemonPerPasture
            .coerceIn(WORKING_CAP_MIN, WORKING_CAP_MAX)
        renderStepperRow(
            context, mouseX, mouseY,
            id = "workingCap",
            rowY = rowY,
            title = "Max Working Pokemon per Pasture",
            description = "How many tethered Pokemon can actively work at once. " +
                "Extras keep their job but wait in queue until a slot opens.",
            value = workingCap,
            unit = if (workingCap == 0) "" else "mons",
            valueLabelOverride = if (workingCap == 0) "Unlimited" else null,
            rangeMin = WORKING_CAP_MIN,
            rangeMax = WORKING_CAP_MAX,
            defaultValue = WORKING_CAP_DEFAULT
        )
        rowY += ROW_H + 10 + ROW_GAP
    }

    private fun renderStepperRow(
        context: DrawContext, mouseX: Int, mouseY: Int,
        id: String, rowY: Int,
        title: String, description: String, value: Int, unit: String,
        valueLabelOverride: String? = null,
        rangeMin: Int = 0,
        rangeMax: Int = 0,
        defaultValue: Int = 0
    ) {
        val rowLeft = x + PADDING
        val rowRight = x + w - PADDING
        // Slider rows are taller to fit the track + range labels under the stepper.
        val totalH = if (rangeMax > rangeMin) ROW_H + 10 else ROW_H
        context.fill(rowLeft, rowY, rowRight, rowY + totalH, 0xFF1F1F2F.toInt())
        context.fill(rowLeft, rowY, rowLeft + 3, rowY + totalH, 0xFF6A6AFF.toInt())

        drawScaled(context, "§f§l$title", rowLeft + 8, rowY + 4, 0xFFFFFF, 0.85f)
        drawScaled(context, "§7$description", rowLeft + 8, rowY + 14, 0x8B8B99, 0.6f)

        // Stepper area: [ − ] [value unit] [ + ]   [↻]
        val stepW = 14
        val stepH = 14
        val resetW = 14
        val valueW = if (valueLabelOverride != null) 72 else 56
        val resetX = rowRight - resetW - 6
        val plusX = resetX - stepW - 4
        val valueX = plusX - valueW - 2
        val minusX = valueX - stepW - 2
        val stepY = rowY + (ROW_H - stepH) / 2

        // Minus
        val minusHov = mouseX in minusX..(minusX + stepW) && mouseY in stepY..(stepY + stepH)
        context.fill(minusX, stepY, minusX + stepW, stepY + stepH, if (minusHov) 0xFF3A3A5A.toInt() else 0xFF2A2A3F.toInt())
        drawScaled(context, "§f§l−", minusX + 5, stepY + 3, 0xFFFFFF, 0.85f)

        // Value box
        context.fill(valueX, stepY, valueX + valueW, stepY + stepH, 0xFF15151E.toInt())
        val valueText = valueLabelOverride?.let { "§e$it" } ?: "§f$value §7$unit"
        drawScaled(context, valueText, valueX + 6, stepY + 3, 0xFFFFFF, 0.85f)

        // Plus
        val plusHov = mouseX in plusX..(plusX + stepW) && mouseY in stepY..(stepY + stepH)
        context.fill(plusX, stepY, plusX + stepW, stepY + stepH, if (plusHov) 0xFF3A3A5A.toInt() else 0xFF2A2A3F.toInt())
        drawScaled(context, "§f§l+", plusX + 5, stepY + 3, 0xFFFFFF, 0.85f)

        // Reset (↻) — visible only when a default is provided.
        if (rangeMax > rangeMin) {
            val resetHov = mouseX in resetX..(resetX + resetW) && mouseY in stepY..(stepY + stepH)
            val resetBg = if (resetHov) 0xFF5A3A3A.toInt() else 0xFF2A2A3F.toInt()
            context.fill(resetX, stepY, resetX + resetW, stepY + stepH, resetBg)
            drawScaled(context, "§e§l↻", resetX + 4, stepY + 3, 0xFFFFFF, 0.85f)
            resetBoxes[id] = intArrayOf(resetX, stepY, resetW, stepH)
        }

        stepperBoxes[id] = intArrayOf(minusX, stepY, stepW, stepH) to intArrayOf(plusX, stepY, stepW, stepH)

        // Slider track + range labels (only when a range is provided).
        if (rangeMax > rangeMin) {
            val trackY = rowY + ROW_H + 1
            val trackH = 4
            val trackLeft = rowLeft + 8
            val trackRight = rowRight - 8
            val trackW = trackRight - trackLeft

            // Track background.
            context.fill(trackLeft, trackY, trackRight, trackY + trackH, 0xFF15151E.toInt())

            // Progress fill from trackLeft to current value.
            val span = (rangeMax - rangeMin).coerceAtLeast(1)
            val valFrac = ((value - rangeMin).toFloat() / span).coerceIn(0f, 1f)
            val fillX = trackLeft + (trackW * valFrac).toInt()
            context.fill(trackLeft, trackY, fillX, trackY + trackH, 0xFF6A6AFF.toInt())

            // Default-value tick — vertical line on the track at the default position.
            val defFrac = ((defaultValue - rangeMin).toFloat() / span).coerceIn(0f, 1f)
            val defX = trackLeft + (trackW * defFrac).toInt()
            context.fill(defX - 1, trackY - 2, defX + 1, trackY + trackH + 2, 0xFFFFD93D.toInt())

            // Thumb — small square at current value.
            val thumbX = fillX - 2
            context.fill(thumbX, trackY - 2, thumbX + 4, trackY + trackH + 2, 0xFFFFFFFF.toInt())

            // Min / max range labels under the track.
            drawScaled(context, "§8$rangeMin", trackLeft, trackY + trackH + 1, 0x666666, 0.55f)
            val maxLabel = "§8$rangeMax"
            val maxW = (textRenderer.getWidth(maxLabel) * 0.55f).toInt()
            drawScaled(context, maxLabel, trackRight - maxW, trackY + trackH + 1, 0x666666, 0.55f)

            // "(default N)" label centered under the track.
            val defText = "§7default §e$defaultValue"
            val defTextW = (textRenderer.getWidth(defText) * 0.55f).toInt()
            drawScaled(context, defText, (trackLeft + trackRight - defTextW) / 2, trackY + trackH + 1, 0x999999, 0.55f)

            sliderTrackBoxes[id] = intArrayOf(trackLeft, trackY - 4, trackW, trackH + 8)
        }
    }

    private fun renderToggleRow(
        context: DrawContext, mouseX: Int, mouseY: Int,
        id: String, rowY: Int,
        title: String, description: String, enabled: Boolean
    ) {
        val rowLeft = x + PADDING
        val rowRight = x + w - PADDING
        // Row background card
        context.fill(rowLeft, rowY, rowRight, rowY + ROW_H, 0xFF1F1F2F.toInt())
        context.fill(rowLeft, rowY, rowLeft + 3, rowY + ROW_H, 0xFF6A6AFF.toInt())

        // Title + description on the left
        drawScaled(context, "§f§l$title", rowLeft + 8, rowY + 4, 0xFFFFFF, 0.85f)
        drawScaled(context, "§7$description", rowLeft + 8, rowY + 14, 0x8B8B99, 0.6f)

        // Toggle button on the right
        val toggleW = 80
        val toggleH = 14
        val toggleX = rowRight - toggleW - 6
        val toggleY = rowY + (ROW_H - toggleH) / 2
        val hovered = mouseX in toggleX..(toggleX + toggleW) && mouseY in toggleY..(toggleY + toggleH)
        val toggleBg = when {
            enabled && hovered -> 0xFF2E5E33.toInt()
            enabled -> 0xFF1F4022.toInt()
            hovered -> 0xFF5E2E2E.toInt()
            else -> 0xFF402020.toInt()
        }
        context.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, toggleBg)
        val accent = if (enabled) 0xFF4CAF50.toInt() else 0xFFD32F2F.toInt()
        context.fill(toggleX, toggleY + toggleH - 1, toggleX + toggleW, toggleY + toggleH, accent)
        val label = if (enabled) "§a✓ Enabled" else "§c✗ Disabled"
        drawScaled(context, label, toggleX + 8, toggleY + 3, 0xFFFFFF, 0.85f)

        toggleBoxes[id] = intArrayOf(toggleX, toggleY, toggleW, toggleH)
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        for ((id, box) in toggleBoxes) {
            val (bx, by, bw, bh) = box
            if (mouseX in bx.toDouble()..(bx + bw).toDouble() && mouseY in by.toDouble()..(by + bh).toDouble()) {
                when (id) {
                    "pokeWiki" -> {
                        val cache = notlown.cobblebase.core.GeneralSettingsCache
                        sendUpdate(pokeWiki = !cache.pokeWikiEnabled)
                    }
                }
                return true
            }
        }
        // Reset button: snap the setting back to its documented default.
        for ((id, box) in resetBoxes) {
            val (bx, by, bw, bh) = box
            if (mouseX in bx.toDouble()..(bx + bw).toDouble() && mouseY in by.toDouble()..(by + bh).toDouble()) {
                when (id) {
                    "pastureRange" -> sendUpdate(pastureRange = PASTURE_RANGE_DEFAULT)
                    "workingCap" -> sendUpdate(workingCap = WORKING_CAP_DEFAULT)
                }
                return true
            }
        }
        // Stepper ± buttons.
        for ((id, pair) in stepperBoxes) {
            val (minus, plus) = pair
            val inMinus = mouseX in minus[0].toDouble()..(minus[0] + minus[2]).toDouble() && mouseY in minus[1].toDouble()..(minus[1] + minus[3]).toDouble()
            val inPlus = mouseX in plus[0].toDouble()..(plus[0] + plus[2]).toDouble() && mouseY in plus[1].toDouble()..(plus[1] + plus[3]).toDouble()
            if (!inMinus && !inPlus) continue
            val delta = if (inPlus) 1 else -1
            applyDelta(id, delta)
            return true
        }
        // Slider track: jump-to-position on click, then start drag tracking.
        for ((id, box) in sliderTrackBoxes) {
            val (bx, by, bw, bh) = box
            if (mouseX in bx.toDouble()..(bx + bw).toDouble() && mouseY in by.toDouble()..(by + bh).toDouble()) {
                setSliderFromMouse(id, mouseX, bx, bw)
                draggingSlider = id
                return true
            }
        }
        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        val id = draggingSlider ?: return false
        val box = sliderTrackBoxes[id] ?: return false
        setSliderFromMouse(id, mouseX, box[0], box[2])
        return true
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (draggingSlider != null) {
            draggingSlider = null
            return true
        }
        return false
    }

    private fun setSliderFromMouse(id: String, mouseX: Double, trackX: Int, trackW: Int) {
        val frac = ((mouseX - trackX) / trackW.toDouble()).coerceIn(0.0, 1.0)
        when (id) {
            "pastureRange" -> {
                val newVal = (PASTURE_RANGE_MIN + frac * (PASTURE_RANGE_MAX - PASTURE_RANGE_MIN)).toInt()
                sendUpdate(pastureRange = newVal.coerceIn(PASTURE_RANGE_MIN, PASTURE_RANGE_MAX))
            }
            "workingCap" -> {
                val newVal = (WORKING_CAP_MIN + frac * (WORKING_CAP_MAX - WORKING_CAP_MIN)).toInt()
                sendUpdate(workingCap = newVal.coerceIn(WORKING_CAP_MIN, WORKING_CAP_MAX))
            }
        }
    }

    private fun applyDelta(id: String, delta: Int) {
        val cache = notlown.cobblebase.core.GeneralSettingsCache
        when (id) {
            "pastureRange" -> {
                val current = if (cache.pastureRange in PASTURE_RANGE_MIN..PASTURE_RANGE_MAX) cache.pastureRange else PASTURE_RANGE_DEFAULT
                val newVal = (current + delta).coerceIn(PASTURE_RANGE_MIN, PASTURE_RANGE_MAX)
                if (newVal != current) sendUpdate(pastureRange = newVal)
            }
            "workingCap" -> {
                val current = cache.maxWorkingPokemonPerPasture.coerceIn(WORKING_CAP_MIN, WORKING_CAP_MAX)
                val newVal = (current + delta).coerceIn(WORKING_CAP_MIN, WORKING_CAP_MAX)
                if (newVal != current) sendUpdate(workingCap = newVal)
            }
        }
    }

    /**
     * Single point of write for every setting in this panel — fills in current
     * cache values for whatever isn't being changed so the server packet always
     * carries a complete snapshot.
     */
    private fun sendUpdate(
        pokeWiki: Boolean? = null,
        pastureRange: Int? = null,
        workingCap: Int? = null,
    ) {
        val cache = notlown.cobblebase.core.GeneralSettingsCache
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            notlown.cobblebase.core.net.GeneralSettingsUpdateC2SPacket(
                cache.discordUrl,
                cache.discordEnabled,
                pokeWiki ?: cache.pokeWikiEnabled,
                pastureRange ?: cache.pastureRange,
                workingCap ?: cache.maxWorkingPokemonPerPasture
            )
        )
    }
    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean = false

    private fun drawScaled(context: DrawContext, text: String, px: Int, py: Int, color: Int, scale: Float) {
        context.matrices.push()
        context.matrices.translate(px.toFloat(), py.toFloat(), 0f)
        context.matrices.scale(scale, scale, 1f)
        context.drawTextWithShadow(textRenderer, text, 0, 0, color)
        context.matrices.pop()
    }
}

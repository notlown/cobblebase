package notlown.cobblebase.fabric.client.gui

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
    /** Min/max bounds per integer setting. */
    private val PASTURE_RANGE_MIN = 5
    private val PASTURE_RANGE_MAX = 30

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

        // Pasture range stepper
        val effectiveRange = notlown.cobblebase.core.GeneralSettingsCache.pastureRange
            .let { if (it in PASTURE_RANGE_MIN..PASTURE_RANGE_MAX) it else 10 }
        renderStepperRow(
            context, mouseX, mouseY,
            id = "pastureRange",
            rowY = rowY,
            title = "Pasture Range",
            description = "Block radius Pokemon use to scan for work and stay near the pasture. " +
                "Applies server-wide.",
            value = effectiveRange,
            unit = "blocks"
        )
        rowY += ROW_H + ROW_GAP
    }

    private fun renderStepperRow(
        context: DrawContext, mouseX: Int, mouseY: Int,
        id: String, rowY: Int,
        title: String, description: String, value: Int, unit: String
    ) {
        val rowLeft = x + PADDING
        val rowRight = x + w - PADDING
        context.fill(rowLeft, rowY, rowRight, rowY + ROW_H, 0xFF1F1F2F.toInt())
        context.fill(rowLeft, rowY, rowLeft + 3, rowY + ROW_H, 0xFF6A6AFF.toInt())

        drawScaled(context, "§f§l$title", rowLeft + 8, rowY + 4, 0xFFFFFF, 0.85f)
        drawScaled(context, "§7$description", rowLeft + 8, rowY + 14, 0x8B8B99, 0.6f)

        // Stepper area: [ − ] [value unit] [ + ]
        val stepW = 14
        val stepH = 14
        val valueW = 56
        val plusX = rowRight - stepW - 6
        val valueX = plusX - valueW - 2
        val minusX = valueX - stepW - 2
        val stepY = rowY + (ROW_H - stepH) / 2

        // Minus button
        val minusHov = mouseX in minusX..(minusX + stepW) && mouseY in stepY..(stepY + stepH)
        context.fill(minusX, stepY, minusX + stepW, stepY + stepH, if (minusHov) 0xFF3A3A5A.toInt() else 0xFF2A2A3F.toInt())
        drawScaled(context, "§f§l−", minusX + 5, stepY + 3, 0xFFFFFF, 0.85f)
        stepperBoxes[id] = intArrayOf(minusX, stepY, stepW, stepH) to intArrayOf(plusX, stepY, stepW, stepH)
        // (We overwrite below with the real pair — keep the placeholder until plus is rendered.)

        // Value box
        context.fill(valueX, stepY, valueX + valueW, stepY + stepH, 0xFF15151E.toInt())
        val valueText = "§f$value §7$unit"
        drawScaled(context, valueText, valueX + 6, stepY + 3, 0xFFFFFF, 0.85f)

        // Plus button
        val plusHov = mouseX in plusX..(plusX + stepW) && mouseY in stepY..(stepY + stepH)
        context.fill(plusX, stepY, plusX + stepW, stepY + stepH, if (plusHov) 0xFF3A3A5A.toInt() else 0xFF2A2A3F.toInt())
        drawScaled(context, "§f§l+", plusX + 5, stepY + 3, 0xFFFFFF, 0.85f)
        stepperBoxes[id] = intArrayOf(minusX, stepY, stepW, stepH) to intArrayOf(plusX, stepY, stepW, stepH)
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
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            notlown.cobblebase.core.net.GeneralSettingsUpdateC2SPacket(
                                cache.discordUrl, cache.discordEnabled, !cache.pokeWikiEnabled, cache.pastureRange
                            )
                        )
                    }
                }
                return true
            }
        }
        for ((id, pair) in stepperBoxes) {
            val (minus, plus) = pair
            val cache = notlown.cobblebase.core.GeneralSettingsCache
            val currentRange = if (cache.pastureRange in PASTURE_RANGE_MIN..PASTURE_RANGE_MAX) cache.pastureRange else 10
            val inMinus = mouseX in minus[0].toDouble()..(minus[0] + minus[2]).toDouble() && mouseY in minus[1].toDouble()..(minus[1] + minus[3]).toDouble()
            val inPlus = mouseX in plus[0].toDouble()..(plus[0] + plus[2]).toDouble() && mouseY in plus[1].toDouble()..(plus[1] + plus[3]).toDouble()
            if (!inMinus && !inPlus) continue
            when (id) {
                "pastureRange" -> {
                    val delta = if (inPlus) 1 else -1
                    val newRange = (currentRange + delta).coerceIn(PASTURE_RANGE_MIN, PASTURE_RANGE_MAX)
                    if (newRange != currentRange) {
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            notlown.cobblebase.core.net.GeneralSettingsUpdateC2SPacket(
                                cache.discordUrl, cache.discordEnabled, cache.pokeWikiEnabled, newRange
                            )
                        )
                    }
                }
            }
            return true
        }
        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean = false
    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean = false
    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean = false

    private fun drawScaled(context: DrawContext, text: String, px: Int, py: Int, color: Int, scale: Float) {
        context.matrices.push()
        context.matrices.translate(px.toFloat(), py.toFloat(), 0f)
        context.matrices.scale(scale, scale, 1f)
        context.drawTextWithShadow(textRenderer, text, 0, 0, color)
        context.matrices.pop()
    }
}

package notlown.cobblebase.neoforge.client.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.math.BlockPos
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.GeneralSettingsCache
import notlown.cobblebase.core.PastureSettings
import notlown.cobblebase.core.PastureSettingsCache
import notlown.cobblebase.core.net.PastureSettingsUpdateC2SPacket

/**
 * Modal popup that lets the pasture owner pick a per-pasture range + below-reach
 * inside the admin caps, plus an access mode that gates who else can open/edit
 * this pasture's UI. Renders as an overlay inside [CobblebaseScreen]; clicks
 * outside the panel close it.
 *
 * Sliders are interactive only when the local player is the pasture owner (or
 * OP). Other players see read-only values + a "private base" indicator.
 */
class BaseSettingsModal(
    private val pasturePos: BlockPos,
    private val ownerUuid: java.util.UUID?,
    private val textRenderer: TextRenderer
) {

    private val PANEL_W = 220
    private val PANEL_H = 170
    private val PADDING = 10
    private val ROW_H = 22
    private val ROW_GAP = 4
    private val TRACK_H = 4

    private val BG = 0xEE0E0E1A.toInt()
    private val BORDER = 0xFF4A4A6A.toInt()
    private val TITLE_DIVIDER = 0xFF3A3A5C.toInt()
    private val ROW_BG = 0xFF1F1F2F.toInt()
    private val ACCENT = 0xFF6A6AFF.toInt()
    private val THUMB = 0xFFFFFFFF.toInt()

    /** Whether the local player may write to this pasture's settings. */
    private val canEdit: Boolean

    /** Computed panel origin — set every render() from screen dimensions. */
    private var panelX = 0
    private var panelY = 0

    /** Slider tracks for hit-testing. Set during render(). */
    private var rangeTrack = IntArray(4)
    private var belowTrack = IntArray(4)
    /** Access pill button hit-box. */
    private var accessBtn = IntArray(4)
    /** Close (×) button hit-box. */
    private var closeBtn = IntArray(4)
    /** Reset (↻) button hit-boxes per slider. */
    private var rangeResetBtn = IntArray(4)
    private var belowResetBtn = IntArray(4)

    private var dragging: String? = null

    init {
        val player = MinecraftClient.getInstance().player
        canEdit = player != null &&
                (player.hasPermissionLevel(2) || (ownerUuid != null && ownerUuid == player.uuid))
    }

    /**
     * Returns the effective per-pasture range — owner override clamped to admin
     * caps, falling back to admin max when no override is set.
     */
    private fun currentRange(): Int {
        val maxV = (GeneralSettingsCache.pastureRangeMax.takeIf { it in 5..30 } ?: 10)
        val minV = (GeneralSettingsCache.pastureRangeMin.takeIf { it in 5..30 } ?: 5)
        val override = PastureSettingsCache.getRange(pasturePos)
        return (override ?: maxV).coerceIn(minV, maxV)
    }
    private fun currentBelow(): Int {
        val maxV = (GeneralSettingsCache.belowPastureReachMax.takeIf { it in 0..30 } ?: 6)
        val minV = (GeneralSettingsCache.belowPastureReachMin.takeIf { it in 0..30 } ?: 0)
        val override = PastureSettingsCache.getBelowReach(pasturePos)
        return (override ?: maxV).coerceIn(minV, maxV)
    }
    private fun currentAccess(): PastureSettings.AccessMode {
        val ord = PastureSettingsCache.getAccessOrdinal(pasturePos)
        return PastureSettings.AccessMode.values().getOrElse(ord) { PastureSettings.AccessMode.OWNER_ONLY }
    }

    fun render(context: DrawContext, screenW: Int, screenH: Int, mouseX: Int, mouseY: Int) {
        // Push the whole modal far enough forward in Z that DrawContext.drawItem
        // (which renders at z≈150 during ItemRenderer's translucent pass) ends
        // up BEHIND the modal instead of poking through the panel background.
        // 400 sits above items (150) but below vanilla tooltips (also ~400),
        // which is fine since the modal owns input while open anyway.
        context.matrices.push()
        context.matrices.translate(0f, 0f, 400f)

        // Backdrop dims the underlying GUI.
        context.fill(0, 0, screenW, screenH, 0xB0000000.toInt())

        panelX = (screenW - PANEL_W) / 2
        panelY = (screenH - PANEL_H) / 2

        // Panel background + border.
        context.fill(panelX - 1, panelY - 1, panelX + PANEL_W + 1, panelY + PANEL_H + 1, BORDER)
        context.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, BG)

        // Title bar.
        drawScaled(context, "§f§lBase Settings", panelX + PADDING, panelY + PADDING, 0xFFFFFF, 0.95f)
        val ownerNote = if (ownerUuid == null) "§7unowned pasture"
            else if (canEdit) "§ayou own this pasture"
            else "§cread-only — not your pasture"
        drawScaled(context, ownerNote, panelX + PADDING, panelY + PADDING + 9, 0xCCCCCC, 0.6f)

        // Close button (×) top-right.
        val closeSize = 12
        closeBtn = intArrayOf(panelX + PANEL_W - closeSize - 4, panelY + 4, closeSize, closeSize)
        val closeHov = mouseX in closeBtn[0]..(closeBtn[0] + closeBtn[2]) &&
                       mouseY in closeBtn[1]..(closeBtn[1] + closeBtn[3])
        context.fill(closeBtn[0], closeBtn[1], closeBtn[0] + closeBtn[2], closeBtn[1] + closeBtn[3],
            if (closeHov) 0xFF5A2A2A.toInt() else 0xFF2A2A3F.toInt())
        drawScaled(context, "§f×", closeBtn[0] + 4, closeBtn[1] + 2, 0xFFFFFF, 0.85f)

        // Divider under title.
        context.fill(panelX + PADDING, panelY + PADDING + 18,
                     panelX + PANEL_W - PADDING, panelY + PADDING + 19, TITLE_DIVIDER)

        var rowY = panelY + PADDING + 24

        // Range slider row.
        val rangeMaxAdmin = (GeneralSettingsCache.pastureRangeMax.takeIf { it in 5..30 } ?: 10)
        val rangeMinAdmin = (GeneralSettingsCache.pastureRangeMin.takeIf { it in 5..30 } ?: 5)
        rangeTrack = drawSliderRow(context, rowY, "Pasture Range",
            value = currentRange(), unit = "blocks",
            min = rangeMinAdmin, max = rangeMaxAdmin, mouseX = mouseX, mouseY = mouseY,
            resetTarget = "range")
        rowY += ROW_H + 12 + ROW_GAP

        // Below-reach slider row.
        val belowMaxAdmin = (GeneralSettingsCache.belowPastureReachMax.takeIf { it in 0..30 } ?: 6)
        val belowMinAdmin = (GeneralSettingsCache.belowPastureReachMin.takeIf { it in 0..30 } ?: 0)
        belowTrack = drawSliderRow(context, rowY, "Below-Pasture Reach",
            value = currentBelow(), unit = "blocks",
            min = belowMinAdmin, max = belowMaxAdmin, mouseX = mouseX, mouseY = mouseY,
            resetTarget = "below")
        rowY += ROW_H + 12 + ROW_GAP

        // Access mode pill row.
        accessBtn = drawAccessRow(context, rowY, mouseX, mouseY)

        context.matrices.pop()
    }

    /**
     * Draws "Title" + slider track + reset button. Returns the slider track box
     * `[trackX, trackY-2, trackW, TRACK_H+4]` for hit-testing.
     */
    private fun drawSliderRow(
        context: DrawContext, rowY: Int, title: String,
        value: Int, unit: String, min: Int, max: Int,
        mouseX: Int, mouseY: Int, resetTarget: String
    ): IntArray {
        val left = panelX + PADDING
        val right = panelX + PANEL_W - PADDING
        // Row background.
        context.fill(left, rowY, right, rowY + ROW_H + 12, ROW_BG)
        context.fill(left, rowY, left + 3, rowY + ROW_H + 12, ACCENT)

        // Title.
        drawScaled(context, "§f$title", left + 8, rowY + 4, 0xFFFFFF, 0.8f)

        // Value pill on the right.
        val valLabel = "§e$value §7$unit"
        val valW = (textRenderer.getWidth(valLabel) * 0.75f).toInt() + 8
        val valX = right - valW - 22 // leave space for reset button
        val valY = rowY + 3
        context.fill(valX, valY, valX + valW, valY + 12, 0xFF15151E.toInt())
        drawScaled(context, valLabel, valX + 4, valY + 3, 0xFFFFFF, 0.75f)

        // Reset button (↻) — only useful when owner.
        val resetSize = 12
        val resetX = right - resetSize - 4
        val resetY = rowY + 3
        val resetHov = canEdit && mouseX in resetX..(resetX + resetSize) && mouseY in resetY..(resetY + resetSize)
        context.fill(resetX, resetY, resetX + resetSize, resetY + resetSize,
            if (resetHov) 0xFF5A3A3A.toInt() else 0xFF2A2A3F.toInt())
        drawScaled(context, if (canEdit) "§e↻" else "§8↻", resetX + 3, resetY + 2, 0xFFFFFF, 0.85f)
        if (resetTarget == "range") rangeResetBtn = intArrayOf(resetX, resetY, resetSize, resetSize)
        else belowResetBtn = intArrayOf(resetX, resetY, resetSize, resetSize)

        // Track.
        val trackY = rowY + ROW_H
        val trackLeft = left + 8
        val trackRight = right - 8
        val trackW = trackRight - trackLeft
        context.fill(trackLeft, trackY, trackRight, trackY + TRACK_H, 0xFF15151E.toInt())
        val span = (max - min).coerceAtLeast(1)
        val frac = ((value - min).toFloat() / span).coerceIn(0f, 1f)
        val fillX = trackLeft + (trackW * frac).toInt()
        context.fill(trackLeft, trackY, fillX, trackY + TRACK_H, ACCENT)

        // Thumb.
        val thumbX = (fillX - 2).coerceIn(trackLeft - 2, trackRight - 2)
        context.fill(thumbX, trackY - 2, thumbX + 4, trackY + TRACK_H + 2, if (canEdit) THUMB else 0xFF888888.toInt())

        // Bounds labels.
        drawScaled(context, "§8$min", trackLeft, trackY + TRACK_H + 1, 0x666666, 0.55f)
        val maxLbl = "§8$max"
        val maxLblW = (textRenderer.getWidth(maxLbl) * 0.55f).toInt()
        drawScaled(context, maxLbl, trackRight - maxLblW, trackY + TRACK_H + 1, 0x666666, 0.55f)

        return intArrayOf(trackLeft, trackY - 4, trackW, TRACK_H + 8)
    }

    /**
     * Access mode row — pill button that cycles through OWNER_ONLY → VIEW_ONLY →
     * PUBLIC on click. Returns the pill button hit-box.
     */
    private fun drawAccessRow(context: DrawContext, rowY: Int, mouseX: Int, mouseY: Int): IntArray {
        val left = panelX + PADDING
        val right = panelX + PANEL_W - PADDING
        context.fill(left, rowY, right, rowY + ROW_H, ROW_BG)
        context.fill(left, rowY, left + 3, rowY + ROW_H, ACCENT)

        drawScaled(context, "§fAccess", left + 8, rowY + 4, 0xFFFFFF, 0.8f)
        drawScaled(context, "§7Who can open this pasture's UI",
            left + 8, rowY + 13, 0x888899, 0.55f)

        val access = currentAccess()
        val (label, color) = when (access) {
            PastureSettings.AccessMode.OWNER_ONLY -> "§c🔒 Private" to 0xFFD32F2F.toInt()
            PastureSettings.AccessMode.VIEW_ONLY -> "§e👁 View-Only" to 0xFFFFC107.toInt()
            PastureSettings.AccessMode.PUBLIC -> "§a🔓 Public" to 0xFF4CAF50.toInt()
        }
        val pillW = 76
        val pillH = 14
        val pillX = right - pillW - 4
        val pillY = rowY + (ROW_H - pillH) / 2
        val hov = canEdit && mouseX in pillX..(pillX + pillW) && mouseY in pillY..(pillY + pillH)
        val bg = if (hov) 0xFF3A3A5A.toInt() else 0xFF2A2A3F.toInt()
        context.fill(pillX, pillY, pillX + pillW, pillY + pillH, bg)
        context.fill(pillX, pillY + pillH - 1, pillX + pillW, pillY + pillH, color)
        drawScaled(context, label, pillX + 6, pillY + 3, 0xFFFFFF, 0.8f)
        return intArrayOf(pillX, pillY, pillW, pillH)
    }

    /** Returns true if the click was consumed (modal handled it OR clicked-outside-to-close). */
    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Close on outside-click.
        if (mouseX < panelX || mouseX > panelX + PANEL_W ||
            mouseY < panelY || mouseY > panelY + PANEL_H) {
            return false // signal "I didn't consume" — caller closes us
        }
        // × button.
        if (hit(mouseX, mouseY, closeBtn)) return true.also { dragging = "close" }
        if (!canEdit) return true // consume click silently
        // Reset buttons.
        if (hit(mouseX, mouseY, rangeResetBtn)) {
            // Reset == clear override → use admin max.
            send(rangeOverride = -1, belowOverride = currentBelow().takeIf { hasBelowOverride() } ?: -1)
            return true
        }
        if (hit(mouseX, mouseY, belowResetBtn)) {
            send(rangeOverride = currentRange().takeIf { hasRangeOverride() } ?: -1, belowOverride = -1)
            return true
        }
        // Access pill cycles.
        if (hit(mouseX, mouseY, accessBtn)) {
            val next = when (currentAccess()) {
                PastureSettings.AccessMode.OWNER_ONLY -> PastureSettings.AccessMode.VIEW_ONLY
                PastureSettings.AccessMode.VIEW_ONLY -> PastureSettings.AccessMode.PUBLIC
                PastureSettings.AccessMode.PUBLIC -> PastureSettings.AccessMode.OWNER_ONLY
            }
            send(rangeOverride = currentRange(), belowOverride = currentBelow(), accessOverride = next)
            return true
        }
        // Slider tracks.
        if (hit(mouseX, mouseY, rangeTrack)) {
            dragging = "range"
            updateDrag(mouseX)
            return true
        }
        if (hit(mouseX, mouseY, belowTrack)) {
            dragging = "below"
            updateDrag(mouseX)
            return true
        }
        return true
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (dragging == null || !canEdit) return false
        updateDrag(mouseX)
        return true
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val d = dragging ?: return false
        dragging = null
        return d != "close"
    }

    private fun updateDrag(mouseX: Double) {
        when (dragging) {
            "range" -> {
                val (frac, min, max) = clickFrac(mouseX, rangeTrack,
                    GeneralSettingsCache.pastureRangeMin.takeIf { it in 5..30 } ?: 5,
                    GeneralSettingsCache.pastureRangeMax.takeIf { it in 5..30 } ?: 10)
                val newVal = (min + frac * (max - min)).toInt().coerceIn(min, max)
                send(rangeOverride = newVal, belowOverride = currentBelow().takeIf { hasBelowOverride() } ?: -1)
            }
            "below" -> {
                val (frac, min, max) = clickFrac(mouseX, belowTrack,
                    GeneralSettingsCache.belowPastureReachMin.takeIf { it in 0..30 } ?: 0,
                    GeneralSettingsCache.belowPastureReachMax.takeIf { it in 0..30 } ?: 6)
                val newVal = (min + frac * (max - min)).toInt().coerceIn(min, max)
                send(rangeOverride = currentRange().takeIf { hasRangeOverride() } ?: -1, belowOverride = newVal)
            }
        }
    }

    private fun clickFrac(mouseX: Double, track: IntArray, min: Int, max: Int): Triple<Float, Int, Int> {
        val frac = ((mouseX - track[0]) / track[2].toDouble()).coerceIn(0.0, 1.0).toFloat()
        return Triple(frac, min, max)
    }

    private fun hasRangeOverride(): Boolean = PastureSettingsCache.getRange(pasturePos) != null
    private fun hasBelowOverride(): Boolean = PastureSettingsCache.getBelowReach(pasturePos) != null

    /** Send the C2S update. -1 sentinel = clear that override on the server side. */
    private fun send(rangeOverride: Int = -1, belowOverride: Int = -1,
                     accessOverride: PastureSettings.AccessMode = currentAccess()) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            PastureSettingsUpdateC2SPacket(pasturePos, rangeOverride, belowOverride, accessOverride.ordinal)
        )
    }

    private fun hit(mx: Double, my: Double, box: IntArray): Boolean {
        if (box.size < 4) return false
        return mx >= box[0] && mx <= box[0] + box[2] && my >= box[1] && my <= box[1] + box[3]
    }

    private fun drawScaled(context: DrawContext, text: String, px: Int, py: Int, color: Int, scale: Float) {
        context.matrices.push()
        context.matrices.translate(px.toFloat(), py.toFloat(), 0f)
        context.matrices.scale(scale, scale, 1f)
        context.drawTextWithShadow(textRenderer, text, 0, 0, color)
        context.matrices.pop()
    }
}

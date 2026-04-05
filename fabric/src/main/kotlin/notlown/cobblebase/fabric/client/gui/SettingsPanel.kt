package notlown.cobblebase.fabric.client.gui

import notlown.cobblebase.core.PastureSettingsCache
import notlown.cobblebase.core.net.PastureSettingsRequestC2SPacket
import notlown.cobblebase.core.net.PastureSettingsUpdateC2SPacket
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import java.util.function.Function

/**
 * Settings tab content - allows the player to configure per-pasture block settings.
 * Currently supports adjusting the search radius within admin-configured limits.
 */
class SettingsPanel(
    private val parent: CobblebaseScreen,
    private val pastureOrigin: BlockPos?,
    private val panelX: Int,
    private val panelY: Int,
    private val panelW: Int,
    private val panelH: Int,
    private val textRenderer: TextRenderer
) {

    private val PADDING = 8
    private var currentRadius = PastureSettingsCache.searchRadius
    private var radiusMin = PastureSettingsCache.radiusMin
    private var radiusMax = PastureSettingsCache.radiusMax
    private var dataLoaded = false
    private var lastSyncCheck = 0L

    fun init(addWidget: Function<ButtonWidget, ButtonWidget>) {
        // Reset sync state and request current settings from server
        dataLoaded = false
        PastureSettingsCache.synced = false
        ClientPlayNetworking.send(PastureSettingsRequestC2SPacket())

        // Done button
        addWidget.apply(ButtonWidget.builder(Text.literal("Done")) { parent.close() }
            .dimensions(panelX + panelW - 54, panelY + panelH - 16, 40, 12).build())
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Check if data has been synced from server
        if (!dataLoaded && PastureSettingsCache.synced) {
            currentRadius = PastureSettingsCache.searchRadius
            radiusMin = PastureSettingsCache.radiusMin
            radiusMax = PastureSettingsCache.radiusMax
            dataLoaded = true
        }

        val centerX = panelX + panelW / 2
        val startY = panelY + 30

        // Title
        val title = "\u00A7fPasture Settings"
        val titleW = textRenderer.getWidth(title)
        context.drawTextWithShadow(textRenderer, title, centerX - titleW / 2, startY, 0xFFFFFF)

        // Separator
        context.fill(panelX + PADDING, startY + 14, panelX + panelW - PADDING, startY + 15, CobblebaseScreen.PANEL_BORDER)

        // Search Radius section
        val sectionY = startY + 28

        // Label
        context.drawTextWithShadow(textRenderer, "\u00A7eSearch Radius", panelX + PADDING, sectionY, 0xFFFF55)

        // Description
        val scale = 0.75f
        context.matrices.push()
        context.matrices.translate((panelX + PADDING).toFloat(), (sectionY + 14).toFloat(), 0f)
        context.matrices.scale(scale, scale, 1f)
        context.drawTextWithShadow(textRenderer, "\u00A77How far your Pokemon search for blocks and items.", 0, 0, 0x888888)
        context.matrices.pop()

        // Radius display area
        val controlY = sectionY + 30
        val controlCenterX = centerX

        // Minus button
        val minusBtnX = controlCenterX - 60
        val minusBtnY = controlY
        val minusBtnW = 24
        val minusBtnH = 20
        val minusHovered = mouseX in minusBtnX..(minusBtnX + minusBtnW) && mouseY in minusBtnY..(minusBtnY + minusBtnH)
        val minusBg = if (minusHovered) 0xFF4A4A6A.toInt() else 0xFF2A2A3E.toInt()
        val minusEnabled = currentRadius > radiusMin
        context.fill(minusBtnX, minusBtnY, minusBtnX + minusBtnW, minusBtnY + minusBtnH, minusBg)
        drawBorder(context, minusBtnX, minusBtnY, minusBtnW, minusBtnH, if (minusEnabled) 0xFF555577.toInt() else 0xFF333344.toInt())
        val minusText = "-"
        val minusColor = if (minusEnabled) 0xFFFFFF else 0x555555
        context.drawCenteredTextWithShadow(textRenderer, minusText, minusBtnX + minusBtnW / 2, minusBtnY + 6, minusColor)

        // Current value display
        val valueText = "$currentRadius"
        val valueW = textRenderer.getWidth(valueText)
        context.drawCenteredTextWithShadow(textRenderer, valueText, controlCenterX, controlY + 6, 0x55FF55)

        // Plus button
        val plusBtnX = controlCenterX + 36
        val plusBtnY = controlY
        val plusBtnW = 24
        val plusBtnH = 20
        val plusHovered = mouseX in plusBtnX..(plusBtnX + plusBtnW) && mouseY in plusBtnY..(plusBtnY + plusBtnH)
        val plusBg = if (plusHovered) 0xFF4A4A6A.toInt() else 0xFF2A2A3E.toInt()
        val plusEnabled = currentRadius < radiusMax
        context.fill(plusBtnX, plusBtnY, plusBtnX + plusBtnW, plusBtnY + plusBtnH, plusBg)
        drawBorder(context, plusBtnX, plusBtnY, plusBtnW, plusBtnH, if (plusEnabled) 0xFF555577.toInt() else 0xFF333344.toInt())
        val plusText = "+"
        val plusColor = if (plusEnabled) 0xFFFFFF else 0x555555
        context.drawCenteredTextWithShadow(textRenderer, plusText, plusBtnX + plusBtnW / 2, plusBtnY + 6, plusColor)

        // Range label
        val rangeY = controlY + 26
        val rangeText = "\u00A78Allowed: $radiusMin - $radiusMax blocks"
        val rangeW = textRenderer.getWidth(rangeText)
        context.drawTextWithShadow(textRenderer, rangeText, controlCenterX - rangeW / 2, rangeY, 0x666666)

        // Visual radius bar
        val barY = rangeY + 18
        val barX = panelX + PADDING + 20
        val barW = panelW - PADDING * 2 - 40
        val barH = 6

        // Track background
        context.fill(barX, barY, barX + barW, barY + barH, 0xFF1A1A2E.toInt())
        drawBorder(context, barX, barY, barW, barH, 0xFF333355.toInt())

        // Filled portion
        val range = radiusMax - radiusMin
        val progress = if (range > 0) (currentRadius - radiusMin).toFloat() / range else 0f
        val filledW = (barW * progress).toInt()
        if (filledW > 0) {
            context.fill(barX, barY, barX + filledW, barY + barH, 0xFF4CAF50.toInt())
        }

        // Thumb indicator
        val thumbX = barX + filledW - 3
        val thumbY2 = barY - 2
        val thumbW = 6
        val thumbH2 = barH + 4
        context.fill(thumbX, thumbY2, thumbX + thumbW, thumbY2 + thumbH2, 0xFFFFFFFF.toInt())

        // Info text at bottom
        val infoY = barY + 20
        context.matrices.push()
        context.matrices.translate((panelX + PADDING).toFloat(), infoY.toFloat(), 0f)
        context.matrices.scale(scale, scale, 1f)
        context.drawTextWithShadow(textRenderer, "\u00A77A larger radius means Pokemon search further but may take longer.", 0, 0, 0x888888)
        context.matrices.pop()

        context.matrices.push()
        context.matrices.translate((panelX + PADDING).toFloat(), (infoY + 12).toFloat(), 0f)
        context.matrices.scale(scale, scale, 1f)
        context.drawTextWithShadow(textRenderer, "\u00A77Changes are saved automatically per Pasture Block.", 0, 0, 0x888888)
        context.matrices.pop()

        // Footer line
        context.fill(panelX, panelY + panelH - 18, panelX + panelW, panelY + panelH - 17, CobblebaseScreen.PANEL_BORDER)
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val controlCenterX = panelX + panelW / 2
        val sectionY = panelY + 30 + 28
        val controlY = sectionY + 30

        // Minus button
        val minusBtnX = controlCenterX - 60
        if (mouseX >= minusBtnX && mouseX <= minusBtnX + 24 &&
            mouseY >= controlY && mouseY <= controlY + 20) {
            if (currentRadius > radiusMin) {
                currentRadius--
                sendUpdate()
            }
            return true
        }

        // Plus button
        val plusBtnX = controlCenterX + 36
        if (mouseX >= plusBtnX && mouseX <= plusBtnX + 24 &&
            mouseY >= controlY && mouseY <= controlY + 20) {
            if (currentRadius < radiusMax) {
                currentRadius++
                sendUpdate()
            }
            return true
        }

        return false
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (verticalAmount > 0 && currentRadius < radiusMax) {
            currentRadius++
            sendUpdate()
            return true
        } else if (verticalAmount < 0 && currentRadius > radiusMin) {
            currentRadius--
            sendUpdate()
            return true
        }
        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean = false
    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean = false

    private fun sendUpdate() {
        ClientPlayNetworking.send(PastureSettingsUpdateC2SPacket(currentRadius))
    }

    private fun drawBorder(context: DrawContext, x: Int, y: Int, w: Int, h: Int, color: Int) {
        context.drawHorizontalLine(x, x + w - 1, y, color)
        context.drawHorizontalLine(x, x + w - 1, y + h - 1, color)
        context.drawVerticalLine(x, y, y + h - 1, color)
        context.drawVerticalLine(x + w - 1, y, y + h - 1, color)
    }
}

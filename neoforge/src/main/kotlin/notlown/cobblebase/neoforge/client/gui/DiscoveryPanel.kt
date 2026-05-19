package notlown.cobblebase.neoforge.client.gui

import notlown.cobblebase.core.DiscoveryRegistry
import notlown.cobblebase.core.DiscoveryRegistry.DiscoveryType
import notlown.cobblebase.core.net.DiscoveryRequestC2SPacket
import net.neoforged.neoforge.network.PacketDistributor
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.function.Function

/**
 * Discovery tab content — shows all permanent discoveries (structures + biomes).
 * Scrollable table with filter buttons by type.
 */
class DiscoveryPanel(
    private val parent: CobblebaseScreen,
    private val panelX: Int,
    private val panelY: Int,
    private val panelW: Int,
    private val panelH: Int,
    private val textRenderer: TextRenderer
) {

    private val ROW_HEIGHT = 14
    private val HEADER_HEIGHT = 18
    private val FILTER_HEIGHT = 18
    private val PADDING = 8
    private val ROW_EVEN = 0x22FFFFFF.toInt()
    private val ROW_ODD = 0x11FFFFFF.toInt()

    private var scrollY = 0
    private var filterType: DiscoveryType? = null // null = show all
    private var isDraggingScrollbar = false

    // Scrollbar track dimensions (updated each render)
    private var trackX = 0
    private var trackTop = 0
    private var trackHeight = 0
    private var totalContentHeight = 0
    private var visibleHeight = 0
    private var thumbHeight = 0
    private var thumbY = 0

    private val dateFormat = SimpleDateFormat("MM/dd HH:mm")

    // Coordinate click tracking — stores (x, z, screenX, screenY, width) per visible row
    private data class CoordHitBox(val worldX: Int, val worldZ: Int, val sx: Int, val sy: Int, val w: Int)
    private var coordHitBoxes = mutableListOf<CoordHitBox>()
    private var copiedToast = 0L // timestamp when last copied, for brief toast

    fun init(addWidget: Function<ButtonWidget, ButtonWidget>) {
        scrollY = 0

        // Request discoveries from server
        try {
            PacketDistributor.sendToServer(DiscoveryRequestC2SPacket())
        } catch (_: Exception) {
            // Not connected or packet not registered yet
        }

        // Filter buttons
        val filterY = panelY + HEADER_HEIGHT + 2
        val btnW = 70
        val btnH = 14
        val startX = panelX + PADDING

        addWidget.apply(ButtonWidget.builder(Text.literal("\u00A7fAll")) {
            filterType = null; scrollY = 0
        }.dimensions(startX, filterY, btnW, btnH).build())

        addWidget.apply(ButtonWidget.builder(Text.literal("\u00A79Structures")) {
            filterType = DiscoveryType.STRUCTURE; scrollY = 0
        }.dimensions(startX + btnW + 2, filterY, btnW + 10, btnH).build())

        addWidget.apply(ButtonWidget.builder(Text.literal("\u00A7aBiomes")) {
            filterType = DiscoveryType.BIOME; scrollY = 0
        }.dimensions(startX + (btnW + 2) + (btnW + 10) + 2, filterY, btnW, btnH).build())

        addWidget.apply(ButtonWidget.builder(Text.literal("\u00A7ePokemon")) {
            filterType = DiscoveryType.WILD_POKEMON; scrollY = 0
        }.dimensions(startX + (btnW + 2) + (btnW + 10) + 2 + btnW + 2, filterY, btnW + 5, btnH).build())

        // Done button removed — Close (X) lives in the parent CobblebaseScreen.
    }

    private fun getFilteredDiscoveries(): List<DiscoveryRegistry.Discovery> {
        val all = DiscoveryRegistry.getClientDiscoveries()
        val ft = filterType
        return if (ft != null) {
            all.filter { it.type == ft }
        } else {
            all
        }
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val contentTop = panelY + HEADER_HEIGHT + FILTER_HEIGHT + 6
        val contentBottom = panelY + panelH - 2



        val entries = getFilteredDiscoveries()

        if (entries.isEmpty()) {
            val msg = "\u00A77No discoveries yet \u2014 assign a Scout to explore!"
            context.drawCenteredTextWithShadow(textRenderer, msg, panelX + panelW / 2, panelY + panelH / 2 - 4, 0x888888)
            // Still render footer
            context.fill(panelX, panelY + panelH - 2, panelX + panelW, panelY + panelH - 1, CobblebaseScreen.PANEL_BORDER)
            return
        }

        // Column headers (no Type column — tab already shows the type)
        val colName = panelX + PADDING + 4
        val colCoords = panelX + PADDING + 100
        val colBy = panelX + PADDING + 210
        val colWhen = panelX + panelW - PADDING - 70
        context.drawTextWithShadow(textRenderer, "\u00A7eName", colName, contentTop - 10, 0xFFFF55)
        context.drawTextWithShadow(textRenderer, "\u00A7eCoordinates", colCoords, contentTop - 10, 0xFFFF55)
        context.drawTextWithShadow(textRenderer, "\u00A7eBy", colBy, contentTop - 10, 0xFFFF55)
        context.drawTextWithShadow(textRenderer, "\u00A7eWhen", colWhen, contentTop - 10, 0xFFFF55)

        // Scrollable content
        context.enableScissor(panelX, contentTop, panelX + panelW, contentBottom)

        coordHitBoxes.clear()
        for ((index, entry) in entries.withIndex()) {
            val ry = contentTop + index * ROW_HEIGHT + scrollY
            if (ry < contentTop - ROW_HEIGHT || ry > contentBottom) continue

            // Row background
            val rowColor = if (index % 2 == 0) ROW_EVEN else ROW_ODD
            context.fill(panelX + 1, ry, panelX + panelW - 1, ry + ROW_HEIGHT - 1, rowColor)

            // Rarity color bar on left
            val rarityColor = entry.rarity.color
            context.fill(panelX + 1, ry, panelX + 4, ry + ROW_HEIGHT - 1, rarityColor)

            val scale = 0.75f

            // Name (colored by rarity, scaled 0.75x)
            context.matrices.push()
            context.matrices.translate(colName.toFloat(), (ry + 2).toFloat(), 0f)
            context.matrices.scale(scale, scale, 1f)
            context.drawTextWithShadow(textRenderer, entry.name, 0, 0, rarityColor)
            context.matrices.pop()

            // Coordinates — clickable with hover effect (scaled 0.75x)
            val coordStr = "${entry.x}, ${entry.z}"
            val coordWidth = (textRenderer.getWidth(coordStr) * scale).toInt()
            val isCoordHovered = mouseX >= colCoords && mouseX <= colCoords + coordWidth && mouseY >= ry + 2 && mouseY <= ry + 2 + 9
            val coordColor = if (isCoordHovered) 0xAAFFFF else 0x55FFFF
            context.matrices.push()
            context.matrices.translate(colCoords.toFloat(), (ry + 2).toFloat(), 0f)
            context.matrices.scale(scale, scale, 1f)
            context.drawTextWithShadow(textRenderer, coordStr, 0, 0, coordColor)
            context.matrices.pop()
            // Underline when hovered
            if (isCoordHovered) {
                context.fill(colCoords, ry + 9, colCoords + coordWidth, ry + 10, 0xAAFFFF.toInt() or (0xFF shl 24))
            }
            coordHitBoxes.add(CoordHitBox(entry.x, entry.z, colCoords, ry + 2, coordWidth))

            // Discovered By (scaled 0.75x)
            context.matrices.push()
            context.matrices.translate(colBy.toFloat(), (ry + 2).toFloat(), 0f)
            context.matrices.scale(scale, scale, 1f)
            context.drawTextWithShadow(textRenderer, entry.discoveredBy, 0, 0, 0xFFFFFF)
            context.matrices.pop()

            // When (scaled 0.75x)
            val whenStr = dateFormat.format(Date(entry.timestamp))
            context.matrices.push()
            context.matrices.translate(colWhen.toFloat(), (ry + 2).toFloat(), 0f)
            context.matrices.scale(scale, scale, 1f)
            context.drawTextWithShadow(textRenderer, whenStr, 0, 0, 0x999999)
            context.matrices.pop()
        }

        context.disableScissor()

        // "Copied!" toast
        if (System.currentTimeMillis() - copiedToast < 1500) {
            context.drawCenteredTextWithShadow(textRenderer, "\u00A7aCopied to clipboard!", panelX + panelW / 2, panelY + panelH - 40, 0x55FF55)
        }

        // Scrollbar
        totalContentHeight = entries.size * ROW_HEIGHT
        visibleHeight = contentBottom - contentTop
        if (totalContentHeight > visibleHeight) {
            trackX = panelX + panelW - 8
            trackTop = contentTop
            trackHeight = visibleHeight
            // Track background
            context.fill(trackX, trackTop, trackX + 6, trackTop + trackHeight, 0x44FFFFFF.toInt())
            // Thumb
            thumbHeight = (visibleHeight.toFloat() / totalContentHeight * trackHeight).toInt().coerceAtLeast(16)
            val scrollRange = totalContentHeight - visibleHeight
            val scrollProgress = (-scrollY).toFloat() / scrollRange.coerceAtLeast(1)
            thumbY = trackTop + ((trackHeight - thumbHeight) * scrollProgress).toInt()
            val isHovered = mouseX in trackX..(trackX + 6) && mouseY in thumbY..(thumbY + thumbHeight)
            val thumbColor = if (isDraggingScrollbar || isHovered) 0xFFDDDDDD.toInt() else 0xFFAAAAAA.toInt()
            context.fill(trackX, thumbY, trackX + 6, thumbY + thumbHeight, thumbColor)
        }

        // Footer line
        context.fill(panelX, panelY + panelH - 2, panelX + panelW, panelY + panelH - 1, CobblebaseScreen.PANEL_BORDER)

        // Discovery count
        val filterLabel = if (filterType != null) " (${filterType!!.displayName}s)" else ""
        context.drawTextWithShadow(textRenderer, "\u00A78${entries.size} discoveries$filterLabel", panelX + PADDING, panelY + panelH - 8, 0x666666)
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        scrollY = (scrollY + verticalAmount.toInt() * 16).coerceAtMost(0)
        clampScroll()
        return true
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Check coordinate clicks
        val mx = mouseX.toInt()
        val my = mouseY.toInt()
        for (hit in coordHitBoxes) {
            if (mx >= hit.sx && mx <= hit.sx + hit.w && my >= hit.sy && my <= hit.sy + 9) {
                val cmd = "/tp @s ${hit.worldX} ~ ${hit.worldZ}"
                MinecraftClient.getInstance().keyboard.clipboard = cmd
                copiedToast = System.currentTimeMillis()
                return true
            }
        }

        if (totalContentHeight <= visibleHeight) return false
        if (mouseX >= trackX && mouseX <= trackX + 6 && mouseY >= trackTop && mouseY <= trackTop + trackHeight) {
            isDraggingScrollbar = true
            updateScrollFromMouse(mouseY)
            return true
        }
        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (isDraggingScrollbar) {
            updateScrollFromMouse(mouseY)
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

    private fun updateScrollFromMouse(mouseY: Double) {
        val scrollRange = totalContentHeight - visibleHeight
        if (scrollRange <= 0) return
        val relativeY = ((mouseY - trackTop - thumbHeight / 2.0) / (trackHeight - thumbHeight)).coerceIn(0.0, 1.0)
        scrollY = -(relativeY * scrollRange).toInt()
        clampScroll()
    }

    private fun clampScroll() {
        val maxScroll = (totalContentHeight - visibleHeight).coerceAtLeast(0)
        scrollY = scrollY.coerceIn(-maxScroll, 0)
    }
}

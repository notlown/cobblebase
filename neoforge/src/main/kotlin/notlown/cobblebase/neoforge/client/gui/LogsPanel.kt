package notlown.cobblebase.neoforge.client.gui

import notlown.cobblebase.core.LogManager
import notlown.cobblebase.core.LogManager.Rarity
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import java.text.SimpleDateFormat
import java.util.Date
import java.util.function.Function

/**
 * Logs tab content - shows recent activity events for this Pasture.
 */
class LogsPanel(
    private val parent: CobblebaseScreen,
    private val pastureOrigin: BlockPos?,
    private val panelX: Int,
    private val panelY: Int,
    private val panelW: Int,
    private val panelH: Int,
    private val textRenderer: TextRenderer
) {

    // 16 px row matches the 16 px sprite; was 14 → sprite overflowed 2 px below row.
    private val ROW_HEIGHT = 16
    private val HEADER_HEIGHT = 18
    private val FILTER_HEIGHT = 18
    private val PADDING = 8
    // Single uniform row background — zebra removed (two consecutive same-action rows
    // looked like two different categories due to zebra + tint blending).
    private val ROW_BG = 0x18FFFFFF.toInt()

    /** Subtle per-action background tint, see Fabric LogsPanel for the rationale. */
    private fun actionTintColor(action: String): Int {
        return when (action.lowercase()) {
            "found" -> 0x22FFD700.toInt()
            "mined" -> 0x22A0A0A0.toInt()
            "fished" -> 0x223399FF.toInt()
            "harvested" -> 0x2255CC55.toInt()
            "supplied" -> 0x2244BB99.toInt()
            "producer", "produced" -> 0x22F0E6C8.toInt()
            "craftsman", "crafted" -> 0x22B07050.toInt()
            "healed" -> 0x22FF6666.toInt()
            "recruited" -> 0x22CC66FF.toInt()
            "sorted" -> 0x22FFA040.toInt()
            "hatched" -> 0x22FF99CC.toInt()
            "spotted", "discovered" -> 0x22DDDD66.toInt()
            "repelled", "extinguisher" -> 0x22FF7733.toInt()
            else -> 0
        }
    }

    private var scrollY = 0
    private var filterRarity: Rarity? = null
    private var isDraggingScrollbar = false

    private var trackX = 0
    private var trackTop = 0
    private var trackHeight = 0
    private var totalContentHeight = 0
    private var visibleHeight = 0
    private var thumbHeight = 0
    private var thumbY = 0

    private val timeFormat = SimpleDateFormat("HH:mm:ss")

    fun init(addWidget: Function<ButtonWidget, ButtonWidget>) {
        scrollY = 0

        val filterY = panelY + HEADER_HEIGHT + 2
        val btnW = 70
        val btnH = 14
        val startX = panelX + PADDING

        addWidget.apply(ButtonWidget.builder(Text.literal("\u00A7fAll")) {
            filterRarity = null; scrollY = 0
        }.dimensions(startX, filterY, btnW, btnH).build())

        addWidget.apply(ButtonWidget.builder(Text.literal("\u00A7aUncommon+")) {
            filterRarity = Rarity.UNCOMMON; scrollY = 0
        }.dimensions(startX + btnW + 2, filterY, btnW, btnH).build())

        addWidget.apply(ButtonWidget.builder(Text.literal("\u00A79Rare+")) {
            filterRarity = Rarity.RARE; scrollY = 0
        }.dimensions(startX + (btnW + 2) * 2, filterY, btnW, btnH).build())

        addWidget.apply(ButtonWidget.builder(Text.literal("\u00A76Ultra Rare")) {
            filterRarity = Rarity.ULTRA_RARE; scrollY = 0
        }.dimensions(startX + (btnW + 2) * 3, filterY, btnW, btnH).build())

        addWidget.apply(ButtonWidget.builder(Text.literal("Done")) { parent.close() }
            .dimensions(panelX + panelW - 54, panelY + panelH - 16, 40, 12).build())
    }

    private fun getFilteredEntries(): List<LogManager.LogEntry> {
        val minRarity = filterRarity
        return if (minRarity != null) LogManager.getClientLogs(minRarity) else LogManager.getClientLogs()
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val contentTop = panelY + HEADER_HEIGHT + FILTER_HEIGHT + 6
        val contentBottom = panelY + panelH - 18



        val entries = getFilteredEntries()

        if (entries.isEmpty()) {
            val msg = if (pastureOrigin == null) "\u00A77No pasture data available"
                else "\u00A77No activity logged yet"
            context.drawCenteredTextWithShadow(textRenderer, msg, panelX + panelW / 2, panelY + panelH / 2 - 4, 0x888888)
            context.fill(panelX, panelY + panelH - 18, panelX + panelW, panelY + panelH - 17, CobblebaseScreen.PANEL_BORDER)
            return
        }

        val ICON_SIZE = 16
        val colTime = panelX + PADDING
        val colPokemon = panelX + PADDING + 60
        val colAction = panelX + PADDING + 82
        val colItem = panelX + PADDING + 148
        val colRarity = panelX + panelW - PADDING - 55
        context.drawTextWithShadow(textRenderer, "\u00A7eTime", colTime, contentTop - 10, 0xFFFF55)
        context.drawTextWithShadow(textRenderer, "\u00A7eMon", colPokemon, contentTop - 10, 0xFFFF55)
        context.drawTextWithShadow(textRenderer, "\u00A7eAction", colAction, contentTop - 10, 0xFFFF55)
        context.drawTextWithShadow(textRenderer, "\u00A7eItem", colItem, contentTop - 10, 0xFFFF55)
        context.drawTextWithShadow(textRenderer, "\u00A7eRarity", colRarity, contentTop - 10, 0xFFFF55)

        context.enableScissor(panelX, contentTop, panelX + panelW, contentBottom)

        for ((index, entry) in entries.withIndex()) {
            val ry = contentTop + index * ROW_HEIGHT + scrollY
            if (ry < contentTop - ROW_HEIGHT || ry > contentBottom) continue

            context.fill(panelX + 1, ry, panelX + panelW - 1, ry + ROW_HEIGHT - 1, ROW_BG)
            val tint = actionTintColor(entry.action)
            if (tint != 0) {
                context.fill(panelX + 1, ry, panelX + panelW - 1, ry + ROW_HEIGHT - 1, tint)
            }

            val rarityColor = entry.rarity.color
            context.fill(panelX + 1, ry, panelX + 3, ry + ROW_HEIGHT - 1, rarityColor)

            val scale = 0.75f

            val timeStr = timeFormat.format(Date(entry.timestamp))
            context.matrices.push()
            context.matrices.translate(colTime.toFloat(), (ry + 2).toFloat(), 0f)
            context.matrices.scale(scale, scale, 1f)
            context.drawTextWithShadow(textRenderer, timeStr, 0, 0, 0x999999)
            context.matrices.pop()

            // Sprite is 16 px, row is 16 px → ry + 0 to center properly. Was ry + 2.
            PokemonSpriteHelper.renderSmallIconByName(
                context, textRenderer, entry.pokemonName,
                colPokemon, ry, delta
            )

            context.matrices.push()
            context.matrices.translate(colAction.toFloat(), (ry + 2).toFloat(), 0f)
            context.matrices.scale(scale, scale, 1f)
            context.drawTextWithShadow(textRenderer, entry.action, 0, 0, 0xCCCCCC)
            context.matrices.pop()

            context.matrices.push()
            context.matrices.translate(colItem.toFloat(), (ry + 2).toFloat(), 0f)
            context.matrices.scale(scale, scale, 1f)
            context.drawTextWithShadow(textRenderer, entry.itemName, 0, 0, 0xCCCCCC)
            context.matrices.pop()

            context.matrices.push()
            context.matrices.translate(colRarity.toFloat(), (ry + 2).toFloat(), 0f)
            context.matrices.scale(scale, scale, 1f)
            context.drawTextWithShadow(textRenderer, entry.rarity.displayName, 0, 0, rarityColor)
            context.matrices.pop()
        }

        context.disableScissor()

        totalContentHeight = entries.size * ROW_HEIGHT
        visibleHeight = contentBottom - contentTop
        if (totalContentHeight > visibleHeight) {
            trackX = panelX + panelW - 8
            trackTop = contentTop
            trackHeight = visibleHeight
            context.fill(trackX, trackTop, trackX + 6, trackTop + trackHeight, 0x44FFFFFF.toInt())
            thumbHeight = (visibleHeight.toFloat() / totalContentHeight * trackHeight).toInt().coerceAtLeast(16)
            val scrollRange = totalContentHeight - visibleHeight
            val scrollProgress = (-scrollY).toFloat() / scrollRange.coerceAtLeast(1)
            thumbY = trackTop + ((trackHeight - thumbHeight) * scrollProgress).toInt()
            val isHovered = mouseX in trackX..(trackX + 6) && mouseY in thumbY..(thumbY + thumbHeight)
            val thumbColor = if (isDraggingScrollbar || isHovered) 0xFFDDDDDD.toInt() else 0xFFAAAAAA.toInt()
            context.fill(trackX, thumbY, trackX + 6, thumbY + thumbHeight, thumbColor)
        }

        context.fill(panelX, panelY + panelH - 18, panelX + panelW, panelY + panelH - 17, CobblebaseScreen.PANEL_BORDER)

        val filterLabel = if (filterRarity != null) " (${filterRarity!!.displayName}+)" else ""
        context.drawTextWithShadow(textRenderer, "\u00A78${entries.size} events$filterLabel", panelX + PADDING, panelY + panelH - 14, 0x666666)
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        scrollY = (scrollY + verticalAmount.toInt() * 16).coerceAtMost(0)
        clampScroll()
        return true
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
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

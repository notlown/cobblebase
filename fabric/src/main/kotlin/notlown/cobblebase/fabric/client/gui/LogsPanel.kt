package notlown.cobblebase.fabric.client.gui

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
 * Scrollable table with filter buttons by rarity.
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

    private val ROW_HEIGHT = 14
    private val HEADER_HEIGHT = 18
    private val FILTER_HEIGHT = 18
    private val PADDING = 8
    private val ROW_EVEN = 0x22FFFFFF.toInt()
    private val ROW_ODD = 0x11FFFFFF.toInt()

    private var scrollY = 0
    private var filterRarity: Rarity? = null // null = show all

    private val timeFormat = SimpleDateFormat("HH:mm:ss")

    fun init(addWidget: Function<ButtonWidget, ButtonWidget>) {
        scrollY = 0

        // Filter buttons
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

        // Done button
        addWidget.apply(ButtonWidget.builder(Text.literal("Done")) { parent.close() }
            .dimensions(panelX + panelW - 54, panelY + panelH - 22, 46, 16).build())
    }

    private fun getFilteredEntries(): List<LogManager.LogEntry> {
        val minRarity = filterRarity
        return if (minRarity != null) {
            LogManager.getClientLogs(minRarity)
        } else {
            LogManager.getClientLogs()
        }
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val contentTop = panelY + HEADER_HEIGHT + FILTER_HEIGHT + 6
        val contentBottom = panelY + panelH - 28

        // Header
        context.drawCenteredTextWithShadow(textRenderer, "\u00A7fActivity Log", panelX + panelW / 2, panelY + 4, 0xFFFFFF)

        val entries = getFilteredEntries()

        if (entries.isEmpty()) {
            val msg = if (pastureOrigin == null) "\u00A77No pasture data available"
                else "\u00A77No activity logged yet"
            context.drawCenteredTextWithShadow(textRenderer, msg, panelX + panelW / 2, panelY + panelH / 2 - 4, 0x888888)
            // Still render footer
            context.fill(panelX, panelY + panelH - 28, panelX + panelW, panelY + panelH - 27, CobblebaseScreen.PANEL_BORDER)
            return
        }

        // Column headers
        val colTime = panelX + PADDING
        val colPokemon = panelX + PADDING + 60
        val colAction = panelX + PADDING + 155
        val colItem = panelX + PADDING + 225
        val colRarity = panelX + panelW - PADDING - 60
        context.drawTextWithShadow(textRenderer, "\u00A7eTime", colTime, contentTop - 10, 0xFFFF55)
        context.drawTextWithShadow(textRenderer, "\u00A7ePokemon", colPokemon, contentTop - 10, 0xFFFF55)
        context.drawTextWithShadow(textRenderer, "\u00A7eAction", colAction, contentTop - 10, 0xFFFF55)
        context.drawTextWithShadow(textRenderer, "\u00A7eItem", colItem, contentTop - 10, 0xFFFF55)
        context.drawTextWithShadow(textRenderer, "\u00A7eRarity", colRarity, contentTop - 10, 0xFFFF55)

        // Scrollable content
        context.enableScissor(panelX, contentTop, panelX + panelW, contentBottom)

        for ((index, entry) in entries.withIndex()) {
            val ry = contentTop + index * ROW_HEIGHT + scrollY
            if (ry < contentTop - ROW_HEIGHT || ry > contentBottom) continue

            // Row background
            val rowColor = if (index % 2 == 0) ROW_EVEN else ROW_ODD
            context.fill(panelX + 1, ry, panelX + panelW - 1, ry + ROW_HEIGHT - 1, rowColor)

            // Rarity color bar
            val rarityColor = entry.rarity.color
            context.fill(panelX + 1, ry, panelX + 3, ry + ROW_HEIGHT - 1, rarityColor)

            // Time
            val timeStr = timeFormat.format(Date(entry.timestamp))
            context.drawTextWithShadow(textRenderer, timeStr, colTime, ry + 2, 0x999999)

            // Pokemon name
            context.drawTextWithShadow(textRenderer, entry.pokemonName, colPokemon, ry + 2, 0xFFFFFF)

            // Action
            context.drawTextWithShadow(textRenderer, entry.action, colAction, ry + 2, 0xCCCCCC)

            // Item
            context.drawTextWithShadow(textRenderer, entry.itemName, colItem, ry + 2, 0xCCCCCC)

            // Rarity
            context.drawTextWithShadow(textRenderer, entry.rarity.displayName, colRarity, ry + 2, rarityColor)
        }

        context.disableScissor()

        // Footer line
        context.fill(panelX, panelY + panelH - 28, panelX + panelW, panelY + panelH - 27, CobblebaseScreen.PANEL_BORDER)

        // Entry count
        val filterLabel = if (filterRarity != null) " (${filterRarity!!.displayName}+)" else ""
        context.drawTextWithShadow(textRenderer, "\u00A78${entries.size} events$filterLabel", panelX + PADDING, panelY + panelH - 22, 0x666666)
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        scrollY = (scrollY + verticalAmount.toInt() * 16).coerceAtMost(0)
        return true
    }
}

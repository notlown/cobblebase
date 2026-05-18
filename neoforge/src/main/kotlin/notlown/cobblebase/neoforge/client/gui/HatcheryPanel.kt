package notlown.cobblebase.neoforge.client.gui

import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket.PasturePokemonDataDTO
import net.neoforged.neoforge.network.PacketDistributor
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import notlown.cobblebase.core.net.HatchLogRequestC2SPacket
import notlown.cobblebase.neoforge.client.HatchLogCache
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Hatchery tab — two sub-tabs:
 *   - Home: stats strip + per-pasture egg availability + active hatcher cards with sprite,
 *     proficiency, and live progress bar (Workshop-style visual language).
 *   - Logs: scrollable chronological list of past hatches.
 *
 * Egg species is intentionally NOT shown on active cards — the user wants the hatchling
 * to remain a surprise. Only after the egg pops does the history line reveal what came out.
 *
 * Polls every 2 seconds while open so the progress bar refreshes smoothly.
 */
class HatcheryPanel(
    private val pokemonList: List<PasturePokemonDataDTO>,
    private val pastureOrigin: BlockPos?,
    private val panelX: Int,
    private val panelY: Int,
    private val panelW: Int,
    private val panelH: Int,
    private val textRenderer: TextRenderer
) {
    private val PADDING = 6
    private val ROW_H = 14
    private val STATS_H = 38
    private val CARD_H = 46
    private val SUBTAB_H = 16
    private val TIME_FORMAT = SimpleDateFormat("MMM dd HH:mm")

    private enum class SubTab { HOME, LOGS }
    private var activeSubTab = SubTab.HOME

    private var scrollOffset = 0
    private val scrollbar = ScrollbarComponent(trackWidth = 4, minThumbHeight = 12)
    private var lastPollMs = 0L
    private val POLL_INTERVAL_MS = 2000L

    /**
     * Icon used throughout this tab — defaults to Cobbreeding's generic pokemon_egg if
     * the mod is installed, falls back to vanilla Items.EGG (chicken egg sprite) so the
     * tab still looks themed even without Cobbreeding.
     */
    private val eggIconStack: ItemStack by lazy {
        val parsed = Identifier.tryParse("cobbreeding:pokemon_egg")
        if (parsed != null) {
            val item = Registries.ITEM.get(parsed)
            if (item != Items.AIR) return@lazy ItemStack(item)
        }
        ItemStack(Items.EGG)
    }

    /** Renders the egg icon at the requested position with optional uniform scale. */
    private fun drawEggIcon(context: DrawContext, x: Int, y: Int, scale: Float = 1f) {
        if (scale == 1f) {
            context.drawItem(eggIconStack, x, y)
            return
        }
        context.matrices.push()
        context.matrices.translate(x.toFloat(), y.toFloat(), 0f)
        context.matrices.scale(scale, scale, 1f)
        context.drawItem(eggIconStack, 0, 0)
        context.matrices.pop()
    }

    // Hit boxes for sub-tab clicks, recomputed each render.
    private var homeTabBox: IntArray = intArrayOf(0, 0, 0, 0)
    private var logsTabBox: IntArray = intArrayOf(0, 0, 0, 0)

    fun init(addWidget: (ClickableWidget) -> Unit) {
        scrollOffset = 0
        lastPollMs = 0L
        sendRequest()
    }

    private fun sendRequest() {
        try {
            PacketDistributor.sendToServer(HatchLogRequestC2SPacket(pastureOrigin))
        } catch (_: Exception) { /* not connected */ }
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC1A1A2A.toInt())

        val nowMs = System.currentTimeMillis()
        if (nowMs - lastPollMs > POLL_INTERVAL_MS) {
            lastPollMs = nowMs
            sendRequest()
        }

        val packet = HatchLogCache.get()
        val tabsBottom = renderSubTabs(context, mouseX, mouseY)
        when (activeSubTab) {
            SubTab.HOME -> renderHomeTab(context, tabsBottom, packet)
            SubTab.LOGS -> renderLogsTab(context, mouseX, mouseY, tabsBottom, packet)
        }
    }

    private fun renderSubTabs(context: DrawContext, mouseX: Int, mouseY: Int): Int {
        val tabsY = panelY + PADDING
        val tabH = SUBTAB_H
        val gap = 4
        val homeW = 60
        val logsW = 60
        val homeX = panelX + PADDING
        val logsX = homeX + homeW + gap

        homeTabBox = intArrayOf(homeX, tabsY, homeW, tabH)
        logsTabBox = intArrayOf(logsX, tabsY, logsW, tabH)

        renderSubTabButton(context, "Home", homeTabBox, activeSubTab == SubTab.HOME, mouseX, mouseY, 0xFFFFB300.toInt())
        renderSubTabButton(context, "Logs", logsTabBox, activeSubTab == SubTab.LOGS, mouseX, mouseY, 0xFF2196F3.toInt())

        return tabsY + tabH + 4
    }

    private fun renderSubTabButton(
        context: DrawContext,
        label: String,
        box: IntArray,
        active: Boolean,
        mouseX: Int, mouseY: Int,
        accent: Int
    ) {
        val (x, y, w, h) = box
        val hovered = mouseX in x..(x + w) && mouseY in y..(y + h)
        val bg = when {
            active -> 0xFF2A2A4A.toInt()
            hovered -> 0xFF252540.toInt()
            else -> 0xFF1A1A2A.toInt()
        }
        context.fill(x, y, x + w, y + h, bg)
        if (active) {
            context.fill(x, y + h - 2, x + w, y + h, accent)
        }
        val labelW = textRenderer.getWidth(label)
        val color = if (active) 0xFFFFFF else 0x999999
        context.drawTextWithShadow(textRenderer, label, x + (w - labelW) / 2, y + 4, color)
    }

    private fun renderHomeTab(
        context: DrawContext,
        startY: Int,
        packet: notlown.cobblebase.core.net.HatchLogSyncS2CPacket?
    ) {
        val statsBottom = renderStatsStrip(context, startY, packet)
        renderActiveHatchers(context, statsBottom, packet)
    }

    private fun renderStatsStrip(
        context: DrawContext,
        startY: Int,
        packet: notlown.cobblebase.core.net.HatchLogSyncS2CPacket?
    ): Int {
        val stripY = startY
        val stripBottom = stripY + STATS_H
        context.fill(panelX + PADDING, stripY, panelX + panelW - PADDING, stripBottom, 0xFF15151E.toInt())
        context.fill(panelX + PADDING, stripY, panelX + panelW - PADDING, stripY + 1, 0xFFFFB300.toInt())

        if (packet == null) {
            context.drawTextWithShadow(textRenderer, "§7Loading hatchery log…", panelX + PADDING + 6, stripY + 14, 0xAAAAAA)
            return stripBottom + 4
        }

        // Title — scaled 0.85× to match other panels' header sizing (no oversized native text).
        // Single egg icon stays at the title for theming, no second icon next to the badge.
        drawEggIcon(context, panelX + PADDING + 6, stripY + 3)
        context.matrices.push()
        context.matrices.translate((panelX + PADDING + 26).toFloat(), (stripY + 5).toFloat(), 0f)
        context.matrices.scale(0.85f, 0.85f, 1f)
        context.drawTextWithShadow(textRenderer, "§e§lHatchery", 0, 0, 0xFFFFFF)
        context.matrices.pop()

        // Egg-availability badge — right-aligned, text only (no second egg icon).
        val countText = "§f§l${packet.availableEggs}"
        val labelText = "§7Eggs available: "
        val scale = 0.85f
        val labelW = (textRenderer.getWidth(labelText) * scale).toInt()
        val countW = (textRenderer.getWidth(countText) * scale).toInt()
        val badgeRight = panelX + panelW - PADDING - 6
        val labelX = badgeRight - labelW - countW
        context.matrices.push()
        context.matrices.translate(labelX.toFloat(), (stripY + 5).toFloat(), 0f)
        context.matrices.scale(scale, scale, 1f)
        context.drawTextWithShadow(textRenderer, "$labelText$countText", 0, 0, 0xFFFFFF)
        context.matrices.pop()

        val statsLine = "§7Total hatched: §f${packet.totalEver}  §8·  " +
            "§7This session: §f${packet.totalThisSession}  §8·  " +
            "§7Unique species: §f${packet.uniqueSpecies}"
        context.drawTextWithShadow(textRenderer, statsLine, panelX + PADDING + 6, stripY + 16, 0xCCCCCC)

        if (packet.topHatchers.isNotEmpty()) {
            val tops = packet.topHatchers.joinToString("  §8·  ") {
                "§f${it.first.replaceFirstChar { c -> c.uppercase() }}§7 ×${it.second}"
            }
            context.drawTextWithShadow(textRenderer, "§7Top hatchers: $tops",
                panelX + PADDING + 6, stripY + 28, 0xCCCCCC)
        }

        return stripBottom + 4
    }

    private fun renderActiveHatchers(
        context: DrawContext,
        startY: Int,
        packet: notlown.cobblebase.core.net.HatchLogSyncS2CPacket?
    ): Int {
        if (packet == null) return startY
        val active = packet.activeHatchers

        // Section header.
        context.fill(panelX + PADDING, startY, panelX + panelW - PADDING, startY + 1, 0xFFFFB300.toInt())
        context.matrices.push()
        context.matrices.translate((panelX + PADDING + 4).toFloat(), (startY + 3).toFloat(), 0f)
        context.matrices.scale(0.65f, 0.65f, 1f)
        context.drawTextWithShadow(textRenderer, "§e§lACTIVE HATCHERS §8(${active.size})", 0, 0, 0xFFB300)
        context.matrices.pop()

        if (active.isEmpty()) {
            // No giant decorative egg here — the section header already implies the theme,
            // and the badge in the stats strip carries the dedicated icon.
            val hint = "§8Assign the Egg Hatcher skill to a Pokemon and put eggs in the pasture or a nearby chest."
            val hintW = textRenderer.getWidth(hint)
            context.drawTextWithShadow(textRenderer, hint,
                panelX + panelW / 2 - hintW / 2, startY + 20, 0x888888)
            return startY + 36
        }

        var y = startY + 14
        for (a in active) {
            renderHatcherCard(context, y, a)
            y += CARD_H
        }
        return y + 2
    }

    private fun renderHatcherCard(
        context: DrawContext,
        y: Int,
        a: notlown.cobblebase.core.net.HatchLogSyncS2CPacket.ActiveHatcher
    ) {
        val cardLeft = panelX + PADDING
        val cardRight = panelX + panelW - PADDING
        val cardBottom = y + CARD_H - 4

        context.fill(cardLeft, y, cardRight, cardBottom, 0x33FFB300)
        context.fill(cardLeft, y, cardRight, y + 1, 0xFFFFB300.toInt())

        // Hatcher sprite, LEFT side at 2x.
        renderScaledSprite(context, a.hatcherSpecies.lowercase(), cardLeft + 4, y + 2, 2.0f)

        val nameX = cardLeft + 40
        context.matrices.push()
        context.matrices.translate(nameX.toFloat(), (y + 4).toFloat(), 0f)
        context.matrices.scale(0.75f, 0.75f, 1f)
        context.drawTextWithShadow(textRenderer, "§f§l${a.hatcherDisplayName}", 0, 0, 0xFFFFFF)
        context.matrices.pop()

        val stars = (1..5).joinToString("") { if (it <= a.proficiency) "★" else "☆" }
        context.matrices.push()
        context.matrices.translate(nameX.toFloat(), (y + 13).toFloat(), 0f)
        context.matrices.scale(0.6f, 0.6f, 1f)
        context.drawTextWithShadow(textRenderer, "§6Hatcher  §e$stars", 0, 0, 0xFFD700)
        context.matrices.pop()

        // Generic status — egg species hidden so the hatchling stays a surprise.
        context.matrices.push()
        context.matrices.translate(nameX.toFloat(), (y + 22).toFloat(), 0f)
        context.matrices.scale(0.7f, 0.7f, 1f)
        context.drawTextWithShadow(textRenderer, "§7§oIncubating…", 0, 0, 0xCCCCCC)
        context.matrices.pop()

        // Progress bar.
        val barX = nameX
        val barW = cardRight - nameX - 6
        val barY = y + 32
        val barH = 6
        context.fill(barX, barY, barX + barW, barY + barH, 0xFF222222.toInt())
        val progress = if (a.initialTimer > 0) {
            ((a.initialTimer - a.currentTimer).toFloat() / a.initialTimer).coerceIn(0f, 1f)
        } else 0f
        val fillW = (barW * progress).toInt()
        if (fillW > 0) context.fill(barX, barY, barX + fillW, barY + barH, 0xFFFF9800.toInt())

        val pctText = "${(progress * 100).toInt()}%"
        val pctW = (textRenderer.getWidth(pctText) * 0.55f).toInt()
        context.matrices.push()
        context.matrices.translate((barX + barW / 2 - pctW / 2).toFloat(), (barY - 1).toFloat(), 0f)
        context.matrices.scale(0.55f, 0.55f, 1f)
        context.drawTextWithShadow(textRenderer, pctText, 0, 0, 0xFFFFFF)
        context.matrices.pop()
    }

    /**
     * Compact 14px-tall log row matching the regular Logs tab. Two small sprites + names
     * + timestamp + prof stars all on a single line.
     *   [hatched icon] HatchedName  ←  [hatcher icon] HatcherName  ★★★★  MMM dd HH:mm
     */
    private fun renderLogRow(
        context: DrawContext,
        rowY: Int,
        idx: Int,
        e: notlown.cobblebase.core.net.HatchLogSyncS2CPacket.Entry
    ) {
        val cardLeft = panelX + PADDING + 2
        val cardRight = panelX + panelW - PADDING - 2

        val bg = if (idx % 2 == 0) 0xFF1F1F2F.toInt() else 0xFF1A1A2A.toInt()
        context.fill(cardLeft, rowY, cardRight, rowY + ROW_H - 1, bg)
        // Thin blue accent stripe on the left edge
        context.fill(cardLeft, rowY, cardLeft + 2, rowY + ROW_H - 1, 0xFF2196F3.toInt())

        val textScale = 0.7f

        // Hatched sprite (16x16 — overflows row slightly, same as LogsPanel).
        PokemonSpriteHelper.renderSmallIconByName(context, textRenderer, e.hatchedSpecies, cardLeft + 4, rowY - 1, 0f)
        // Hatched name
        val hatchedName = e.hatchedSpecies.replaceFirstChar { c -> c.uppercase() }
        context.matrices.push()
        context.matrices.translate((cardLeft + 22).toFloat(), (rowY + 3).toFloat(), 0f)
        context.matrices.scale(textScale, textScale, 1f)
        context.drawTextWithShadow(textRenderer, "§f$hatchedName", 0, 0, 0xFFFFFF)
        context.matrices.pop()

        // Divider arrow + hatcher sprite mid-row
        val midX = cardLeft + 100
        context.matrices.push()
        context.matrices.translate(midX.toFloat(), (rowY + 3).toFloat(), 0f)
        context.matrices.scale(textScale, textScale, 1f)
        context.drawTextWithShadow(textRenderer, "§8←", 0, 0, 0x666666)
        context.matrices.pop()

        val hatcherX = midX + 8
        PokemonSpriteHelper.renderSmallIconByName(context, textRenderer, e.hatcherSpecies, hatcherX, rowY - 1, 0f)
        context.matrices.push()
        context.matrices.translate((hatcherX + 18).toFloat(), (rowY + 3).toFloat(), 0f)
        context.matrices.scale(textScale, textScale, 1f)
        context.drawTextWithShadow(textRenderer, "§e${e.hatcherDisplayName}", 0, 0, 0xFFFF55)
        context.matrices.pop()

        // Prof stars and timestamp on the right
        val ts = TIME_FORMAT.format(Date(e.realTimestamp))
        val stars = (1..5).joinToString("") { if (it <= e.proficiency) "★" else "☆" }
        val tsScaled = (textRenderer.getWidth(ts) * textScale).toInt()
        context.matrices.push()
        context.matrices.translate((cardRight - tsScaled - 4).toFloat(), (rowY + 3).toFloat(), 0f)
        context.matrices.scale(textScale, textScale, 1f)
        context.drawTextWithShadow(textRenderer, "§8$ts", 0, 0, 0x888888)
        context.matrices.pop()

        val starsScaled = (textRenderer.getWidth(stars) * textScale).toInt()
        context.matrices.push()
        context.matrices.translate((cardRight - tsScaled - starsScaled - 10).toFloat(), (rowY + 3).toFloat(), 0f)
        context.matrices.scale(textScale, textScale, 1f)
        context.drawTextWithShadow(textRenderer, "§6$stars", 0, 0, 0xFFD700)
        context.matrices.pop()
    }

    private fun renderScaledSprite(context: DrawContext, speciesPath: String, x: Int, y: Int, scale: Float) {
        context.matrices.push()
        context.matrices.translate(x.toFloat(), y.toFloat(), 0f)
        context.matrices.scale(scale, scale, 1f)
        PokemonSpriteHelper.renderPortraitOnly(context, textRenderer, speciesPath, 0, 0, 0f)
        context.matrices.pop()
    }

    private fun renderLogsTab(
        context: DrawContext,
        mouseX: Int, mouseY: Int,
        startY: Int,
        packet: notlown.cobblebase.core.net.HatchLogSyncS2CPacket?
    ) {
        context.fill(panelX + PADDING, startY, panelX + panelW - PADDING, startY + 1, 0xFF2196F3.toInt())
        context.matrices.push()
        context.matrices.translate((panelX + PADDING + 4).toFloat(), (startY + 3).toFloat(), 0f)
        context.matrices.scale(0.65f, 0.65f, 1f)
        val total = packet?.entries?.size ?: 0
        context.drawTextWithShadow(textRenderer, "§b§lHATCH LOG §8($total entries)", 0, 0, 0x2196F3)
        context.matrices.pop()

        val listTop = startY + 14
        val listBottom = panelY + panelH - PADDING
        val listH = (listBottom - listTop).coerceAtLeast(ROW_H)
        val maxVisible = (listH / ROW_H).coerceAtLeast(1)

        val entries = packet?.entries ?: emptyList()
        if (entries.isEmpty()) {
            val msg = "§8No Pokemon have hatched yet."
            val msgW = textRenderer.getWidth(msg)
            context.drawTextWithShadow(textRenderer, msg,
                panelX + panelW / 2 - msgW / 2, listTop + 10, 0x888888)
            return
        }
        val maxScroll = (entries.size - maxVisible).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        context.enableScissor(panelX + PADDING, listTop, panelX + panelW - PADDING, listBottom)
        for (i in 0 until maxVisible) {
            val idx = scrollOffset + i
            if (idx >= entries.size) break
            val e = entries[idx]
            val rowY = listTop + i * ROW_H
            renderLogRow(context, rowY, idx, e)
        }
        context.disableScissor()

        // Reusable scrollbar component — render-only path here, mouseClicked/mouseDragged
        // below delegate the drag interaction. scrollOffset is in row-units; convert to
        // pixels for the component, back to row-units after the interaction.
        scrollbar.layout(
            trackX = panelX + panelW - PADDING - 4,
            trackY = listTop,
            trackHeight = maxVisible * ROW_H,
            contentHeight = entries.size * ROW_H,
            viewportHeight = maxVisible * ROW_H,
            currentScroll = scrollOffset * ROW_H,
        )
        scrollbar.render(context, 0, 0)
        scrollOffset = scrollbar.scroll / ROW_H
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Sub-tab click handling.
        if (inBox(mouseX, mouseY, homeTabBox)) { activeSubTab = SubTab.HOME; scrollOffset = 0; return true }
        if (inBox(mouseX, mouseY, logsTabBox)) { activeSubTab = SubTab.LOGS; scrollOffset = 0; return true }
        // Scrollbar click — re-layout's last frame is still authoritative.
        if (scrollbar.mouseClicked(mouseX, mouseY)) {
            scrollOffset = scrollbar.scroll / ROW_H
            return true
        }
        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dx: Double, dy: Double): Boolean {
        if (scrollbar.mouseDragged(mouseY)) {
            scrollOffset = scrollbar.scroll / ROW_H
            return true
        }
        return false
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean = scrollbar.mouseReleased()

    private fun inBox(mx: Double, my: Double, box: IntArray): Boolean {
        val (x, y, w, h) = box
        return mx >= x && mx <= x + w && my >= y && my <= y + h
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, h: Double, v: Double): Boolean {
        if (activeSubTab == SubTab.LOGS) {
            scrollOffset = (scrollOffset - v.toInt()).coerceAtLeast(0)
            return true
        }
        return false
    }
}

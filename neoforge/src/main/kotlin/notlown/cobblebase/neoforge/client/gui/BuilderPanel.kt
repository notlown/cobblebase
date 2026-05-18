package notlown.cobblebase.neoforge.client.gui

import net.neoforged.neoforge.network.PacketDistributor
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket.PasturePokemonDataDTO
import notlown.cobblebase.core.AssignmentCache
import notlown.cobblebase.core.BuilderCache
import notlown.cobblebase.core.net.BuildJobStatusRequestC2SPacket
import notlown.cobblebase.core.net.SkillAssignmentC2SPacket
import notlown.cobblebase.core.net.StructureTemplateListRequestC2SPacket
import notlown.cobblebase.core.net.StructureTemplateListSyncS2CPacket
import notlown.cobblebase.neoforge.client.BuildJobStatusCache
import notlown.cobblebase.neoforge.client.render.BuildPreviewState

/**
 * Builder tab — lists every structure template the server knows about and lets the
 * player start a preview placement for a chosen template at the pasture.
 *
 * Workflow:
 *  1. Tab opens, requests the template list from the server (cached on subsequent opens).
 *  2. Player optionally filters via the search field.
 *  3. Player clicks a template row → "Place" button activates.
 *  4. "Place" closes the GUI and starts BuildPreviewState at the pasture position.
 *  5. Player nudges/rotates with WASD/Q/E/R/M and confirms with Enter (handled by
 *     [BuildPreviewKeyHandler]). The confirm sends a BuildJobConfigureC2SPacket.
 */
class BuilderPanel(
    private val parent: CobblebaseScreen,
    private val pokemonList: List<PasturePokemonDataDTO>,
    private val pastureOrigin: BlockPos?,
    private val panelX: Int,
    private val panelY: Int,
    private val panelW: Int,
    private val panelH: Int,
    private val textRenderer: TextRenderer
) {
    private val PADDING = 8
    private val ROW_HEIGHT = 26
    private val SEARCH_HEIGHT = 14
    private val SELECTED_DETAIL_H = 38   // footer detail strip height when a template is selected
    private val PREVIEW_ICON = 14         // size of each inline block-icon thumbnail
    private val PREVIEW_ICON_LARGE = 18   // size of icons in the detail strip

    private var searchField: TextFieldWidget? = null
    private var searchText: String = ""
    private var scrollOffset: Int = 0
    private var selectedId: String? = null
    private var dataRequested: Boolean = false
    private var placeButton: ButtonWidget? = null
    private var clearButton: ButtonWidget? = null
    private val ACTIVE_JOB_PANEL_H = 36
    private var lastStatusPollMs: Long = 0L
    private val STATUS_POLL_INTERVAL_MS = 2000L

    fun init(addWidget: (ClickableWidget) -> Unit) {
        scrollOffset = 0

        if (!dataRequested || BuilderCache.templates.isEmpty()) {
            PacketDistributor.sendToServer(StructureTemplateListRequestC2SPacket())
            dataRequested = true
        }

        // Search box at the top of the panel
        searchField = TextFieldWidget(
            textRenderer,
            panelX + PADDING, panelY + PADDING, panelW - PADDING * 2, SEARCH_HEIGHT,
            Text.literal("")
        ).also {
            it.setMaxLength(64)
            it.setPlaceholder(Text.literal("Search templates…"))
            it.setChangedListener { text -> searchText = text; scrollOffset = 0 }
            addWidget(it)
        }

        // Footer: Place + Clear Job buttons
        val footerY = panelY + panelH - 22
        placeButton = ButtonWidget.builder(Text.literal("\u00A7aPlace Preview")) {
            startPreview()
        }.dimensions(panelX + panelW - 220, footerY, 100, 16).build().also {
            it.active = canPlace()
            addWidget(it)
        }
        clearButton = ButtonWidget.builder(Text.literal("\u00A7cClear Active Job")) {
            clearActiveJob()
        }.dimensions(panelX + panelW - 110, footerY, 100, 16).build().also {
            it.active = pastureOrigin != null
            addWidget(it)
        }
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Background
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC1A1A2A.toInt())

        // Poll the server for active-job status every 2s; the status panel uses the latest response.
        val pasture = pastureOrigin
        val nowMs = System.currentTimeMillis()
        if (pasture != null && nowMs - lastStatusPollMs > STATUS_POLL_INTERVAL_MS) {
            lastStatusPollMs = nowMs
            try {
                PacketDistributor.sendToServer(BuildJobStatusRequestC2SPacket(pasture))
            } catch (_: Exception) { /* not connected */ }
        }
        renderActiveJobPanel(context, mouseX, mouseY)
        renderHelpersRow(context, mouseX, mouseY)

        // Loading state
        if (!BuilderCache.loaded) {
            context.drawTextWithShadow(textRenderer, "\u00A77Loading templates…",
                panelX + PADDING, panelY + panelH / 2, 0xAAAAAA)
            placeButton?.active = false
            return
        }

        val all = BuilderCache.templates
        if (all.isEmpty()) {
            renderEmptyState(context)
            placeButton?.active = false
            return
        }

        val filtered = filterTemplates(all)
        renderList(context, filtered, mouseX, mouseY)

        // Footer detail strip — top blocks of the selected template (visual material preview)
        val sel = filtered.firstOrNull { it.id == selectedId }
        renderSelectedDetail(context, sel, mouseX, mouseY)

        placeButton?.active = canPlace()
    }

    /**
     * Renders the per-template detail strip above the action buttons. Shows:
     *   - Template name + dimensions + total non-air block count.
     *   - Icon row of the top-N most common blocks (with hover tooltip showing count).
     * Falls back to a hint string when nothing is selected.
     */
    private fun renderSelectedDetail(
        context: DrawContext,
        sel: StructureTemplateListSyncS2CPacket.TemplateDTO?,
        mouseX: Int, mouseY: Int
    ) {
        val stripY = panelY + panelH - 22 - SELECTED_DETAIL_H
        context.fill(panelX + PADDING, stripY,
            panelX + panelW - PADDING, stripY + SELECTED_DETAIL_H, 0xFF15151E.toInt())
        context.fill(panelX + PADDING, stripY, panelX + panelW - PADDING, stripY + 1, 0xFF8BC34A.toInt())

        if (sel == null) {
            context.drawTextWithShadow(textRenderer,
                "\u00A77Select a template to see its materials and place a preview.",
                panelX + PADDING + 6, stripY + 14, 0xAAAAAA)
            return
        }

        val title = "\u00A7e${sel.displayName}  \u00A78\u00B7  \u00A7b${sel.sizeX}\u00D7${sel.sizeY}\u00D7${sel.sizeZ}"
        context.drawTextWithShadow(textRenderer, title, panelX + PADDING + 6, stripY + 4, 0xFFFFFF)
        val totalLabel = if (sel.totalBlockCount > 0) "\u00A77${sel.totalBlockCount} blocks" else "\u00A78no block data"
        val tlw = textRenderer.getWidth(totalLabel)
        context.drawTextWithShadow(textRenderer, totalLabel,
            panelX + panelW - PADDING - tlw - 6, stripY + 4, 0xAAAAAA)

        if (sel.topBlocks.isNotEmpty()) {
            var iconX = panelX + PADDING + 6
            val iconY = stripY + 16
            for ((blockId, count) in sel.topBlocks) {
                val stack = blockIdToStack(blockId)
                if (!stack.isEmpty) {
                    context.drawItem(stack, iconX, iconY)
                    val countTxt = "\u00A7f\u00D7$count"
                    context.matrices.push()
                    context.matrices.translate((iconX).toFloat(), (iconY + PREVIEW_ICON_LARGE + 1).toFloat(), 0f)
                    context.matrices.scale(0.6f, 0.6f, 1f)
                    context.drawTextWithShadow(textRenderer, countTxt, 0, 0, 0xCCCCCC)
                    context.matrices.pop()
                    if (mouseX in iconX..(iconX + PREVIEW_ICON_LARGE) &&
                        mouseY in iconY..(iconY + PREVIEW_ICON_LARGE)) {
                        context.drawTooltip(textRenderer,
                            listOf(Text.literal("\u00A7e$blockId"),
                                Text.literal("\u00A77count: \u00A7f$count")),
                            mouseX, mouseY)
                    }
                }
                iconX += PREVIEW_ICON_LARGE + 4
                if (iconX > panelX + panelW - PADDING - PREVIEW_ICON_LARGE - 6) break
            }
        } else {
            context.drawTextWithShadow(textRenderer,
                "\u00A78(no block preview available - template may be too old to scan)",
                panelX + PADDING + 6, stripY + 18, 0x888888)
        }
    }

    /**
     * Top-of-panel strip showing active build-job status: template name, progress bar,
     * and the next missing block. Visible whenever the server reports an active job for
     * this pasture. Hidden when there's nothing to show.
     */
    private fun renderActiveJobPanel(context: DrawContext, mouseX: Int, mouseY: Int) {
        val pasture = pastureOrigin ?: return
        val status = BuildJobStatusCache.get(pasture) ?: return
        if (!status.present) return

        val stripY = panelY + PADDING + SEARCH_HEIGHT + 4
        // Reserve space — the list-area shrinks below it.
        val stripBottom = stripY + ACTIVE_JOB_PANEL_H
        context.fill(panelX + PADDING, stripY, panelX + panelW - PADDING, stripBottom, 0xFF1F2A1F.toInt())
        // Top accent
        val accent = if (status.completed) 0xFF4CAF50.toInt() else 0xFFFFB300.toInt()
        context.fill(panelX + PADDING, stripY, panelX + panelW - PADDING, stripY + 1, accent)

        // Header line: building name + completion state
        val header = if (status.completed) "§a✓ Build complete: §e${status.displayName}"
                     else "§eBuilding: §f${status.displayName}"
        context.drawTextWithShadow(textRenderer, header, panelX + PADDING + 6, stripY + 4, 0xFFFFFF)

        // Progress text and bar
        val pct = if (status.totalBlocks > 0) status.placedBlocks * 100 / status.totalBlocks else 0
        val progressTxt = "§7${status.placedBlocks} / ${status.totalBlocks} blocks  §8(${pct}%)"
        context.drawTextWithShadow(textRenderer, progressTxt, panelX + PADDING + 6, stripY + 14, 0xCCCCCC)

        val barX = panelX + PADDING + 6
        val barY = stripY + 24
        val barW = (panelW - PADDING * 2 - 12).coerceAtLeast(40)
        val barH = 5
        context.fill(barX, barY, barX + barW, barY + barH, 0xFF333333.toInt())
        val fillW = if (status.totalBlocks > 0) (barW * status.placedBlocks / status.totalBlocks).coerceAtLeast(0) else 0
        if (fillW > 0) {
            context.fill(barX, barY, barX + fillW, barY + barH, accent)
        }

        // Right-side: missing block icon + label
        if (!status.completed && status.nextMissingBlockId.isNotEmpty()) {
            val stack = blockIdToStack(status.nextMissingBlockId)
            val iconX = panelX + panelW - PADDING - 18
            val iconY = stripY + 4
            if (!stack.isEmpty) {
                context.drawItem(stack, iconX, iconY)
                if (mouseX in iconX..(iconX + 16) && mouseY in iconY..(iconY + 16)) {
                    context.drawTooltip(textRenderer,
                        listOf(
                            Text.literal("§eWaiting for material:"),
                            Text.literal("§f${status.nextMissingBlockId}"),
                            Text.literal("§7Builder Pokemon will keep checking nearby chests.")
                        ),
                        mouseX, mouseY)
                }
            }
            val needLabel = "§7Next: §f${status.nextMissingBlockId.substringAfter(':')}"
            val lw = (textRenderer.getWidth(needLabel) * 0.7f).toInt()
            context.matrices.push()
            context.matrices.translate((panelX + panelW - PADDING - 22 - lw - 4).toFloat(), (stripY + 14).toFloat(), 0f)
            context.matrices.scale(0.7f, 0.7f, 1f)
            context.drawTextWithShadow(textRenderer, needLabel, 0, 0, 0xCCCCCC)
            context.matrices.pop()
        }
    }

    /** Returns the pixel-Y offset to add to the list start when the active-job panel is visible. */
    private fun activeJobOffsetY(): Int {
        val pasture = pastureOrigin ?: return 0
        val status = BuildJobStatusCache.get(pasture) ?: return 0
        if (!status.present) return 0
        // Active job panel + helpers row
        return ACTIVE_JOB_PANEL_H + 4 + HELPERS_ROW_H + 2
    }

    private val HELPERS_ROW_H = 22
    private val HELPER_ICON_W = 22

    /**
     * Renders the inline "Helpers" row just under the active-job panel — one button per
     * tethered Pokemon at the pasture. Clicking toggles the `builder_helper` assignment.
     * Pokemon already assigned as helpers show a green border; others show grey.
     */
    private fun renderHelpersRow(context: DrawContext, mouseX: Int, mouseY: Int) {
        val pasture = pastureOrigin ?: return
        val status = BuildJobStatusCache.get(pasture) ?: return
        if (!status.present || status.completed) return  // hide when no active job

        val rowY = panelY + PADDING + SEARCH_HEIGHT + 4 + ACTIVE_JOB_PANEL_H + 4
        // Background panel
        context.fill(panelX + PADDING, rowY, panelX + panelW - PADDING, rowY + HELPERS_ROW_H, 0xFF181A22.toInt())
        // Header label
        context.matrices.push()
        context.matrices.translate((panelX + PADDING + 6).toFloat(), (rowY + 2).toFloat(), 0f)
        context.matrices.scale(0.7f, 0.7f, 1f)
        context.drawTextWithShadow(textRenderer, "§7Helpers §8(click to toggle)", 0, 0, 0xAAAAAA)
        context.matrices.pop()

        var x = panelX + PADDING + 80
        val iconY = rowY + 3
        for (mon in pokemonList) {
            if (x + HELPER_ICON_W > panelX + panelW - PADDING - 4) break
            val isHelper = AssignmentCache.getAssignment(mon.pokemonId) == BUILDER_HELPER_ASSIGNMENT
            val borderColor = if (isHelper) 0xFF4CAF50.toInt() else 0xFF555555.toInt()
            val hovered = mouseX in x..(x + HELPER_ICON_W) && mouseY in iconY..(iconY + HELPER_ICON_W - 6)
            context.fill(x - 1, iconY - 1, x + HELPER_ICON_W + 1, iconY + HELPER_ICON_W - 5, borderColor)
            context.fill(x, iconY, x + HELPER_ICON_W, iconY + HELPER_ICON_W - 6, 0xFF222227.toInt())
            // 16-px Pokemon sprite icon
            PokemonSpriteHelper.renderSmallIconByName(
                context, textRenderer, mon.species.path,
                x + 3, iconY + 0, 0f
            )
            if (hovered) {
                val role = if (isHelper) "remove helper" else "make helper"
                context.drawTooltip(textRenderer,
                    listOf(
                        net.minecraft.text.Text.literal("§e${mon.displayName.string}"),
                        net.minecraft.text.Text.literal("§7Click to $role")
                    ),
                    mouseX, mouseY)
            }
            x += HELPER_ICON_W + 2
        }
    }

    private val BUILDER_HELPER_ASSIGNMENT = "builder_helper"

    private fun handleHelperToggleClick(mouseX: Double, mouseY: Double): Boolean {
        val pasture = pastureOrigin ?: return false
        val status = BuildJobStatusCache.get(pasture) ?: return false
        if (!status.present || status.completed) return false

        val rowY = panelY + PADDING + SEARCH_HEIGHT + 4 + ACTIVE_JOB_PANEL_H + 4
        val iconY = rowY + 3
        if (mouseY < iconY || mouseY > iconY + HELPER_ICON_W - 6) return false
        var x = panelX + PADDING + 80
        for (mon in pokemonList) {
            if (x + HELPER_ICON_W > panelX + panelW - PADDING - 4) break
            if (mouseX >= x && mouseX <= x + HELPER_ICON_W) {
                val isHelper = AssignmentCache.getAssignment(mon.pokemonId) == BUILDER_HELPER_ASSIGNMENT
                val newAssignment: String? = if (isHelper) null else BUILDER_HELPER_ASSIGNMENT
                PacketDistributor.sendToServer(SkillAssignmentC2SPacket(mon.pokemonId, newAssignment ?: ""))
                AssignmentCache.setAssignment(mon.pokemonId, newAssignment)
                return true
            }
            x += HELPER_ICON_W + 2
        }
        return false
    }

    /** Resolves a block ID to its inventory ItemStack. Falls back to a barrier icon. */
    private fun blockIdToStack(blockId: String): ItemStack {
        return try {
            val id = Identifier.tryParse(blockId) ?: return ItemStack.EMPTY
            val block = Registries.BLOCK.get(id)
            val item = block.asItem()
            if (item == Items.AIR) ItemStack(Items.BARRIER) else ItemStack(item)
        } catch (_: Exception) {
            ItemStack(Items.BARRIER)
        }
    }

    private fun renderEmptyState(context: DrawContext) {
        val cx = panelX + panelW / 2
        val cy = panelY + panelH / 2
        val title = "\u00A7eNo Templates Found"
        val tw = textRenderer.getWidth(title)
        context.drawTextWithShadow(textRenderer, title, cx - tw / 2, cy - 18, 0xFFE082)

        val lines = listOf(
            "Install a Pokemon structure datapack such as",
            "\u00A7bCobbleTowns\u00A7r, \u00A7bRadical Gyms\u00A7r or \u00A7bCobbleBuilds: Leaders\u00A7r",
            "— or place .nbt files under \u00A77data/<namespace>/structures/\u00A7r."
        )
        for ((i, line) in lines.withIndex()) {
            val w = textRenderer.getWidth(line)
            context.drawTextWithShadow(textRenderer, line, cx - w / 2, cy - 4 + i * 11, 0xCCCCCC)
        }
    }

    private fun filterTemplates(
        all: List<StructureTemplateListSyncS2CPacket.TemplateDTO>
    ): List<StructureTemplateListSyncS2CPacket.TemplateDTO> {
        if (searchText.isBlank()) return all
        val needle = searchText.lowercase()
        return all.filter {
            it.displayName.lowercase().contains(needle) || it.id.lowercase().contains(needle)
        }
    }

    private fun renderList(
        context: DrawContext,
        list: List<StructureTemplateListSyncS2CPacket.TemplateDTO>,
        mouseX: Int, mouseY: Int
    ) {
        val listTop = panelY + PADDING + SEARCH_HEIGHT + 6 + activeJobOffsetY()
        val listBottom = panelY + panelH - 22 - SELECTED_DETAIL_H - 4
        val visibleRows = ((listBottom - listTop) / ROW_HEIGHT).coerceAtLeast(1)
        val maxScroll = (list.size - visibleRows).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        val rowX = panelX + PADDING
        val rowW = panelW - PADDING * 2 - 6  // -6 for scrollbar

        for (i in 0 until visibleRows) {
            val idx = scrollOffset + i
            if (idx >= list.size) break
            val t = list[idx]
            val rowY = listTop + i * ROW_HEIGHT
            val isSelected = t.id == selectedId
            val isHovered = mouseX in rowX..(rowX + rowW) && mouseY in rowY..(rowY + ROW_HEIGHT - 2)

            val bg = when {
                isSelected -> 0xFF2A6A2E.toInt()
                isHovered -> 0xFF333355.toInt()
                idx % 2 == 0 -> 0xFF1F1F2F.toInt()
                else -> 0xFF1A1A2A.toInt()
            }
            context.fill(rowX, rowY, rowX + rowW, rowY + ROW_HEIGHT - 2, bg)
            // Left accent
            context.fill(rowX, rowY, rowX + 2, rowY + ROW_HEIGHT - 2, 0xFF8BC34A.toInt())

            // Display name
            context.drawTextWithShadow(textRenderer, "\u00A7f${t.displayName}",
                rowX + 8, rowY + 3, 0xFFFFFF)

            // Subtext: ID + size
            val sub = "\u00A77${t.id}  \u00A78\u00B7  \u00A7b${t.sizeX}\u00D7${t.sizeY}\u00D7${t.sizeZ}"
            context.drawTextWithShadow(textRenderer, sub, rowX + 8, rowY + 12, 0xAAAAAA)
        }

        // Scrollbar
        if (maxScroll > 0) {
            val barX = panelX + panelW - PADDING - 4
            val barTrackY = listTop
            val barTrackH = visibleRows * ROW_HEIGHT
            val barHandleH = (barTrackH * visibleRows / list.size).coerceAtLeast(12)
            val barHandleY = barTrackY + (barTrackH - barHandleH) * scrollOffset / maxScroll
            context.fill(barX, barTrackY, barX + 3, barTrackY + barTrackH, 0xFF202030.toInt())
            context.fill(barX, barHandleY, barX + 3, barHandleY + barHandleH, 0xFF8BC34A.toInt())
        }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return false
        // Helper-row clicks take priority over template-list clicks.
        if (handleHelperToggleClick(mouseX, mouseY)) return true
        val list = filterTemplates(BuilderCache.templates)
        if (list.isEmpty()) return false

        val listTop = panelY + PADDING + SEARCH_HEIGHT + 6 + activeJobOffsetY()
        val listBottom = panelY + panelH - 22 - SELECTED_DETAIL_H - 4
        val visibleRows = ((listBottom - listTop) / ROW_HEIGHT).coerceAtLeast(1)
        val rowX = panelX + PADDING
        val rowW = panelW - PADDING * 2 - 6

        for (i in 0 until visibleRows) {
            val idx = scrollOffset + i
            if (idx >= list.size) break
            val rowY = listTop + i * ROW_HEIGHT
            if (mouseX >= rowX && mouseX <= rowX + rowW &&
                mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 2
            ) {
                selectedId = list[idx].id
                placeButton?.active = canPlace()
                return true
            }
        }
        return false
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, h: Double, v: Double): Boolean {
        scrollOffset = (scrollOffset - v.toInt()).coerceAtLeast(0)
        return true
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dx: Double, dy: Double): Boolean = false
    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean = false

    private fun canPlace(): Boolean {
        return pastureOrigin != null && selectedId != null && BuilderCache.templates.any { it.id == selectedId }
    }

    private fun startPreview() {
        val pasture = pastureOrigin ?: return
        val template = BuilderCache.templates.firstOrNull { it.id == selectedId } ?: return

        // Default origin: 1 block above + 2 blocks east of the pasture so the preview
        // doesn't immediately overlap the pasture itself.
        val origin = pasture.add(2, 1, 0)
        BuildPreviewState.start(pasture, template, origin)
        parent.close()
        MinecraftClient.getInstance().player?.sendMessage(Text.literal(
            "\u00A7a[Cobblebase] Preview active — WASD/Q/E to nudge, R to rotate, M to mirror, Enter to confirm, Esc to cancel."
        ), false)
    }

    private fun clearActiveJob() {
        val pasture = pastureOrigin ?: return
        PacketDistributor.sendToServer(notlown.cobblebase.core.net.BuildJobConfigureC2SPacket(
            pasturePos = pasture,
            templateId = "",
            originX = 0, originY = 0, originZ = 0,
            rotationName = "NONE",
            mirrorName = "NONE"
        ))
        MinecraftClient.getInstance().player?.sendMessage(Text.literal(
            "\u00A7e[Cobblebase] Active build job cleared."
        ), true)
    }
}

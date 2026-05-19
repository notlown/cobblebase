package notlown.cobblebase.fabric.client.gui

import net.minecraft.util.math.BlockPos
import notlown.cobblebase.core.AssignmentCache
import notlown.cobblebase.core.BaseManager
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.JobConfigOverrides
import notlown.cobblebase.core.SkillRegistry
import notlown.cobblebase.core.SpeciesSkillRegistry
import notlown.cobblebase.core.net.SkillAssignmentC2SPacket
import notlown.cobblebase.fabric.client.render.RadiusRenderer
import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket.PasturePokemonDataDTO
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import java.util.UUID
import java.util.function.Function

/**
 * Skills tab content - refactored from the original SkillAssignmentScreen.
 * Shows Pokemon list with skill assignment buttons and proficiency stars.
 */
class SkillsPanel(
    private val parent: CobblebaseScreen,
    private val pokemonList: List<PasturePokemonDataDTO>,
    private val pastureOrigin: BlockPos?,
    private val panelX: Int,
    private val panelY: Int,
    private val panelW: Int,
    private val panelH: Int,
    private val textRenderer: TextRenderer
) {

    // Pasture redesign (2026-05-18):
    //   * Single, compact row height (20 px) — fits 10+ Pokemon on screen.
    //   * Dynamic chip width per Pokemon — fits all skills into ONE row by
    //     shrinking chips when needed, instead of wrapping into a second row.
    //   * Active chip = hero treatment (category color + white border + stars + icon).
    //   * Available chips = demoted (dim BG, name only, thin category-color underline).
    //   * Same chip HEIGHT for both — only color and content differ. Height
    //     differences made the row silhouette "bulge" wherever the active chip sat.
    private val ROW_HEIGHT_SMALL = 18
    private val ROW_HEIGHT_LARGE = 18
    private val HEADER_HEIGHT = 14
    private val PANEL_PADDING = 8
    private val SUBTAB_H = 14

    enum class SubTab { ACTIVE_WORKERS, MY_POKEMON, POKEWIKI }
    private var activeSubTab = SubTab.ACTIVE_WORKERS
    private val subTabBoxes = mutableMapOf<SubTab, IntArray>()
    private val ICON_OFFSET = PokemonSpriteHelper.ICON_SIZE + 4 // 16px icon + 4px gap
    private val NAME_WIDTH = 56 + ICON_OFFSET            // bumped from 50 → 56 (full-scale name fits)
    private val AURA_ICON_WIDTH = 15
    private val AUTO_BTN_WIDTH = 26                      // Relax is just an Off toggle
    private val BTN_WIDTH = 58                           // baseline chip width when Pokemon has ≤ btnsPerRow skills
    private val BTN_HEIGHT = 16                          // unified chip height — active and available are the SAME size now (height-difference broke the row silhouette)
    private val BTN_MIN_WIDTH = 38                       // dynamic shrink floor for Pokemon with many skills
    private val BTN_GAP = 2

    /** Per-Pokemon chip width (varies when many skills must fit into one row). */
    private val chipWidthByPokemon = mutableMapOf<UUID, Int>()

    private val ROW_EVEN = 0x44FFFFFF.toInt()
    private val ROW_ODD = 0x22FFFFFF.toInt()

    private val allButtons = mutableListOf<SkillButtonData>()
    private var scrollX = 0
    private var scrollY = 0
    private var contentY = 0
    private var isDraggingScrollbar = false

    /**
     * Reusable scrollbar for the main right-side grid in the My Pokemon and WorkerWiki
     * sub-tabs. Only one of those is visible at a time, so a single instance with
     * geometry refreshed each frame is sufficient.
     */
    private val mainGridScrollbar = ScrollbarComponent(trackWidth = 4, minThumbHeight = 14)

    // Per-pokemon layout: cumulative Y offset and row height
    private val rowOffsets = mutableListOf<Int>()
    private val rowHeights = mutableListOf<Int>()

    // Scrollbar track dimensions (updated each render)
    private var trackX = 0
    private var trackTop = 0
    private var trackHeight = 0
    private var totalContentHeight = 0
    private var visibleHeight = 0
    private var thumbHeight = 0
    private var thumbY = 0

    fun init(addWidget: Function<ButtonWidget, ButtonWidget>) {
        allButtons.clear()
        rowOffsets.clear()
        rowHeights.clear()
        chipWidthByPokemon.clear()
        scrollX = 0
        scrollY = 0
        // contentY shifts down to leave room for the sub-tab strip at the top.
        contentY = panelY + SUBTAB_H + HEADER_HEIGHT + PANEL_PADDING

        // Bottom bar (Discord / Mute / Radius / Admin) + Close button now live in the
        // parent CobblebaseScreen so they're consistent across every tab.

        // Auto-Assign button: for every Pokemon in the pasture, pick the skill with
        // the highest proficiency (excluding passive buffs) and assign it. Lives on
        // the header row, right side, next to the "Skills" column header.
        val autoAssignW = 80
        val autoAssignH = 12
        val autoAssignX = panelX + panelW - PANEL_PADDING - autoAssignW
        val autoAssignY = panelY + SUBTAB_H + HEADER_HEIGHT + PANEL_PADDING - 14
        addWidget.apply(ButtonWidget.builder(Text.literal("§6Auto-Assign Best")) {
            autoAssignAll()
        }.dimensions(autoAssignX, autoAssignY, autoAssignW, autoAssignH).build())

        var cumulativeY = 0

        pokemonList.forEachIndexed { index, pokemonData ->
            val pokemonId = pokemonData.pokemonId
            val speciesName = SpeciesSkillRegistry.resolveFormName(pokemonData.species.path, pokemonData.aspects)
            val currentAssignment = AssignmentCache.getAssignment(pokemonId)
            val speciesSkills = SpeciesSkillRegistry.getSkills(speciesName)
            val availableSkills = speciesSkills?.skills ?: emptyList()

            // Count assignable (non-buff, enabled) skills (without Auto)
            val skillCount = availableSkills.count { entry ->
                val skillDef = SkillRegistry.get(entry.skillId)
                skillDef != null && !BaseManager.isBuffExecutor(skillDef.executor) && JobConfigOverrides.isEnabled(entry.skillId)
            }

            // Auto button has its own column, skills start after it
            val autoX = panelX + PANEL_PADDING + NAME_WIDTH + AURA_ICON_WIDTH
            val skillStartX = autoX + AUTO_BTN_WIDTH + BTN_GAP
            val skillAreaW = panelX + panelW - PANEL_PADDING - skillStartX

            // Dynamic chip width: divide available horizontal space evenly when the
            // Pokemon has more skills than the baseline width allows. Clamped at
            // BTN_MIN_WIDTH so labels like "Friend Recruiter" stay readable. With
            // fewer skills we keep BTN_WIDTH so the row doesn't get sparse pills.
            val chipW = if (skillCount <= 0) BTN_WIDTH else {
                val perChipBudget = (skillAreaW - (skillCount - 1) * BTN_GAP) / skillCount
                perChipBudget.coerceIn(BTN_MIN_WIDTH, BTN_WIDTH)
            }
            chipWidthByPokemon[pokemonId] = chipW

            rowOffsets.add(cumulativeY)
            rowHeights.add(ROW_HEIGHT_SMALL)  // single, predictable row height

            val rowY = contentY + cumulativeY

            // Auto/Relax in its own column (small Off-toggle, not a "Skill")
            allButtons.add(SkillButtonData(pokemonId, null, "Relax", 0, "", autoX, rowY, currentAssignment == null))

            // Skill buttons start after Auto column, all on a single row
            var btnX = skillStartX
            val btnY = rowY

            for (entry in availableSkills) {
                val skillDef = SkillRegistry.get(entry.skillId) ?: continue
                if (BaseManager.isBuffExecutor(skillDef.executor)) continue
                if (!JobConfigOverrides.isEnabled(entry.skillId)) continue
                allButtons.add(SkillButtonData(
                    pokemonId, entry.skillId, skillDef.name, entry.proficiency,
                    skillDef.category, btnX, btnY, currentAssignment == entry.skillId
                ))
                btnX += chipW + BTN_GAP
            }

            cumulativeY += ROW_HEIGHT_SMALL
        }
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderSubTabs(context, mouseX, mouseY)
        when (activeSubTab) {
            SubTab.ACTIVE_WORKERS -> renderActiveWorkers(context, mouseX, mouseY, delta)
            SubTab.MY_POKEMON -> renderMyPokemonView(context, mouseX, mouseY)
            SubTab.POKEWIKI -> renderPokeWikiStub(context, mouseX, mouseY)
        }
    }

    private fun renderSubTabs(context: DrawContext, mouseX: Int, mouseY: Int) {
        val y0 = panelY + 2
        val tabW = 80
        val gap = 3
        val labelScale = 0.75f
        val tabs = listOf(
            SubTab.ACTIVE_WORKERS to "Pasture",
            SubTab.MY_POKEMON to "My Pokemon",
            SubTab.POKEWIKI to "WorkerWiki"
        )
        subTabBoxes.clear()
        for ((i, pair) in tabs.withIndex()) {
            val (tab, label) = pair
            val tx = panelX + PANEL_PADDING + i * (tabW + gap)
            val active = activeSubTab == tab
            val hovered = mouseX in tx..(tx + tabW) && mouseY in y0..(y0 + SUBTAB_H)
            val accent = when (tab) {
                SubTab.ACTIVE_WORKERS -> 0xFF4CAF50.toInt()
                SubTab.MY_POKEMON -> 0xFF2196F3.toInt()
                SubTab.POKEWIKI -> 0xFFFF9800.toInt()
            }
            val bg = when {
                active -> 0xFF2A2A4A.toInt()
                hovered -> 0xFF252540.toInt()
                else -> 0xFF1A1A2A.toInt()
            }
            context.fill(tx, y0, tx + tabW, y0 + SUBTAB_H, bg)
            if (active) context.fill(tx, y0 + SUBTAB_H - 1, tx + tabW, y0 + SUBTAB_H, accent)
            val labelW = (textRenderer.getWidth(label) * labelScale).toInt()
            val labelColor = if (active) 0xFFFFFF else 0x999999
            context.matrices.push()
            context.matrices.translate((tx + (tabW - labelW) / 2).toFloat(), (y0 + 4).toFloat(), 0f)
            context.matrices.scale(labelScale, labelScale, 1f)
            context.drawTextWithShadow(textRenderer, label, 0, 0, labelColor)
            context.matrices.pop()
            subTabBoxes[tab] = intArrayOf(tx, y0, tabW, SUBTAB_H)
        }
    }

    // ---- My Pokemon sub-tab ----

    private var myPokemonScroll = 0
    /** Set of skill IDs the user has checked. Empty = no filter (show all). */
    private val myPokemonSelectedSkills = mutableSetOf<String>()
    /** Set of category names whose skill-list is currently expanded in the sidebar. */
    private val myPokemonExpandedCats = mutableSetOf<String>()
    private var myPokemonLastRequestMs = 0L

    private fun renderMyPokemonView(context: DrawContext, mouseX: Int, mouseY: Int) {
        val now = System.currentTimeMillis()
        if (now - myPokemonLastRequestMs > 5000L) {
            myPokemonLastRequestMs = now
            try {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    notlown.cobblebase.core.net.MyPokemonRequestC2SPacket()
                )
            } catch (_: Exception) {}
        }
        val boxes = notlown.cobblebase.fabric.client.MyPokemonCache.boxes()
        currentHighlightSkills = myPokemonSelectedSkills
        renderBoxedMonGrid(
            context, mouseX, mouseY, boxes,
            selectedSkills = myPokemonSelectedSkills,
            expandedCats = myPokemonExpandedCats,
            scrollOffset = myPokemonScroll,
            onScrollSet = { myPokemonScroll = it },
            sidebarHeaderColor = 0x6AB8FF,
            sidebarTitle = "My Pokemon"
        )
    }

    /**
     * Box-grouped grid that mirrors Cobblemon's PC layout 1:1 \u2014 Party + every PC box, each
     * with its own header and 6\u00D7N slot grid. Boxes with zero matching mons are skipped;
     * remaining boxes flow 2-per-row (or more if the panel is wider).
     */
    private fun renderBoxedMonGrid(
        context: DrawContext, mouseX: Int, mouseY: Int,
        boxes: List<notlown.cobblebase.core.net.MyPokemonSyncS2CPacket.Box>,
        selectedSkills: MutableSet<String>,
        expandedCats: MutableSet<String>,
        scrollOffset: Int,
        onScrollSet: (Int) -> Unit,
        sidebarHeaderColor: Int,
        sidebarTitle: String
    ) {
        val listTop = panelY + SUBTAB_H + HEADER_HEIGHT
        val listBottom = panelY + panelH - 20

        // ---- Sidebar (expandable categories + per-skill checkboxes) ----
        val mainX = renderSkillSidebar(
            context, mouseX, mouseY, listTop, listBottom,
            allEntries = boxes.flatMap { it.slots.filterNotNull() },
            selectedSkills = selectedSkills,
            expandedCats = expandedCats,
            sidebarTitle = sidebarTitle
        )

        // ---- Filter to non-empty boxes (after applying the skill filter) ----
        val mainW = panelX + panelW - mainX - 6
        val nonEmptyBoxes = boxes.filter { box -> box.slots.any { it != null && matchesSelected(it, selectedSkills) } }
        val totalCount = nonEmptyBoxes.sumOf { box -> box.slots.count { it != null && matchesSelected(it, selectedSkills) } }
        context.drawTextWithShadow(textRenderer, "\u00A7f\u00A7l$sidebarTitle \u00A78($totalCount)", mainX, listTop - 12, sidebarHeaderColor)

        if (boxes.isEmpty()) {
            val msg = "\u00A77\u00A7oLoading your Pokemon from Party + PC\u2026"
            val msgW = textRenderer.getWidth(msg)
            context.drawTextWithShadow(textRenderer, msg, mainX + (mainW - msgW) / 2, listTop + 30, 0xAAAAAA)
            return
        }
        if (nonEmptyBoxes.isEmpty()) {
            val msg = "\u00A77\u00A7oNo Pokemon match the selected filters."
            val msgW = textRenderer.getWidth(msg)
            context.drawTextWithShadow(textRenderer, msg, mainX + (mainW - msgW) / 2, listTop + 30, 0xAAAAAA)
            return
        }

        // ---- Layout: greedy masonry \u2014 each box drops into whichever column is shortest. ----
        val cellSize = GRID_CELL
        val gap = GRID_GAP
        val headerH = 10
        val boxGap = 8
        val boxRenderW = 8 + GRID_COLS * cellSize + (GRID_COLS - 1) * gap // 4px padding each side
        val boxesPerRow = ((mainW + boxGap) / (boxRenderW + boxGap)).coerceAtLeast(1)
        val gridW = boxesPerRow * boxRenderW + (boxesPerRow - 1) * boxGap
        val gridLeftX = mainX + ((mainW - gridW) / 2).coerceAtLeast(0)

        // Track each column's accumulated height. Place each box in the shortest column
        // (ties \u2192 leftmost). Mirrors CSS masonry \u2014 a tall PC box on the right won't leave
        // a 100px-tall hole next to a short Party box on the left.
        data class BoxPlan(
            val box: notlown.cobblebase.core.net.MyPokemonSyncS2CPacket.Box,
            val col: Int, val y: Int, val rows: Int
        )
        val plan = mutableListOf<BoxPlan>()
        val colHeights = IntArray(boxesPerRow)
        for (box in nonEmptyBoxes) {
            val rows = (box.slots.size + GRID_COLS - 1) / GRID_COLS
            val boxH = headerH + rows * (cellSize + gap) + 4
            // Pick shortest column (leftmost on tie).
            var col = 0
            for (c in 1 until boxesPerRow) if (colHeights[c] < colHeights[col]) col = c
            val y = colHeights[col]
            plan.add(BoxPlan(box, col, y, rows))
            colHeights[col] = y + boxH + boxGap
        }
        val contentH = (colHeights.maxOrNull() ?: 0) - boxGap  // last boxGap doesn't count
        val viewportH = listBottom - listTop
        val maxScroll = (contentH - viewportH).coerceAtLeast(0)
        val clamped = scrollOffset.coerceIn(0, maxScroll)
        onScrollSet(clamped)

        pendingMonTooltipLines = emptyList()
        context.enableScissor(mainX, listTop, mainX + mainW - 4, listBottom)

        for (bp in plan) {
            val (box, col, y, _) = bp
            val baseX = gridLeftX + col * (boxRenderW + boxGap)
            val baseY = listTop + y - clamped
            // Header bar
            context.fill(baseX, baseY, baseX + boxRenderW, baseY + headerH, 0xFF2A2A4A.toInt())
            context.fill(baseX, baseY + headerH - 1, baseX + boxRenderW, baseY + headerH, 0xFF6A6AFF.toInt())
            val filledCount = box.slots.count { it != null }
            drawScaled(context, "\u00A7f\u00A7l${box.name} \u00A78($filledCount)", baseX + 4, baseY + 2, 0xFFFFFF, 0.75f)
            // Grid
            for ((idx, entry) in box.slots.withIndex()) {
                val r = idx / GRID_COLS
                val c = idx % GRID_COLS
                val cx = baseX + 4 + c * (cellSize + gap)
                val cy = baseY + headerH + 2 + r * (cellSize + gap)
                if (cy + cellSize < listTop || cy > listBottom) continue
                if (entry == null) {
                    renderEmptySlot(context, cx, cy)
                } else if (matchesSelected(entry, selectedSkills)) {
                    val view = entryToView(entry)
                    renderMonCell(context, view, cx, cy, idx, mouseX, mouseY)
                } else {
                    renderEmptySlot(context, cx, cy, dim = true)
                }
            }
        }
        context.disableScissor()

        if (pendingMonTooltipLines.isNotEmpty()) {
            context.drawTooltip(textRenderer, pendingMonTooltipLines, pendingMonTooltipX, pendingMonTooltipY)
        }

        // Scrollbar — handed off to the reusable component so click-and-drag works,
        // not just wheel scroll. Geometry refreshes per frame; component owns drag state.
        mainGridScrollbar.layout(
            trackX = panelX + panelW - 6,
            trackY = listTop,
            trackHeight = viewportH,
            contentHeight = contentH,
            viewportHeight = viewportH,
            currentScroll = clamped,
        )
        mainGridScrollbar.render(context, mouseX, mouseY)
        if (mainGridScrollbar.scroll != clamped) onScrollSet(mainGridScrollbar.scroll)
    }

    /**
     * Renders the expandable skill-filter sidebar used by both My Pokemon and PokeWiki.
     * Returns the X coordinate where the main pane should start (just after the sidebar).
     *
     * Layout per row:
     *   [All]      ← clears all selections
     *   [▶ ■ Cat (N)]
     *     [☑ icon Skill (N)]   ← per-skill checkbox (visible when category expanded)
     *     ...
     */
    private fun renderSkillSidebar(
        context: DrawContext, mouseX: Int, mouseY: Int,
        listTop: Int, listBottom: Int,
        allEntries: List<notlown.cobblebase.core.net.MyPokemonSyncS2CPacket.Entry>,
        selectedSkills: MutableSet<String>,
        expandedCats: MutableSet<String>,
        sidebarTitle: String
    ): Int {
        val sbX = panelX + 2
        val sbW = SIDEBAR_W_SKILLS
        context.fill(sbX, listTop - 6, sbX + sbW, listBottom, 0xCC15152A.toInt())
        context.fill(sbX + sbW, listTop - 6, sbX + sbW + 1, listBottom, 0xFF3A3A5C.toInt())
        drawScaled(context, "§f§l$sidebarTitle", sbX + 4, listTop - 4, 0xFFFFFF, 0.75f)

        sidebarCategoryHitBoxes.clear()
        sidebarSkillHitBoxes.clear()
        sidebarAllHitBox = intArrayOf(0, 0, 0, 0)
        sidebarContentTop = listTop + 6
        sidebarContentBottom = listBottom

        // Build skill catalog grouped by category.
        val allSkills = notlown.cobblebase.core.SkillRegistry.getAll().values.toList()
        val byCat = allSkills.groupBy { it.category }.toSortedMap()
        fun countMonsForSkill(id: String) = allEntries.count { it.skillIds.contains(id) }
        fun countMonsForCat(cat: String) = allEntries.count { e -> e.skillIds.any { id -> notlown.cobblebase.core.SkillRegistry.get(id)?.category == cat } }

        val rowH = 11
        // Compute total content height so we can clamp scroll + render a scrollbar.
        var totalH = rowH + 1 + 1  // "All" row + 1px separator
        for ((cat, skills) in byCat) {
            totalH += rowH
            if (cat in expandedCats) totalH += skills.size * rowH
        }
        val viewportH = listBottom - sidebarContentTop
        val maxSidebarScroll = (totalH - viewportH).coerceAtLeast(0)
        sidebarScroll = sidebarScroll.coerceIn(0, maxSidebarScroll)

        context.enableScissor(sbX, sidebarContentTop, sbX + sbW, listBottom)
        var sy = sidebarContentTop - sidebarScroll

        // "All" pseudo-row — clears selections.
        run {
            val isActive = selectedSkills.isEmpty()
            val hovered = mouseX in sbX..(sbX + sbW) && mouseY in sy..(sy + rowH) && mouseY in sidebarContentTop..listBottom
            if (isActive) context.fill(sbX + 1, sy, sbX + sbW, sy + rowH, 0x442196F3)
            else if (hovered) context.fill(sbX + 1, sy, sbX + sbW, sy + rowH, 0x22FFFFFF)
            drawScaled(context, "§f§lAll §8(${allEntries.size})", sbX + 4, sy + 2, if (isActive) 0xFFFFFF else 0xCCCCCC, 0.7f)
            sidebarAllHitBox = intArrayOf(sbX, sy, sbW, rowH)
            sy += rowH + 1
            context.fill(sbX + 3, sy - 1, sbX + sbW - 3, sy, 0xFF3A3A5C.toInt())
        }

        // Category rows + (expanded) skill rows
        for ((cat, skills) in byCat) {
            val expanded = cat in expandedCats
            val catCount = countMonsForCat(cat)
            val visible = sy + rowH >= sidebarContentTop && sy <= listBottom
            val hovered = visible && mouseX in sbX..(sbX + sbW) && mouseY in sy..(sy + rowH) && mouseY in sidebarContentTop..listBottom
            if (visible) {
                if (hovered) context.fill(sbX + 1, sy, sbX + sbW, sy + rowH, 0x22FFFFFF)
                drawScaled(context, "§7${if (expanded) "▼" else "▶"}", sbX + 3, sy + 2, 0x888888, 0.7f)
                val color = categoryColor(cat)
                context.fill(sbX + 11, sy + 4, sbX + 15, sy + 8, color)
                val catLabel = cat.replaceFirstChar { it.uppercase() }
                drawScaled(context, "§f$catLabel §8($catCount)", sbX + 18, sy + 2, 0xCCCCCC, 0.7f)
            }
            sidebarCategoryHitBoxes[cat] = intArrayOf(sbX, sy, sbW, rowH)
            sy += rowH

            if (expanded) {
                for (skill in skills.sortedBy { it.name }) {
                    val sVisible = sy + rowH >= sidebarContentTop && sy <= listBottom
                    val isSel = skill.id in selectedSkills
                    val sHov = sVisible && mouseX in sbX..(sbX + sbW) && mouseY in sy..(sy + rowH) && mouseY in sidebarContentTop..listBottom
                    if (sVisible) {
                        if (sHov) context.fill(sbX + 1, sy, sbX + sbW, sy + rowH, 0x22FFFFFF)
                        val cbX = sbX + 16
                        val cbY = sy + 2
                        context.fill(cbX, cbY, cbX + 7, cbY + 7, if (isSel) 0xFF4CAF50.toInt() else 0xFF333344.toInt())
                        context.fill(cbX, cbY, cbX + 7, cbY + 1, 0xFF777788.toInt())
                        context.fill(cbX, cbY + 6, cbX + 7, cbY + 7, 0xFF777788.toInt())
                        context.fill(cbX, cbY, cbX + 1, cbY + 7, 0xFF777788.toInt())
                        context.fill(cbX + 6, cbY, cbX + 7, cbY + 7, 0xFF777788.toInt())
                        if (isSel) drawScaled(context, "§f✓", cbX + 1, cbY - 1, 0xFFFFFF, 0.7f)
                        val n = countMonsForSkill(skill.id)
                        drawScaled(context, "§f${skill.name} §8($n)", cbX + 11, sy + 2, if (isSel) 0xFFFFFF else 0xAAAAAA, 0.65f)
                    }
                    sidebarSkillHitBoxes[skill.id] = intArrayOf(sbX, sy, sbW, rowH)
                    sy += rowH
                }
            }
        }
        context.disableScissor()

        // Scrollbar (when content overflows)
        if (maxSidebarScroll > 0) {
            val trackX = sbX + sbW - 2
            val thumbH = ((viewportH.toFloat() / totalH) * viewportH).toInt().coerceAtLeast(8)
            val thumbY = sidebarContentTop + ((sidebarScroll.toFloat() / maxSidebarScroll) * (viewportH - thumbH)).toInt()
            context.fill(trackX, sidebarContentTop, trackX + 1, sidebarContentTop + viewportH, 0x33FFFFFF)
            context.fill(trackX, thumbY, trackX + 1, thumbY + thumbH, 0xAAFFFFFF.toInt())
        }

        return sbX + sbW + 4
    }

    /** AND-filter: entry must contain every skill in selectedSkills. Empty selection = match. */
    private fun matchesSelected(
        entry: notlown.cobblebase.core.net.MyPokemonSyncS2CPacket.Entry,
        selectedSkills: Set<String>
    ): Boolean {
        if (selectedSkills.isEmpty()) return true
        return selectedSkills.all { it in entry.skillIds }
    }

    private fun renderEmptySlot(context: DrawContext, cx: Int, cy: Int, dim: Boolean = false) {
        val bg = if (dim) 0xFF15151E.toInt() else 0xFF1A1A2A.toInt()
        val border = if (dim) 0xFF2A2A3C.toInt() else 0xFF3A3A5C.toInt()
        context.fill(cx, cy, cx + GRID_CELL, cy + GRID_CELL, bg)
        context.drawHorizontalLine(cx, cx + GRID_CELL - 1, cy, border)
        context.drawHorizontalLine(cx, cx + GRID_CELL - 1, cy + GRID_CELL - 1, border)
        context.drawVerticalLine(cx, cy, cy + GRID_CELL - 1, border)
        context.drawVerticalLine(cx + GRID_CELL - 1, cy, cy + GRID_CELL - 1, border)
    }

    /** Convert a packet entry into the local MonView model (resolves proficiencies). */
    private fun entryToView(entry: notlown.cobblebase.core.net.MyPokemonSyncS2CPacket.Entry): MonView {
        val speciesSkills = notlown.cobblebase.core.SpeciesSkillRegistry
            .getSkills(entry.species)?.skills.orEmpty()
        val skills = entry.skillIds.map { id ->
            id to (speciesSkills.find { it.skillId == id }?.proficiency ?: 0)
        }
        return MonView(entry.species, entry.displayName, entry.level, location = "",
            skills = skills, aspects = entry.aspects.toSet(), speciesId = entry.speciesId)
    }

    // ---- Shared sidebar + mon-list rendering (used by PokeWiki + My Pokemon) ----

    /** Display info for one mon row in the shared renderer. */
    private data class MonView(
        val species: String,
        val displayName: String,
        val level: Int,
        val location: String, // empty for PokeWiki
        val skills: List<Pair<String, Int>>, // (skillId, proficiency)
        val aspects: Set<String> = emptySet(),
        /** Canonical "modid:species" identifier when known — bypasses name-resolution heuristics. */
        val speciesId: String = ""
    )

    private val SIDEBAR_W_SKILLS = 110
    private val MON_ROW_H = 16  // legacy — replaced by grid layout below

    // Cobblemon-PC-style grid constants
    private val GRID_CELL = 28
    private val GRID_GAP = 2
    private val GRID_COLS = 6

    /** Pending tooltip lines for the current frame's mon row hover. Cleared each render. */
    private var pendingMonTooltipLines: List<net.minecraft.text.Text> = emptyList()
    private var pendingMonTooltipX = 0
    private var pendingMonTooltipY = 0

    /** Hit-box for the "All" pseudo-row at top of sidebar — clears selectedSkills. */
    private var sidebarAllHitBox: IntArray = intArrayOf(0, 0, 0, 0)
    /** Hit-boxes for category rows (toggle expanded). Keyed by category name. */
    private var sidebarCategoryHitBoxes = mutableMapOf<String, IntArray>()
    /** Hit-boxes for individual skill rows (toggle in selectedSkills). Keyed by skill id. */
    private var sidebarSkillHitBoxes = mutableMapOf<String, IntArray>()
    /** Sort-field cycler hit-box for the WorkerWiki view. */
    private var pokeWikiSortFieldHitBox: IntArray = intArrayOf(0, 0, 0, 0)
    /** Asc/desc toggle hit-box for the WorkerWiki view. */
    private var pokeWikiSortDirHitBox: IntArray = intArrayOf(0, 0, 0, 0)
    /** "Open Web DB" button hit-box for the WorkerWiki view. */
    private var pokeWikiWebDbHitBox: IntArray = intArrayOf(0, 0, 0, 0)
    private val POKEWIKI_WEB_DB_URL = "https://notlown.github.io/cobblebase-web/database/"
    /** Shared scroll offset for the sidebar (My Pokemon + WorkerWiki use the same widget). */
    private var sidebarScroll: Int = 0
    /** Sidebar viewport bounds — recomputed each render, consulted by mouseScrolled. */
    private var sidebarContentTop: Int = 0
    private var sidebarContentBottom: Int = 0
    /** Skill IDs the active sub-tab is currently filtering by. Used to highlight tooltip rows. */
    private var currentHighlightSkills: Set<String> = emptySet()

    /**
     * Solid dark cell background with a clear-but-subtle hue shift per rarity. Alpha
     * blending over the navy panel kept producing muddy/brown results — opaque dark hues
     * lift the cell above the panel by a couple of luminance steps while keeping each
     * color visually unambiguous. The panel's dark navy still reads through the gap
     * between cells.
     */
    private fun rarityBgColor(species: String): Int {
        return when (notlown.cobblebase.core.SpawnData.getBucket(species)) {
            notlown.cobblebase.core.SpawnData.Bucket.COMMON -> 0xFF2A2A38.toInt()      // lifted neutral
            notlown.cobblebase.core.SpawnData.Bucket.UNCOMMON -> 0xFF2D5C36.toInt()    // pastel green
            notlown.cobblebase.core.SpawnData.Bucket.RARE -> 0xFF2E4A85.toInt()        // pastel blue
            notlown.cobblebase.core.SpawnData.Bucket.ULTRA_RARE -> 0xFF8A6E20.toInt()  // pastel gold
        }
    }

    /** Rarity-colored prefix tag for tooltips: "(C)/(UC)/(R)/(UR)" with matching color code. */
    private fun rarityTag(species: String): String {
        return when (notlown.cobblebase.core.SpawnData.getBucket(species)) {
            notlown.cobblebase.core.SpawnData.Bucket.COMMON -> "§7(C)"
            notlown.cobblebase.core.SpawnData.Bucket.UNCOMMON -> "§a(UC)"
            notlown.cobblebase.core.SpawnData.Bucket.RARE -> "§9(R)"
            notlown.cobblebase.core.SpawnData.Bucket.ULTRA_RARE -> "§6(UR)"
        }
    }

    /** Single Cobblemon-PC-style cell with sprite + rarity tint + hover detection. */
    private fun renderMonCell(
        context: DrawContext, mon: MonView,
        cx: Int, cy: Int, idx: Int,
        mouseX: Int, mouseY: Int
    ) {
        val hovered = mouseX in cx..(cx + GRID_CELL) && mouseY in cy..(cy + GRID_CELL)
        // Solid hue-shifted dark cell bg (one fill — alpha overlay produced muddy colors).
        context.fill(cx, cy, cx + GRID_CELL, cy + GRID_CELL, rarityBgColor(mon.species))
        // Border: gold on hover, dark frame otherwise.
        val borderColor = if (hovered) 0xFFFFD700.toInt() else 0xFF15151E.toInt()
        context.drawHorizontalLine(cx, cx + GRID_CELL - 1, cy, borderColor)
        context.drawHorizontalLine(cx, cx + GRID_CELL - 1, cy + GRID_CELL - 1, borderColor)
        context.drawVerticalLine(cx, cy, cy + GRID_CELL - 1, borderColor)
        context.drawVerticalLine(cx + GRID_CELL - 1, cy, cy + GRID_CELL - 1, borderColor)
        // Portrait-only sprite (no type-colored bg/border so the rarity color shows through).
        context.matrices.push()
        context.matrices.translate((cx + 2).toFloat(), (cy + 2).toFloat(), 0f)
        context.matrices.scale(1.5f, 1.5f, 1f)
        if (mon.speciesId.isNotBlank()) {
            val id = try { net.minecraft.util.Identifier.tryParse(mon.speciesId) } catch (_: Exception) { null }
            if (id != null) {
                PokemonSpriteHelper.renderPortraitOnly(context, id, mon.aspects, 0, 0, 0f)
            } else {
                PokemonSpriteHelper.renderPortraitOnlyByName(context, mon.species, mon.aspects, 0, 0, 0f)
            }
        } else {
            PokemonSpriteHelper.renderPortraitOnlyByName(context, mon.species, mon.aspects, 0, 0, 0f)
        }
        context.matrices.pop()
        // Tiny level number bottom-right
        if (mon.level > 0) {
            context.matrices.push()
            context.matrices.translate((cx + GRID_CELL - 14).toFloat(), (cy + GRID_CELL - 8).toFloat(), 0f)
            context.matrices.scale(0.55f, 0.55f, 1f)
            context.drawTextWithShadow(textRenderer, "§fLv${mon.level}", 0, 0, 0xFFFFFF)
            context.matrices.pop()
        }
        if (hovered) buildMonTooltip(mon, mouseX, mouseY)
    }

    private fun skillCategory(skillId: String): String =
        notlown.cobblebase.core.SkillRegistry.get(skillId)?.category ?: "all"

    private fun categoryColor(catKey: String): Int = when (catKey) {
        "gathering" -> 0xFF4CAF50.toInt()
        "combat" -> 0xFFF44336.toInt()
        "support" -> 0xFFE91E9E.toInt()
        "utility" -> 0xFF2196F3.toInt()
        "generation" -> 0xFFFF9800.toInt()
        "legendary" -> 0xFFFFD700.toInt()
        "social" -> 0xFFFF55FF.toInt()
        else -> 0xFF666666.toInt()
    }

    /**
     * Compact one-line mon row. Skills are shown only on hover via tooltip so the list can
     * pack many mons into the visible area.
     *
     * Layout: [loc pill][sprite][name §8Lv.N][space ...][job count + category dots right-aligned]
     */
    private fun renderMonRow(
        context: DrawContext, mon: MonView,
        mainX: Int, mainW: Int, rowY: Int, idx: Int,
        mouseX: Int, mouseY: Int
    ) {
        val bg = if (idx % 2 == 0) 0x44FFFFFF.toInt() else 0x22FFFFFF.toInt()
        val rowBottom = rowY + MON_ROW_H - 1
        context.fill(mainX, rowY, mainX + mainW, rowBottom, bg)

        // Location pill on left edge (My Pokemon only)
        if (mon.location.isNotBlank()) {
            val locColor = when (mon.location) {
                "Party" -> 0xFF55FF55.toInt()
                "PC" -> 0xFF5555FF.toInt()
                else -> 0xFFAAAAAA.toInt()
            }
            context.fill(mainX, rowY, mainX + 3, rowBottom, locColor)
        }

        // Sprite (16x16 overflows row slightly — same trick LogsPanel uses)
        PokemonSpriteHelper.renderSmallIconByName(context, textRenderer, mon.species, mainX + 5, rowY - 1, 0f)

        // Name + level (single line, scaled 0.85)
        val display = mon.displayName.ifBlank { mon.species.replaceFirstChar { it.uppercase() } }
        val nameText = if (mon.level > 0) "§f§l$display §8Lv.${mon.level}" else "§f§l$display"
        drawScaled(context, nameText, mainX + 24, rowY + 3, 0xFFFFFF, 0.85f)

        // Right-side: small category dots showing which categories this mon supports + job count.
        val seenCats = linkedSetOf<String>()
        for ((skillId, _) in mon.skills) {
            val cat = notlown.cobblebase.core.SkillRegistry.get(skillId)?.category ?: continue
            seenCats.add(cat)
        }
        // Render dots from right-to-left so they don't push into name area.
        var dotX = mainX + mainW - 8
        for (cat in seenCats.toList().reversed()) {
            context.fill(dotX, rowY + 5, dotX + 4, rowY + 9, categoryColor(cat))
            dotX -= 6
        }
        val jobCount = "§7${mon.skills.size}"
        val jobCountW = (textRenderer.getWidth(jobCount) * 0.7f).toInt()
        drawScaled(context, jobCount, dotX - jobCountW - 4, rowY + 4, 0xAAAAAA, 0.7f)

        // Hover → build tooltip lines lazily
        if (mouseX in mainX..(mainX + mainW) && mouseY in rowY..rowBottom) {
            buildMonTooltip(mon, mouseX, mouseY)
        }
    }

    private fun buildMonTooltip(mon: MonView, mouseX: Int, mouseY: Int) {
        val lines = mutableListOf<net.minecraft.text.Text>()
        val display = mon.displayName.ifBlank { mon.species.replaceFirstChar { it.uppercase() } }
        val tag = rarityTag(mon.species)
        val title = if (mon.level > 0) "$tag §f§l$display §8Lv.${mon.level}" else "$tag §f§l$display"
        lines.add(net.minecraft.text.Text.literal(title))
        if (mon.location.isNotBlank()) lines.add(net.minecraft.text.Text.literal("§7Location: §f${mon.location}"))
        lines.add(net.minecraft.text.Text.literal("§8─────────"))
        // Filtered skills float to the top so the user sees them first, then the rest
        // sorted by category + prof descending.
        val sorted = mon.skills.sortedWith(
            compareByDescending<Pair<String, Int>> { it.first in currentHighlightSkills }
                .thenBy { notlown.cobblebase.core.SkillRegistry.get(it.first)?.category ?: "z" }
                .thenByDescending { it.second }
        )
        for ((skillId, prof) in sorted) {
            val def = notlown.cobblebase.core.SkillRegistry.get(skillId) ?: continue
            val stars = (1..5).joinToString("") { if (it <= prof) "★" else "☆" }
            val starColor = when {
                prof >= 5 -> "§6"
                prof >= 4 -> "§e"
                prof >= 3 -> "§a"
                else -> "§7"
            }
            val catShort = def.category.replaceFirstChar { it.uppercase() }.take(4)
            // Highlight skills the user is currently filtering by — yellow arrow + bold name.
            val highlighted = skillId in currentHighlightSkills
            val line = if (highlighted) {
                "§e▶ §8[$catShort] §e§l${def.name} $starColor$stars"
            } else {
                "§8[$catShort] §f${def.name} $starColor$stars"
            }
            lines.add(net.minecraft.text.Text.literal(line))
        }
        pendingMonTooltipLines = lines
        pendingMonTooltipX = mouseX
        pendingMonTooltipY = mouseY
    }

    private fun drawScaled(context: DrawContext, text: String, px: Int, py: Int, color: Int, scale: Float) {
        context.matrices.push()
        context.matrices.translate(px.toFloat(), py.toFloat(), 0f)
        context.matrices.scale(scale, scale, 1f)
        context.drawTextWithShadow(textRenderer, text, 0, 0, color)
        context.matrices.pop()
    }

    // ---- PokeWiki sub-tab ----

    private var pokeWikiScroll = 0
    private val pokeWikiSelectedSkills = mutableSetOf<String>()
    private val pokeWikiExpandedCats = mutableSetOf<String>()

    /** What to sort by — picks the field. Direction is a separate toggle. */
    enum class SortField { DEX, NAME, PROF, SKILLS, RARITY }
    private var pokeWikiSortField: SortField = SortField.DEX
    /** true = descending (high → low), false = ascending. */
    private var pokeWikiSortDesc: Boolean = false

    private fun renderPokeWikiStub(context: DrawContext, mouseX: Int, mouseY: Int) {
        if (!notlown.cobblebase.core.GeneralSettingsCache.pokeWikiEnabled) {
            val cx = panelX + panelW / 2
            val cy = panelY + panelH / 2
            val msg = "\u00A77\u00A7lWorkerWiki disabled"
            val msgW = textRenderer.getWidth(msg)
            context.drawTextWithShadow(textRenderer, msg, cx - msgW / 2, cy - 8, 0xCCCCCC)
            val sub = "\u00A78This server has the in-game species reference disabled. Discover skills by experimenting."
            val subW = (textRenderer.getWidth(sub) * 0.7f).toInt()
            context.matrices.push()
            context.matrices.translate((cx - subW / 2).toFloat(), (cy + 6).toFloat(), 0f)
            context.matrices.scale(0.7f, 0.7f, 1f)
            context.drawTextWithShadow(textRenderer, sub, 0, 0, 0x888888)
            context.matrices.pop()
            return
        }
        renderPokeWikiGrid(context, mouseX, mouseY)
    }

    /**
     * Flat species grid that fills the whole right pane (no boxes). Sort toggle in
     * the top-right cycles Dex / A-Z / Z-A. Filter is the same expandable-sidebar
     * pattern as My Pokemon (AND-logic on selectedSkills).
     */
    private fun renderPokeWikiGrid(context: DrawContext, mouseX: Int, mouseY: Int) {
        val listTop = panelY + SUBTAB_H + HEADER_HEIGHT
        val listBottom = panelY + panelH - 20
        currentHighlightSkills = pokeWikiSelectedSkills

        // Build species pool & represent each as a packet Entry so the sidebar's counter logic works.
        val pool = notlown.cobblebase.core.SpeciesSkillRegistry.getAllAssigned()
            .toList()
            .map { (species, data) ->
                notlown.cobblebase.core.net.MyPokemonSyncS2CPacket.Entry(
                    species = species,
                    displayName = species.replaceFirstChar { it.uppercase() },
                    level = 0,
                    skillIds = data.skills.map { it.skillId }
                )
            }

        val mainX = renderSkillSidebar(
            context, mouseX, mouseY, listTop, listBottom,
            allEntries = pool,
            selectedSkills = pokeWikiSelectedSkills,
            expandedCats = pokeWikiExpandedCats,
            sidebarTitle = "Species"
        )
        val mainW = panelX + panelW - mainX - 6

        // Top-right controls: [\u{1F310} Web DB] [Field \u25BE] [\u2191/\u2193]. Web DB opens the web
        // species database in the user's default browser; sort buttons are unchanged.
        val fieldLabel = when (pokeWikiSortField) {
            SortField.DEX -> "\u00A7fDex#"
            SortField.NAME -> "\u00A7fName"
            SortField.PROF -> "\u00A7fProf"
            SortField.SKILLS -> "\u00A7fSkills"
            SortField.RARITY -> "\u00A7fRarity"
        }
        val dirLabel = if (pokeWikiSortDesc) "\u00A7f\u2193" else "\u00A7f\u2191"
        val fieldW = 44
        val dirW = 14
        val webDbW = 56
        val sortH = 12
        val dirX = panelX + panelW - dirW - 8
        val fieldX = dirX - fieldW - 2
        val webDbX = fieldX - webDbW - 6
        val sortY = listTop - 14
        // Web DB button (accent gold to draw the eye to the external option)
        val wHov = mouseX in webDbX..(webDbX + webDbW) && mouseY in sortY..(sortY + sortH)
        context.fill(webDbX, sortY, webDbX + webDbW, sortY + sortH, if (wHov) 0xFF6C5A2A.toInt() else 0xFF453820.toInt())
        context.fill(webDbX, sortY, webDbX + webDbW, sortY + 1, 0xFFF7C948.toInt())
        drawScaled(context, "\u00A7e\u00A7l\u26F1 Web DB", webDbX + 4, sortY + 3, 0xFFD700, 0.75f)
        pokeWikiWebDbHitBox = intArrayOf(webDbX, sortY, webDbW, sortH)
        // Field button
        val fHov = mouseX in fieldX..(fieldX + fieldW) && mouseY in sortY..(sortY + sortH)
        context.fill(fieldX, sortY, fieldX + fieldW, sortY + sortH, if (fHov) 0xFF3A3A6C.toInt() else 0xFF252545.toInt())
        context.fill(fieldX, sortY, fieldX + fieldW, sortY + 1, 0xFF6A6AFF.toInt())
        drawScaled(context, fieldLabel, fieldX + 4, sortY + 3, 0xFFFFFF, 0.75f)
        pokeWikiSortFieldHitBox = intArrayOf(fieldX, sortY, fieldW, sortH)
        // Direction button
        val dHov = mouseX in dirX..(dirX + dirW) && mouseY in sortY..(sortY + sortH)
        context.fill(dirX, sortY, dirX + dirW, sortY + sortH, if (dHov) 0xFF3A3A6C.toInt() else 0xFF252545.toInt())
        context.fill(dirX, sortY, dirX + dirW, sortY + 1, 0xFF6A6AFF.toInt())
        drawScaled(context, dirLabel, dirX + 4, sortY + 3, 0xFFFFFF, 0.75f)
        pokeWikiSortDirHitBox = intArrayOf(dirX, sortY, dirW, sortH)

        // Filter + sort.
        val filtered = pool.filter { matchesSelected(it, pokeWikiSelectedSkills) }
        // Sort key helpers.
        fun profSum(entry: notlown.cobblebase.core.net.MyPokemonSyncS2CPacket.Entry): Int {
            val s = notlown.cobblebase.core.SpeciesSkillRegistry.getSkills(entry.species)?.skills.orEmpty()
            // When no skills are selected, fall back to the mon's overall best proficiency
            // so PROF still has a meaningful ordering (highest 5\u2605 mons float up).
            if (pokeWikiSelectedSkills.isEmpty()) return s.maxOfOrNull { it.proficiency } ?: 0
            return pokeWikiSelectedSkills.sumOf { id -> s.find { it.skillId == id }?.proficiency ?: 0 }
        }
        fun skillCount(entry: notlown.cobblebase.core.net.MyPokemonSyncS2CPacket.Entry): Int = entry.skillIds.size
        fun rarityOrdinal(entry: notlown.cobblebase.core.net.MyPokemonSyncS2CPacket.Entry): Int =
            notlown.cobblebase.core.SpawnData.getBucket(entry.species).ordinal

        // Build the primary comparator based on field; layer name as tiebreaker.
        val byField: Comparator<notlown.cobblebase.core.net.MyPokemonSyncS2CPacket.Entry> = when (pokeWikiSortField) {
            SortField.DEX -> compareBy { pokedexNumber(it.species) }
            SortField.NAME -> compareBy { it.species }
            SortField.PROF -> compareBy { profSum(it) }
            SortField.SKILLS -> compareBy { skillCount(it) }
            SortField.RARITY -> compareBy { rarityOrdinal(it) }
        }
        val full = byField.thenBy { it.species }
        val sorted = if (pokeWikiSortDesc) filtered.sortedWith(full.reversed()) else filtered.sortedWith(full)

        context.drawTextWithShadow(textRenderer, "\u00A7f\u00A7lSpecies \u00A78(${sorted.size})", mainX, listTop - 12, 0xFFD700)

        if (sorted.isEmpty()) {
            val msg = "\u00A77\u00A7oNo species match the selected filters."
            val msgW = textRenderer.getWidth(msg)
            context.drawTextWithShadow(textRenderer, msg, mainX + (mainW - msgW) / 2, listTop + 30, 0xAAAAAA)
            return
        }

        // Dynamic columns \u2014 fill full mainW.
        val cell = GRID_CELL
        val gap = GRID_GAP
        val usableW = mainW - 8
        val cols = ((usableW + gap) / (cell + gap)).coerceAtLeast(1)
        val rows = (sorted.size + cols - 1) / cols
        val rowH = cell + gap
        val viewportH = listBottom - listTop
        val contentH = rows * rowH
        val maxScroll = (contentH - viewportH).coerceAtLeast(0)
        val clamped = pokeWikiScroll.coerceIn(0, maxScroll)
        pokeWikiScroll = clamped

        pendingMonTooltipLines = emptyList()
        context.enableScissor(mainX, listTop, mainX + mainW - 4, listBottom)
        // Outer frame
        context.fill(mainX, listTop, mainX + mainW - 4, listBottom, 0xFF0E0E1A.toInt())
        for (i in sorted.indices) {
            val r = i / cols
            val c = i % cols
            val cx = mainX + 4 + c * (cell + gap)
            val cy = listTop + 4 + r * rowH - clamped
            if (cy + cell < listTop || cy > listBottom) continue
            val entry = sorted[i]
            val view = entryToView(entry)
            renderMonCell(context, view, cx, cy, i, mouseX, mouseY)
        }
        context.disableScissor()

        if (pendingMonTooltipLines.isNotEmpty()) {
            context.drawTooltip(textRenderer, pendingMonTooltipLines, pendingMonTooltipX, pendingMonTooltipY)
        }

        // Shared scrollbar component (also used by My Pokemon). Click on the track
        // jumps the thumb and starts a drag; drag-and-release works as expected.
        mainGridScrollbar.layout(
            trackX = panelX + panelW - 6,
            trackY = listTop,
            trackHeight = viewportH,
            contentHeight = contentH,
            viewportHeight = viewportH,
            currentScroll = clamped,
        )
        mainGridScrollbar.render(context, mouseX, mouseY)
        if (mainGridScrollbar.scroll != clamped) pokeWikiScroll = mainGridScrollbar.scroll
    }

    /** Pokedex number for sorting. Mirrors AdminSpeciesListPanel's helper. */
    private fun pokedexNumber(species: String): Int {
        return try {
            com.cobblemon.mod.common.api.pokemon.PokemonSpecies
                .getByName(species)?.nationalPokedexNumber ?: Int.MAX_VALUE
        } catch (_: Exception) { Int.MAX_VALUE }
    }

    private fun renderActiveWorkers(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Column headers
        val headerY = contentY - 12
        context.drawTextWithShadow(textRenderer, "\u00A7ePokemon", panelX + PANEL_PADDING, headerY, 0xFFFF55)
        // "Skills" header sits over the actual skill chips, NOT over the Relax/Auto
        // toggle column to its left. Previously the header started right after the
        // Pokemon column, which made it look like "Relax" was the first skill.
        val skillsHeaderX = panelX + PANEL_PADDING + NAME_WIDTH + AURA_ICON_WIDTH + AUTO_BTN_WIDTH + BTN_GAP
        context.drawTextWithShadow(textRenderer, "\u00A7eSkills", skillsHeaderX, headerY, 0xFFFF55)

        // Content area with scissor
        val contentBottom = panelY + panelH - 18
        context.enableScissor(panelX, contentY - 2, panelX + panelW, contentBottom)

        // Row backgrounds + Pokemon names with sprite icons
        pokemonList.forEachIndexed { index, pokemonData ->
            val rowH = rowHeights[index]
            val ry = contentY + rowOffsets[index] + scrollY
            if (ry < contentY - ROW_HEIGHT_LARGE || ry > contentBottom) return@forEachIndexed

            val rowColor = if (index % 2 == 0) ROW_EVEN else ROW_ODD
            context.fill(panelX + 1, ry, panelX + panelW - 1, ry + rowH - 1, rowColor)

            // Pokemon portrait — 1.0× scale (16 px native). Fits the compact 18-px
            // row exactly. Name + Lv stack vertically next to it.
            val name = pokemonData.displayName.string
            val spriteScale = 1.15f
            context.matrices.push()
            context.matrices.translate((panelX + PANEL_PADDING).toFloat(), (ry + 1).toFloat(), 0f)
            context.matrices.scale(spriteScale, spriteScale, 1f)
            PokemonSpriteHelper.renderIcon(
                context, textRenderer, pokemonData.species, name, pokemonData.aspects,
                0, 0, delta
            )
            context.matrices.pop()

            // Name at 0.9 scale — slightly tighter than full size so it doesn't
            // crowd the chip column for longer names. Lv stays at 0.75.
            val nameX = panelX + PANEL_PADDING + ICON_OFFSET + 2
            val nameScale = 0.9f
            context.matrices.push()
            context.matrices.translate(nameX.toFloat(), (ry + 2).toFloat(), 0f)
            context.matrices.scale(nameScale, nameScale, 1f)
            context.drawTextWithShadow(textRenderer, name, 0, 0, 0xFFFFFFFF.toInt())
            context.matrices.pop()

            val lvScale = 0.75f
            context.matrices.push()
            context.matrices.translate(nameX.toFloat(), (ry + 11).toFloat(), 0f)
            context.matrices.scale(lvScale, lvScale, 1f)
            context.drawTextWithShadow(textRenderer, "\u00A77Lv.${pokemonData.level}", 0, 0, 0xAAAAAA)
            context.matrices.pop()

            // Aura icon between Pokemon and Skills (only if mon has buff)
            val speciesName = SpeciesSkillRegistry.resolveFormName(pokemonData.species.path, pokemonData.aspects)
            val speciesSkills = SpeciesSkillRegistry.getSkills(speciesName)
            if (speciesSkills != null) {
                val buffEmoji = speciesSkills.skills.firstNotNullOfOrNull { entry ->
                    val skillDef = SkillRegistry.get(entry.skillId)
                    if (skillDef != null && BaseManager.isBuffExecutor(skillDef.executor))
                        getBuffEmoji(skillDef.executor)
                    else null
                }
                if (buffEmoji != null) {
                    val auraX = panelX + PANEL_PADDING + NAME_WIDTH
                    val auraScale = 0.75f
                    context.matrices.push()
                    context.matrices.translate(auraX.toFloat(), (ry + 5).toFloat(), 0f)
                    context.matrices.scale(auraScale, auraScale, 1f)
                    context.drawTextWithShadow(textRenderer, buffEmoji, 0, 0, 0xFFFFFF)
                    context.matrices.pop()
                }
            }
        }

        // Collect supplier Pokemon IDs for skipping their buttons
        val supplierPokemonIds = pokemonList
            .filter { AssignmentCache.isCraftsmanSupplier(it.pokemonId) }
            .map { it.pokemonId }.toSet()

        // Skill chips — see PASTURE_REDESIGN_HOOK below.
        for (btn in allButtons) {
            if (btn.pokemonId in supplierPokemonIds) continue
            val rx = btn.baseX + scrollX
            val ry = btn.baseY + scrollY
            val isAutoBtn = btn.skillId == null
            val bw = if (isAutoBtn) AUTO_BTN_WIDTH else (chipWidthByPokemon[btn.pokemonId] ?: BTN_WIDTH)

            if (ry < contentY - ROW_HEIGHT_LARGE || ry > contentBottom) continue
            if (rx + bw < panelX + PANEL_PADDING + NAME_WIDTH || rx > panelX + panelW) continue

            val hovered = mouseX in rx..(rx + bw) && mouseY in ry..(ry + BTN_HEIGHT)
            // Per-job accent from JobColors (Mining = stone gray, Fishing = ocean
            // blue, etc.) — falls back to category color for jobs not yet in the
            // palette. Variable name kept as "categoryColor" so the downstream
            // render code stays identical.
            val skillKey = btn.skillId
            val categoryColor = if (skillKey != null) JobColors.colorFor(skillKey)
                else CobblebaseScreen.CATEGORY_COLORS[btn.category] ?: 0xFF666666.toInt()
            val scale = 0.75f

            // Unified vertical slot \u2014 ALL chips render at the same size and Y
            // position. The previous active/available height difference made the
            // row "bulge" wherever the active chip sat. Differentiation is purely
            // color + content now.
            val chipTop = ry + 1
            val chipBot = chipTop + BTN_HEIGHT          // ry + 17 (fits in 18-px row)

            // Pick BG / border / text color per state. Same geometry across all.
            val isRelaxActive = isAutoBtn && btn.selected
            val isJobActive = btn.selected && !isAutoBtn

            val (bg, border, textColor) = when {
                isJobActive   -> Triple(categoryColor, 0xFFFFFFFF.toInt(), 0xFFFFFF)
                isRelaxActive -> Triple(0xFF5A4A2A.toInt(), 0xFFAA8844.toInt(), 0xFFCC88)
                hovered       -> Triple(0xFF3A3A4E.toInt(), 0xFF7777AA.toInt(), 0xDDDDDD)
                else          -> Triple(0xFF22222E.toInt(), 0xFF44445A.toInt(), 0x999999)
            }

            context.fill(rx, chipTop, rx + bw, chipBot, bg)
            context.drawHorizontalLine(rx, rx + bw - 1, chipTop, border)
            context.drawHorizontalLine(rx, rx + bw - 1, chipBot - 1, border)
            context.drawVerticalLine(rx, chipTop, chipBot - 1, border)
            context.drawVerticalLine(rx + bw - 1, chipTop, chipBot - 1, border)

            // Available (non-active job) chips get a thin category-tinted underline
            // as a quiet cue \u2014 keeps category info present without loud BG fills.
            if (!isJobActive && !isAutoBtn) {
                val stripe = (categoryColor and 0x00FFFFFF) or 0xCC000000.toInt()
                context.fill(rx + 1, chipBot - 2, rx + bw - 1, chipBot - 1, stripe)
            }

            val nameText = btn.displayName
            val nameWidth = (textRenderer.getWidth(nameText) * scale).toInt()

            if (isJobActive) {
                // Active: name top + stars bottom + job-icon corner.
                context.matrices.push()
                context.matrices.translate((rx + (bw - nameWidth) / 2).toFloat(), (chipTop + 2).toFloat(), 0f)
                context.matrices.scale(scale, scale, 1f)
                context.drawTextWithShadow(textRenderer, nameText, 0, 0, textColor)
                context.matrices.pop()

                if (btn.proficiency > 0) {
                    val stars = "\u2605".repeat(btn.proficiency) + "\u2606".repeat(5 - btn.proficiency)
                    val starColor = when {
                        btn.proficiency >= 5 -> 0xFFD700
                        btn.proficiency >= 4 -> 0xFFA500
                        btn.proficiency >= 3 -> 0x88CC88
                        else -> 0x888888
                    }
                    val starWidth = (textRenderer.getWidth(stars) * scale).toInt()
                    context.matrices.push()
                    context.matrices.translate((rx + (bw - starWidth) / 2).toFloat(), (chipTop + 9).toFloat(), 0f)
                    context.matrices.scale(scale, scale, 1f)
                    context.drawText(textRenderer, stars, 0, 0, starColor, false)
                    context.matrices.pop()

                    if (btn.skillId != null) {
                        val iconStack = JobIcons.stackFor(btn.skillId)
                        context.matrices.push()
                        context.matrices.translate((rx + 1).toFloat(), (chipTop + 8).toFloat(), 0f)
                        context.matrices.scale(0.4f, 0.4f, 1f)
                        context.drawItem(iconStack, 0, 0)
                        context.matrices.pop()
                    }
                }
            } else {
                // Available / inactive Relax: name centered vertically (single line,
                // no stars). 5 stars \u00d7 6 chips per row was pure visual noise.
                context.matrices.push()
                context.matrices.translate((rx + (bw - nameWidth) / 2).toFloat(), (chipTop + 5).toFloat(), 0f)
                context.matrices.scale(scale, scale, 1f)
                context.drawTextWithShadow(textRenderer, nameText, 0, 0, textColor)
                context.matrices.pop()
            }
        }

        // "Helps your Craftsman" label for supplier Mons (rendered where buttons would be)
        pokemonList.forEachIndexed { index, pokemonData ->
            if (pokemonData.pokemonId in supplierPokemonIds) {
                val rowH = rowHeights[index]
                val ry = contentY + rowOffsets[index] + scrollY
                if (ry < contentY - ROW_HEIGHT_LARGE || ry > contentBottom) return@forEachIndexed

                val overlayX = panelX + PANEL_PADDING + NAME_WIDTH + AURA_ICON_WIDTH
                // Solid background to fully hide where buttons would be
                context.fill(overlayX, ry, panelX + panelW - 2, ry + rowH - 1, 0xFF1E1E2E.toInt())
                // Orange accent bar
                context.fill(overlayX, ry, overlayX + 2, ry + rowH - 1, 0xFFFF9800.toInt())

                val label = "\u00A76Helps your Craftsman"
                val labelW = textRenderer.getWidth(label)
                val centerX = overlayX + (panelX + panelW - 2 - overlayX - labelW) / 2
                context.drawTextWithShadow(textRenderer, label, centerX, ry + rowH / 2 - 4, 0xFFAA00)
            }
        }

        context.disableScissor()

        // Scrollbar
        totalContentHeight = if (rowOffsets.isEmpty()) 0 else rowOffsets.last() + rowHeights.last()
        visibleHeight = contentBottom - contentY
        if (totalContentHeight > visibleHeight) {
            trackX = panelX + panelW - 8
            trackTop = contentY
            trackHeight = visibleHeight
            // Track background
            context.fill(trackX, trackTop, trackX + 6, trackTop + trackHeight, 0x44FFFFFF.toInt())
            // Thumb
            thumbHeight = (visibleHeight.toFloat() / totalContentHeight * trackHeight).toInt().coerceAtLeast(16)
            val scrollRange = totalContentHeight - visibleHeight
            val scrollProgress = (-scrollY).toFloat() / scrollRange.coerceAtLeast(1)
            thumbY = trackTop + ((trackHeight - thumbHeight) * scrollProgress).toInt()
            // Highlight when hovering or dragging
            val isHovered = mouseX in trackX..(trackX + 6) && mouseY in thumbY..(thumbY + thumbHeight)
            val thumbColor = if (isDraggingScrollbar || isHovered) 0xFFDDDDDD.toInt() else 0xFFAAAAAA.toInt()
            context.fill(trackX, thumbY, trackX + 6, thumbY + thumbHeight, thumbColor)
        }

        // Footer line
        context.fill(panelX, panelY + panelH - 18, panelX + panelW, panelY + panelH - 17, CobblebaseScreen.PANEL_BORDER)
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Sub-tab click takes priority over content clicks.
        for ((tab, box) in subTabBoxes) {
            val (bx, by, bw, bh) = box
            if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                activeSubTab = tab
                return true
            }
        }
        // Sidebar (expandable + checkbox) clicks for PokeWiki / My Pokemon sub-tabs.
        if (activeSubTab == SubTab.POKEWIKI || activeSubTab == SubTab.MY_POKEMON) {
            val isWiki = activeSubTab == SubTab.POKEWIKI
            val selectedSkills = if (isWiki) pokeWikiSelectedSkills else myPokemonSelectedSkills
            val expandedCats = if (isWiki) pokeWikiExpandedCats else myPokemonExpandedCats

            // Right-side grid scrollbar — claim the click before any sidebar/grid hit-test,
            // otherwise drag attempts on the thumb get intercepted by row clicks underneath.
            if (mainGridScrollbar.mouseClicked(mouseX, mouseY)) {
                if (isWiki) pokeWikiScroll = mainGridScrollbar.scroll
                else myPokemonScroll = mainGridScrollbar.scroll
                return true
            }

            // Sort controls (WorkerWiki only). Field button cycles Dex → Name → Prof → Skills → Rarity.
            // Direction button toggles ascending/descending independently.
            // Web DB button opens the live web species database in the user's default browser.
            if (isWiki) {
                run {
                    val (bx, by, bw, bh) = pokeWikiWebDbHitBox
                    if (bw > 0 && mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                        try {
                            net.minecraft.util.Util.getOperatingSystem().open(java.net.URI(POKEWIKI_WEB_DB_URL))
                        } catch (_: Exception) {}
                        return true
                    }
                }
                run {
                    val (bx, by, bw, bh) = pokeWikiSortFieldHitBox
                    if (bw > 0 && mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                        pokeWikiSortField = when (pokeWikiSortField) {
                            SortField.DEX -> SortField.NAME
                            SortField.NAME -> SortField.PROF
                            SortField.PROF -> SortField.SKILLS
                            SortField.SKILLS -> SortField.RARITY
                            SortField.RARITY -> SortField.DEX
                        }
                        return true
                    }
                }
                run {
                    val (bx, by, bw, bh) = pokeWikiSortDirHitBox
                    if (bw > 0 && mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                        pokeWikiSortDesc = !pokeWikiSortDesc
                        return true
                    }
                }
            }

            // Sidebar row clicks must fall inside the sidebar viewport — scrolled-out rows
            // have hit-boxes at off-screen Y but could still overlap with the title area.
            val inSidebarViewport = mouseY >= sidebarContentTop && mouseY <= sidebarContentBottom

            // "All" pseudo-row — clears selections.
            if (inSidebarViewport) {
                val (bx, by, bw, bh) = sidebarAllHitBox
                if (bw > 0 && mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                    selectedSkills.clear()
                    if (isWiki) pokeWikiScroll = 0 else myPokemonScroll = 0
                    return true
                }
            }
            // Skill rows first (more specific than category rows, may overlap).
            if (inSidebarViewport) for ((skillId, box) in sidebarSkillHitBoxes) {
                val (bx, by, bw, bh) = box
                if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                    if (skillId in selectedSkills) selectedSkills.remove(skillId) else selectedSkills.add(skillId)
                    if (isWiki) pokeWikiScroll = 0 else myPokemonScroll = 0
                    return true
                }
            }
            // Category rows — toggle expand.
            if (inSidebarViewport) for ((cat, box) in sidebarCategoryHitBoxes) {
                val (bx, by, bw, bh) = box
                if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                    if (cat in expandedCats) expandedCats.remove(cat) else expandedCats.add(cat)
                    return true
                }
            }
            return false
        }
        // Content interactions are only active on the Active Workers sub-tab.
        if (activeSubTab != SubTab.ACTIVE_WORKERS) return false

        // Check scrollbar click first
        if (totalContentHeight > visibleHeight &&
            mouseX >= trackX && mouseX <= trackX + 6 &&
            mouseY >= trackTop && mouseY <= trackTop + trackHeight
        ) {
            isDraggingScrollbar = true
            updateScrollFromMouse(mouseY)
            return true
        }

        val contentBottom = panelY + panelH - 18
        for (btn in allButtons) {
            // Skip clicks on supplier Mons — they're locked to Craftsman
            if (AssignmentCache.isCraftsmanSupplier(btn.pokemonId)) continue

            val rx = btn.baseX + scrollX
            val ry = btn.baseY + scrollY
            val bw = if (btn.skillId == null) AUTO_BTN_WIDTH else (chipWidthByPokemon[btn.pokemonId] ?: BTN_WIDTH)
            // Hit area matches the unified chip rectangle (chipTop = ry + 1,
            // height = BTN_HEIGHT).
            if (mouseX >= rx && mouseX <= rx + bw &&
                mouseY >= ry + 1 && mouseY <= ry + 1 + BTN_HEIGHT &&
                mouseY >= contentY && mouseY < contentBottom
            ) {
                selectSkill(btn.pokemonId, btn.skillId)
                return true
            }
        }
        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (isDraggingScrollbar) {
            updateScrollFromMouse(mouseY)
            return true
        }
        if (mainGridScrollbar.mouseDragged(mouseY)) {
            if (activeSubTab == SubTab.MY_POKEMON) myPokemonScroll = mainGridScrollbar.scroll
            else if (activeSubTab == SubTab.POKEWIKI) pokeWikiScroll = mainGridScrollbar.scroll
            return true
        }
        return false
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false
            return true
        }
        if (mainGridScrollbar.mouseReleased()) return true
        return false
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        // Sidebar takes scroll precedence when the mouse is over it.
        if (activeSubTab == SubTab.POKEWIKI || activeSubTab == SubTab.MY_POKEMON) {
            val sbX = panelX + 2
            val sbRight = sbX + SIDEBAR_W_SKILLS
            val inSidebar = mouseX in sbX.toDouble()..sbRight.toDouble() &&
                mouseY in sidebarContentTop.toDouble()..sidebarContentBottom.toDouble()
            if (inSidebar) {
                sidebarScroll = (sidebarScroll - verticalAmount.toInt() * 6).coerceAtLeast(0)
                return true
            }
        }
        if (activeSubTab == SubTab.POKEWIKI) {
            pokeWikiScroll = (pokeWikiScroll - verticalAmount.toInt() * 2).coerceAtLeast(0)
            return true
        }
        if (activeSubTab == SubTab.MY_POKEMON) {
            myPokemonScroll = (myPokemonScroll - verticalAmount.toInt() * 2).coerceAtLeast(0)
            return true
        }
        if (activeSubTab != SubTab.ACTIVE_WORKERS) return false
        if (net.minecraft.client.gui.screen.Screen.hasShiftDown()) {
            scrollX = (scrollX + verticalAmount.toInt() * 16).coerceAtMost(0)
        } else {
            scrollY = (scrollY + verticalAmount.toInt() * 16).coerceAtMost(0)
            clampScroll()
        }
        return true
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

    private fun selectSkill(pokemonId: UUID, skillId: String?) {
        for (btn in allButtons) {
            if (btn.pokemonId == pokemonId) {
                btn.selected = btn.skillId == skillId
            }
        }
        // Update local client-side cache so GUI shows correct state on re-init
        AssignmentCache.setAssignment(pokemonId, skillId)
        ClientPlayNetworking.send(SkillAssignmentC2SPacket(pokemonId, skillId ?: ""))
    }

    /**
     * For every Pokemon in this pasture, pick the highest-proficiency non-passive
     * job and assign it. Ties are broken by the first occurrence in the species'
     * skill list. Pokemon already at their best-prof skill stay unchanged.
     */
    private fun autoAssignAll() {
        for (pokemonData in pokemonList) {
            // Supplier-locked Pokemon are exempt — they're tethered to a Craftsman.
            if (AssignmentCache.isCraftsmanSupplier(pokemonData.pokemonId)) continue

            val speciesName = SpeciesSkillRegistry.resolveFormName(pokemonData.species.path, pokemonData.aspects)
            val speciesSkills = SpeciesSkillRegistry.getSkills(speciesName) ?: continue

            val best = speciesSkills.skills
                .filter { entry ->
                    val skillDef = SkillRegistry.get(entry.skillId) ?: return@filter false
                    !BaseManager.isBuffExecutor(skillDef.executor) && JobConfigOverrides.isEnabled(entry.skillId)
                }
                .maxByOrNull { it.proficiency }
                ?: continue

            if (AssignmentCache.getAssignment(pokemonData.pokemonId) != best.skillId) {
                selectSkill(pokemonData.pokemonId, best.skillId)
            }
        }
    }

    private fun getBuffEmoji(executor: String): String? {
        if (executor == "speed_boost") return "\u26A1"
        if (executor == "strength_boost") return "\uD83D\uDCAA"
        if (executor == "resistance_boost") return "\uD83D\uDEE1"
        if (executor == "night_vision") return "\uD83D\uDC41"
        if (executor == "water_breathing") return "\uD83E\uDEE7"
        if (executor == "jump_boost") return "\uD83E\uDD98"
        if (executor == "haste_boost") return "\u2692"
        if (executor == "saturation_boost") return "\uD83C\uDF56"
        if (executor == "lucky_charm") return "\uD83C\uDF1F"
        if (executor == "aura") return "\u2728"
        if (executor == "growth") return "\uD83C\uDF31"
        return null
    }

    private fun getMuteIcon(): String {
        val muted = !CobblebaseConfig.cryEnabled || CobblebaseConfig.cryVolume <= 0
        return if (muted) "\uD83D\uDD07" else "\uD83D\uDD0A"
    }

    /**
     * Returns the actual movement boundary for Pokemon in this pasture.
     * This is the safety teleport distance from CobblebaseConfig — Pokemon that
     * wander further get snapped back to the pasture. It's a global config
     * value (default 30) so the rendered box is the same for every pasture.
     *
     * Skill-level searchRadius values are NOT used here because they represent
     * work-detection range (e.g. Scout uses 100 blocks for map exploration),
     * not actual movement limits.
     */
    private fun computeMaxRadius(): Int {
        return CobblebaseConfig.safetyTeleportDistance
    }
}

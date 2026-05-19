package notlown.cobblebase.fabric.client.gui

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.item.Items
import net.minecraft.text.Text
import notlown.cobblebase.core.AdminJobDataCache
import notlown.cobblebase.core.JobConfigOverrides
import notlown.cobblebase.core.SpeciesSkillRegistry
import notlown.cobblebase.core.net.AdminJobsUpdateC2SPacket
import java.util.function.Function

/**
 * Admin GUI "Jobs" tab — sidebar + landing-grid + per-job detail view.
 *
 * **Sidebar (left)**: categories listed at the top level; clicking a category expands
 * it to show its individual jobs. Clicking a category itself = grid view filtered to
 * that category. Clicking a specific job = detail view for that job.
 *
 * **Landing (right pane, when no job selected)**: grid of every job as a clickable tile
 * showing name, category color, and on/off status. Lets admins see the full picture
 * at a glance instead of scrolling a long list.
 *
 * **Detail (right pane, when one job selected)**: three sub-tabs that put every setting
 * for that job in one place:
 *   - **Settings** — enable/disable, cooldown, search radius, every tuning field
 *   - **Loot** — loot-table summary for jobs that drop loot (link to dedicated Loot tab
 *     for full editing, plus the table list previewed inline)
 *   - **Stats** — top Pokemon by proficiency for this skill, total species that can use
 *     it, average proficiency
 */
class AdminJobsPanel(
    private val x: Int,
    private val y: Int,
    private val w: Int,
    private val h: Int,
    private val textRenderer: TextRenderer
) {
    private val PADDING = 4
    private val SIDEBAR_W = 110
    private val ROW_H = 14
    private val SCALE = 0.7f
    private val BUTTON_AREA_H = 18
    private val HEADER_H = 14
    private val SUBTAB_H = 12

    private val CATEGORY_COLORS = mapOf(
        "gathering" to 0xFF4CAF50.toInt(),
        "generation" to 0xFFFF9800.toInt(),
        "combat" to 0xFFF44336.toInt(),
        "support" to 0xFFE91E9E.toInt(),
        "utility" to 0xFF2196F3.toInt(),
        "legendary" to 0xFFFFD700.toInt(),
        "social" to 0xFFFF55FF.toInt()
    )

    private val FIELD_BG = 0xFF2A2A3E.toInt()
    private val FIELD_BORDER = 0xFF4A4A6E.toInt()
    private val FIELD_ACTIVE = 0xFF7A7ABE.toInt()
    private val SIDEBAR_BG = 0xCC15152A.toInt()
    private val ROW_HOVER = 0x22FFFFFF
    private val SEPARATOR = 0xFF3A3A5C.toInt()
    private val CHECKBOX_ON = 0xFF4CAF50.toInt()
    private val CHECKBOX_OFF = 0xFF555555.toInt()
    private val TILE_BG = 0xFF1F1F32.toInt()
    private val TILE_BG_HOVER = 0xFF2A2A4A.toInt()

    private fun jobIcon(skillId: String) = JobIcons.stackFor(skillId)

    private var saveButton: ButtonWidget? = null
    private var resetButton: ButtonWidget? = null
    private var backButton: ButtonWidget? = null

    private val jobEdits = mutableListOf<JobEditData>()
    private val categories = mutableListOf<String>()
    private val expandedCategories = mutableSetOf<String>()
    private var activeCategory: String = "all"

    private var scrollOffset = 0
    private var isDraggingScrollbar = false
    private val gridScrollbar = ScrollbarComponent(trackWidth = 4, minThumbHeight = 14)

    private var activeFieldJob: Int = -1
    private var activeFieldType: FieldType = FieldType.NONE
    private var activeTuningKey: String = ""
    private var fieldText = ""
    private var cursorBlink = 0

    // === New navigation state ===
    enum class ViewMode { GRID, DETAIL }
    enum class DetailTab { SETTINGS, LOOT, RECIPES, STATS }
    private var viewMode = ViewMode.GRID
    private var detailJobIdx = -1
    private var detailTab = DetailTab.SETTINGS

    // Hit-test boxes — recomputed each render.
    private var sidebarRows = listOf<SidebarRow>()
    private var gridTiles = listOf<GridTile>()
    private var subTabBoxes = mutableMapOf<DetailTab, IntArray>()
    private var enableToggleBox: IntArray = intArrayOf(0, 0, 0, 0)

    enum class FieldType { NONE, COOLDOWN, RADIUS, TUNING }

    data class JobEditData(
        val skillId: String,
        val displayName: String,
        val description: String,
        val category: String,
        val defaultCooldown: Long,
        val defaultRadius: Int,
        var cooldownSeconds: Long,
        var searchRadius: Int,
        var enabled: Boolean,
        val tuningFields: Map<String, notlown.cobblebase.core.TuningField> = emptyMap(),
        val tuningValues: MutableMap<String, Double> = mutableMapOf(),
        var dirty: Boolean = false
    )

    private sealed class SidebarRow {
        data class Category(val name: String, val expanded: Boolean) : SidebarRow()
        data class Job(val jobIdx: Int) : SidebarRow()
    }
    private data class GridTile(val jobIdx: Int, val x: Int, val y: Int, val w: Int, val h: Int)

    var pendingTooltip: List<String> = emptyList()
        private set
    var tooltipX: Int = 0
        private set
    var tooltipY: Int = 0
        private set

    fun rebuild() {
        jobEdits.clear()
        categories.clear()

        val allJobs = AdminJobDataCache.allJobs.values.sortedWith(
            compareBy({ it.category }, { it.name })
        )
        val overrides = AdminJobDataCache.jobOverrides

        for (job in allJobs) {
            val override = overrides[job.id]
            val declared = job.tuning ?: emptyMap()
            // Inject 5 synthetic prof-cooldown rows. These look like normal tuning fields
            // but use reserved keys `_prof{N}Cd` and represent the cooldown at each prof level.
            // Defaults come from the existing global formula so admins see the current values
            // and only need to edit ones they want to override.
            val synthetic = LinkedHashMap<String, notlown.cobblebase.core.TuningField>()
            for (prof in 1..5) {
                val key = "_prof${prof}Cd"
                val defaultCd = if (job.cooldownSeconds > 0) {
                    (job.cooldownSeconds * (6 - prof) / 3.0).coerceAtLeast(0.5)
                } else 1.0
                val stars = (1..5).joinToString("") { if (it <= prof) "★" else "☆" }
                synthetic[key] = notlown.cobblebase.core.TuningField(
                    label = "Prof $prof $stars",
                    defaultValue = defaultCd,
                    min = 0.1, max = 7200.0, step = 1.0,
                    unit = "s",
                    tooltip = "Cooldown in seconds at Proficiency $prof. Lower = faster job cycle. Default = base cooldown × (6 - prof) / 3."
                )
            }
            // Merge: declared fields first, then synthetic prof rows.
            val tuningFields = LinkedHashMap<String, notlown.cobblebase.core.TuningField>()
            tuningFields.putAll(declared)
            tuningFields.putAll(synthetic)
            val tuningValues = mutableMapOf<String, Double>()
            for ((key, field) in tuningFields) {
                tuningValues[key] = override?.tuning?.get(key) ?: field.defaultValue
            }
            jobEdits.add(JobEditData(
                skillId = job.id,
                displayName = job.name,
                description = job.description,
                category = job.category,
                defaultCooldown = job.cooldownSeconds,
                defaultRadius = job.searchRadius,
                cooldownSeconds = override?.cooldownSeconds ?: job.cooldownSeconds,
                searchRadius = override?.searchRadius ?: job.searchRadius,
                enabled = override?.enabled ?: true,
                tuningFields = tuningFields,
                tuningValues = tuningValues
            ))
        }

        val seen = linkedSetOf<String>()
        for (j in jobEdits) seen.add(j.category)
        categories.addAll(seen)

        scrollOffset = 0
        if (detailJobIdx >= jobEdits.size) {
            detailJobIdx = -1
            viewMode = ViewMode.GRID
        }
    }

    /** Stored widget-adder used for lazy-creation of embedded AdminLootPanel instances. */
    private var widgetAdder: Function<ClickableWidget, ClickableWidget>? = null
    /** Per-job embedded loot panels, keyed by base name (e.g. "mining"). */
    private val lootPanelCache = mutableMapOf<String, AdminLootPanel>()
    private var currentLootPanel: AdminLootPanel? = null

    fun init(addWidget: Function<ClickableWidget, ClickableWidget>) {
        widgetAdder = addWidget
        saveButton = ButtonWidget.builder(Text.literal("§2Save")) { commitActiveField(); saveAllChanges() }
            .dimensions(x + w - 86, y + h - 16, 40, 12).build()
        addWidget.apply(saveButton!!)

        resetButton = ButtonWidget.builder(Text.literal("§cReset")) { resetChanges() }
            .dimensions(x + w - 44, y + h - 16, 40, 12).build()
        addWidget.apply(resetButton!!)

        backButton = ButtonWidget.builder(Text.literal("§7← back")) {
            commitActiveField()
            viewMode = ViewMode.GRID
            detailJobIdx = -1
            setActiveLootPanel(null)
        }.dimensions(x + SIDEBAR_W + 6, y + h - 16, 50, 12).build()
        addWidget.apply(backButton!!)
    }

    /**
     * Lazy-creates (or reuses) the embedded AdminLootPanel for [baseName], initializes its
     * widgets if first time, and switches it to be the active one (hiding others' widgets).
     * Returns null if the job has no loot tables.
     */
    private fun getOrCreateLootPanel(baseName: String): AdminLootPanel? {
        val tables = notlown.cobblebase.core.AdminLootDataCache.tables.filter {
            val tb = it.id.removePrefix("cobblebase:")
            tb == baseName || tb.startsWith("${baseName}_")
        }
        if (tables.isEmpty()) return null
        return lootPanelCache.getOrPut(baseName) {
            val rightX = x + SIDEBAR_W + 2
            val rightW = w - SIDEBAR_W - 2
            val tabsY = y + PADDING + HEADER_H + 2
            val contentTop = tabsY + SUBTAB_H + 4
            val contentBottom = y + h - BUTTON_AREA_H - 2
            val panel = AdminLootPanel(
                rightX, contentTop, rightW, contentBottom - contentTop,
                textRenderer,
                lockedToBaseName = baseName
            )
            widgetAdder?.let { adder ->
                panel.init { w -> adder.apply(w) }
            }
            // Hide its widgets initially — they'll show only when Loot tab is active.
            panel.setWidgetsVisible(false)
            panel
        }
    }

    /** Marks one loot panel as the currently active one; hides widgets of any others. */
    private fun setActiveLootPanel(panel: AdminLootPanel?) {
        if (currentLootPanel === panel) return
        currentLootPanel?.setWidgetsVisible(false)
        currentLootPanel = panel
        panel?.setWidgetsVisible(true)
    }

    // ---- Rendering ----

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        pendingTooltip = emptyList()
        cursorBlink++

        // Background
        context.fill(x, y, x + w, y + h, 0xCC1E1E2E.toInt())

        // Sidebar
        context.fill(x, y, x + SIDEBAR_W, y + h, SIDEBAR_BG)
        context.fill(x + SIDEBAR_W, y, x + SIDEBAR_W + 1, y + h, SEPARATOR)
        renderSidebar(context, mouseX, mouseY)

        // Right pane content
        val rightX = x + SIDEBAR_W + 2
        val rightW = w - SIDEBAR_W - 2

        when (viewMode) {
            ViewMode.GRID -> renderGrid(context, mouseX, mouseY, rightX, rightW)
            ViewMode.DETAIL -> renderDetail(context, mouseX, mouseY, delta, rightX, rightW)
        }

        // Back button visibility — only show in DETAIL mode
        backButton?.visible = viewMode == ViewMode.DETAIL
        // Hide our own Save/Reset when the embedded loot panel is active — it has its own.
        val inLootTab = viewMode == ViewMode.DETAIL && detailTab == DetailTab.LOOT
        saveButton?.visible = !inLootTab
        resetButton?.visible = !inLootTab
        // Safety net: if for any reason a loot panel's widgets are still showing but we're
        // NOT on the loot sub-tab, hide them. Catches paths that mutated viewMode/detailTab
        // without explicitly calling setActiveLootPanel(null).
        if (!inLootTab && currentLootPanel != null) setActiveLootPanel(null)

        // Unsaved indicator
        if (jobEdits.any { it.dirty }) {
            saveButton?.let { sb ->
                drawScaledText(context, "§e*unsaved", sb.x - 50, sb.y + 3, 0xFFFF00)
            }
        }
    }

    private fun renderSidebar(context: DrawContext, mouseX: Int, mouseY: Int) {
        drawScaledText(context, "§f§lCategories", x + PADDING, y + PADDING, 0xFFFFFF)

        val rows = mutableListOf<SidebarRow>()
        // "All Jobs" pseudo-category at top
        rows.add(SidebarRow.Category("all", false))
        for (cat in categories) {
            rows.add(SidebarRow.Category(cat, cat in expandedCategories))
            if (cat in expandedCategories) {
                for ((idx, job) in jobEdits.withIndex()) {
                    if (job.category == cat) rows.add(SidebarRow.Job(idx))
                }
            }
        }
        sidebarRows = rows

        var sy = y + PADDING + 11
        for (row in rows) {
            if (sy > y + h - ROW_H - BUTTON_AREA_H) break
            val isHovered = mouseX in x..(x + SIDEBAR_W) && mouseY in sy..(sy + ROW_H)
            when (row) {
                is SidebarRow.Category -> {
                    val isActive = activeCategory == row.name && viewMode == ViewMode.GRID
                    if (isActive) context.fill(x + 1, sy, x + SIDEBAR_W, sy + ROW_H, 0x442196F3)
                    else if (isHovered) context.fill(x + 1, sy, x + SIDEBAR_W, sy + ROW_H, ROW_HOVER)

                    // Expand chevron
                    val chevron = if (row.name == "all") " " else if (row.expanded) "▼" else "▶"
                    drawScaledText(context, "§7$chevron", x + 4, sy + 3, 0x888888)

                    val color = CATEGORY_COLORS[row.name] ?: 0xFFAAAAAA.toInt()
                    if (row.name != "all") context.fill(x + 12, sy + 5, x + 16, sy + 9, color)

                    val label = if (row.name == "all") "All Jobs" else row.name.replaceFirstChar { it.uppercase() }
                    val count = if (row.name == "all") jobEdits.size else jobEdits.count { it.category == row.name }
                    val labelColor = if (isActive) 0xFFFFFF else 0xCCCCCC
                    val labelX = if (row.name == "all") x + 12 else x + 19
                    drawScaledText(context, "§f$label §8($count)", labelX, sy + 3, labelColor)
                }
                is SidebarRow.Job -> {
                    val isActive = viewMode == ViewMode.DETAIL && detailJobIdx == row.jobIdx
                    if (isActive) context.fill(x + 1, sy, x + SIDEBAR_W, sy + ROW_H, 0x44FFB300)
                    else if (isHovered) context.fill(x + 1, sy, x + SIDEBAR_W, sy + ROW_H, ROW_HOVER)

                    val job = jobEdits[row.jobIdx]
                    val color = CATEGORY_COLORS[job.category] ?: 0xFFAAAAAA.toInt()
                    context.fill(x + 16, sy + 5, x + 18, sy + 9, color)
                    val dim = if (!job.enabled) "§8" else "§f"
                    drawScaledText(context, "$dim${job.displayName}", x + 22, sy + 3, if (job.enabled) 0xFFFFFF else 0x666666)
                }
            }
            sy += ROW_H
        }
    }

    private fun renderGrid(context: DrawContext, mouseX: Int, mouseY: Int, rightX: Int, rightW: Int) {
        drawScaledText(context, "§f§lJobs Overview", rightX + PADDING, y + PADDING, 0xFFFFFF)
        val subtitleLabel = if (activeCategory == "all") "All ${jobEdits.size} jobs"
            else "${jobEdits.count { it.category == activeCategory }} in ${activeCategory.replaceFirstChar { it.uppercase() }}"
        drawScaledText(context, "§7Click a tile to edit. §8($subtitleLabel)", rightX + PADDING + 84, y + PADDING, 0xAAAAAA)

        val gridTop = y + PADDING + HEADER_H + 2
        val gridBottom = y + h - BUTTON_AREA_H - 2
        val gridLeft = rightX + PADDING
        val gridRight = rightX + rightW - PADDING
        val gridW = gridRight - gridLeft

        // Tile size — 4 columns wide.
        val cols = 4
        val tileW = (gridW - (cols - 1) * 4) / cols
        val tileH = 36

        val visible = jobEdits.withIndex().filter { activeCategory == "all" || it.value.category == activeCategory }
        val tiles = mutableListOf<GridTile>()

        for ((i, indexed) in visible.withIndex()) {
            val (jobIdx, job) = indexed
            val col = i % cols
            val rowI = i / cols
            val tx = gridLeft + col * (tileW + 4)
            val ty = gridTop + rowI * (tileH + 4) + scrollOffset
            if (ty + tileH < gridTop || ty > gridBottom) {
                tiles.add(GridTile(jobIdx, tx, ty, tileW, tileH))
                continue
            }
            renderJobTile(context, mouseX, mouseY, jobIdx, job, tx, ty, tileW, tileH)
            tiles.add(GridTile(jobIdx, tx, ty, tileW, tileH))
        }
        gridTiles = tiles

        // Scrollbar for grid if it overflows.
        // AdminJobsPanel uses NEGATIVE scrollOffset (0 = top, -N = scrolled down); the
        // component expects positive values, so we feed -scrollOffset and read back -scroll.
        val rowsTotal = (visible.size + cols - 1) / cols
        val totalH = rowsTotal * (tileH + 4)
        val visibleH = gridBottom - gridTop
        gridScrollbar.layout(
            trackX = rightX + rightW - 4,
            trackY = gridTop,
            trackHeight = visibleH,
            contentHeight = totalH,
            viewportHeight = visibleH,
            currentScroll = -scrollOffset,
        )
        gridScrollbar.render(context, mouseX, mouseY)
        scrollOffset = -gridScrollbar.scroll
    }

    private fun renderJobTile(
        context: DrawContext, mouseX: Int, mouseY: Int,
        jobIdx: Int, job: JobEditData,
        tx: Int, ty: Int, tw: Int, th: Int
    ) {
        val hovered = mouseX in tx..(tx + tw) && mouseY in ty..(ty + th)
        val bg = if (hovered) TILE_BG_HOVER else TILE_BG
        context.fill(tx, ty, tx + tw, ty + th, bg)
        val cat = CATEGORY_COLORS[job.category] ?: 0xFFAAAAAA.toInt()
        context.fill(tx, ty, tx + tw, ty + 2, cat)

        // Thematic item icon on the left side of the tile (16x16 native).
        context.drawItem(jobIcon(job.skillId), tx + 4, ty + 6)

        val nameColor = if (job.enabled) 0xFFFFFF else 0x666666
        drawScaledText(context, "§f§l${job.displayName}", tx + 24, ty + 6, nameColor)

        // Category + on/off badges
        val catLabel = job.category.replaceFirstChar { it.uppercase() }
        drawScaledText(context, "§7$catLabel", tx + 24, ty + 17, 0xAAAAAA)

        val status = if (job.enabled) "§a● ON" else "§c● OFF"
        val statusW = (textRenderer.getWidth(status) * 0.65f).toInt()
        context.matrices.push()
        context.matrices.translate((tx + tw - statusW - 4).toFloat(), (ty + 17).toFloat(), 0f)
        context.matrices.scale(0.65f, 0.65f, 1f)
        context.drawTextWithShadow(textRenderer, status, 0, 0, 0xFFFFFF)
        context.matrices.pop()

        // Override indicator (small, below the row to avoid overlap with icon)
        val hasOverride = job.cooldownSeconds != job.defaultCooldown ||
                job.searchRadius != job.defaultRadius || !job.enabled ||
                job.tuningFields.any { (k, f) -> (job.tuningValues[k] ?: f.defaultValue) != f.defaultValue }
        if (hasOverride) drawScaledText(context, "§6• custom", tx + 24, ty + 26, 0xFF9800)
    }

    private fun renderDetail(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float, rightX: Int, rightW: Int) {
        if (detailJobIdx !in jobEdits.indices) {
            viewMode = ViewMode.GRID
            return
        }
        val job = jobEdits[detailJobIdx]

        // Job title with category color stripe — use textRenderer.getWidth on the bolded
        // string so getWidth includes the +1px-per-char that bold rendering adds; otherwise
        // the subtitle ran into the name with no gap. Also bumped the visual gap from 8 → 12.
        val cat = CATEGORY_COLORS[job.category] ?: 0xFFAAAAAA.toInt()
        context.fill(rightX + PADDING, y + PADDING, rightX + PADDING + 3, y + PADDING + 11, cat)
        // Job item icon next to the detail header — same vocabulary as the grid tile
        // and Pasture skill chips.
        context.matrices.push()
        context.matrices.translate((rightX + PADDING + 8).toFloat(), (y + PADDING - 3).toFloat(), 0f)
        context.matrices.scale(0.7f, 0.7f, 1f)
        context.drawItem(jobIcon(job.skillId), 0, 0)
        context.matrices.pop()
        val boldName = "§f§l${job.displayName}"
        drawScaledText(context, boldName, rightX + PADDING + 22, y + PADDING + 1, 0xFFFFFF)
        val subtitle = "§7${job.category.replaceFirstChar { it.uppercase() }} job"
        val nameWidth = (textRenderer.getWidth(boldName) * SCALE).toInt()
        val subX = rightX + PADDING + 22 + nameWidth + 12
        drawScaledText(context, subtitle, subX, y + PADDING + 1, 0xAAAAAA)

        // Sub-tab bar
        val tabsY = y + PADDING + HEADER_H + 2
        renderSubTabs(context, mouseX, mouseY, rightX + PADDING, tabsY, rightW - PADDING * 2)
        // Job Enable/Disable toggle — large pill on the right at the same Y as sub-tabs.
        renderEnableToggle(context, mouseX, mouseY, job, rightX, rightW, tabsY)

        // Sub-tab content area
        val contentTop = tabsY + SUBTAB_H + 4
        val contentBottom = y + h - BUTTON_AREA_H - 2
        when (detailTab) {
            DetailTab.SETTINGS -> renderSettings(context, mouseX, mouseY, job, detailJobIdx, rightX, rightW, contentTop, contentBottom)
            DetailTab.LOOT -> renderLoot(context, mouseX, mouseY, delta, job, rightX, rightW, contentTop, contentBottom)
            DetailTab.RECIPES -> renderRecipes(context, mouseX, mouseY, rightX, rightW, contentTop, contentBottom)
            DetailTab.STATS -> renderStats(context, job, rightX, rightW, contentTop, contentBottom)
        }
    }

    /** Big "Job Enable / Disable" toggle in the detail header, right-aligned with sub-tabs. */
    private fun renderEnableToggle(
        context: DrawContext, mouseX: Int, mouseY: Int,
        job: JobEditData, rightX: Int, rightW: Int, tabsY: Int
    ) {
        val w0 = 80
        val h0 = SUBTAB_H
        val tx = rightX + rightW - PADDING - w0
        val ty = tabsY
        enableToggleBox = intArrayOf(tx, ty, w0, h0)
        val hovered = mouseX in tx..(tx + w0) && mouseY in ty..(ty + h0)
        val bg = if (job.enabled) {
            if (hovered) 0xFF2E5E33.toInt() else 0xFF1F4022.toInt()
        } else {
            if (hovered) 0xFF5E2E2E.toInt() else 0xFF402020.toInt()
        }
        val accent = if (job.enabled) 0xFF4CAF50.toInt() else 0xFFD32F2F.toInt()
        context.fill(tx, ty, tx + w0, ty + h0, bg)
        context.fill(tx, ty + h0 - 1, tx + w0, ty + h0, accent)
        val label = if (job.enabled) "§a✓ Job Enabled" else "§c✗ Job Disabled"
        val labelScale = 0.75f
        val labelW = (textRenderer.getWidth(label) * labelScale).toInt()
        context.matrices.push()
        context.matrices.translate((tx + (w0 - labelW) / 2).toFloat(), (ty + 3).toFloat(), 0f)
        context.matrices.scale(labelScale, labelScale, 1f)
        context.drawTextWithShadow(textRenderer, label, 0, 0, 0xFFFFFF)
        context.matrices.pop()
    }

    private fun renderSubTabs(context: DrawContext, mouseX: Int, mouseY: Int, x0: Int, y0: Int, totalW: Int) {
        val tabW = 46
        val gap = 3
        val labelScale = 0.75f
        // Recipes sub-tab is Craftsman-only (the only job with a per-recipe enable/disable layer).
        val isCraftsman = detailJobIdx in jobEdits.indices &&
            jobEdits[detailJobIdx].skillId == "cobblebase:craftsman"
        val tabs = buildList {
            add(DetailTab.SETTINGS to "Settings")
            add(DetailTab.LOOT to "Loot")
            if (isCraftsman) add(DetailTab.RECIPES to "Recipes")
            add(DetailTab.STATS to "Stats")
        }
        subTabBoxes.clear()
        for ((i, pair) in tabs.withIndex()) {
            val (tab, label) = pair
            val tx = x0 + i * (tabW + gap)
            val active = detailTab == tab
            val hovered = mouseX in tx..(tx + tabW) && mouseY in y0..(y0 + SUBTAB_H)
            val accent = when (tab) {
                DetailTab.SETTINGS -> 0xFF4CAF50.toInt()
                DetailTab.LOOT -> 0xFFFF9800.toInt()
                DetailTab.RECIPES -> 0xFFE91E63.toInt()
                DetailTab.STATS -> 0xFF2196F3.toInt()
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
            context.matrices.translate((tx + (tabW - labelW) / 2).toFloat(), (y0 + 3).toFloat(), 0f)
            context.matrices.scale(labelScale, labelScale, 1f)
            context.drawTextWithShadow(textRenderer, label, 0, 0, labelColor)
            context.matrices.pop()
            subTabBoxes[tab] = intArrayOf(tx, y0, tabW, SUBTAB_H)
        }
    }

    /** Settings sub-tab — cooldown / radius / enabled / tuning sliders (existing functionality). */
    private fun renderSettings(
        context: DrawContext, mouseX: Int, mouseY: Int,
        job: JobEditData, jobIdx: Int,
        rightX: Int, rightW: Int, contentTop: Int, contentBottom: Int
    ) {
        // Description block at top
        if (job.description.isNotBlank()) {
            drawScaledText(context, "§7${job.description}", rightX + PADDING, contentTop, 0xCCCCCC)
        }

        // Settings card
        val cardY = contentTop + 14
        context.fill(rightX + PADDING, cardY, rightX + rightW - PADDING, cardY + 1, SEPARATOR)
        drawScaledText(context, "§e§lConfiguration", rightX + PADDING + 4, cardY + 4, 0xFFD700)

        // 3 main fields on one line: cooldown, radius, enabled
        val rowY = cardY + 16
        val labelColor = 0xAAAAAA
        val fieldW = 50
        val fieldH = 12

        // Cooldown
        drawScaledText(context, "§7Cooldown (s):", rightX + PADDING + 4, rowY + 2, labelColor)
        val cdX = rightX + PADDING + 140
        if (job.skillId == "cobblebase:producer") {
            drawScaledText(context, "§8per-species (managed per Pokemon)", cdX + 2, rowY + 2, 0x666666)
        } else {
            renderField(context, cdX, rowY, fieldW, fieldH,
                if (activeFieldJob == jobIdx && activeFieldType == FieldType.COOLDOWN) fieldText else job.cooldownSeconds.toString(),
                isActive = activeFieldJob == jobIdx && activeFieldType == FieldType.COOLDOWN,
                isOverride = job.cooldownSeconds != job.defaultCooldown
            )
            drawScaledText(context, "§8default ${job.defaultCooldown}", cdX + fieldW + 6, rowY + 2, 0x666666)
        }

        // Radius
        val radRowY = rowY + 16
        drawScaledText(context, "§7Radius (blocks):", rightX + PADDING + 4, radRowY + 2, labelColor)
        renderField(context, cdX, radRowY, fieldW, fieldH,
            if (activeFieldJob == jobIdx && activeFieldType == FieldType.RADIUS) fieldText else job.searchRadius.toString(),
            isActive = activeFieldJob == jobIdx && activeFieldType == FieldType.RADIUS,
            isOverride = job.searchRadius != job.defaultRadius
        )
        drawScaledText(context, "§8default ${job.defaultRadius}", cdX + fieldW + 6, radRowY + 2, 0x666666)

        // Enabled toggle now lives at the detail header (renderEnableToggle), not here.
        // Tuning fields section
        if (job.tuningFields.isNotEmpty()) {
            val tuningTop = radRowY + 18
            context.fill(rightX + PADDING, tuningTop, rightX + rightW - PADDING, tuningTop + 1, SEPARATOR)
            drawScaledText(context, "§e§lTuning", rightX + PADDING + 4, tuningTop + 4, 0xFFD700)

            var ty = tuningTop + 16
            for ((key, field) in job.tuningFields) {
                if (ty + ROW_H > contentBottom) break
                renderTuningRowDetail(context, mouseX, mouseY, jobIdx, key, field, rightX, rightW, ty, fieldW, fieldH)
                ty += 16
            }
        }
    }

    private fun renderTuningRowDetail(
        context: DrawContext, mouseX: Int, mouseY: Int,
        jobIdx: Int, key: String,
        tField: notlown.cobblebase.core.TuningField,
        rightX: Int, rightW: Int, rowY: Int, fieldW: Int, fieldH: Int
    ) {
        val job = jobEdits[jobIdx]
        val value = job.tuningValues[key] ?: tField.defaultValue
        // Field column must match handleSettingsClick (cdX = rightX + PADDING + 140), otherwise
        // clicks land offset from what's drawn.
        val cdX = rightX + PADDING + 140

        val labelText = if (tField.unit != null) "§7${tField.label}: §8(${tField.unit})" else "§7${tField.label}"
        drawScaledText(context, labelText, rightX + PADDING + 4, rowY + 2, 0xAAAAAA)

        if (tField.tooltip != null && mouseX in (rightX + PADDING)..(cdX - 4) && mouseY in rowY..(rowY + 14)) {
            val tip = mutableListOf("§f${tField.label}")
            for (line in tField.tooltip!!.split("\n")) tip.add(if (line.startsWith("§")) line else "§7$line")
            tip.add("§8Default: §f${formatTuning(tField.defaultValue, tField.step)}   §8Range: §f${formatTuning(tField.min, tField.step)} - ${formatTuning(tField.max, tField.step)}")
            pendingTooltip = tip
            tooltipX = mouseX; tooltipY = mouseY
        }

        val isActiveField = activeFieldJob == jobIdx && activeFieldType == FieldType.TUNING && activeTuningKey == key
        val isOverride = value != tField.defaultValue
        renderField(context, cdX, rowY, fieldW, fieldH,
            if (isActiveField) fieldText else formatTuning(value, tField.step),
            isActive = isActiveField,
            isOverride = isOverride
        )
        drawScaledText(context, "§8default ${formatTuning(tField.defaultValue, tField.step)}", cdX + fieldW + 6, rowY + 2, 0x666666)
    }

    /** Loot sub-tab — embeds the full AdminLootPanel locked to this job. */
    private fun renderLoot(
        context: DrawContext, mouseX: Int, mouseY: Int, delta: Float,
        job: JobEditData,
        rightX: Int, rightW: Int, contentTop: Int, contentBottom: Int
    ) {
        val baseName = job.skillId.removePrefix("cobblebase:")
        val panel = getOrCreateLootPanel(baseName)
        if (panel == null) {
            drawScaledText(context, "§8This job doesn't drop loot.", rightX + PADDING + 4, contentTop + 4, 0x888888)
            drawScaledText(context, "§8Only jobs like Mining, Fishing, Diving, Finder-* etc. have loot tables.", rightX + PADDING + 4, contentTop + 16, 0x666666)
            return
        }
        setActiveLootPanel(panel)
        panel.render(context, mouseX, mouseY, delta)
        // Forward tooltip request from inner panel to our own slot so the screen renders it.
        if (panel.pendingTooltip.isNotEmpty()) {
            pendingTooltip = panel.pendingTooltip
            tooltipX = panel.tooltipX
            tooltipY = panel.tooltipY
        }
    }

    // ---- Recipes sub-tab (Craftsman only) ----

    /** Currently selected category in the Recipes sub-tab. Null = pick the first available. */
    private var recipesSelectedCategory: String? = null
    /** Whether we already asked the server for the recipe list this session. Reset on tab open. */
    private var recipesRequestSent: Boolean = false
    /** Scrollbar for the right-pane recipe list. */
    private val recipesScrollbar = ScrollbarComponent(trackWidth = 4, minThumbHeight = 12)
    /** Vertical scroll offset for the right-pane recipe list (pixels). */
    private var recipesScroll: Int = 0
    /** Hit-box per category sidebar row. */
    private val recipesCategoryHitBoxes = linkedMapOf<String, IntArray>()
    /** Hit-box per recipe-row toggle button. Keyed by recipeId. */
    private val recipesToggleHitBoxes = mutableMapOf<String, IntArray>()
    /** Hit-box for the bulk Enable-All / Disable-All category buttons. */
    private var recipesEnableAllHitBox = intArrayOf(0, 0, 0, 0)
    private var recipesDisableAllHitBox = intArrayOf(0, 0, 0, 0)

    /** Recipes sub-tab — category sidebar on the left + per-recipe toggle list on the right. */
    private fun renderRecipes(
        context: DrawContext, mouseX: Int, mouseY: Int,
        rightX: Int, rightW: Int, contentTop: Int, contentBottom: Int
    ) {
        // Fire the request once on first render. Server replies with the full list +
        // disabled snapshot, populating AdminRecipesCache. Re-requests on next tab switch
        // are debounced by the recipesRequestSent flag.
        if (!recipesRequestSent) {
            try {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
                    .send(notlown.cobblebase.core.net.AdminRecipesRequestC2SPacket)
                recipesRequestSent = true
            } catch (_: Throwable) {}
        }

        val recipes = notlown.cobblebase.core.AdminRecipesCache.getAll()
        if (recipes.isEmpty()) {
            drawScaledText(context, "§8Loading recipes…", rightX + PADDING + 4, contentTop + 6, 0x888888)
            return
        }
        val categories = notlown.cobblebase.core.AdminRecipesCache.getCategories()
        if (recipesSelectedCategory == null || recipesSelectedCategory !in categories) {
            recipesSelectedCategory = categories.firstOrNull()
        }

        val sidebarW = 110
        val sidebarX = rightX + PADDING
        val sidebarTop = contentTop + 4
        val rowH = 12

        // ---- Sidebar: categories ----
        recipesCategoryHitBoxes.clear()
        var cy = sidebarTop
        drawScaledText(context, "§e§lCategories", sidebarX, cy, 0xFFD700)
        cy += 12
        for (cat in categories) {
            val catRecipes = notlown.cobblebase.core.AdminRecipesCache.byCategory(cat)
            val enabledCount = catRecipes.count { notlown.cobblebase.core.AdminRecipesCache.isEnabled(it.recipeId) }
            val total = catRecipes.size
            val active = cat == recipesSelectedCategory
            val hovered = mouseX in sidebarX..(sidebarX + sidebarW) && mouseY in cy..(cy + rowH)
            val rowBg = when {
                active -> 0xFF2A2A4A.toInt()
                hovered -> 0xFF1F1F33.toInt()
                else -> 0
            }
            if (rowBg != 0) context.fill(sidebarX, cy, sidebarX + sidebarW, cy + rowH, rowBg)
            // Accent line on the active row
            if (active) context.fill(sidebarX, cy, sidebarX + 2, cy + rowH, 0xFFE91E63.toInt())
            // Label color: dimmed when no recipe in the category is enabled
            val labelColor = when {
                enabledCount == 0 -> 0x666666     // fully disabled
                enabledCount == total -> 0xFFFFFF  // fully enabled
                else -> 0xCCCCCC                   // mixed
            }
            drawScaledText(context, cat, sidebarX + 6, cy + 2, labelColor)
            drawScaledText(context, "§7$enabledCount/$total", sidebarX + sidebarW - 28, cy + 2, 0x888888)
            recipesCategoryHitBoxes[cat] = intArrayOf(sidebarX, cy, sidebarW, rowH)
            cy += rowH
        }

        // ---- Right pane: recipes of selected category ----
        val rightPaneX = sidebarX + sidebarW + 6
        val rightPaneW = rightX + rightW - rightPaneX - PADDING
        val selectedCat = recipesSelectedCategory ?: return
        val catRecipes = notlown.cobblebase.core.AdminRecipesCache.byCategory(selectedCat)
            .sortedBy { it.outputDisplayName }

        drawScaledText(context, "§e§l$selectedCat", rightPaneX, contentTop + 4, 0xFFD700)
        val totalInCat = catRecipes.size
        val enabledInCat = catRecipes.count { notlown.cobblebase.core.AdminRecipesCache.isEnabled(it.recipeId) }
        drawScaledText(context, "§7$enabledInCat / $totalInCat enabled", rightPaneX + 90, contentTop + 4, 0x888888)

        // Bulk Enable-All / Disable-All buttons (under the title, above the list).
        val bulkY = contentTop + 16
        val bulkBtnH = 12
        val bulkBtnW = 70
        val enableX = rightPaneX
        val disableX = enableX + bulkBtnW + 4
        val enableHov = mouseX in enableX..(enableX + bulkBtnW) && mouseY in bulkY..(bulkY + bulkBtnH)
        val disableHov = mouseX in disableX..(disableX + bulkBtnW) && mouseY in bulkY..(bulkY + bulkBtnH)
        context.fill(enableX, bulkY, enableX + bulkBtnW, bulkY + bulkBtnH,
            if (enableHov) 0xFF2E5E33.toInt() else 0xFF1F4022.toInt())
        context.fill(enableX, bulkY + bulkBtnH - 1, enableX + bulkBtnW, bulkY + bulkBtnH, 0xFF4CAF50.toInt())
        drawScaledText(context, "§a✓ Enable All", enableX + 6, bulkY + 3, 0xFFFFFF)
        recipesEnableAllHitBox = intArrayOf(enableX, bulkY, bulkBtnW, bulkBtnH)

        context.fill(disableX, bulkY, disableX + bulkBtnW, bulkY + bulkBtnH,
            if (disableHov) 0xFF5E2E2E.toInt() else 0xFF402020.toInt())
        context.fill(disableX, bulkY + bulkBtnH - 1, disableX + bulkBtnW, bulkY + bulkBtnH, 0xFFD32F2F.toInt())
        drawScaledText(context, "§c✗ Disable All", disableX + 6, bulkY + 3, 0xFFFFFF)
        recipesDisableAllHitBox = intArrayOf(disableX, bulkY, bulkBtnW, bulkBtnH)

        // Recipe list (scrollable)
        val listTop = bulkY + bulkBtnH + 6
        val listBottom = contentBottom - 4
        val listH = listBottom - listTop
        val recipeRowH = 14
        val contentHeight = catRecipes.size * recipeRowH

        recipesScrollbar.layout(
            trackX = rightPaneX + rightPaneW - 6,
            trackY = listTop,
            trackHeight = listH,
            contentHeight = contentHeight,
            viewportHeight = listH,
            currentScroll = recipesScroll,
        )

        context.enableScissor(rightPaneX, listTop, rightPaneX + rightPaneW - 8, listBottom)
        recipesToggleHitBoxes.clear()
        for ((i, r) in catRecipes.withIndex()) {
            val ry = listTop + i * recipeRowH - recipesScroll
            if (ry + recipeRowH < listTop || ry > listBottom) continue
            val enabled = notlown.cobblebase.core.AdminRecipesCache.isEnabled(r.recipeId)
            // Row background (zebra)
            val rowBg = if (i % 2 == 0) 0xFF14141E.toInt() else 0xFF18181F.toInt()
            context.fill(rightPaneX, ry, rightPaneX + rightPaneW - 8, ry + recipeRowH, rowBg)
            // Output item icon — render via the registries lookup
            val itemId = try { net.minecraft.util.Identifier.of(r.outputItemId) } catch (_: Throwable) { null }
            val itemStack = itemId?.let { net.minecraft.item.ItemStack(net.minecraft.registry.Registries.ITEM.get(it)) }
            if (itemStack != null && !itemStack.isEmpty) {
                context.drawItem(itemStack, rightPaneX + 4, ry - 1)
            }
            // Display name, dimmed when disabled
            val nameColor = if (enabled) 0xFFFFFF else 0x777777
            val namePrefix = if (enabled) "§f" else "§7§o"
            drawScaledText(context, "$namePrefix${r.outputDisplayName}", rightPaneX + 22, ry + 3, nameColor)
            // Toggle button on the right
            val toggleW = 56
            val toggleH = 10
            val toggleX = rightPaneX + rightPaneW - toggleW - 14
            val toggleY = ry + (recipeRowH - toggleH) / 2
            val toggleHov = mouseX in toggleX..(toggleX + toggleW) && mouseY in toggleY..(toggleY + toggleH)
            val toggleBg = when {
                enabled && toggleHov -> 0xFF2E5E33.toInt()
                enabled -> 0xFF1F4022.toInt()
                toggleHov -> 0xFF5E2E2E.toInt()
                else -> 0xFF402020.toInt()
            }
            context.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, toggleBg)
            val accent = if (enabled) 0xFF4CAF50.toInt() else 0xFFD32F2F.toInt()
            context.fill(toggleX, toggleY + toggleH - 1, toggleX + toggleW, toggleY + toggleH, accent)
            val label = if (enabled) "§a✓ Enabled" else "§c✗ Disabled"
            drawScaledText(context, label, toggleX + 4, toggleY + 2, 0xFFFFFF)
            recipesToggleHitBoxes[r.recipeId] = intArrayOf(toggleX, toggleY, toggleW, toggleH)
        }
        context.disableScissor()
        recipesScrollbar.render(context, mouseX, mouseY)
        recipesScroll = recipesScrollbar.scroll
    }

    /** Sends a partial recipe-override update to the server + optimistically applies on the client. */
    private fun submitRecipeChanges(changes: Map<String, Boolean>) {
        if (changes.isEmpty()) return
        notlown.cobblebase.core.AdminRecipesCache.setLocalMany(
            changes.filterValues { !it }.keys, false
        )
        notlown.cobblebase.core.AdminRecipesCache.setLocalMany(
            changes.filterValues { it }.keys, true
        )
        try {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                notlown.cobblebase.core.net.RecipeOverridesUpdateC2SPacket(changes)
            )
        } catch (_: Throwable) {}
    }

    /** Stats sub-tab — top Pokemon by proficiency, total species count. */
    private fun renderStats(
        context: DrawContext, job: JobEditData,
        rightX: Int, rightW: Int, contentTop: Int, contentBottom: Int
    ) {
        val skillId = job.skillId
        val allSpecies = SpeciesSkillRegistry.getAllAssigned()
        data class Entry(val species: String, val prof: Int)
        val matching = allSpecies.mapNotNull { (sp, data) ->
            data.skills.find { it.skillId == skillId }?.let { Entry(sp, it.proficiency) }
        }

        if (matching.isEmpty()) {
            drawScaledText(context, "§8No species can use this skill yet.", rightX + PADDING + 4, contentTop + 4, 0x888888)
            return
        }

        val total = matching.size
        val avgProf = matching.map { it.prof }.average()
        val maxProf = matching.maxOf { it.prof }
        val byProf = matching.groupingBy { it.prof }.eachCount()

        drawScaledText(context, "§e§lOverview", rightX + PADDING + 4, contentTop + 4, 0xFFD700)
        drawScaledText(context, "§7Total species: §f$total   §7Avg prof: §f${String.format("%.1f", avgProf)}   §7Max prof: §f$maxProf", rightX + PADDING + 4, contentTop + 16, 0xCCCCCC)

        // Proficiency distribution — "stars (count)" instead of "stars=count" (less misleading).
        val distY = contentTop + 30
        drawScaledText(context, "§7Distribution:", rightX + PADDING + 4, distY, 0xAAAAAA)
        var bx = rightX + PADDING + 60
        for (prof in 1..5) {
            val n = byProf[prof] ?: 0
            val stars = (1..5).joinToString("") { if (it <= prof) "★" else "☆" }
            drawScaledText(context, "§e$stars §8(§f$n§8)", bx, distY, 0xCCCCCC)
            bx += 56
        }

        // Top mons section — 2-column grid to use horizontal space.
        val topY = distY + 16
        context.fill(rightX + PADDING, topY, rightX + rightW - PADDING, topY + 1, SEPARATOR)
        drawScaledText(context, "§e§lTop 10 by Proficiency", rightX + PADDING + 4, topY + 4, 0xFFD700)

        val sorted = matching.sortedWith(compareByDescending<Entry> { it.prof }.thenBy { it.species })
        val rowHeight = 20
        val cols = 2
        val colGap = 4
        val innerW = rightW - PADDING * 2
        val colW = (innerW - colGap * (cols - 1)) / cols
        val gridTop = topY + 16
        for ((i, entry) in sorted.take(10).withIndex()) {
            val col = i % cols
            val rowI = i / cols
            val cx = rightX + PADDING + col * (colW + colGap)
            val ry = gridTop + rowI * rowHeight
            if (ry + rowHeight > contentBottom) break

            val bg = if (rowI % 2 == 0) 0xFF1F1F2F.toInt() else 0xFF1A1A2A.toInt()
            context.fill(cx, ry, cx + colW, ry + rowHeight - 1, bg)

            // Sprite at column left
            PokemonSpriteHelper.renderSmallIconByName(context, textRenderer, entry.species, cx + 4, ry + 2, 0f)

            val rank = "§7#${i + 1}"
            val name = entry.species.replaceFirstChar { it.uppercase() }
            val stars = (1..5).joinToString("") { if (it <= entry.prof) "★" else "☆" }
            context.matrices.push()
            context.matrices.translate((cx + 24).toFloat(), (ry + 3).toFloat(), 0f)
            context.matrices.scale(0.85f, 0.85f, 1f)
            context.drawTextWithShadow(textRenderer, "$rank §f§l$name", 0, 0, 0xFFFFFF)
            context.matrices.pop()
            context.matrices.push()
            context.matrices.translate((cx + 24).toFloat(), (ry + 12).toFloat(), 0f)
            context.matrices.scale(0.65f, 0.65f, 1f)
            context.drawTextWithShadow(textRenderer, "§6$stars", 0, 0, 0xFFD700)
            context.matrices.pop()
        }
    }

    // ---- Existing helpers (kept) ----

    private fun renderField(
        context: DrawContext,
        fx: Int, fy: Int, fw: Int, fh: Int,
        text: String,
        isActive: Boolean,
        isOverride: Boolean
    ) {
        val border = if (isActive) FIELD_ACTIVE else FIELD_BORDER
        context.fill(fx - 1, fy - 1, fx + fw + 1, fy + fh + 1, border)
        context.fill(fx, fy, fx + fw, fy + fh, FIELD_BG)
        val color = if (isOverride) 0xFFFF00 else 0xCCCCCC
        drawScaledText(context, text, fx + 2, fy + 2, color)
        if (isActive && (cursorBlink / 20) % 2 == 0) {
            val cursorX = fx + 2 + (textRenderer.getWidth(text) * SCALE).toInt()
            drawScaledText(context, "|", cursorX, fy + 2, 0xFFFFFF)
        }
    }

    private fun formatTuning(value: Double, step: Double): String {
        return if (step >= 1.0) value.toInt().toString() else String.format("%.2f", value)
    }

    private fun drawScaledText(context: DrawContext, text: String, px: Int, py: Int, color: Int) {
        context.matrices.push()
        context.matrices.translate(px.toFloat(), py.toFloat(), 0f)
        context.matrices.scale(SCALE, SCALE, 1f)
        context.drawTextWithShadow(textRenderer, text, 0, 0, color)
        context.matrices.pop()
    }

    // ---- Input handling ----

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Grid scrollbar — claim drag clicks before anything else so the thumb is grabbable.
        if (gridScrollbar.mouseClicked(mouseX, mouseY)) {
            scrollOffset = -gridScrollbar.scroll
            return true
        }
        // Sidebar click
        if (mouseX in x.toDouble()..(x + SIDEBAR_W).toDouble() && mouseY in y.toDouble()..(y + h).toDouble()) {
            val relY = mouseY - (y + PADDING + 11)
            if (relY < 0) return true
            val idx = (relY / ROW_H).toInt()
            if (idx in sidebarRows.indices) {
                val row = sidebarRows[idx]
                commitActiveField()
                // Any sidebar navigation pulls us off the current Loot sub-tab → ensure the
                // embedded loot panel's widgets (Add Item / Save / Reset) get hidden again.
                // Without this they leak onto Settings/Stats and look like ghost buttons.
                setActiveLootPanel(null)
                when (row) {
                    is SidebarRow.Category -> {
                        if (row.name == "all") {
                            activeCategory = "all"
                            viewMode = ViewMode.GRID
                        } else {
                            // Toggle expand on click
                            if (row.name in expandedCategories) expandedCategories.remove(row.name)
                            else expandedCategories.add(row.name)
                            activeCategory = row.name
                            viewMode = ViewMode.GRID
                        }
                        scrollOffset = 0
                    }
                    is SidebarRow.Job -> {
                        detailJobIdx = row.jobIdx
                        viewMode = ViewMode.DETAIL
                        detailTab = DetailTab.SETTINGS
                    }
                }
                return true
            }
            return true
        }

        // Sub-tab click (in detail mode)
        if (viewMode == ViewMode.DETAIL) {
            for ((tab, box) in subTabBoxes) {
                if (inBox(mouseX, mouseY, box)) {
                    commitActiveField()
                    detailTab = tab
                    // Hide loot widgets when leaving Loot tab; ensure shown when entering.
                    if (tab != DetailTab.LOOT) setActiveLootPanel(null)
                    // Reset the recipes request flag so each tab open refreshes the list,
                    // catching admin edits made in another client session.
                    if (tab == DetailTab.RECIPES) recipesRequestSent = false
                    return true
                }
            }
            // Enable/Disable toggle click (header level, right of sub-tabs)
            if (detailJobIdx in jobEdits.indices && inBox(mouseX, mouseY, enableToggleBox)) {
                commitActiveField()
                val job = jobEdits[detailJobIdx]
                job.enabled = !job.enabled
                job.dirty = true
                return true
            }
        }

        // Forward to embedded loot panel when in Loot sub-tab
        if (viewMode == ViewMode.DETAIL && detailTab == DetailTab.LOOT) {
            currentLootPanel?.let { if (it.mouseClicked(mouseX, mouseY, button)) return true }
        }

        // Recipes sub-tab interactions
        if (viewMode == ViewMode.DETAIL && detailTab == DetailTab.RECIPES) {
            // Scrollbar drag start first — must consume the click before row hit-tests.
            if (recipesScrollbar.mouseClicked(mouseX, mouseY)) {
                recipesScroll = recipesScrollbar.scroll
                return true
            }
            // Category sidebar click
            for ((cat, box) in recipesCategoryHitBoxes) {
                if (inBox(mouseX, mouseY, box)) {
                    recipesSelectedCategory = cat
                    recipesScroll = 0
                    return true
                }
            }
            // Bulk Enable-All / Disable-All for current category
            val selectedCat = recipesSelectedCategory
            if (selectedCat != null) {
                val catRecipes = notlown.cobblebase.core.AdminRecipesCache.byCategory(selectedCat)
                if (inBox(mouseX, mouseY, recipesEnableAllHitBox)) {
                    submitRecipeChanges(catRecipes.associate { it.recipeId to true })
                    return true
                }
                if (inBox(mouseX, mouseY, recipesDisableAllHitBox)) {
                    submitRecipeChanges(catRecipes.associate { it.recipeId to false })
                    return true
                }
            }
            // Per-recipe toggle click
            for ((recipeId, box) in recipesToggleHitBoxes) {
                if (inBox(mouseX, mouseY, box)) {
                    val currentlyEnabled = notlown.cobblebase.core.AdminRecipesCache.isEnabled(recipeId)
                    submitRecipeChanges(mapOf(recipeId to !currentlyEnabled))
                    return true
                }
            }
        }

        // Grid tile click
        if (viewMode == ViewMode.GRID) {
            for (tile in gridTiles) {
                if (mouseX in tile.x.toDouble()..(tile.x + tile.w).toDouble() &&
                    mouseY in tile.y.toDouble()..(tile.y + tile.h).toDouble()) {
                    commitActiveField()
                    // Hide any previously-cached loot panel's widgets before entering a new job.
                    setActiveLootPanel(null)
                    detailJobIdx = tile.jobIdx
                    viewMode = ViewMode.DETAIL
                    detailTab = DetailTab.SETTINGS
                    return true
                }
            }
        }

        // Settings field click (in detail mode, settings sub-tab)
        if (viewMode == ViewMode.DETAIL && detailTab == DetailTab.SETTINGS && detailJobIdx in jobEdits.indices) {
            return handleSettingsClick(mouseX, mouseY, jobEdits[detailJobIdx], detailJobIdx)
        }

        return false
    }

    private fun handleSettingsClick(mouseX: Double, mouseY: Double, job: JobEditData, jobIdx: Int): Boolean {
        val rightX = x + SIDEBAR_W + 2
        val rightW = w - SIDEBAR_W - 2
        val tabsY = y + PADDING + HEADER_H + 2
        val contentTop = tabsY + SUBTAB_H + 4
        val cardY = contentTop + 14
        val fieldW = 50
        val fieldH = 12
        val cdX = rightX + PADDING + 140

        commitActiveField()

        // Cooldown field
        val cdRowY = cardY + 16
        if (job.skillId != "cobblebase:producer" &&
            mouseX in cdX.toDouble()..(cdX + fieldW).toDouble() &&
            mouseY in cdRowY.toDouble()..(cdRowY + fieldH).toDouble()) {
            activeFieldJob = jobIdx
            activeFieldType = FieldType.COOLDOWN
            fieldText = job.cooldownSeconds.toString()
            return true
        }

        // Radius field
        val radRowY = cdRowY + 16
        if (mouseX in cdX.toDouble()..(cdX + fieldW).toDouble() &&
            mouseY in radRowY.toDouble()..(radRowY + fieldH).toDouble()) {
            activeFieldJob = jobIdx
            activeFieldType = FieldType.RADIUS
            fieldText = job.searchRadius.toString()
            return true
        }

        // Tuning fields
        if (job.tuningFields.isNotEmpty()) {
            val tuningTop = radRowY + 18
            var ty = tuningTop + 16
            for ((key, _) in job.tuningFields) {
                if (mouseX in cdX.toDouble()..(cdX + fieldW).toDouble() &&
                    mouseY in ty.toDouble()..(ty + fieldH).toDouble()) {
                    activeFieldJob = jobIdx
                    activeFieldType = FieldType.TUNING
                    activeTuningKey = key
                    val tField = job.tuningFields[key]!!
                    val cur = job.tuningValues[key] ?: tField.defaultValue
                    fieldText = formatTuning(cur, tField.step)
                    return true
                }
                ty += 16
            }
        }

        activeFieldJob = -1
        activeFieldType = FieldType.NONE
        activeTuningKey = ""
        return false
    }

    private fun inBox(mx: Double, my: Double, box: IntArray): Boolean {
        val (bx, by, bw, bh) = box
        return mx >= bx && mx <= bx + bw && my >= by && my <= by + bh
    }

    private fun commitActiveField() {
        if (activeFieldJob < 0 || activeFieldType == FieldType.NONE) return
        val job = jobEdits[activeFieldJob]
        when (activeFieldType) {
            FieldType.COOLDOWN -> {
                fieldText.toLongOrNull()?.takeIf { it > 0 }?.let { job.cooldownSeconds = it; job.dirty = true }
            }
            FieldType.RADIUS -> {
                fieldText.toIntOrNull()?.takeIf { it > 0 }?.let { job.searchRadius = it; job.dirty = true }
            }
            FieldType.TUNING -> {
                val tField = job.tuningFields[activeTuningKey]
                if (tField != null) fieldText.toDoubleOrNull()?.let { raw ->
                    val clamped = raw.coerceIn(tField.min, tField.max)
                    job.tuningValues[activeTuningKey] = clamped
                    job.dirty = true
                }
            }
            FieldType.NONE -> {}
        }
        activeFieldJob = -1
        activeFieldType = FieldType.NONE
        activeTuningKey = ""
    }

    private fun saveAllChanges() {
        val newOverrides = AdminJobDataCache.jobOverrides.toMutableMap()
        for (job in jobEdits) {
            if (!job.dirty) continue
            val cooldownOverride = if (job.cooldownSeconds != job.defaultCooldown) job.cooldownSeconds else null
            val radiusOverride = if (job.searchRadius != job.defaultRadius) job.searchRadius else null
            val tuningOverride = job.tuningFields.mapNotNull { (key, field) ->
                val current = job.tuningValues[key] ?: field.defaultValue
                if (current != field.defaultValue) key to current else null
            }.toMap()

            ClientPlayNetworking.send(AdminJobsUpdateC2SPacket(
                job.skillId, cooldownOverride, radiusOverride, job.enabled, tuningOverride
            ))
            if (cooldownOverride == null && radiusOverride == null && job.enabled && tuningOverride.isEmpty()) {
                newOverrides.remove(job.skillId)
            } else {
                newOverrides[job.skillId] = JobConfigOverrides.JobOverride(
                    cooldownSeconds = cooldownOverride,
                    searchRadius = radiusOverride,
                    enabled = job.enabled,
                    tuning = tuningOverride.takeIf { it.isNotEmpty() }
                )
            }
            job.dirty = false
        }
        AdminJobDataCache.update(AdminJobDataCache.allJobs, newOverrides)
    }

    private fun resetChanges() {
        activeFieldJob = -1
        activeFieldType = FieldType.NONE
        rebuild()
    }

    fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (viewMode == ViewMode.DETAIL && detailTab == DetailTab.LOOT) {
            currentLootPanel?.let { if (it.charTyped(chr, modifiers)) return true }
        }
        if (activeFieldJob < 0 || activeFieldType == FieldType.NONE) return false
        if (activeFieldType == FieldType.TUNING) {
            if (fieldText.length >= 8) return false
            if (chr.isDigit()) { fieldText += chr; return true }
            if (chr == '.' && !fieldText.contains('.')) { fieldText += chr; return true }
            if (chr == '-' && fieldText.isEmpty()) { fieldText += chr; return true }
            return false
        }
        if (chr.isDigit() && fieldText.length < 8) {
            fieldText += chr
            return true
        }
        return false
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (viewMode == ViewMode.DETAIL && detailTab == DetailTab.LOOT) {
            currentLootPanel?.let { if (it.keyPressed(keyCode, scanCode, modifiers)) return true }
        }
        if (activeFieldJob < 0 || activeFieldType == FieldType.NONE) {
            if (keyCode == 256 && viewMode == ViewMode.DETAIL) {
                viewMode = ViewMode.GRID
                detailJobIdx = -1
                setActiveLootPanel(null)
                return true
            }
            return false
        }
        if (keyCode == 259 && fieldText.isNotEmpty()) {
            fieldText = fieldText.dropLast(1)
            return true
        }
        if (keyCode == 257 || keyCode == 335) { commitActiveField(); return true }
        if (keyCode == 256) {
            activeFieldJob = -1
            activeFieldType = FieldType.NONE
            activeTuningKey = ""
            return true
        }
        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (viewMode == ViewMode.DETAIL && detailTab == DetailTab.LOOT) {
            return currentLootPanel?.mouseDragged(mouseX, mouseY, button, deltaX, deltaY) ?: false
        }
        if (viewMode == ViewMode.DETAIL && detailTab == DetailTab.RECIPES &&
            recipesScrollbar.mouseDragged(mouseY)) {
            recipesScroll = recipesScrollbar.scroll
            return true
        }
        if (gridScrollbar.mouseDragged(mouseY)) {
            scrollOffset = -gridScrollbar.scroll
            return true
        }
        return false
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (viewMode == ViewMode.DETAIL && detailTab == DetailTab.LOOT) {
            return currentLootPanel?.mouseReleased(mouseX, mouseY, button) ?: false
        }
        if (recipesScrollbar.mouseReleased()) return true
        if (gridScrollbar.mouseReleased()) return true
        return false
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (viewMode == ViewMode.DETAIL && detailTab == DetailTab.LOOT) {
            return currentLootPanel?.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount) ?: false
        }
        if (viewMode == ViewMode.DETAIL && detailTab == DetailTab.RECIPES) {
            recipesScroll = (recipesScroll - (verticalAmount * 14).toInt()).coerceAtLeast(0)
            return true
        }
        if (viewMode == ViewMode.GRID && mouseX >= x + SIDEBAR_W) {
            scrollOffset = (scrollOffset + verticalAmount.toInt() * 16).coerceAtMost(0)
            return true
        }
        return false
    }
}

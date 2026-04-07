package notlown.cobblebase.neoforge.client.gui

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Drawable
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text

/**
 * Admin GUI screen for managing Pokemon species skill assignments and job configuration.
 * Tab bar at top: "Species" (two-pane layout) and "Jobs" (full-width config panel).
 *
 * IMPORTANT: Never call super.render() or renderBackground() — see CLAUDE.md.
 */
class AdminScreen : Screen(Text.literal("Cobblebase Admin")) {

    companion object {
        const val PANEL_COLOR = 0xCC1E1E1E.toInt()
        const val PANEL_BORDER = 0xFF3A3A5C.toInt()
        const val HEADER_COLOR = 0xCC252545.toInt()

        const val TAB_ACTIVE = 0xFF3A3A6C.toInt()
        const val TAB_INACTIVE = 0xFF252545.toInt()
        const val TAB_HOVER = 0xFF2E2E55.toInt()
        const val TAB_HEIGHT = 16
    }

    private var panelX = 0
    private var panelY = 0
    private var panelW = 0
    private var panelH = 0

    private var activeTab = "species"

    private lateinit var speciesListPanel: AdminSpeciesListPanel
    private lateinit var skillEditorPanel: AdminSkillEditorPanel
    private lateinit var jobsPanel: AdminJobsPanel
    private lateinit var lootPanel: AdminLootPanel
    // generalPanel is intentionally kept but not surfaced — the General tab is
    // hidden until a later release. Discord button on the Skills panel falls
    // back to GeneralSettingsCache defaults (enabled = true).
    private lateinit var generalPanel: AdminGeneralPanel
    private lateinit var wikiPanel: AdminWikiPanel

    private val speciesWidgets = mutableListOf<net.minecraft.client.gui.widget.ClickableWidget>()
    private val jobsWidgets = mutableListOf<net.minecraft.client.gui.widget.ClickableWidget>()
    private val lootWidgets = mutableListOf<net.minecraft.client.gui.widget.ClickableWidget>()
    private val generalWidgets = mutableListOf<net.minecraft.client.gui.widget.ClickableWidget>()
    private val wikiWidgets = mutableListOf<net.minecraft.client.gui.widget.ClickableWidget>()

    override fun init() {
        super.init()

        panelW = (width * 0.88).toInt().coerceAtMost(640)
        panelH = (height * 0.82).toInt().coerceAtMost(440)
        panelX = (width - panelW) / 2
        panelY = (height - panelH) / 2

        val leftW = (panelW * 0.25).toInt()
        val rightW = panelW - leftW - 2 // 2px separator

        val tabBarY = panelY + 22 // below header
        val contentY = tabBarY + TAB_HEIGHT // below tab bar

        speciesListPanel = AdminSpeciesListPanel(
            panelX, contentY, leftW, panelH - 22 - TAB_HEIGHT, textRenderer
        ) { species ->
            skillEditorPanel.setSpecies(species)
        }

        skillEditorPanel = AdminSkillEditorPanel(
            panelX + leftW + 2, contentY, rightW, panelH - 22 - TAB_HEIGHT, textRenderer
        ) {
            // onSaved callback
        }

        jobsPanel = AdminJobsPanel(
            panelX, contentY, panelW, panelH - 22 - TAB_HEIGHT, textRenderer
        )
        jobsPanel.rebuild()

        generalPanel = AdminGeneralPanel(
            panelX, contentY, panelW, panelH - 22 - TAB_HEIGHT, textRenderer
        )

        wikiPanel = AdminWikiPanel(
            panelX, contentY, panelW, panelH - 22 - TAB_HEIGHT, textRenderer
        )

        lootPanel = AdminLootPanel(
            panelX, contentY, panelW, panelH - 22 - TAB_HEIGHT, textRenderer
        )

        clearChildren()
        speciesWidgets.clear()
        jobsWidgets.clear()
        lootWidgets.clear()
        generalWidgets.clear()
        wikiWidgets.clear()

        // Init list panel search field
        speciesListPanel.init { widget: TextFieldWidget ->
            speciesWidgets.add(widget)
            addDrawableChild(widget)
        }

        // Init editor panel buttons
        skillEditorPanel.init { widget: ButtonWidget ->
            speciesWidgets.add(widget)
            addDrawableChild(widget)
        }

        // Init jobs panel buttons
        jobsPanel.init { widget: ButtonWidget ->
            jobsWidgets.add(widget)
            addDrawableChild(widget)
        }

        // General tab is hidden until a later release — skip widget init.
        // Discord button on Skills panel uses GeneralSettingsCache defaults
        // (enabled = true, default Cobblebase invite URL).

        // Init wiki panel widgets
        wikiPanel.init { widget ->
            wikiWidgets.add(widget)
            addDrawableChild(widget)
            widget
        }

        // Init loot panel widgets
        lootPanel.init { widget ->
            lootWidgets.add(widget)
            addDrawableChild(widget)
            widget
        }

        updateWidgetVisibility()
    }

    private fun updateWidgetVisibility() {
        for (w in speciesWidgets) w.visible = (activeTab == "species")
        for (w in jobsWidgets) w.visible = (activeTab == "jobs")
        for (w in lootWidgets) w.visible = (activeTab == "loot")
        for (w in generalWidgets) w.visible = (activeTab == "general")
        for (w in wikiWidgets) w.visible = (activeTab == "wiki")
    }

    private fun getTabBarY(): Int = panelY + 22

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Main panel border + background
        context.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, PANEL_BORDER)
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_COLOR)

        // Header bar
        context.fill(panelX, panelY, panelX + panelW, panelY + 22, HEADER_COLOR)
        context.fill(panelX, panelY + 21, panelX + panelW, panelY + 22, PANEL_BORDER)
        context.drawTextWithShadow(textRenderer, "\u00A7f\u00A7lCobblebase Admin", panelX + 8, panelY + 7, 0xFFFFFF)

        // Tab bar
        val tabBarY = getTabBarY()
        context.fill(panelX, tabBarY, panelX + panelW, tabBarY + TAB_HEIGHT, 0xCC1A1A30.toInt())
        context.fill(panelX, tabBarY + TAB_HEIGHT - 1, panelX + panelW, tabBarY + TAB_HEIGHT, PANEL_BORDER)

        // Tab bar
        val tabW = 64
        val speciesTabX = panelX + 4
        val jobsTabX = speciesTabX + tabW + 4
        val lootTabX = jobsTabX + tabW + 4
        val wikiTabX = lootTabX + tabW + 4
        val scale = 0.75f

        // General tab is hidden until a later release.
        renderTab(context, "Species", speciesTabX, tabBarY + 2, tabW, TAB_HEIGHT - 3,
            activeTab == "species", mouseX, mouseY, scale)
        renderTab(context, "Jobs", jobsTabX, tabBarY + 2, tabW, TAB_HEIGHT - 3,
            activeTab == "jobs", mouseX, mouseY, scale)
        renderTab(context, "Loot", lootTabX, tabBarY + 2, tabW, TAB_HEIGHT - 3,
            activeTab == "loot", mouseX, mouseY, scale)
        renderTab(context, "Wiki", wikiTabX, tabBarY + 2, tabW, TAB_HEIGHT - 3,
            activeTab == "wiki", mouseX, mouseY, scale)

        // Render active tab content
        when (activeTab) {
            "species" -> {
                val leftW = (panelW * 0.25).toInt()
                context.fill(panelX + leftW, tabBarY + TAB_HEIGHT, panelX + leftW + 1, panelY + panelH, PANEL_BORDER)
                speciesListPanel.render(context, mouseX, mouseY, delta)
                skillEditorPanel.render(context, mouseX, mouseY, delta)
            }
            "jobs" -> jobsPanel.render(context, mouseX, mouseY, delta)
            "loot" -> lootPanel.render(context, mouseX, mouseY, delta)
            "wiki" -> wikiPanel.render(context, mouseX, mouseY, delta)
        }

        // Render widgets without calling super.render() (avoids 1.21+ blur shader)
        for (child in this.children()) {
            if (child is Drawable) {
                child.render(context, mouseX, mouseY, delta)
            }
        }

        if (activeTab == "jobs" && jobsPanel.pendingTooltip.isNotEmpty()) {
            val lines = jobsPanel.pendingTooltip.map { Text.literal(it) }
            context.drawTooltip(textRenderer, lines, jobsPanel.tooltipX, jobsPanel.tooltipY)
        }
        if (activeTab == "loot" && lootPanel.pendingTooltip.isNotEmpty()) {
            val lines = lootPanel.pendingTooltip.map { Text.literal(it) }
            context.drawTooltip(textRenderer, lines, lootPanel.tooltipX, lootPanel.tooltipY)
        }
    }

    private fun renderTab(
        context: DrawContext, label: String,
        x: Int, y: Int, w: Int, h: Int,
        active: Boolean, mouseX: Int, mouseY: Int, scale: Float
    ) {
        val isHovered = mouseX in x..(x + w) && mouseY in y..(y + h)
        val bg = when {
            active -> TAB_ACTIVE
            isHovered -> TAB_HOVER
            else -> TAB_INACTIVE
        }
        context.fill(x, y, x + w, y + h, bg)
        if (active) {
            context.fill(x, y + h - 1, x + w, y + h, 0xFF6A6AFF.toInt())
        }

        val textColor = if (active) 0xFFFFFF else 0xAAAAAA
        context.matrices.push()
        val textW = textRenderer.getWidth(label)
        val textX = x + (w - (textW * scale).toInt()) / 2
        val textY = y + (h - (9 * scale).toInt()) / 2
        context.matrices.translate(textX.toFloat(), textY.toFloat(), 0f)
        context.matrices.scale(scale, scale, 1f)
        context.drawTextWithShadow(textRenderer, label, 0, 0, textColor)
        context.matrices.pop()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val tabBarY = getTabBarY()
        val tabW = 64
        val speciesTabX = panelX + 4
        val jobsTabX = speciesTabX + tabW + 4
        val lootTabX = jobsTabX + tabW + 4
        val wikiTabX = lootTabX + tabW + 4
        // General tab is hidden until a later release.
        if (mouseY >= tabBarY + 2 && mouseY <= tabBarY + TAB_HEIGHT - 1) {
            if (mouseX >= speciesTabX && mouseX <= speciesTabX + tabW) { activeTab = "species"; updateWidgetVisibility(); return true }
            if (mouseX >= jobsTabX && mouseX <= jobsTabX + tabW) { activeTab = "jobs"; updateWidgetVisibility(); return true }
            if (mouseX >= lootTabX && mouseX <= lootTabX + tabW) { activeTab = "loot"; updateWidgetVisibility(); return true }
            if (mouseX >= wikiTabX && mouseX <= wikiTabX + tabW) { activeTab = "wiki"; updateWidgetVisibility(); return true }
        }

        when (activeTab) {
            "species" -> {
                if (super.mouseClicked(mouseX, mouseY, button)) return true
                if (speciesListPanel.mouseClicked(mouseX, mouseY, button)) return true
                if (skillEditorPanel.mouseClicked(mouseX, mouseY, button)) return true
            }
            "jobs" -> {
                if (super.mouseClicked(mouseX, mouseY, button)) return true
                if (jobsPanel.mouseClicked(mouseX, mouseY, button)) return true
            }
            "loot" -> {
                if (super.mouseClicked(mouseX, mouseY, button)) return true
                if (lootPanel.mouseClicked(mouseX, mouseY, button)) return true
            }
            "wiki" -> {
                if (super.mouseClicked(mouseX, mouseY, button)) return true
                if (wikiPanel.mouseClicked(mouseX, mouseY, button)) return true
            }
        }
        return false
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        when (activeTab) {
            "species" -> {
                if (speciesListPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true
                if (skillEditorPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true
            }
            "wiki" -> if (wikiPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true
            else -> if (jobsPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        when (activeTab) {
            "species" -> {
                if (speciesListPanel.mouseReleased(mouseX, mouseY, button)) return true
                if (skillEditorPanel.mouseReleased(mouseX, mouseY, button)) return true
            }
            "wiki" -> if (wikiPanel.mouseReleased(mouseX, mouseY, button)) return true
            else -> if (jobsPanel.mouseReleased(mouseX, mouseY, button)) return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        when (activeTab) {
            "species" -> {
                if (speciesListPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true
                if (skillEditorPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true
            }
            "jobs" -> if (jobsPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true
            "loot" -> if (lootPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true
            "wiki" -> if (wikiPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true
        }
        return false
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (activeTab == "jobs") {
            if (jobsPanel.charTyped(chr, modifiers)) return true
        }
        if (activeTab == "loot") {
            if (lootPanel.charTyped(chr, modifiers)) return true
        }
        return super.charTyped(chr, modifiers)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (activeTab == "jobs") {
            if (jobsPanel.keyPressed(keyCode, scanCode, modifiers)) return true
        }
        if (activeTab == "loot") {
            if (lootPanel.keyPressed(keyCode, scanCode, modifiers)) return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun close() {
        client?.setScreen(null)
    }

    override fun shouldPause(): Boolean = false
}

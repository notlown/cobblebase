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
    private lateinit var generalPanel: AdminGeneralPanel

    // Track widgets per tab for visibility toggling
    private val speciesWidgets = mutableListOf<net.minecraft.client.gui.widget.ClickableWidget>()
    private val jobsWidgets = mutableListOf<net.minecraft.client.gui.widget.ClickableWidget>()
    private val generalWidgets = mutableListOf<net.minecraft.client.gui.widget.ClickableWidget>()

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

        clearChildren()
        speciesWidgets.clear()
        jobsWidgets.clear()
        generalWidgets.clear()

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

        // Init general panel widgets
        generalPanel.init { widget ->
            generalWidgets.add(widget)
            addDrawableChild(widget)
            widget
        }

        updateWidgetVisibility()
    }

    private fun updateWidgetVisibility() {
        for (w in speciesWidgets) w.visible = (activeTab == "species")
        for (w in jobsWidgets) w.visible = (activeTab == "jobs")
        for (w in generalWidgets) w.visible = (activeTab == "general")
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

        // Species tab
        val tabW = 70
        val speciesTabX = panelX + 4
        val jobsTabX = speciesTabX + tabW + 4
        val generalTabX = jobsTabX + tabW + 4
        val scale = 0.75f

        renderTab(context, "Species", speciesTabX, tabBarY + 2, tabW, TAB_HEIGHT - 3,
            activeTab == "species", mouseX, mouseY, scale)
        renderTab(context, "Jobs", jobsTabX, tabBarY + 2, tabW, TAB_HEIGHT - 3,
            activeTab == "jobs", mouseX, mouseY, scale)
        renderTab(context, "General", generalTabX, tabBarY + 2, tabW, TAB_HEIGHT - 3,
            activeTab == "general", mouseX, mouseY, scale)

        // Render active tab content
        when (activeTab) {
            "species" -> {
                val leftW = (panelW * 0.25).toInt()
                context.fill(panelX + leftW, tabBarY + TAB_HEIGHT, panelX + leftW + 1, panelY + panelH, PANEL_BORDER)
                speciesListPanel.render(context, mouseX, mouseY, delta)
                skillEditorPanel.render(context, mouseX, mouseY, delta)
            }
            "jobs" -> jobsPanel.render(context, mouseX, mouseY, delta)
            "general" -> generalPanel.render(context, mouseX, mouseY, delta)
        }

        // Render widgets without calling super.render() (avoids 1.21+ blur shader)
        for (child in this.children()) {
            if (child is Drawable) {
                child.render(context, mouseX, mouseY, delta)
            }
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
        // Check tab clicks
        val tabBarY = getTabBarY()
        val tabW = 70
        val speciesTabX = panelX + 4
        val jobsTabX = speciesTabX + tabW + 4

        val generalTabX = jobsTabX + tabW + 4
        if (mouseY >= tabBarY + 2 && mouseY <= tabBarY + TAB_HEIGHT - 1) {
            if (mouseX >= speciesTabX && mouseX <= speciesTabX + tabW) {
                activeTab = "species"
                updateWidgetVisibility()
                return true
            }
            if (mouseX >= jobsTabX && mouseX <= jobsTabX + tabW) {
                activeTab = "jobs"
                updateWidgetVisibility()
                return true
            }
            if (mouseX >= generalTabX && mouseX <= generalTabX + tabW) {
                activeTab = "general"
                updateWidgetVisibility()
                return true
            }
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
            "general" -> {
                if (super.mouseClicked(mouseX, mouseY, button)) return true
                if (generalPanel.mouseClicked(mouseX, mouseY, button)) return true
            }
        }
        return false
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (activeTab == "species") {
            if (speciesListPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true
            if (skillEditorPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true
        } else {
            if (jobsPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (activeTab == "species") {
            if (speciesListPanel.mouseReleased(mouseX, mouseY, button)) return true
            if (skillEditorPanel.mouseReleased(mouseX, mouseY, button)) return true
        } else {
            if (jobsPanel.mouseReleased(mouseX, mouseY, button)) return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (activeTab == "species") {
            if (speciesListPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true
            if (skillEditorPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true
        } else {
            if (jobsPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true
        }
        return false
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (activeTab == "jobs") {
            if (jobsPanel.charTyped(chr, modifiers)) return true
        }
        return super.charTyped(chr, modifiers)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (activeTab == "jobs") {
            if (jobsPanel.keyPressed(keyCode, scanCode, modifiers)) return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun close() {
        client?.setScreen(null)
    }

    override fun shouldPause(): Boolean = false
}

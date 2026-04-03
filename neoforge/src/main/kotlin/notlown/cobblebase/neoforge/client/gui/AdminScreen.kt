package notlown.cobblebase.neoforge.client.gui

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Drawable
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text

/**
 * Admin GUI screen for managing Pokemon species skill assignments.
 * Two-pane layout: species list (left) + skill editor (right).
 *
 * IMPORTANT: Never call super.render() or renderBackground() — see CLAUDE.md.
 */
class AdminScreen : Screen(Text.literal("Cobblebase Admin")) {

    companion object {
        const val PANEL_COLOR = 0xCC1E1E1E.toInt()
        const val PANEL_BORDER = 0xFF3A3A5C.toInt()
        const val HEADER_COLOR = 0xCC252545.toInt()
    }

    private var panelX = 0
    private var panelY = 0
    private var panelW = 0
    private var panelH = 0

    private lateinit var speciesListPanel: AdminSpeciesListPanel
    private lateinit var skillEditorPanel: AdminSkillEditorPanel

    override fun init() {
        super.init()

        panelW = (width * 0.88).toInt().coerceAtMost(640)
        panelH = (height * 0.82).toInt().coerceAtMost(440)
        panelX = (width - panelW) / 2
        panelY = (height - panelH) / 2

        val leftW = (panelW * 0.25).toInt()
        val rightW = panelW - leftW - 2 // 2px separator

        val contentY = panelY + 22 // header height

        speciesListPanel = AdminSpeciesListPanel(
            panelX, contentY, leftW, panelH - 22, textRenderer
        ) { species ->
            skillEditorPanel.setSpecies(species)
        }

        skillEditorPanel = AdminSkillEditorPanel(
            panelX + leftW + 2, contentY, rightW, panelH - 22, textRenderer
        ) {
            // onSaved callback
        }

        clearChildren()

        // Init list panel search field
        speciesListPanel.init { widget: TextFieldWidget ->
            addDrawableChild(widget)
        }

        // Init editor panel buttons
        skillEditorPanel.init { widget: ButtonWidget ->
            addDrawableChild(widget)
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Main panel border + background
        context.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, PANEL_BORDER)
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_COLOR)

        // Header bar
        context.fill(panelX, panelY, panelX + panelW, panelY + 22, HEADER_COLOR)
        context.fill(panelX, panelY + 21, panelX + panelW, panelY + 22, PANEL_BORDER)
        context.drawTextWithShadow(textRenderer, "\u00A7f\u00A7lCobblebase Admin \u00A77\u2014 Species Skill Manager", panelX + 8, panelY + 7, 0xFFFFFF)

        // Separator between panes
        val leftW = (panelW * 0.25).toInt()
        context.fill(panelX + leftW, panelY + 22, panelX + leftW + 1, panelY + panelH, PANEL_BORDER)

        // Render panels
        speciesListPanel.render(context, mouseX, mouseY, delta)
        skillEditorPanel.render(context, mouseX, mouseY, delta)

        // Render widgets without calling super.render() (avoids 1.21+ blur shader)
        for (child in this.children()) {
            if (child is Drawable) {
                child.render(context, mouseX, mouseY, delta)
            }
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (super.mouseClicked(mouseX, mouseY, button)) return true
        if (speciesListPanel.mouseClicked(mouseX, mouseY, button)) return true
        if (skillEditorPanel.mouseClicked(mouseX, mouseY, button)) return true
        return false
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (speciesListPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true
        if (skillEditorPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (speciesListPanel.mouseReleased(mouseX, mouseY, button)) return true
        if (skillEditorPanel.mouseReleased(mouseX, mouseY, button)) return true
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (speciesListPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true
        if (skillEditorPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true
        return false
    }

    override fun close() {
        client?.setScreen(null)
    }

    override fun shouldPause(): Boolean = false
}

package notlown.cobblebase.fabric.client.gui

import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket.PasturePokemonDataDTO
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos

/**
 * Main Cobblebase screen with 3 tabs: Skills, Buffs, Logs.
 * Opened from the Pasture Block UI via the "Cobblebase" button.
 */
class CobblebaseScreen(
    private val pokemonList: List<PasturePokemonDataDTO>,
    private val pastureOrigin: BlockPos?,
    private val parentScreen: Screen?
) : Screen(Text.literal("Cobblebase")) {

    enum class Tab { SKILLS, BUFFS, LOGS, DISCOVERY }

    private var activeTab = Tab.SKILLS

    // Panel layout
    private var panelX = 0
    private var panelY = 0
    private var panelW = 0
    private var panelH = 0
    private var contentY = 0

    // Tab buttons
    private val TAB_HEIGHT = 22
    private val TAB_GAP = 2

    // Style constants matching existing dark theme
    companion object {
        const val PANEL_COLOR = 0xCC1E1E1E.toInt()
        const val PANEL_BORDER = 0xFF3A3A5C.toInt()
        const val PANEL_HEADER = 0xCC252545.toInt()
        const val TAB_ACTIVE = 0xFF2A2A5E.toInt()
        const val TAB_INACTIVE = 0xFF1A1A2E.toInt()
        const val TAB_HOVER = 0xFF333355.toInt()

        val CATEGORY_COLORS = mapOf(
            "gathering" to 0xFF4CAF50.toInt(),
            "generation" to 0xFFFF9800.toInt(),
            "combat" to 0xFFF44336.toInt(),
            "support" to 0xFFE91E9E.toInt(),
            "utility" to 0xFF2196F3.toInt(),
            "legendary" to 0xFFFFD700.toInt()
        )
    }

    // Delegates for each tab's content
    private lateinit var skillsPanel: SkillsPanel
    private lateinit var buffsPanel: BuffsPanel
    private lateinit var logsPanel: LogsPanel
    private lateinit var discoveryPanel: DiscoveryPanel

    override fun init() {
        super.init()

        panelW = (width * 0.88).toInt().coerceAtMost(640)
        panelH = (height * 0.82).toInt().coerceAtMost(440)
        panelX = (width - panelW) / 2
        panelY = (height - panelH) / 2
        contentY = panelY + TAB_HEIGHT + 4

        skillsPanel = SkillsPanel(this, pokemonList, panelX, contentY, panelW, panelH - TAB_HEIGHT - 4, textRenderer)
        buffsPanel = BuffsPanel(this, pokemonList, pastureOrigin, panelX, contentY, panelW, panelH - TAB_HEIGHT - 4, textRenderer)
        logsPanel = LogsPanel(this, pastureOrigin, panelX, contentY, panelW, panelH - TAB_HEIGHT - 4, textRenderer)
        discoveryPanel = DiscoveryPanel(this, panelX, contentY, panelW, panelH - TAB_HEIGHT - 4, textRenderer)

        initCurrentTab()
    }

    private fun initCurrentTab() {
        clearChildren()
        when (activeTab) {
            Tab.SKILLS -> skillsPanel.init(this::addDrawableChild)
            Tab.BUFFS -> buffsPanel.init(this::addDrawableChild)
            Tab.LOGS -> logsPanel.init(this::addDrawableChild)
            Tab.DISCOVERY -> discoveryPanel.init(this::addDrawableChild)
        }
    }

    fun switchTab(tab: Tab) {
        if (activeTab == tab) return
        activeTab = tab
        initCurrentTab()
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Main panel border + background
        context.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, PANEL_BORDER)
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_COLOR)

        // Tab bar
        renderTabs(context, mouseX, mouseY)

        // Separator line below tabs
        context.fill(panelX, panelY + TAB_HEIGHT, panelX + panelW, panelY + TAB_HEIGHT + 1, PANEL_BORDER)

        // Tab content
        when (activeTab) {
            Tab.SKILLS -> skillsPanel.render(context, mouseX, mouseY, delta)
            Tab.BUFFS -> buffsPanel.render(context, mouseX, mouseY, delta)
            Tab.LOGS -> logsPanel.render(context, mouseX, mouseY, delta)
            Tab.DISCOVERY -> discoveryPanel.render(context, mouseX, mouseY, delta)
        }

        // Render widgets (buttons etc.) without calling super.render()
        // which would apply vanilla blur shader in 1.21+
        for (child in this.children()) {
            if (child is net.minecraft.client.gui.Drawable) {
                child.render(context, mouseX, mouseY, delta)
            }
        }
    }

    private fun renderTabs(context: DrawContext, mouseX: Int, mouseY: Int) {
        val tabs = Tab.entries
        val tabCount = tabs.size
        val tabW = (panelW - TAB_GAP * (tabCount - 1)) / tabCount

        for ((i, tab) in tabs.withIndex()) {
            val tx = panelX + i * (tabW + TAB_GAP)
            val ty = panelY
            val isActive = activeTab == tab
            val isHovered = mouseX in tx..(tx + tabW) && mouseY in ty..(ty + TAB_HEIGHT)

            val bg = when {
                isActive -> TAB_ACTIVE
                isHovered -> TAB_HOVER
                else -> TAB_INACTIVE
            }
            context.fill(tx, ty, tx + tabW, ty + TAB_HEIGHT, bg)

            // Active tab has a colored bottom border
            if (isActive) {
                val accentColor = when (tab) {
                    Tab.SKILLS -> 0xFF4CAF50.toInt()
                    Tab.BUFFS -> 0xFFFF9800.toInt()
                    Tab.LOGS -> 0xFF2196F3.toInt()
                    Tab.DISCOVERY -> 0xFF9C27B0.toInt()
                }
                context.fill(tx, ty + TAB_HEIGHT - 2, tx + tabW, ty + TAB_HEIGHT, accentColor)
            }

            // Tab label
            val label = when (tab) {
                Tab.SKILLS -> "\u00A7fSkills"
                Tab.BUFFS -> "\u00A7fBuffs"
                Tab.LOGS -> "\u00A7fLogs"
                Tab.DISCOVERY -> "\u00A7fScout"
            }
            val labelW = textRenderer.getWidth(label)
            val textColor = if (isActive) 0xFFFFFF else 0x999999
            context.drawTextWithShadow(textRenderer, label, tx + (tabW - labelW) / 2, ty + 7, textColor)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Check tab clicks
        val tabs = Tab.entries
        val tabCount = tabs.size
        val tabW = (panelW - TAB_GAP * (tabCount - 1)) / tabCount
        for ((i, tab) in tabs.withIndex()) {
            val tx = panelX + i * (tabW + TAB_GAP)
            val ty = panelY
            if (mouseX >= tx && mouseX <= tx + tabW && mouseY >= ty && mouseY <= ty + TAB_HEIGHT) {
                switchTab(tab)
                return true
            }
        }

        if (super.mouseClicked(mouseX, mouseY, button)) return true

        // Delegate to active tab
        return when (activeTab) {
            Tab.SKILLS -> skillsPanel.mouseClicked(mouseX, mouseY, button)
            Tab.BUFFS -> buffsPanel.mouseClicked(mouseX, mouseY, button)
            Tab.LOGS -> logsPanel.mouseClicked(mouseX, mouseY, button)
            Tab.DISCOVERY -> discoveryPanel.mouseClicked(mouseX, mouseY, button)
        }
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        val handled = when (activeTab) {
            Tab.SKILLS -> skillsPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
            Tab.BUFFS -> buffsPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
            Tab.LOGS -> logsPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
            Tab.DISCOVERY -> discoveryPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
        }
        if (handled) return true
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val handled = when (activeTab) {
            Tab.SKILLS -> skillsPanel.mouseReleased(mouseX, mouseY, button)
            Tab.BUFFS -> buffsPanel.mouseReleased(mouseX, mouseY, button)
            Tab.LOGS -> logsPanel.mouseReleased(mouseX, mouseY, button)
            Tab.DISCOVERY -> discoveryPanel.mouseReleased(mouseX, mouseY, button)
        }
        if (handled) return true
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        return when (activeTab) {
            Tab.SKILLS -> skillsPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
            Tab.BUFFS -> buffsPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
            Tab.LOGS -> logsPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
            Tab.DISCOVERY -> discoveryPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        }
    }

    override fun close() {
        client?.setScreen(null)
    }

    override fun shouldPause(): Boolean = false
}

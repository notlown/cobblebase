package notlown.cobblebase.neoforge.client.gui

import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket.PasturePokemonDataDTO
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import notlown.cobblebase.core.CobblebaseConfig
import me.shedaniel.autoconfig.AutoConfig
import notlown.cobblebase.core.CobblebaseClothConfig

/**
 * Main Cobblebase screen with 3 tabs: Skills, Buffs, Logs.
 * Opened from the Pasture Block UI via the "Cobblebase" button.
 */
class CobblebaseScreen(
    private val pokemonList: List<PasturePokemonDataDTO>,
    private val pastureOrigin: BlockPos?,
    private val parentScreen: Screen?
) : Screen(Text.literal("Cobblebase")) {

    enum class Tab { SKILLS, BUFFS, WORKSHOP, HATCHERY, LOGS, DISCOVERY }

    /**
     * Tabs that depend on a server-controllable job — when the admin disables the
     * job in JobConfigOverrides, the corresponding tab disappears from the user
     * GUI entirely (same vocabulary as the per-Pokemon skill chips already hide).
     */
    private fun visibleTabs(): List<Tab> = Tab.entries.filter { tab ->
        when (tab) {
            Tab.WORKSHOP -> notlown.cobblebase.core.JobConfigOverrides.isEnabled("cobblebase:craftsman")
            Tab.HATCHERY -> notlown.cobblebase.core.JobConfigOverrides.isEnabled("cobblebase:egg_hatcher")
            Tab.DISCOVERY -> notlown.cobblebase.core.JobConfigOverrides.isEnabled("cobblebase:scout")
            else -> true
        }
    }

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

    /** Bottom strip reserved for the persistent Discord/Mute/Radius/Admin bar. */
    private val BOTTOM_BAR_H = 20

    /** Close button (top-right X). 14×14 sits inside the panel header. */
    private val CLOSE_BTN_SIZE = 14
    private val CLOSE_BTN_INSET = 4
    private var closeBtnX = 0
    private var closeBtnY = 0

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
            "legendary" to 0xFFFFD700.toInt(),
        "social" to 0xFFFF55FF.toInt()
        )
    }

    // Delegates for each tab's content
    private lateinit var skillsPanel: SkillsPanel
    private lateinit var buffsPanel: BuffsPanel
    private lateinit var logsPanel: LogsPanel
    private lateinit var discoveryPanel: DiscoveryPanel
    private lateinit var workshopPanel: WorkshopPanel
    private lateinit var hatcheryPanel: HatcheryPanel

    override fun init() {
        super.init()

        panelW = (width * 0.88).toInt().coerceAtMost(640)
        panelH = (height * 0.82).toInt().coerceAtMost(440)
        panelX = (width - panelW) / 2
        panelY = (height - panelH) / 2
        contentY = panelY + TAB_HEIGHT + 4

        // Reserve the bottom strip for the persistent bottom bar (Discord / Mute /
        // Radius / Admin). Sub-panels now render their content above this strip
        // instead of placing their own Done buttons.
        val subPanelH = panelH - TAB_HEIGHT - 4 - BOTTOM_BAR_H
        skillsPanel = SkillsPanel(this, pokemonList, pastureOrigin, panelX, contentY, panelW, subPanelH, textRenderer)
        buffsPanel = BuffsPanel(this, pokemonList, pastureOrigin, panelX, contentY, panelW, subPanelH, textRenderer)
        logsPanel = LogsPanel(this, pastureOrigin, panelX, contentY, panelW, subPanelH, textRenderer)
        discoveryPanel = DiscoveryPanel(this, panelX, contentY, panelW, subPanelH, textRenderer)
        workshopPanel = WorkshopPanel(this, pokemonList, pastureOrigin, panelX, contentY, panelW, subPanelH, textRenderer)
        hatcheryPanel = HatcheryPanel(pokemonList, pastureOrigin, panelX, contentY, panelW, subPanelH, textRenderer)

        initCurrentTab()
    }

    /** Bottom-bar buttons that persist across tab switches. Re-added each tab init. */
    private fun addBottomBarWidgets() {
        val barY = panelY + panelH - BOTTOM_BAR_H + 4
        val barBtnH = 12

        // Discord icon button — only if enabled via GeneralSettings.
        val discordEnabled = notlown.cobblebase.core.GeneralSettingsCache.discordEnabled
        var nextX = panelX + 6
        if (discordEnabled) {
            addDrawableChild(ButtonWidget.builder(Text.literal("§9⚉")) {
                val url = notlown.cobblebase.core.GeneralSettingsCache.discordUrl
                try { net.minecraft.util.Util.getOperatingSystem().open(java.net.URI(url)) } catch (_: Exception) {}
            }.dimensions(nextX, barY, 14, barBtnH).build())
            nextX += 16
        }

        // Mute toggle.
        val muteBtn = ButtonWidget.builder(Text.literal(getMuteIcon())) { btn ->
            val config = me.shedaniel.autoconfig.AutoConfig.getConfigHolder(notlown.cobblebase.core.CobblebaseClothConfig::class.java).config
            config.cry.cryEnabled = !config.cry.cryEnabled
            me.shedaniel.autoconfig.AutoConfig.getConfigHolder(notlown.cobblebase.core.CobblebaseClothConfig::class.java).save()
            btn.message = Text.literal(getMuteIcon())
        }.dimensions(nextX, barY, 14, barBtnH).build()
        addDrawableChild(muteBtn)
        nextX += 16

        // Show Radius toggle (only if we have a pastureOrigin).
        if (pastureOrigin != null) {
            val activeHere = notlown.cobblebase.neoforge.client.render.RadiusRenderer.isActiveAt(pastureOrigin)
            val radiusLabel = if (activeHere) "§aRadius ON" else "§cRadius OFF"
            addDrawableChild(ButtonWidget.builder(Text.literal(radiusLabel)) {
                notlown.cobblebase.neoforge.client.render.RadiusRenderer.toggle(pastureOrigin, computeMaxRadius())
                close()
            }.dimensions(nextX, barY, 58, barBtnH).build())
        }

        // Admin button (only if OP) — bottom-right.
        val client = net.minecraft.client.MinecraftClient.getInstance()
        if (client.player?.hasPermissionLevel(2) == true) {
            addDrawableChild(ButtonWidget.builder(Text.literal("§6Admin")) {
                close()
                notlown.cobblebase.neoforge.client.CobblebaseNeoForgeClient.requestAdminScreen()
            }.dimensions(panelX + panelW - 44, barY, 38, barBtnH).build())
        }
    }

    private fun getMuteIcon(): String {
        return if (notlown.cobblebase.core.CobblebaseConfig.cryEnabled) "§e♫" else "§8♫"
    }

    /**
     * Maximum pasture work radius across all jobs — used by the Radius wireframe.
     * Mirrors the per-job effective radius calculation from SkillRegistry.
     */
    private fun computeMaxRadius(): Int {
        return notlown.cobblebase.core.SkillRegistry.getAll().keys
            .maxOfOrNull { notlown.cobblebase.core.SkillRegistry.getEffectiveRadius(it) } ?: 16
    }

    private fun initCurrentTab() {
        // Fall back to SKILLS if the admin disabled the currently-active job tab.
        val tabs = visibleTabs()
        if (activeTab !in tabs) activeTab = tabs.firstOrNull() ?: Tab.SKILLS

        clearChildren()
        addBottomBarWidgets()  // persists across tab switches
        when (activeTab) {
            Tab.SKILLS -> skillsPanel.init(this::addDrawableChild)
            Tab.BUFFS -> buffsPanel.init(this::addDrawableChild)
            Tab.WORKSHOP -> workshopPanel.init(this::addDrawableChild)
            Tab.HATCHERY -> hatcheryPanel.init(this::addDrawableChild)
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

        // Mute button moved to SkillsPanel bottom bar

        // Tab content
        when (activeTab) {
            Tab.SKILLS -> skillsPanel.render(context, mouseX, mouseY, delta)
            Tab.BUFFS -> buffsPanel.render(context, mouseX, mouseY, delta)
            Tab.WORKSHOP -> workshopPanel.render(context, mouseX, mouseY, delta)
            Tab.HATCHERY -> hatcheryPanel.render(context, mouseX, mouseY, delta)
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
        val tabs = visibleTabs()
        val tabCount = tabs.size
        // Reserve space on the right for the close button so tabs don't overlap it.
        val tabsAreaW = panelW - CLOSE_BTN_SIZE - CLOSE_BTN_INSET * 2
        val tabW = (tabsAreaW - TAB_GAP * (tabCount - 1)) / tabCount

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
                    Tab.WORKSHOP -> 0xFFFF5722.toInt()
                    Tab.HATCHERY -> 0xFFFFB300.toInt()
                    Tab.LOGS -> 0xFF2196F3.toInt()
                    Tab.DISCOVERY -> 0xFF9C27B0.toInt()
                }
                context.fill(tx, ty + TAB_HEIGHT - 2, tx + tabW, ty + TAB_HEIGHT, accentColor)
            }

            // Tab icon (vanilla item, same vocabulary as the Admin job grid) + label.
            val iconItem = when (tab) {
                Tab.SKILLS -> net.minecraft.item.Items.ENCHANTED_BOOK
                Tab.BUFFS -> net.minecraft.item.Items.POTION
                Tab.WORKSHOP -> net.minecraft.item.Items.CRAFTING_TABLE
                Tab.HATCHERY -> JobIcons.POKEMON_EGG  // Cobbreeding's pokemon_egg, falls back to vanilla
                Tab.LOGS -> net.minecraft.item.Items.PAPER
                Tab.DISCOVERY -> net.minecraft.item.Items.SPYGLASS
            }
            val label = when (tab) {
                Tab.SKILLS -> "\u00A7fPokemon"
                Tab.BUFFS -> "\u00A7fBuffs"
                Tab.WORKSHOP -> "\u00A7fWorkshop"
                Tab.HATCHERY -> "\u00A7fHatchery"
                Tab.LOGS -> "\u00A7fLogs"
                Tab.DISCOVERY -> "\u00A7fScout"
            }
            val labelW = textRenderer.getWidth(label)
            // Compose icon (8px after 0.5 scale) + 2px gap + label, centered in tab.
            val iconRenderW = 9
            val totalW = iconRenderW + 2 + labelW
            val groupX = tx + (tabW - totalW) / 2

            // Icon Y: text drawn at ty+7 (height 9) → text center ty+11.5. Icon scaled height
            // ≈ 9 → icon-top should be ty+7 so its center matches the text center.
            context.matrices.push()
            context.matrices.translate(groupX.toFloat(), (ty + 7).toFloat(), 0f)
            context.matrices.scale(0.55f, 0.55f, 1f)
            context.drawItem(net.minecraft.item.ItemStack(iconItem), 0, 0)
            context.matrices.pop()

            val textColor = if (isActive) 0xFFFFFF else 0x999999
            context.drawTextWithShadow(textRenderer, label, groupX + iconRenderW + 2, ty + 7, textColor)
        }

        // Close button (X) on the top-right of the tab strip — replaces the per-panel
        // Done buttons. Hover turns red so it reads as "destructive / closes the window".
        closeBtnX = panelX + panelW - CLOSE_BTN_INSET - CLOSE_BTN_SIZE
        closeBtnY = panelY + (TAB_HEIGHT - CLOSE_BTN_SIZE) / 2
        val closeHovered = mouseX in closeBtnX..(closeBtnX + CLOSE_BTN_SIZE) &&
            mouseY in closeBtnY..(closeBtnY + CLOSE_BTN_SIZE)
        val closeBg = if (closeHovered) 0xFFB23A3A.toInt() else 0xFF44445A.toInt()
        context.fill(closeBtnX, closeBtnY, closeBtnX + CLOSE_BTN_SIZE, closeBtnY + CLOSE_BTN_SIZE, closeBg)
        val xLabel = "§f✕"
        val xW = textRenderer.getWidth(xLabel)
        context.drawTextWithShadow(
            textRenderer, xLabel,
            closeBtnX + (CLOSE_BTN_SIZE - xW) / 2,
            closeBtnY + (CLOSE_BTN_SIZE - 8) / 2,
            0xFFFFFF
        )
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Close button (X) — top-right of the tab strip.
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + CLOSE_BTN_SIZE &&
            mouseY >= closeBtnY && mouseY <= closeBtnY + CLOSE_BTN_SIZE) {
            close()
            return true
        }

        // Tab clicks (width matches the tab-area excluding the X button).
        val tabs = visibleTabs()
        val tabCount = tabs.size
        val tabsAreaW = panelW - CLOSE_BTN_SIZE - CLOSE_BTN_INSET * 2
        val tabW = (tabsAreaW - TAB_GAP * (tabCount - 1)) / tabCount
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
            Tab.WORKSHOP -> workshopPanel.mouseClicked(mouseX, mouseY, button)
            Tab.HATCHERY -> hatcheryPanel.mouseClicked(mouseX, mouseY, button)
            Tab.LOGS -> logsPanel.mouseClicked(mouseX, mouseY, button)
            Tab.DISCOVERY -> discoveryPanel.mouseClicked(mouseX, mouseY, button)
        }
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        val handled = when (activeTab) {
            Tab.SKILLS -> skillsPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
            Tab.BUFFS -> buffsPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
            Tab.WORKSHOP -> workshopPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
            Tab.HATCHERY -> hatcheryPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
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
            Tab.WORKSHOP -> workshopPanel.mouseReleased(mouseX, mouseY, button)
            Tab.HATCHERY -> hatcheryPanel.mouseReleased(mouseX, mouseY, button)
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
            Tab.WORKSHOP -> workshopPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
            Tab.HATCHERY -> hatcheryPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
            Tab.LOGS -> logsPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
            Tab.DISCOVERY -> discoveryPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        }
    }

    override fun close() {
        client?.setScreen(null)
    }

    override fun shouldPause(): Boolean = false
}

package notlown.cobblebase.fabric.client.gui

import notlown.cobblebase.core.BaseManager
import notlown.cobblebase.core.SkillRegistry
import notlown.cobblebase.core.SpeciesSkillRegistry
import notlown.cobblebase.core.net.SkillAssignmentC2SPacket
import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket.PasturePokemonDataDTO
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import java.util.UUID

class SkillAssignmentScreen(
    private val pokemonList: List<PasturePokemonDataDTO>,
    private val parentScreen: Screen?
) : Screen(Text.literal("Skill Assignment")) {

    private val PANEL_COLOR = 0xCC1A1A2E.toInt()
    private val PANEL_BORDER = 0xFF3A3A5C.toInt()
    private val PANEL_HEADER = 0xCC252545.toInt()
    private val ROW_EVEN = 0x44FFFFFF.toInt()
    private val ROW_ODD = 0x22FFFFFF.toInt()

    private val ROW_HEIGHT = 26
    private val HEADER_HEIGHT = 36
    private val PANEL_PADDING = 8
    private val NAME_WIDTH = 90
    private val BTN_WIDTH = 62
    private val BTN_HEIGHT = 18
    private val BTN_GAP = 3

    private var scrollX = 0
    private var scrollY = 0

    private val CATEGORY_COLORS = mapOf(
        "gathering" to 0xFF4CAF50.toInt(),
        "generation" to 0xFFFF9800.toInt(),
        "combat" to 0xFFF44336.toInt(),
        "support" to 0xFFE91E9E.toInt(),
        "utility" to 0xFF2196F3.toInt(),
        "legendary" to 0xFFFFD700.toInt()
    )

    // SkillButton is defined at file level below to avoid ClassLoader issues

    private val allButtons = mutableListOf<SkillButtonData>()
    private var panelX = 0
    private var panelY = 0
    private var panelW = 0
    private var panelH = 0
    private var contentY = 0

    override fun init() {
        super.init()
        allButtons.clear()
        scrollX = 0
        scrollY = 0

        panelW = (width * 0.85).toInt().coerceAtMost(600)
        panelH = (height * 0.8).toInt().coerceAtMost(400)
        panelX = (width - panelW) / 2
        panelY = (height - panelH) / 2
        contentY = panelY + HEADER_HEIGHT + PANEL_PADDING

        addDrawableChild(ButtonWidget.builder(Text.literal("Done")) { close() }
            .dimensions(panelX + panelW - 54, panelY + panelH - 22, 46, 16).build())

        pokemonList.forEachIndexed { index, pokemonData ->
            val pokemonId = pokemonData.pokemonId
            val speciesName = pokemonData.species.path
            val currentAssignment = BaseManager.getAssignment(pokemonId)
            val speciesSkills = SpeciesSkillRegistry.getSkills(speciesName)
            val availableSkills = speciesSkills?.skills ?: emptyList()
            println("[CobblebaseGUI] species='$speciesName' found=${speciesSkills != null} skills=${availableSkills.size}")

            val rowY = contentY + index * ROW_HEIGHT
            var btnX = panelX + PANEL_PADDING + NAME_WIDTH

            // Auto button
            allButtons.add(SkillButtonData(pokemonId, null, "Auto", 0, "", btnX, rowY, currentAssignment == null))
            btnX += BTN_WIDTH + BTN_GAP

            for (entry in availableSkills) {
                val skillDef = SkillRegistry.get(entry.skillId) ?: continue
                allButtons.add(SkillButtonData(
                    pokemonId, entry.skillId, skillDef.name, entry.proficiency,
                    skillDef.category, btnX, rowY, currentAssignment == entry.skillId
                ))
                btnX += BTN_WIDTH + BTN_GAP
            }
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context, mouseX, mouseY, delta)

        // Main panel background
        context.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, PANEL_BORDER)
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_COLOR)

        // Header
        context.fill(panelX, panelY, panelX + panelW, panelY + HEADER_HEIGHT, PANEL_HEADER)
        context.fill(panelX, panelY + HEADER_HEIGHT, panelX + panelW, panelY + HEADER_HEIGHT + 1, PANEL_BORDER)
        context.drawCenteredTextWithShadow(textRenderer, "\u00A7l\u00A7fSkill Assignment", panelX + panelW / 2, panelY + 6, 0xFFFFFF)
        context.drawCenteredTextWithShadow(textRenderer, "\u00A77Click to assign \u00A78| \u00A77Scroll: \u2191\u2193 \u00A78| \u00A77Shift+Scroll: \u2190\u2192", panelX + panelW / 2, panelY + 20, 0x888888)

        // Column headers
        val headerY = contentY - 12
        context.drawTextWithShadow(textRenderer, "\u00A7ePokemon", panelX + PANEL_PADDING, headerY, 0xFFFF55)
        context.drawTextWithShadow(textRenderer, "\u00A7eSkills", panelX + PANEL_PADDING + NAME_WIDTH, headerY, 0xFFFF55)

        // Content area with scissor
        val contentBottom = panelY + panelH - 28
        context.enableScissor(panelX, contentY - 2, panelX + panelW, contentBottom)

        // Row backgrounds + Pokemon names
        pokemonList.forEachIndexed { index, pokemonData ->
            val ry = contentY + index * ROW_HEIGHT + scrollY
            if (ry < contentY - ROW_HEIGHT || ry > contentBottom) return@forEachIndexed

            // Alternating row color
            val rowColor = if (index % 2 == 0) ROW_EVEN else ROW_ODD
            context.fill(panelX + 1, ry, panelX + panelW - 1, ry + ROW_HEIGHT - 1, rowColor)

            // Name + level
            val name = pokemonData.displayName.string
            context.drawTextWithShadow(textRenderer, name, panelX + PANEL_PADDING, ry + 4, 0xFFFFFF)
            context.drawTextWithShadow(textRenderer, "\u00A77Lv.${pokemonData.level}", panelX + PANEL_PADDING, ry + 14, 0xAAAAAA)
        }

        // Skill buttons
        for (btn in allButtons) {
            val rx = btn.baseX + scrollX
            val ry = btn.baseY + scrollY

            if (ry < contentY - ROW_HEIGHT || ry > contentBottom) continue
            if (rx + BTN_WIDTH < panelX + PANEL_PADDING + NAME_WIDTH || rx > panelX + panelW) continue

            val hovered = mouseX in rx..(rx + BTN_WIDTH) && mouseY in ry..(ry + BTN_HEIGHT)

            // Button background
            val categoryColor = CATEGORY_COLORS[btn.category] ?: 0xFF666666.toInt()
            val bg = when {
                btn.selected -> categoryColor
                hovered -> 0xFF4A4A6A.toInt()
                else -> 0xFF2A2A3E.toInt()
            }
            context.fill(rx, ry + 2, rx + BTN_WIDTH, ry + 2 + BTN_HEIGHT, bg)

            // Border
            val border = if (btn.selected) 0xFFFFFFFF.toInt() else 0xFF555577.toInt()
            context.drawHorizontalLine(rx, rx + BTN_WIDTH - 1, ry + 2, border)
            context.drawHorizontalLine(rx, rx + BTN_WIDTH - 1, ry + 1 + BTN_HEIGHT, border)
            context.drawVerticalLine(rx, ry + 2, ry + 1 + BTN_HEIGHT, border)
            context.drawVerticalLine(rx + BTN_WIDTH - 1, ry + 2, ry + 1 + BTN_HEIGHT, border)

            // Skill name
            val textColor = if (btn.selected) 0xFFFFFF else 0xBBBBBB
            val nameText = btn.displayName
            val nameWidth = textRenderer.getWidth(nameText)
            context.drawTextWithShadow(textRenderer, nameText, rx + (BTN_WIDTH - nameWidth) / 2, ry + 4, textColor)

            // Proficiency stars below name (only for actual skills, not Auto)
            if (btn.proficiency > 0) {
                val stars = "\u2605".repeat(btn.proficiency) + "\u2606".repeat(5 - btn.proficiency)
                val starColor = when {
                    btn.proficiency >= 5 -> 0xFFD700
                    btn.proficiency >= 4 -> 0xFFA500
                    btn.proficiency >= 3 -> 0x88CC88
                    else -> 0x888888
                }
                val starWidth = textRenderer.getWidth(stars)
                context.drawText(textRenderer, stars, rx + (BTN_WIDTH - starWidth) / 2, ry + 13, starColor, false)
            }
        }

        context.disableScissor()

        // Footer line
        context.fill(panelX, panelY + panelH - 28, panelX + panelW, panelY + panelH - 27, PANEL_BORDER)

        super.render(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (super.mouseClicked(mouseX, mouseY, button)) return true

        val contentBottom = panelY + panelH - 28
        for (btn in allButtons) {
            val rx = btn.baseX + scrollX
            val ry = btn.baseY + scrollY
            if (mouseX >= rx && mouseX <= rx + BTN_WIDTH &&
                mouseY >= ry + 2 && mouseY <= ry + 2 + BTN_HEIGHT &&
                mouseY >= contentY && mouseY < contentBottom) {
                selectSkill(btn.pokemonId, btn.skillId)
                return true
            }
        }
        return false
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (hasShiftDown()) {
            scrollX = (scrollX + verticalAmount.toInt() * 16).coerceAtMost(0)
        } else {
            scrollY = (scrollY + verticalAmount.toInt() * 16).coerceAtMost(0)
        }
        return true
    }

    private fun selectSkill(pokemonId: UUID, skillId: String?) {
        for (btn in allButtons) {
            if (btn.pokemonId == pokemonId) {
                btn.selected = btn.skillId == skillId
            }
        }
        ClientPlayNetworking.send(SkillAssignmentC2SPacket(pokemonId, skillId ?: ""))
    }

    override fun close() {
        client?.setScreen(null)
    }

    override fun shouldPause(): Boolean = false
}
// SkillButtonData is defined in SkillButtonData.java to avoid Fabric ClassLoader issues

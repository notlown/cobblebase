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

    private val ROW_HEIGHT = 22
    private val HEADER_Y = 8
    private val LIST_START_Y = 28
    private val NAME_COL_WIDTH = 85
    private val BTN_WIDTH = 58
    private val BTN_HEIGHT = 16
    private val BTN_SPACING = 2

    private var scrollX = 0
    private var scrollY = 0

    private data class SkillButton(
        val pokemonId: UUID,
        val skillId: String?, // null = Auto
        val displayName: String,
        var baseX: Int,
        var baseY: Int,
        var selected: Boolean
    )

    private val allButtons = mutableListOf<SkillButton>()

    override fun init() {
        super.init()
        allButtons.clear()
        scrollX = 0
        scrollY = 0

        addDrawableChild(ButtonWidget.builder(Text.literal("< Back")) { close() }
            .dimensions(4, height - 18, 50, 16).build())

        pokemonList.forEachIndexed { index, pokemonData ->
            val pokemonId = pokemonData.pokemonId
            val speciesName = pokemonData.species.path
            val currentAssignment = BaseManager.getAssignment(pokemonId)

            val speciesSkills = SpeciesSkillRegistry.getSkills(speciesName)
            val availableSkills = speciesSkills?.skills ?: emptyList()

            val y = LIST_START_Y + index * ROW_HEIGHT
            var btnX = NAME_COL_WIDTH + 4

            // Auto button
            allButtons.add(SkillButton(pokemonId, null, "Auto", btnX, y, currentAssignment == null))
            btnX += BTN_WIDTH + BTN_SPACING

            // Skill buttons - only skills this species can do
            for (entry in availableSkills) {
                val skillDef = SkillRegistry.get(entry.skillId) ?: continue
                val profStars = "\u2605".repeat(entry.proficiency.coerceIn(1, 5))
                allButtons.add(SkillButton(pokemonId, entry.skillId, skillDef.name, btnX, y, currentAssignment == entry.skillId))
                btnX += BTN_WIDTH + BTN_SPACING
            }
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context, mouseX, mouseY, delta)

        context.drawCenteredTextWithShadow(textRenderer, "\u00A7l\u00A7eSkill Assignment", width / 2, HEADER_Y, 0xFFFFFF)
        context.drawCenteredTextWithShadow(textRenderer, "\u00A77Scroll: Wheel | Shift+Scroll: Horizontal", width / 2, HEADER_Y + 11, 0x888888)

        context.enableScissor(0, LIST_START_Y - 2, width, height - 22)

        // Pokemon names
        pokemonList.forEachIndexed { index, pokemonData ->
            val y = LIST_START_Y + index * ROW_HEIGHT + scrollY
            if (y < LIST_START_Y - ROW_HEIGHT || y > height) return@forEachIndexed

            context.drawTextWithShadow(textRenderer, pokemonData.displayName.string, 4, y + 2, 0xFFFFFF)
            context.drawTextWithShadow(textRenderer, "\u00A77Lv.${pokemonData.level}", 4, y + 12, 0xAAAAAA)
        }

        // Skill buttons
        for (btn in allButtons) {
            val rx = btn.baseX + scrollX
            val ry = btn.baseY + scrollY

            if (ry < LIST_START_Y - ROW_HEIGHT || ry > height - 22) continue
            if (rx + BTN_WIDTH < NAME_COL_WIDTH || rx > width) continue

            val hovered = mouseX in rx..(rx + BTN_WIDTH) && mouseY in ry..(ry + BTN_HEIGHT)

            val bg = when {
                btn.selected -> 0xFF2D7D2D.toInt()
                hovered -> 0xFF555555.toInt()
                else -> 0xFF333333.toInt()
            }
            context.fill(rx, ry, rx + BTN_WIDTH, ry + BTN_HEIGHT, bg)

            val border = if (btn.selected) 0xFF44CC44.toInt() else 0xFF666666.toInt()
            context.drawHorizontalLine(rx, rx + BTN_WIDTH - 1, ry, border)
            context.drawHorizontalLine(rx, rx + BTN_WIDTH - 1, ry + BTN_HEIGHT - 1, border)
            context.drawVerticalLine(rx, ry, ry + BTN_HEIGHT - 1, border)
            context.drawVerticalLine(rx + BTN_WIDTH - 1, ry, ry + BTN_HEIGHT - 1, border)

            val textColor = if (btn.selected) 0xFFFFFF else 0xAAAAAA
            val textX = rx + (BTN_WIDTH - textRenderer.getWidth(btn.displayName)) / 2
            context.drawTextWithShadow(textRenderer, btn.displayName, textX, ry + 4, textColor)
        }

        context.disableScissor()
        super.render(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (super.mouseClicked(mouseX, mouseY, button)) return true

        for (btn in allButtons) {
            val rx = btn.baseX + scrollX
            val ry = btn.baseY + scrollY
            if (mouseX >= rx && mouseX <= rx + BTN_WIDTH &&
                mouseY >= ry && mouseY <= ry + BTN_HEIGHT &&
                mouseY >= LIST_START_Y && mouseY < height - 22) {
                selectSkill(btn.pokemonId, btn.skillId)
                return true
            }
        }
        return false
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (hasShiftDown()) {
            scrollX = (scrollX + verticalAmount.toInt() * 12).coerceAtMost(0)
        } else {
            scrollY = (scrollY + verticalAmount.toInt() * 12).coerceAtMost(0)
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
        client?.setScreen(parentScreen)
    }

    override fun shouldPause(): Boolean = false
}

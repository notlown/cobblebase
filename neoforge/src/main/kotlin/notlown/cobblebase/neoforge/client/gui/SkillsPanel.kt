package notlown.cobblebase.neoforge.client.gui

import notlown.cobblebase.core.BaseManager
import notlown.cobblebase.core.SkillRegistry
import notlown.cobblebase.core.SpeciesSkillRegistry
import notlown.cobblebase.core.net.SkillAssignmentC2SPacket
import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket.PasturePokemonDataDTO
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID
import java.util.function.Function

/**
 * Skills tab content - shows Pokemon list with skill assignment buttons and proficiency stars.
 */
class SkillsPanel(
    private val parent: CobblebaseScreen,
    private val pokemonList: List<PasturePokemonDataDTO>,
    private val panelX: Int,
    private val panelY: Int,
    private val panelW: Int,
    private val panelH: Int,
    private val textRenderer: TextRenderer
) {

    private val ROW_HEIGHT_SMALL = 24
    private val ROW_HEIGHT_LARGE = 42
    private val HEADER_HEIGHT = 14
    private val PANEL_PADDING = 8
    private val ICON_OFFSET = PokemonSpriteHelper.ICON_SIZE + 4
    private val NAME_WIDTH = 50 + ICON_OFFSET
    private val AURA_ICON_WIDTH = 15
    private val BTN_WIDTH = 58
    private val BTN_HEIGHT = 16
    private val BTN_GAP = 2

    private val ROW_EVEN = 0x44FFFFFF.toInt()
    private val ROW_ODD = 0x22FFFFFF.toInt()

    private val allButtons = mutableListOf<SkillButtonData>()
    private var scrollX = 0
    private var scrollY = 0
    private var contentY = 0
    private var isDraggingScrollbar = false

    private val rowOffsets = mutableListOf<Int>()
    private val rowHeights = mutableListOf<Int>()

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
        scrollX = 0
        scrollY = 0
        contentY = panelY + HEADER_HEIGHT + PANEL_PADDING

        addWidget.apply(ButtonWidget.builder(Text.literal("Done")) { parent.close() }
            .dimensions(panelX + panelW - 54, panelY + panelH - 16, 40, 12).build())

        var cumulativeY = 0

        pokemonList.forEachIndexed { index, pokemonData ->
            val pokemonId = pokemonData.pokemonId
            val speciesName = pokemonData.species.path
            val currentAssignment = BaseManager.getAssignment(pokemonId)
            val speciesSkills = SpeciesSkillRegistry.getSkills(speciesName)
            val availableSkills = speciesSkills?.skills ?: emptyList()

            val assignableCount = 1 + availableSkills.count { entry ->
                val skillDef = SkillRegistry.get(entry.skillId)
                skillDef != null && !BaseManager.isBuffExecutor(skillDef.executor)
            }

            // Aura column is always reserved so buttons stay aligned
            val btnStartX = panelX + PANEL_PADDING + NAME_WIDTH + AURA_ICON_WIDTH
            val maxBtnX = panelX + panelW - PANEL_PADDING - BTN_WIDTH
            val btnsPerRow = ((maxBtnX - btnStartX) / (BTN_WIDTH + BTN_GAP)) + 1
            val needsTwoRows = assignableCount > btnsPerRow
            val rowH = if (needsTwoRows) ROW_HEIGHT_LARGE else ROW_HEIGHT_SMALL

            rowOffsets.add(cumulativeY)
            rowHeights.add(rowH)

            val rowY = contentY + cumulativeY
            var btnX = btnStartX
            var btnY = rowY

            allButtons.add(SkillButtonData(pokemonId, null, "Auto", 0, "", btnX, btnY, currentAssignment == null))
            btnX += BTN_WIDTH + BTN_GAP

            for (entry in availableSkills) {
                val skillDef = SkillRegistry.get(entry.skillId) ?: continue
                if (BaseManager.isBuffExecutor(skillDef.executor)) continue
                if (btnX > maxBtnX) {
                    btnX = btnStartX
                    btnY = rowY + BTN_HEIGHT + BTN_GAP
                }
                allButtons.add(SkillButtonData(
                    pokemonId, entry.skillId, skillDef.name, entry.proficiency,
                    skillDef.category, btnX, btnY, currentAssignment == entry.skillId
                ))
                btnX += BTN_WIDTH + BTN_GAP
            }

            cumulativeY += rowH
        }
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val headerY = contentY - 12
        context.drawTextWithShadow(textRenderer, "\u00A7ePokemon", panelX + PANEL_PADDING, headerY, 0xFFFF55)
        context.drawTextWithShadow(textRenderer, "\u00A7eSkills", panelX + PANEL_PADDING + NAME_WIDTH + AURA_ICON_WIDTH, headerY, 0xFFFF55)

        val contentBottom = panelY + panelH - 18
        context.enableScissor(panelX, contentY - 2, panelX + panelW, contentBottom)

        pokemonList.forEachIndexed { index, pokemonData ->
            val rowH = rowHeights[index]
            val ry = contentY + rowOffsets[index] + scrollY
            if (ry < contentY - ROW_HEIGHT_LARGE || ry > contentBottom) return@forEachIndexed

            val rowColor = if (index % 2 == 0) ROW_EVEN else ROW_ODD
            context.fill(panelX + 1, ry, panelX + panelW - 1, ry + rowH - 1, rowColor)

            val name = pokemonData.displayName.string
            PokemonSpriteHelper.renderIcon(
                context, textRenderer, pokemonData.species, name, pokemonData.aspects,
                panelX + PANEL_PADDING, ry + 4, delta
            )

            context.drawTextWithShadow(textRenderer, name, panelX + PANEL_PADDING + ICON_OFFSET, ry + 4, 0xFFFFFF)
            context.drawTextWithShadow(textRenderer, "\u00A77Lv.${pokemonData.level}", panelX + PANEL_PADDING + ICON_OFFSET, ry + 14, 0xAAAAAA)

            // Aura icon between Pokemon and Skills (only if mon has buff)
            val speciesName = pokemonData.species.path
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
                    context.drawTextWithShadow(textRenderer, buffEmoji, auraX, ry + 4, 0xFFFFFF)
                }
            }
        }

        for (btn in allButtons) {
            val rx = btn.baseX + scrollX
            val ry = btn.baseY + scrollY

            if (ry < contentY - ROW_HEIGHT_LARGE || ry > contentBottom) continue
            if (rx + BTN_WIDTH < panelX + PANEL_PADDING + NAME_WIDTH || rx > panelX + panelW) continue

            val hovered = mouseX in rx..(rx + BTN_WIDTH) && mouseY in ry..(ry + BTN_HEIGHT)

            val categoryColor = CobblebaseScreen.CATEGORY_COLORS[btn.category] ?: 0xFF666666.toInt()
            val bg = when {
                btn.selected -> categoryColor
                hovered -> 0xFF4A4A6A.toInt()
                else -> 0xFF2A2A3E.toInt()
            }
            context.fill(rx, ry + 2, rx + BTN_WIDTH, ry + 2 + BTN_HEIGHT, bg)

            val border = if (btn.selected) 0xFFFFFFFF.toInt() else 0xFF555577.toInt()
            context.drawHorizontalLine(rx, rx + BTN_WIDTH - 1, ry + 2, border)
            context.drawHorizontalLine(rx, rx + BTN_WIDTH - 1, ry + 1 + BTN_HEIGHT, border)
            context.drawVerticalLine(rx, ry + 2, ry + 1 + BTN_HEIGHT, border)
            context.drawVerticalLine(rx + BTN_WIDTH - 1, ry + 2, ry + 1 + BTN_HEIGHT, border)

            val textColor = if (btn.selected) 0xFFFFFF else 0xBBBBBB
            val nameText = btn.displayName
            val nameWidth = textRenderer.getWidth(nameText)
            context.drawTextWithShadow(textRenderer, nameText, rx + (BTN_WIDTH - nameWidth) / 2, ry + 4, textColor)

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

        totalContentHeight = if (rowOffsets.isEmpty()) 0 else rowOffsets.last() + rowHeights.last()
        visibleHeight = contentBottom - contentY
        if (totalContentHeight > visibleHeight) {
            trackX = panelX + panelW - 8
            trackTop = contentY
            trackHeight = visibleHeight
            context.fill(trackX, trackTop, trackX + 6, trackTop + trackHeight, 0x44FFFFFF.toInt())
            thumbHeight = (visibleHeight.toFloat() / totalContentHeight * trackHeight).toInt().coerceAtLeast(16)
            val scrollRange = totalContentHeight - visibleHeight
            val scrollProgress = (-scrollY).toFloat() / scrollRange.coerceAtLeast(1)
            thumbY = trackTop + ((trackHeight - thumbHeight) * scrollProgress).toInt()
            val isHovered = mouseX in trackX..(trackX + 6) && mouseY in thumbY..(thumbY + thumbHeight)
            val thumbColor = if (isDraggingScrollbar || isHovered) 0xFFDDDDDD.toInt() else 0xFFAAAAAA.toInt()
            context.fill(trackX, thumbY, trackX + 6, thumbY + thumbHeight, thumbColor)
        }

        context.fill(panelX, panelY + panelH - 18, panelX + panelW, panelY + panelH - 17, CobblebaseScreen.PANEL_BORDER)
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
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
            val rx = btn.baseX + scrollX
            val ry = btn.baseY + scrollY
            if (mouseX >= rx && mouseX <= rx + BTN_WIDTH &&
                mouseY >= ry + 2 && mouseY <= ry + 2 + BTN_HEIGHT &&
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
        return false
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false
            return true
        }
        return false
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
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
        PacketDistributor.sendToServer(SkillAssignmentC2SPacket(pokemonId, skillId ?: ""))
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
        return null
    }
}

package notlown.cobblebase.fabric.client.gui

import notlown.cobblebase.core.AssignmentCache
import notlown.cobblebase.core.BaseManager
import notlown.cobblebase.core.SkillRegistry
import notlown.cobblebase.core.SpeciesSkillRegistry
import notlown.cobblebase.core.CobblebaseConfig
import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket.PasturePokemonDataDTO
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import java.util.function.Function

/**
 * Buffs tab content - shows all currently active jobs/effects for this Pasture.
 * Each active Pokemon shows: Pokemon Name | Job Name | Effect Description
 * Color-coded by skill category.
 */
class BuffsPanel(
    private val parent: CobblebaseScreen,
    private val pokemonList: List<PasturePokemonDataDTO>,
    private val pastureOrigin: BlockPos?,
    private val panelX: Int,
    private val panelY: Int,
    private val panelW: Int,
    private val panelH: Int,
    private val textRenderer: TextRenderer
) {

    private val ROW_HEIGHT = 28
    private val HEADER_HEIGHT = 18
    private val PADDING = 8
    private val ROW_EVEN = 0x33FFFFFF.toInt()
    private val ROW_ODD = 0x18FFFFFF.toInt()

    private var scrollY = 0
    private var isDraggingScrollbar = false

    // Scrollbar track dimensions (updated each render)
    private var trackX = 0
    private var trackTop = 0
    private var trackHeight = 0
    private var totalContentHeight = 0
    private var visibleHeight = 0
    private var thumbHeight = 0
    private var thumbY = 0

    data class BuffEntry(
        val pokemonName: String,
        val species: Identifier,
        val aspects: Set<String>,
        val level: Int,
        val skillName: String,
        val category: String,
        val proficiency: Int,
        val description: String,
        val isPassiveBuff: Boolean = false
    )

    private var entries = listOf<BuffEntry>()

    fun init(addWidget: Function<ButtonWidget, ButtonWidget>) {
        scrollY = 0
        entries = buildBuffEntries()

        addWidget.apply(ButtonWidget.builder(Text.literal("Done")) { parent.close() }
            .dimensions(panelX + panelW - 54, panelY + panelH - 16, 40, 12).build())
    }

    private fun buildBuffEntries(): List<BuffEntry> {
        val result = mutableListOf<BuffEntry>()

        for (pokemonData in pokemonList) {
            val pokemonId = pokemonData.pokemonId
            val speciesName = pokemonData.species.path
            val pokemonName = pokemonData.displayName.string
            val species = pokemonData.species
            val aspects = pokemonData.aspects
            val level = pokemonData.level
            val assignment = AssignmentCache.getAssignment(pokemonId)
            val speciesSkills = SpeciesSkillRegistry.getSkills(speciesName)
            val availableSkills = speciesSkills?.skills ?: emptyList()

            // Always add passive buff skills (they are always active in the pasture)
            for (entry in availableSkills) {
                val skillDef = SkillRegistry.get(entry.skillId) ?: continue
                if (BaseManager.isBuffExecutor(skillDef.executor)) {
                    result.add(BuffEntry(
                        pokemonName = pokemonName,
                        species = species,
                        aspects = aspects,
                        level = level,
                        skillName = skillDef.name,
                        category = skillDef.category,
                        proficiency = entry.proficiency,
                        description = generateDescription(skillDef.name, skillDef.executor, entry.proficiency, skillDef.cooldownSeconds),
                        isPassiveBuff = true
                    ))
                }
            }

            // Add assigned job (non-buff skills only)
            if (assignment != null) {
                val entry = availableSkills.find { it.skillId == assignment }
                if (entry != null) {
                    val skillDef = SkillRegistry.get(entry.skillId)
                    if (skillDef != null && !BaseManager.isBuffExecutor(skillDef.executor)) {
                        result.add(BuffEntry(
                            pokemonName = pokemonName,
                            species = species,
                            aspects = aspects,
                            level = level,
                            skillName = skillDef.name,
                            category = skillDef.category,
                            proficiency = entry.proficiency,
                            description = generateDescription(skillDef.name, skillDef.executor, entry.proficiency, skillDef.cooldownSeconds)
                        ))
                    }
                }
            }
            // Relax (null assignment) = only passive buffs shown (already added above)
        }
        return result
    }

    private fun generateDescription(skillName: String, executor: String, proficiency: Int, cooldownSeconds: Long): String {
        val prof = proficiency.coerceIn(1, 5)
        val effectiveCooldown = if (CobblebaseConfig.devMode) 5L
            else cooldownSeconds * (6 - prof) / 3

        return when (executor) {
            "mentor" -> {
                val multiplier = (prof / 3.0) * CobblebaseConfig.mentorMaxBoost
                val percent = ((multiplier) * 100).toInt()
                "+${percent}% Bonus XP every 60s"
            }
            "harvester" -> "Harvesting crops every ${effectiveCooldown}s"
            "mining" -> "Mining for ores, fossils, and gems"
            "scout" -> "Scouting for wild Pokemon and structures"
            "fishing" -> "Fishing every ${effectiveCooldown}s"
            "guard" -> "Defending base, repelling wild Pokemon"
            "gather_items" -> "Sorting items into nearby chests"
            "healer" -> {
                val monsCount = if (prof >= 5) 6 else prof
                "Healing $monsCount team Pokemon every 180s"
            }
            "finder", "finder_evo", "finder_hea", "finder_bui", "finder_ore", "finder_see", "finder_bal", "finder_exp",
            "finder_food", "finder_stat", "finder_held", "finder_treasure", "finder_smith" -> {
                val typeLabel = when (executor) {
                    "finder_evo" -> "evolution items"
                    "finder_hea" -> "healing items"
                    "finder_bui" -> "building materials"
                    "finder_ore" -> "ores"
                    "finder_see" -> "seeds & mulch"
                    "finder_bal" -> "Pokeballs"
                    "finder_exp" -> "XP Candies"
                    "finder_food" -> "food & cooking items"
                    "finder_stat" -> "vitamins & training items"
                    "finder_held" -> "battle held items"
                    "finder_treasure" -> "relics & treasure"
                    "finder_smith" -> "smithing templates & pottery"
                    else -> "items"
                }
                "Finding $typeLabel"
            }
            "irrigate" -> "Hydrating farmland, boosting growth"
            "recruiter" -> "Attracting wild Pokemon to the area"
            "cauldron_fill" -> "Filling cauldrons"
            "furnace_fuel", "brew_fuel" -> "Fueling furnaces/brewers"
            "speed_boost" -> "Speed II boost for nearby players"
            "strength_boost" -> "Strength boost for nearby players"
            "resistance_boost" -> "Resistance boost for nearby players"
            "night_vision" -> "Night Vision for nearby players"
            "water_breathing" -> "Water Breathing for nearby players"
            "jump_boost" -> "Jump Boost for nearby players"
            "haste_boost" -> "Haste boost for nearby players"
            "saturation_boost" -> "Saturation for nearby players"
            "lucky_charm" -> "Boosts shiny rate for wild Pokemon"
            "extinguish" -> "Extinguishes fire near the base"
            "aura" -> "Luck boost for nearby players"
            "growth" -> "Accelerates crop growth nearby"
            else -> "Active"
        }
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val contentTop = panelY + HEADER_HEIGHT
        val contentBottom = panelY + panelH - 18



        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, "\u00A77No Pokemon in this Pasture", panelX + panelW / 2, panelY + panelH / 2 - 4, 0x888888)
            return
        }

        // Column headers (offset for sprite icon)
        val ICON_OFFSET = PokemonSpriteHelper.ICON_SIZE + 4 // 16px icon + 4px gap
        val colPokemon = panelX + PADDING
        val colSkill = panelX + PADDING + 80 + ICON_OFFSET
        val colDesc = panelX + PADDING + 150 + ICON_OFFSET
        context.drawTextWithShadow(textRenderer, "\u00A7ePokemon", colPokemon + ICON_OFFSET, contentTop, 0xFFFF55)
        context.drawTextWithShadow(textRenderer, "\u00A7eJob", colSkill, contentTop, 0xFFFF55)
        context.drawTextWithShadow(textRenderer, "\u00A7eEffect", colDesc, contentTop, 0xFFFF55)

        // Scrollable content
        context.enableScissor(panelX, contentTop + 12, panelX + panelW, contentBottom)

        for ((index, entry) in entries.withIndex()) {
            val ry = contentTop + 14 + index * ROW_HEIGHT + scrollY
            if (ry < contentTop - ROW_HEIGHT || ry > contentBottom) continue

            // Row background
            val rowColor = if (index % 2 == 0) ROW_EVEN else ROW_ODD
            context.fill(panelX + 1, ry, panelX + panelW - 1, ry + ROW_HEIGHT - 1, rowColor)

            // Category color bar on the left (green for passive buffs)
            val catColor = if (entry.isPassiveBuff) 0xFF55FFAA.toInt()
                else CobblebaseScreen.CATEGORY_COLORS[entry.category] ?: 0xFF666666.toInt()
            context.fill(panelX + 1, ry, panelX + 4, ry + ROW_HEIGHT - 1, catColor)

            val scale = 0.75f

            // Pokemon portrait icon (top-aligned)
            PokemonSpriteHelper.renderIcon(
                context, textRenderer, entry.species, entry.pokemonName, entry.aspects,
                colPokemon + 4, ry + 4, delta
            )

            // Pokemon name + level (shifted right for icon, scaled 0.75x)
            val nameX = (colPokemon + 4 + ICON_OFFSET).toFloat()
            context.matrices.push()
            context.matrices.translate(nameX, (ry + 4).toFloat(), 0f)
            context.matrices.scale(scale, scale, 1f)
            context.drawTextWithShadow(textRenderer, entry.pokemonName, 0, 0, 0xFFFFFF)
            context.matrices.pop()

            context.matrices.push()
            context.matrices.translate(nameX, (ry + 14).toFloat(), 0f)
            context.matrices.scale(scale, scale, 1f)
            context.drawTextWithShadow(textRenderer, "\u00A77Lv.${entry.level}", 0, 0, 0xAAAAAA)
            context.matrices.pop()

            // Skill name with category color + PASSIVE tag for buff skills (scaled 0.75x)
            context.matrices.push()
            context.matrices.translate(colSkill.toFloat(), (ry + 4).toFloat(), 0f)
            context.matrices.scale(scale, scale, 1f)
            if (entry.isPassiveBuff) {
                context.drawTextWithShadow(textRenderer, entry.skillName, 0, 0, 0x55FFAA)
                context.drawTextWithShadow(textRenderer, "\u00A72PASSIVE", textRenderer.getWidth(entry.skillName) + 4, 0, 0x55FF55)
            } else {
                context.drawTextWithShadow(textRenderer, entry.skillName, 0, 0, catColor)
            }
            context.matrices.pop()

            // Proficiency stars (scaled 0.75x)
            val stars = "\u2605".repeat(entry.proficiency) + "\u2606".repeat(5 - entry.proficiency)
            val starColor = when {
                entry.proficiency >= 5 -> 0xFFD700
                entry.proficiency >= 4 -> 0xFFA500
                entry.proficiency >= 3 -> 0x88CC88
                else -> 0x888888
            }
            context.matrices.push()
            context.matrices.translate(colSkill.toFloat(), (ry + 14).toFloat(), 0f)
            context.matrices.scale(scale, scale, 1f)
            context.drawText(textRenderer, stars, 0, 0, starColor, false)
            context.matrices.pop()

            // Effect description (scaled 0.75x)
            val descColor = if (entry.isPassiveBuff) 0x88DDAA else 0xCCCCCC
            context.matrices.push()
            context.matrices.translate(colDesc.toFloat(), (ry + 9).toFloat(), 0f)
            context.matrices.scale(scale, scale, 1f)
            context.drawTextWithShadow(textRenderer, entry.description, 0, 0, descColor)
            context.matrices.pop()
        }

        context.disableScissor()

        // Scrollbar
        totalContentHeight = entries.size * ROW_HEIGHT
        visibleHeight = contentBottom - contentTop - 14
        if (totalContentHeight > visibleHeight) {
            trackX = panelX + panelW - 8
            trackTop = contentTop + 14
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

        // Entry count
        context.drawTextWithShadow(textRenderer, "\u00A78${entries.size} active", panelX + PADDING, panelY + panelH - 14, 0x666666)
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        scrollY = (scrollY + verticalAmount.toInt() * 16).coerceAtMost(0)
        clampScroll()
        return true
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (totalContentHeight <= visibleHeight) return false
        if (mouseX >= trackX && mouseX <= trackX + 6 && mouseY >= trackTop && mouseY <= trackTop + trackHeight) {
            isDraggingScrollbar = true
            updateScrollFromMouse(mouseY)
            return true
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
}

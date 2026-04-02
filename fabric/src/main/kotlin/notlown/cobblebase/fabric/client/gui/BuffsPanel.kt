package notlown.cobblebase.fabric.client.gui

import notlown.cobblebase.core.BaseManager
import notlown.cobblebase.core.SkillRegistry
import notlown.cobblebase.core.SpeciesSkillRegistry
import notlown.cobblebase.core.CobblebaseConfig
import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket.PasturePokemonDataDTO
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
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

    data class BuffEntry(
        val pokemonName: String,
        val level: Int,
        val skillName: String,
        val category: String,
        val proficiency: Int,
        val description: String
    )

    private var entries = listOf<BuffEntry>()

    fun init(addWidget: Function<ButtonWidget, ButtonWidget>) {
        scrollY = 0
        entries = buildBuffEntries()

        addWidget.apply(ButtonWidget.builder(Text.literal("Done")) { parent.close() }
            .dimensions(panelX + panelW - 54, panelY + panelH - 22, 46, 16).build())
    }

    private fun buildBuffEntries(): List<BuffEntry> {
        val result = mutableListOf<BuffEntry>()

        for (pokemonData in pokemonList) {
            val pokemonId = pokemonData.pokemonId
            val speciesName = pokemonData.species.path
            val pokemonName = pokemonData.displayName.string
            val level = pokemonData.level
            val assignment = BaseManager.getAssignment(pokemonId)
            val speciesSkills = SpeciesSkillRegistry.getSkills(speciesName)
            val availableSkills = speciesSkills?.skills ?: emptyList()

            if (assignment != null) {
                // Specific skill assigned
                val entry = availableSkills.find { it.skillId == assignment }
                if (entry != null) {
                    val skillDef = SkillRegistry.get(entry.skillId)
                    if (skillDef != null) {
                        result.add(BuffEntry(
                            pokemonName = pokemonName,
                            level = level,
                            skillName = skillDef.name,
                            category = skillDef.category,
                            proficiency = entry.proficiency,
                            description = generateDescription(skillDef.name, skillDef.executor, entry.proficiency, skillDef.cooldownSeconds)
                        ))
                    }
                }
            } else {
                // Auto mode: all skills active
                for (entry in availableSkills) {
                    val skillDef = SkillRegistry.get(entry.skillId) ?: continue
                    result.add(BuffEntry(
                        pokemonName = pokemonName,
                        level = level,
                        skillName = skillDef.name,
                        category = skillDef.category,
                        proficiency = entry.proficiency,
                        description = generateDescription(skillDef.name, skillDef.executor, entry.proficiency, skillDef.cooldownSeconds)
                    ))
                }
            }
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
            "harvester", "mining" -> "Harvesting crops every ${effectiveCooldown}s"
            "fishing" -> "Fishing every ${effectiveCooldown}s"
            "guard" -> "Defending base, repelling wild Pokemon"
            "gather_items" -> "Sorting items into nearby chests"
            "healer" -> {
                val monsCount = if (prof >= 5) 6 else prof
                "Healing $monsCount team Pokemon every 180s"
            }
            "finder", "finder_evo", "finder_hea", "finder_bui", "finder_ore", "finder_see", "finder_bal", "finder_exp" -> {
                val typeLabel = when (executor) {
                    "finder_evo" -> "evolution items"
                    "finder_hea" -> "healing items"
                    "finder_bui" -> "building materials"
                    "finder_ore" -> "ores"
                    "finder_see" -> "seeds"
                    "finder_bal" -> "Pokeballs"
                    "finder_exp" -> "XP Candies"
                    else -> "items"
                }
                "Finding $typeLabel"
            }
            "irrigate" -> "Hydrating farmland, boosting growth"
            "recruiter" -> "Attracting wild Pokemon to the area"
            "cauldron_fill" -> "Filling cauldrons"
            "furnace_fuel", "brew_fuel" -> "Fueling furnaces/brewers"
            else -> "Active"
        }
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val contentTop = panelY + HEADER_HEIGHT
        val contentBottom = panelY + panelH - 28

        // Header
        context.drawCenteredTextWithShadow(textRenderer, "\u00A7fActive Buffs & Jobs", panelX + panelW / 2, panelY + 4, 0xFFFFFF)

        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, "\u00A77No Pokemon in this Pasture", panelX + panelW / 2, panelY + panelH / 2 - 4, 0x888888)
            return
        }

        // Column headers
        val colPokemon = panelX + PADDING
        val colSkill = panelX + PADDING + 110
        val colDesc = panelX + PADDING + 180
        context.drawTextWithShadow(textRenderer, "\u00A7ePokemon", colPokemon, contentTop, 0xFFFF55)
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

            // Category color bar on the left
            val catColor = CobblebaseScreen.CATEGORY_COLORS[entry.category] ?: 0xFF666666.toInt()
            context.fill(panelX + 1, ry, panelX + 4, ry + ROW_HEIGHT - 1, catColor)

            // Pokemon name + level
            context.drawTextWithShadow(textRenderer, entry.pokemonName, colPokemon + 4, ry + 4, 0xFFFFFF)
            context.drawTextWithShadow(textRenderer, "\u00A77Lv.${entry.level}", colPokemon + 4, ry + 14, 0xAAAAAA)

            // Skill name with category color
            context.drawTextWithShadow(textRenderer, entry.skillName, colSkill, ry + 4, catColor)

            // Proficiency stars
            val stars = "\u2605".repeat(entry.proficiency) + "\u2606".repeat(5 - entry.proficiency)
            val starColor = when {
                entry.proficiency >= 5 -> 0xFFD700
                entry.proficiency >= 4 -> 0xFFA500
                entry.proficiency >= 3 -> 0x88CC88
                else -> 0x888888
            }
            context.drawText(textRenderer, stars, colSkill, ry + 14, starColor, false)

            // Effect description
            context.drawTextWithShadow(textRenderer, entry.description, colDesc, ry + 9, 0xCCCCCC)
        }

        context.disableScissor()

        // Scrollbar
        val totalContentHeight = entries.size * ROW_HEIGHT
        val visibleHeight = contentBottom - contentTop - 14
        if (totalContentHeight > visibleHeight) {
            val trackX = panelX + panelW - 6
            val trackTop = contentTop + 14
            val trackHeight = visibleHeight
            // Track background
            context.fill(trackX, trackTop, trackX + 4, trackTop + trackHeight, 0x44FFFFFF.toInt())
            // Thumb
            val thumbHeight = (visibleHeight.toFloat() / totalContentHeight * trackHeight).toInt().coerceAtLeast(16)
            val scrollRange = totalContentHeight - visibleHeight
            val scrollProgress = (-scrollY).toFloat() / scrollRange.coerceAtLeast(1)
            val thumbY = trackTop + ((trackHeight - thumbHeight) * scrollProgress).toInt()
            context.fill(trackX, thumbY, trackX + 4, thumbY + thumbHeight, 0xFFAAAAAA.toInt())
        }

        // Footer line
        context.fill(panelX, panelY + panelH - 28, panelX + panelW, panelY + panelH - 27, CobblebaseScreen.PANEL_BORDER)

        // Entry count
        context.drawTextWithShadow(textRenderer, "\u00A78${entries.size} active", panelX + PADDING, panelY + panelH - 22, 0x666666)
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        scrollY = (scrollY + verticalAmount.toInt() * 16).coerceAtMost(0)
        return true
    }
}

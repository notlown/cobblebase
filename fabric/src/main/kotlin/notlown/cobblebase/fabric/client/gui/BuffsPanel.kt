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

    // Compacted from 28 — Active/Passive entries had way too much vertical breathing
    // room after removing the category color bar and packing the name + level into the
    // same column. 22 keeps the 16-px sprite (now scaled 1.3× to 20-21 px) fully visible
    // and the two-line name/level stack readable, without wasting half a row on padding.
    private val ROW_HEIGHT = 22
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
        val skillId: String,
        val skillName: String,
        val category: String,
        val proficiency: Int,
        val description: String,
        val isPassiveBuff: Boolean = false
    )

    private var entries = listOf<BuffEntry>()

    private enum class SubTab { ACTIVE, PASSIVE }
    private var activeSubTab = SubTab.ACTIVE
    private val SUBTAB_H = 16
    private var activeTabBox = intArrayOf(0, 0, 0, 0)
    private var passiveTabBox = intArrayOf(0, 0, 0, 0)

    fun init(addWidget: Function<ButtonWidget, ButtonWidget>) {
        scrollY = 0
        entries = buildBuffEntries()

        addWidget.apply(ButtonWidget.builder(Text.literal("Done")) { parent.close() }
            .dimensions(panelX + panelW - 54, panelY + panelH - 16, 40, 12).build())
    }

    /** Returns entries filtered to the active sub-tab. */
    private fun visibleEntries(): List<BuffEntry> = when (activeSubTab) {
        SubTab.ACTIVE -> entries.filter { !it.isPassiveBuff }
        SubTab.PASSIVE -> entries.filter { it.isPassiveBuff }
    }

    private fun renderSubTabs(context: DrawContext, mouseX: Int, mouseY: Int): Int {
        val tabsY = panelY + 2
        val tabH = SUBTAB_H
        val gap = 4
        val activeW = 60
        val passiveW = 64
        val activeX = panelX + PADDING
        val passiveX = activeX + activeW + gap
        activeTabBox = intArrayOf(activeX, tabsY, activeW, tabH)
        passiveTabBox = intArrayOf(passiveX, tabsY, passiveW, tabH)

        renderSubTabButton(context, "Active", activeTabBox, activeSubTab == SubTab.ACTIVE, mouseX, mouseY, 0xFFFF9800.toInt())
        renderSubTabButton(context, "Passive", passiveTabBox, activeSubTab == SubTab.PASSIVE, mouseX, mouseY, 0xFF55FFAA.toInt())

        // Per-tab counter on the right.
        val visible = visibleEntries()
        val counter = "§8${visible.size} entries"
        val counterW = textRenderer.getWidth(counter)
        context.drawTextWithShadow(textRenderer, counter, panelX + panelW - PADDING - counterW, tabsY + 4, 0x666666)

        return tabsY + tabH + 4
    }

    private fun renderSubTabButton(
        context: DrawContext,
        label: String,
        box: IntArray,
        active: Boolean,
        mouseX: Int, mouseY: Int,
        accent: Int
    ) {
        val (x, y, w, h) = box
        val hovered = mouseX in x..(x + w) && mouseY in y..(y + h)
        val bg = when {
            active -> 0xFF2A2A4A.toInt()
            hovered -> 0xFF252540.toInt()
            else -> 0xFF1A1A2A.toInt()
        }
        context.fill(x, y, x + w, y + h, bg)
        if (active) context.fill(x, y + h - 2, x + w, y + h, accent)
        val labelW = textRenderer.getWidth(label)
        val color = if (active) 0xFFFFFF else 0x999999
        context.drawTextWithShadow(textRenderer, label, x + (w - labelW) / 2, y + 4, color)
    }

    private fun inBox(mx: Double, my: Double, box: IntArray): Boolean {
        val (x, y, w, h) = box
        return mx >= x && mx <= x + w && my >= y && my <= y + h
    }

    private fun buildBuffEntries(): List<BuffEntry> {
        val result = mutableListOf<BuffEntry>()

        for (pokemonData in pokemonList) {
            val pokemonId = pokemonData.pokemonId
            val speciesName = SpeciesSkillRegistry.resolveFormName(pokemonData.species.path, pokemonData.aspects)
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
                        skillId = skillDef.id,
                        skillName = skillDef.name,
                        category = skillDef.category,
                        proficiency = entry.proficiency,
                        description = generateDescription(skillDef.name, skillDef.executor, entry.proficiency, skillDef.cooldownSeconds, speciesName),
                        isPassiveBuff = true
                    ))
                }
            }

            // Add assigned job (non-buff skills only)
            if (assignment != null) {
                if (assignment.startsWith("craftsman_supply:")) {
                    // Synthetic entry — supplier Mons don't have a regular skill assigned
                    // (the assignment string is "craftsman_supply:<craftsmanSkillId>:<itemId>"),
                    // so they were getting dropped from the Active sub-tab entirely.
                    val parts = assignment.split(":")
                    val targetItem = if (parts.size >= 4) parts.last() else null
                    val materialLabel = targetItem
                        ?.substringAfterLast(":")
                        ?.replace("_", " ")
                        ?.split(" ")
                        ?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                    val desc = if (materialLabel != null) "Supplying §6$materialLabel§7 for the Craftsman"
                        else "Helping the Craftsman by sourcing materials"
                    result.add(BuffEntry(
                        pokemonName = pokemonName,
                        species = species,
                        aspects = aspects,
                        level = level,
                        skillId = "cobblebase:craftsman",
                        skillName = "Craftsman Supplier",
                        category = "gathering",
                        proficiency = 0,
                        description = desc
                    ))
                } else {
                    val entry = availableSkills.find { it.skillId == assignment }
                    if (entry != null) {
                        val skillDef = SkillRegistry.get(entry.skillId)
                        if (skillDef != null && !BaseManager.isBuffExecutor(skillDef.executor)) {
                            result.add(BuffEntry(
                                pokemonName = pokemonName,
                                species = species,
                                aspects = aspects,
                                level = level,
                                skillId = skillDef.id,
                                skillName = skillDef.name,
                                category = skillDef.category,
                                proficiency = entry.proficiency,
                                description = generateDescription(skillDef.name, skillDef.executor, entry.proficiency, skillDef.cooldownSeconds, speciesName)
                            ))
                        }
                    }
                }
            }
            // Relax (null assignment) = only passive buffs shown (already added above)
        }
        return result
    }

    private fun generateDescription(skillName: String, executor: String, proficiency: Int, cooldownSeconds: Long, speciesName: String = ""): String {
        val prof = proficiency.coerceIn(1, 5)
        val effectiveCooldown = if (CobblebaseConfig.devMode) 5L
            else cooldownSeconds * (6 - prof) / 3
        val cooldownLabel = if (effectiveCooldown > 0) " · every ${effectiveCooldown}s" else ""

        return when (executor) {
            "mentor" -> {
                val multiplier = (prof / 3.0) * CobblebaseConfig.mentorMaxBoost
                val percent = ((multiplier) * 100).toInt()
                "Pokemon gain ${percent}% Passive XP · every 60s"
            }
            "harvester" -> "Harvesting crops, berries & apricorns$cooldownLabel"
            "mining" -> "Mining ores, fossils & gems$cooldownLabel"
            "scout" -> "Scouting for wild Pokemon$cooldownLabel"
            "fishing" -> "Fishing$cooldownLabel"
            "guard" -> "Defending base$cooldownLabel"
            "gather_items" -> "Sorting items into chests$cooldownLabel"
            "healer" -> {
                val monsCount = if (prof >= 5) 6 else prof
                "Healing $monsCount team Pokemon · every 180s"
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
                "Finding $typeLabel$cooldownLabel"
            }
            "irrigate" -> "Hydrating farmland, boosting growth"
            "recruiter" -> "Attracting wild Pokemon$cooldownLabel"
            "cauldron_fill" -> "Filling cauldrons$cooldownLabel"
            "furnace_fuel", "brew_fuel" -> "Fueling furnaces/brewers$cooldownLabel"
            "producer" -> {
                val produce = notlown.cobblebase.core.executors.ProducerExecutor.getProduceEntry(speciesName)
                // Use per-species cooldown if set, otherwise fall back to global
                val producerCooldown = produce?.cooldownSeconds ?: cooldownSeconds
                val effectiveProducerCd = if (CobblebaseConfig.devMode) 5L
                    else producerCooldown * (6 - prof) / 3
                val producerCdLabel = if (effectiveProducerCd > 0) " · every ${effectiveProducerCd}s" else ""
                if (produce != null) "Producing ${produce.displayName} x${produce.count}$producerCdLabel"
                else "Producing items$producerCdLabel"
            }
            // Passive buffs — no cooldown shown (always active)
            "speed_boost" -> "Speed II for nearby players"
            "strength_boost" -> "Strength for nearby players"
            "resistance_boost" -> "Resistance for nearby players"
            "night_vision" -> "Night Vision for nearby players"
            "water_breathing" -> "Water Breathing for nearby players"
            "jump_boost" -> "Jump Boost for nearby players"
            "haste_boost" -> "Haste for nearby players"
            "saturation_boost" -> "Saturation for nearby players"
            "lucky_charm" -> "Boosts shiny rate for wild Pokemon"
            "extinguish" -> "Extinguishes fire near the base"
            "aura" -> "Luck for nearby players"
            "growth" -> "Accelerates crop growth nearby"
            "egg_hatcher" -> {
                // Speed multiplier is the prof-keyed tuning field; falls back to prof level
                // (default linear scaling) when admins haven't customized it.
                val tuningKey = "prof${prof}Speed"
                val multiplier = try {
                    SkillRegistry.getEffectiveTuning("cobblebase:egg_hatcher", tuningKey, prof.toDouble())
                } catch (_: Throwable) { prof.toDouble() }
                val label = if (multiplier % 1.0 == 0.0) multiplier.toInt().toString()
                    else String.format("%.1f", multiplier)
                "Incubating Cobbreeding eggs · ${label}× speed"
            }
            else -> "Working"
        }
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Refresh entries each frame to pick up assignment/config changes
        entries = buildBuffEntries()

        // Sub-tab bar at top \u2192 shifts content header below it.
        val tabsBottom = renderSubTabs(context, mouseX, mouseY)
        val contentTop = tabsBottom
        val contentBottom = panelY + panelH - 18

        val visible = visibleEntries()
        if (visible.isEmpty()) {
            val message = when (activeSubTab) {
                SubTab.ACTIVE -> if (pokemonList.isEmpty()) "\u00A77No Pokemon in this Pasture" else "\u00A77No active jobs \u2014 assign a job in the Skills tab"
                SubTab.PASSIVE -> if (pokemonList.isEmpty()) "\u00A77No Pokemon in this Pasture" else "\u00A77No passive buffs available from this Pasture's Pokemon"
            }
            context.drawCenteredTextWithShadow(textRenderer, message, panelX + panelW / 2, panelY + panelH / 2 - 4, 0x888888)
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

        // Passive sub-tab: dedupe winners per buff type so the player sees clearly that
        // only the highest-proficiency mon per buff actually applies its effect (no
        // stacking). Group by skillId, pick the max prof, the rest render dimmed.
        val maxProfPerSkill: Map<String, Int> = if (activeSubTab == SubTab.PASSIVE) {
            visible.groupingBy { it.skillId }.fold(0) { acc, e ->
                if (e.proficiency > acc) e.proficiency else acc
            }
        } else emptyMap()

        // Scrollable content
        context.enableScissor(panelX, contentTop + 12, panelX + panelW, contentBottom)

        for ((index, entry) in visible.withIndex()) {
            val ry = contentTop + 14 + index * ROW_HEIGHT + scrollY
            if (ry < contentTop - ROW_HEIGHT || ry > contentBottom) continue

            // Row background \u2014 uniform tone, no category color bar (user found the
            // colored boxes distracting). The action/buff category is still encoded by
            // the icon and skill-name color, just not by a row-edge stripe.
            val rowColor = if (index % 2 == 0) ROW_EVEN else ROW_ODD
            context.fill(panelX + 1, ry, panelX + panelW - 1, ry + ROW_HEIGHT - 1, rowColor)

            // Passive-buff dedup state: this entry "loses" if there's another passive
            // buff of the same skillId with a higher proficiency. Render dimmed so the
            // player sees "this Pokemon contributes nothing \u2014 overshadowed."
            val maxProfHere = maxProfPerSkill[entry.skillId]
            val isOvershadowed = activeSubTab == SubTab.PASSIVE && maxProfHere != null && entry.proficiency < maxProfHere

            val scale = 0.75f
            val cat = if (entry.isPassiveBuff) 0xFF55FFAA.toInt()
                else CobblebaseScreen.CATEGORY_COLORS[entry.category] ?: 0xFF666666.toInt()

            // Pokemon portrait icon \u2014 scaled up 1.3\u00D7 so a 16-px sprite renders at ~21 px
            // (we got rid of the category color bar so there's room to make sprites the
            // visual anchor of the row). Anchored ry+1 so it stays inside the 22-px row.
            val spriteScale = 1.3f
            val spriteX = colPokemon + 4
            val spriteY = ry + 1
            context.matrices.push()
            context.matrices.translate(spriteX.toFloat(), spriteY.toFloat(), 0f)
            context.matrices.scale(spriteScale, spriteScale, 1f)
            PokemonSpriteHelper.renderIcon(
                context, textRenderer, entry.species, entry.pokemonName, entry.aspects,
                0, 0, delta
            )
            context.matrices.pop()
            // Use the wider icon footprint for the text column offset.
            val scaledIconWidth = (PokemonSpriteHelper.ICON_SIZE * spriteScale).toInt()
            val nameOffset = scaledIconWidth + 4

            // Pokemon name + level (shifted right past the bigger icon).
            val nameX = (colPokemon + 4 + nameOffset).toFloat()
            val nameColor = if (isOvershadowed) 0x666666 else 0xFFFFFF
            context.matrices.push()
            context.matrices.translate(nameX, (ry + 4).toFloat(), 0f)
            context.matrices.scale(scale, scale, 1f)
            context.drawTextWithShadow(textRenderer, entry.pokemonName, 0, 0, nameColor)
            context.matrices.pop()

            context.matrices.push()
            context.matrices.translate(nameX, (ry + 14).toFloat(), 0f)
            context.matrices.scale(scale, scale, 1f)
            val lvlColor = if (isOvershadowed) 0x555555 else 0xAAAAAA
            context.drawTextWithShadow(textRenderer, "\u00A77Lv.${entry.level}", 0, 0, lvlColor)
            context.matrices.pop()

            // Job icon left of the skill name (same vocabulary as Skills/Jobs tab).
            // Small scale 0.55 \u2248 9 px so it fits next to scaled text without crowding.
            context.matrices.push()
            context.matrices.translate(colSkill.toFloat(), (ry + 3).toFloat(), 0f)
            context.matrices.scale(0.55f, 0.55f, 1f)
            context.drawItem(JobIcons.stackFor(entry.skillId), 0, 0)
            context.matrices.pop()

            // Skill name (PASSIVE label removed \u2014 sub-tab already separates the two).
            // Overshadowed passive rows: skill name uses a dim gray instead of the buff color.
            val skillTextX = colSkill + 11
            val skillNameColor = when {
                isOvershadowed -> 0x666666
                entry.isPassiveBuff -> 0x55FFAA
                else -> cat
            }
            context.matrices.push()
            context.matrices.translate(skillTextX.toFloat(), (ry + 4).toFloat(), 0f)
            context.matrices.scale(scale, scale, 1f)
            context.drawTextWithShadow(textRenderer, entry.skillName, 0, 0, skillNameColor)
            context.matrices.pop()

            // Proficiency stars (scaled 0.75x). Overshadowed mons get gray stars
            // regardless of their actual prof \u2014 visual cue that this rank "doesn't count."
            val stars = "\u2605".repeat(entry.proficiency) + "\u2606".repeat(5 - entry.proficiency)
            val starColor = when {
                isOvershadowed -> 0x555555
                entry.proficiency >= 5 -> 0xFFD700
                entry.proficiency >= 4 -> 0xFFA500
                entry.proficiency >= 3 -> 0x88CC88
                else -> 0x888888
            }
            context.matrices.push()
            context.matrices.translate((colSkill + 11).toFloat(), (ry + 14).toFloat(), 0f)
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
        totalContentHeight = visible.size * ROW_HEIGHT
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
        context.drawTextWithShadow(textRenderer, "\u00A78${visible.size} shown", panelX + PADDING, panelY + panelH - 14, 0x666666)
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        scrollY = (scrollY + verticalAmount.toInt() * 16).coerceAtMost(0)
        clampScroll()
        return true
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Sub-tab switching takes priority over scroll-track clicks.
        if (inBox(mouseX, mouseY, activeTabBox)) { activeSubTab = SubTab.ACTIVE; scrollY = 0; return true }
        if (inBox(mouseX, mouseY, passiveTabBox)) { activeSubTab = SubTab.PASSIVE; scrollY = 0; return true }
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

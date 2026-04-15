package notlown.cobblebase.fabric.client.gui

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import notlown.cobblebase.core.AssignmentCache
import notlown.cobblebase.core.SpeciesSkillRegistry
import notlown.cobblebase.core.WorkshopCache
import notlown.cobblebase.core.net.RecipeListSyncS2CPacket
import notlown.cobblebase.core.net.WorkshopRequestC2SPacket
import notlown.cobblebase.core.net.WorkshopSelectC2SPacket
import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket.PasturePokemonDataDTO
import java.util.UUID

/**
 * Workshop tab with two sub-tabs: Overview (project status + suppliers) and Recipes (browser).
 */
class WorkshopPanel(
    private val parent: CobblebaseScreen,
    private val pokemonList: List<PasturePokemonDataDTO>,
    private val panelX: Int,
    private val panelY: Int,
    private val panelW: Int,
    private val panelH: Int,
    private val textRenderer: TextRenderer
) {
    private val PADDING = 6
    private val ROW_HEIGHT = 18
    private val SCALE = 0.75f
    private val ICON_SCALE = 10f / 16f

    private enum class SubTab { OVERVIEW, RECIPES }
    private var activeSubTab = SubTab.OVERVIEW

    private var searchField: TextFieldWidget? = null
    private var searchText = ""
    private var selectedCategory = "All"
    private var scrollOffset = 0
    private var scrollAccumulator = 0.0
    private var dataRequested = false
    private var lastRefreshTime = 0L

    private val categories = mutableListOf("All")

    // Supplier row click targets
    private data class SupplierRow(val y: Int, val pokemonId: UUID, val skillId: String)
    private var supplierRows = listOf<SupplierRow>()

    fun init(addWidget: (net.minecraft.client.gui.widget.ClickableWidget) -> Unit) {
        scrollOffset = 0
        if (!dataRequested) {
            ClientPlayNetworking.send(WorkshopRequestC2SPacket())
            dataRequested = true
        }
        searchField = TextFieldWidget(textRenderer, panelX + PADDING, panelY + PADDING + 16, panelW / 3, 12, Text.literal(""))
        searchField!!.setMaxLength(64)
        searchField!!.setPlaceholder(Text.literal("Search..."))
        searchField!!.setChangedListener { text -> searchText = text; scrollOffset = 0 }
        searchField!!.visible = false
        addWidget(searchField!!)
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTime > 3000L) {
            lastRefreshTime = now
            ClientPlayNetworking.send(WorkshopRequestC2SPacket())
        }

        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC1E1E2E.toInt())

        if (!WorkshopCache.recipesLoaded) {
            context.drawTextWithShadow(textRenderer, "Loading...", panelX + PADDING, panelY + panelH / 2, 0x888888)
            return
        }

        if (categories.size <= 1) {
            val cats = WorkshopCache.recipes.map { it.category }.distinct().sorted()
            categories.clear(); categories.add("All"); categories.addAll(cats)
        }

        // --- Sub-tab bar ---
        val tabY = panelY + 2
        val tabH = 14
        renderSubTab(context, mouseX, mouseY, "Overview", SubTab.OVERVIEW, panelX + PADDING, tabY, 52, tabH)
        renderSubTab(context, mouseX, mouseY, "Recipes", SubTab.RECIPES, panelX + PADDING + 56, tabY, 46, tabH)

        searchField?.visible = activeSubTab == SubTab.RECIPES
        val contentY = tabY + tabH + 4

        when (activeSubTab) {
            SubTab.OVERVIEW -> renderOverview(context, mouseX, mouseY, contentY)
            SubTab.RECIPES -> renderRecipeBrowser(context, mouseX, mouseY, contentY)
        }
    }

    private fun renderSubTab(context: DrawContext, mouseX: Int, mouseY: Int, label: String, tab: SubTab, x: Int, y: Int, w: Int, h: Int) {
        val isActive = activeSubTab == tab
        val isHovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h
        val bg = when { isActive -> 0xFF3A3A6E.toInt(); isHovered -> 0xFF2A2A4E.toInt(); else -> 0xFF1A1A2E.toInt() }
        context.fill(x, y, x + w, y + h, bg)
        if (isActive) context.fill(x, y + h - 2, x + w, y + h, 0xFFFF5722.toInt())
        context.matrices.push()
        context.matrices.translate((x + 4).toFloat(), (y + 3).toFloat(), 0f)
        context.matrices.scale(0.7f, 0.7f, 1f)
        context.drawTextWithShadow(textRenderer, label, 0, 0, if (isActive) 0xFFFFFF else 0x999999)
        context.matrices.pop()
    }

    // ========== OVERVIEW TAB ==========

    private fun renderOverview(context: DrawContext, mouseX: Int, mouseY: Int, startY: Int) {
        val craftsmen = getCraftsmanPokemon()
        if (craftsmen.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "\u00A77Assign a Craftsman to get started", panelX + PADDING, startY + 20, 0x888888)
            context.matrices.push()
            context.matrices.translate((panelX + PADDING).toFloat(), (startY + 32).toFloat(), 0f)
            context.matrices.scale(0.6f, 0.6f, 1f)
            context.drawTextWithShadow(textRenderer, "Go to Skills tab and assign a Pokemon the Craftsman job", 0, 0, 0x666666)
            context.matrices.pop()
            return
        }

        val activeProjects = craftsmen.filter { WorkshopCache.projects.containsKey(it.pokemonId) }
        if (activeProjects.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "\u00A77No active project", panelX + PADDING, startY + 20, 0x888888)
            context.matrices.push()
            context.matrices.translate((panelX + PADDING).toFloat(), (startY + 32).toFloat(), 0f)
            context.matrices.scale(0.6f, 0.6f, 1f)
            context.drawTextWithShadow(textRenderer, "Switch to the Recipes tab to select what to build", 0, 0, 0x666666)
            context.matrices.pop()
            return
        }

        val pokemonData = activeProjects.first()
        val project = WorkshopCache.projects[pokemonData.pokemonId] ?: return
        val recipe = WorkshopCache.recipes.find { it.recipeId == project.recipeId }
        var y = startY

        // --- Project Header Card ---
        context.fill(panelX + 2, y, panelX + panelW - 2, y + 22, 0x33FF5722)
        context.fill(panelX + 2, y, panelX + panelW - 2, y + 1, 0xFFFF5722.toInt())

        // Craftsman sprite + name
        PokemonSpriteHelper.renderSmallIconByName(context, textRenderer, pokemonData.species.path, panelX + PADDING, y + 2, 0f)
        context.matrices.push()
        context.matrices.translate((panelX + PADDING + 14).toFloat(), (y + 4).toFloat(), 0f)
        context.matrices.scale(SCALE, SCALE, 1f)
        context.drawTextWithShadow(textRenderer, "\u00A7f\u00A7l${pokemonData.displayName.string}", 0, 0, 0xFFFFFF)
        context.matrices.pop()

        // Phase + craft count
        val phaseText = when (project.phase) {
            "GATHERING" -> "\u00A7eGathering materials..."
            "CRAFTING" -> "\u00A76Crafting..."
            "DEPOSITING" -> "\u00A7aDone! Depositing..."
            else -> "\u00A77Waiting..."
        }
        val craftCount = project.craftCount
        val countText = if (craftCount > 0) " \u00A78| \u00A7a${craftCount} crafted" else ""
        context.matrices.push()
        context.matrices.translate((panelX + PADDING + 14).toFloat(), (y + 13).toFloat(), 0f)
        context.matrices.scale(0.6f, 0.6f, 1f)
        context.drawTextWithShadow(textRenderer, "$phaseText$countText", 0, 0, 0xAAAAAA)
        context.matrices.pop()

        // Output icon + name (right side of header card)
        if (recipe != null) {
            val outputStack = makeStack(recipe.outputItemId)
            if (!outputStack.isEmpty) {
                context.matrices.push()
                context.matrices.translate((panelX + panelW - PADDING - 60).toFloat(), (y + 3).toFloat(), 0f)
                context.matrices.scale(ICON_SCALE, ICON_SCALE, 1f)
                context.drawItem(outputStack, 0, 0)
                context.matrices.pop()
            }
            context.matrices.push()
            context.matrices.translate((panelX + panelW - PADDING - 46).toFloat(), (y + 5).toFloat(), 0f)
            context.matrices.scale(0.65f, 0.65f, 1f)
            context.drawTextWithShadow(textRenderer, "\u00A7fBuilding: \u00A7l${recipe.outputDisplayName}", 0, 0, 0xFFFFFF)
            context.matrices.pop()
        }

        y += 24

        // --- Material progress ---
        context.fill(panelX + 2, y, panelX + panelW - 2, y + 1, 0xFF444444.toInt())
        y += 3
        context.matrices.push()
        context.matrices.translate((panelX + PADDING).toFloat(), y.toFloat(), 0f)
        context.matrices.scale(0.65f, 0.65f, 1f)
        context.drawTextWithShadow(textRenderer, "\u00A78\u00A7lMATERIALS", 0, 0, 0x888888)
        context.matrices.pop()
        y += 10

        var totalNeeded = 0; var totalGathered = 0
        for ((itemId, needed) in project.requiredItems) {
            val gathered = (project.gatheredItems[itemId] ?: 0).coerceAtMost(needed)
            val done = gathered >= needed
            totalNeeded += needed; totalGathered += gathered

            val stack = makeStack(itemId)
            if (!stack.isEmpty) {
                context.matrices.push()
                context.matrices.translate((panelX + PADDING).toFloat(), y.toFloat(), 0f)
                context.matrices.scale(ICON_SCALE, ICON_SCALE, 1f)
                context.drawItem(stack, 0, 0)
                context.matrices.pop()
            }

            val itemName = itemId.substringAfterLast(":").replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            context.matrices.push()
            context.matrices.translate((panelX + PADDING + 14).toFloat(), (y + 2).toFloat(), 0f)
            context.matrices.scale(SCALE, SCALE, 1f)
            context.drawTextWithShadow(textRenderer, itemName, 0, 0, if (done) 0x55FF55 else 0xFFFFFF)
            context.matrices.pop()

            // Progress bar
            val barX = panelX + PADDING + 14 + (panelW * 0.3f).toInt()
            val barW = (panelW * 0.35f).toInt()
            context.fill(barX, y + 2, barX + barW, y + 8, 0xFF333333.toInt())
            val fillW = if (needed > 0) (barW * gathered.toFloat() / needed).toInt() else 0
            if (fillW > 0) context.fill(barX, y + 2, barX + fillW, y + 8, if (done) 0xFF4CAF50.toInt() else 0xFFFF9800.toInt())

            context.matrices.push()
            context.matrices.translate((barX + barW + 4).toFloat(), (y + 1).toFloat(), 0f)
            context.matrices.scale(SCALE, SCALE, 1f)
            context.drawTextWithShadow(textRenderer, "$gathered / $needed", 0, 0, if (done) 0x55FF55 else 0xFFAA00)
            // Skill suggestion for this material
            if (!done) {
                val suppliers = notlown.cobblebase.core.SupplierHelper.getSupplierJobs(itemId)
                val skillNames = suppliers.filter { it.skillId != "manual" }.joinToString("/") { it.skillName }
                if (skillNames.isNotEmpty()) {
                    val countTextW = textRenderer.getWidth("$gathered / $needed") + 4
                    context.drawTextWithShadow(textRenderer, "\u00A78($skillNames)", countTextW, 0, 0x666666)
                }
            }
            context.matrices.pop()

            y += 13
        }

        // Overall progress bar
        y += 2
        val progress = if (totalNeeded > 0) totalGathered.toFloat() / totalNeeded else 0f
        val tBarX = panelX + PADDING; val tBarW = panelW - PADDING * 2
        context.fill(tBarX, y, tBarX + tBarW, y + 6, 0xFF222222.toInt())
        val tFillW = (tBarW * progress).toInt()
        val tColor = when (project.phase) { "CRAFTING" -> 0xFFFF5722.toInt(); "DEPOSITING" -> 0xFF4CAF50.toInt(); else -> 0xFFFF9800.toInt() }
        if (tFillW > 0) context.fill(tBarX, y, tBarX + tFillW, y + 6, tColor)
        context.matrices.push()
        context.matrices.translate((tBarX + tBarW / 2 - 6).toFloat(), (y - 1).toFloat(), 0f)
        context.matrices.scale(0.55f, 0.55f, 1f)
        context.drawTextWithShadow(textRenderer, "${(progress * 100).toInt()}%", 0, 0, 0xFFFFFF)
        context.matrices.pop()

        y += 12

        // --- Suppliers Section ---
        context.fill(panelX + 2, y, panelX + panelW - 2, y + 1, 0xFFFF5722.toInt())
        y += 4
        context.matrices.push()
        context.matrices.translate((panelX + PADDING).toFloat(), y.toFloat(), 0f)
        context.matrices.scale(0.7f, 0.7f, 1f)
        context.drawTextWithShadow(textRenderer, "\u00A76\u00A7lSuppliers", 0, 0, 0xFFAA00)
        context.matrices.pop()
        y += 12

        val neededJobs = notlown.cobblebase.core.SupplierHelper.getNeededSuppliers(project.requiredItems)
        val rows = mutableListOf<SupplierRow>()
        val nonCraftsmen = pokemonList.filter { AssignmentCache.getAssignment(it.pokemonId) != "cobblebase:craftsman" }

        for (suggestion in neededJobs) {
            val matchingMons = nonCraftsmen.filter { p ->
                val speciesName = SpeciesSkillRegistry.resolveFormName(p.species.path, p.aspects)
                val skills = SpeciesSkillRegistry.getSkills(speciesName)?.skills ?: emptyList()
                skills.any { it.skillId == suggestion.skillId }
            }

            if (matchingMons.isEmpty()) {
                // No matching mon
                context.fill(panelX + PADDING, y, panelX + panelW - PADDING, y + 16, 0x22FF5555)
                context.matrices.push()
                context.matrices.translate((panelX + PADDING + 4).toFloat(), (y + 4).toFloat(), 0f)
                context.matrices.scale(0.65f, 0.65f, 1f)
                context.drawTextWithShadow(textRenderer, "\u00A7c${suggestion.skillName}\u00A78 — no Pokemon available", 0, 0, 0x888888)
                context.matrices.pop()
                y += 18
                continue
            }

            for (mon in matchingMons) {
                val currentAssignment = AssignmentCache.getAssignment(mon.pokemonId)
                val isActive = currentAssignment == "craftsman_supply:${suggestion.skillId}" || currentAssignment == suggestion.skillId

                // Row background
                val rowBg = if (isActive) 0x224CAF50 else 0x22444444
                context.fill(panelX + PADDING, y, panelX + panelW - PADDING, y + 16, rowBg)
                if (isActive) context.fill(panelX + PADDING, y, panelX + PADDING + 2, y + 16, 0xFF4CAF50.toInt())

                // Pokemon sprite
                PokemonSpriteHelper.renderSmallIconByName(context, textRenderer, mon.species.path, panelX + PADDING + 4, y + 1, 0f)

                // Mon name
                context.matrices.push()
                context.matrices.translate((panelX + PADDING + 18).toFloat(), (y + 2).toFloat(), 0f)
                context.matrices.scale(0.7f, 0.7f, 1f)
                context.drawTextWithShadow(textRenderer, mon.displayName.string, 0, 0, if (isActive) 0xFFFFFF else 0xCCCCCC)
                context.matrices.pop()

                // Job type + cooldown estimate
                val speciesName = SpeciesSkillRegistry.resolveFormName(mon.species.path, mon.aspects)
                val monSkills = SpeciesSkillRegistry.getSkills(speciesName)?.skills ?: emptyList()
                val skillEntry = monSkills.find { it.skillId == suggestion.skillId }
                val skillDef = if (skillEntry != null) notlown.cobblebase.core.SkillRegistry.get(skillEntry.skillId) else null
                val cooldownText = if (isActive && skillDef != null && skillEntry != null) {
                    val cd = notlown.cobblebase.core.CobblebaseConfig.getEffectiveCooldownTicks(skillDef.cooldownSeconds, skillEntry.proficiency) / 20
                    " \u00A78| \u00A7e~${cd}s per item"
                } else ""

                context.matrices.push()
                context.matrices.translate((panelX + PADDING + 18).toFloat(), (y + 10).toFloat(), 0f)
                context.matrices.scale(0.5f, 0.5f, 1f)
                context.drawTextWithShadow(textRenderer, "\u00A78${suggestion.skillName} — ${suggestion.description}$cooldownText", 0, 0, 0x888888)
                context.matrices.pop()

                // Status / Assign or Remove button (right side)
                val btnX = panelX + panelW - PADDING - 38
                if (isActive) {
                    val removeHover = mouseX >= btnX && mouseX < btnX + 36 && mouseY >= y && mouseY < y + 16
                    context.fill(btnX, y + 2, btnX + 36, y + 14, if (removeHover) 0xFFFF5555.toInt() else 0xFF5A3A3A.toInt())
                    context.matrices.push()
                    context.matrices.translate((btnX + 3).toFloat(), (y + 4).toFloat(), 0f)
                    context.matrices.scale(0.6f, 0.6f, 1f)
                    context.drawTextWithShadow(textRenderer, "Remove", 0, 0, 0xFFFFFF)
                    context.matrices.pop()
                    rows.add(SupplierRow(y, mon.pokemonId, "REMOVE"))
                } else {
                    val btnHover = mouseX >= btnX && mouseX < btnX + 36 && mouseY >= y && mouseY < y + 16
                    context.fill(btnX, y + 2, btnX + 36, y + 14, if (btnHover) 0xFF4CAF50.toInt() else 0xFF3A6A3A.toInt())
                    context.matrices.push()
                    context.matrices.translate((btnX + 6).toFloat(), (y + 4).toFloat(), 0f)
                    context.matrices.scale(0.6f, 0.6f, 1f)
                    context.drawTextWithShadow(textRenderer, "Assign", 0, 0, 0xFFFFFF)
                    context.matrices.pop()
                    rows.add(SupplierRow(y, mon.pokemonId, suggestion.skillId))
                }

                y += 18
            }
        }

        supplierRows = rows

        // Hint
        if (project.phase == "GATHERING" && totalGathered == 0 && neededJobs.isEmpty()) {
            context.matrices.push()
            context.matrices.translate((panelX + PADDING).toFloat(), (y + 4).toFloat(), 0f)
            context.matrices.scale(0.5f, 0.5f, 1f)
            context.drawTextWithShadow(textRenderer, "Place materials in a nearby chest", 0, 0, 0xFF8888)
            context.matrices.pop()
        }
    }

    // ========== RECIPES TAB ==========

    private fun renderRecipeBrowser(context: DrawContext, mouseX: Int, mouseY: Int, startY: Int) {
        searchField?.visible = true

        // Category bar
        val catY = startY + 14
        renderCategoryBar(context, mouseX, mouseY, catY)

        val listY = catY + 14
        val listH = panelH - (listY - panelY) - PADDING
        val filtered = getFilteredRecipes()
        val maxVisible = listH / ROW_HEIGHT
        val maxScroll = (filtered.size - maxVisible).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        context.enableScissor(panelX, listY, panelX + panelW, listY + listH)

        for (i in 0 until maxVisible) {
            val idx = i + scrollOffset
            if (idx >= filtered.size) break
            val recipe = filtered[idx]
            val rowY = listY + i * ROW_HEIGHT
            val isHovered = mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
            if (isHovered) context.fill(panelX + 2, rowY, panelX + panelW - 2, rowY + ROW_HEIGHT, 0x22FFFFFF)

            val outputStack = makeStack(recipe.outputItemId)
            if (!outputStack.isEmpty) {
                context.matrices.push()
                context.matrices.translate((panelX + PADDING).toFloat(), (rowY + 1).toFloat(), 0f)
                context.matrices.scale(ICON_SCALE, ICON_SCALE, 1f)
                context.drawItem(outputStack, 0, 0)
                context.matrices.pop()
            }

            context.matrices.push()
            context.matrices.translate((panelX + PADDING + 14).toFloat(), (rowY + 2).toFloat(), 0f)
            context.matrices.scale(SCALE, SCALE, 1f)
            val countSuffix = if (recipe.outputCount > 1) " x${recipe.outputCount}" else ""
            context.drawTextWithShadow(textRenderer, "${recipe.outputDisplayName}$countSuffix", 0, 0, 0xFFFFFF)
            context.matrices.pop()

            // Input icons
            var mx = panelX + (panelW * 0.45f).toInt()
            for ((inputId, count) in recipe.inputs) {
                val inputStack = makeStack(inputId)
                if (!inputStack.isEmpty) {
                    context.matrices.push()
                    context.matrices.translate(mx.toFloat(), (rowY + 2).toFloat(), 0f)
                    context.matrices.scale(ICON_SCALE * 0.8f, ICON_SCALE * 0.8f, 1f)
                    context.drawItem(inputStack, 0, 0)
                    context.matrices.pop()
                }
                context.matrices.push()
                context.matrices.translate((mx + 8).toFloat(), (rowY + 5).toFloat(), 0f)
                context.matrices.scale(0.55f, 0.55f, 1f)
                context.drawTextWithShadow(textRenderer, "${count}x", 0, 0, 0xAAAAAA)
                context.matrices.pop()
                mx += 26
                if (mx > panelX + panelW - 40) break
            }

            if (isHovered && getCraftsmanPokemon().isNotEmpty()) {
                val btnX = panelX + panelW - PADDING - 20
                context.fill(btnX, rowY + 2, btnX + 18, rowY + ROW_HEIGHT - 2, 0xFF4CAF50.toInt())
                context.matrices.push()
                context.matrices.translate((btnX + 3).toFloat(), (rowY + 5).toFloat(), 0f)
                context.matrices.scale(0.55f, 0.55f, 1f)
                context.drawTextWithShadow(textRenderer, "Set", 0, 0, 0xFFFFFF)
                context.matrices.pop()
            }
        }

        context.disableScissor()

        if (filtered.size > maxVisible) {
            val trackX = panelX + panelW - 3
            val thumbH = ((maxVisible.toFloat() / filtered.size) * listH).toInt().coerceAtLeast(10)
            val thumbY = listY + ((scrollOffset.toFloat() / maxScroll) * (listH - thumbH)).toInt()
            context.fill(trackX, listY, trackX + 2, listY + listH, 0x33FFFFFF)
            context.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xAAFFFFFF.toInt())
        }

        if (getCraftsmanPokemon().isEmpty()) {
            context.matrices.push()
            context.matrices.translate((panelX + PADDING).toFloat(), (panelY + panelH - 14).toFloat(), 0f)
            context.matrices.scale(0.55f, 0.55f, 1f)
            context.drawTextWithShadow(textRenderer, "Assign a Craftsman first to select recipes", 0, 0, 0xFF8888)
            context.matrices.pop()
        }
    }

    private fun renderCategoryBar(context: DrawContext, mouseX: Int, mouseY: Int, catY: Int) {
        var cx = panelX + PADDING + panelW / 3 + 8
        for (cat in categories) {
            val catW = (textRenderer.getWidth(cat) * 0.6f).toInt() + 8
            val isActive = selectedCategory == cat
            val isHovered = mouseX >= cx && mouseX < cx + catW && mouseY >= catY && mouseY < catY + 11
            val bg = when { isActive -> 0xFF3A3A6E.toInt(); isHovered -> 0xFF2A2A4E.toInt(); else -> 0xFF1A1A2E.toInt() }
            context.fill(cx, catY, cx + catW, catY + 11, bg)
            if (isActive) context.fill(cx, catY + 10, cx + catW, catY + 11, 0xFFFF9800.toInt())
            context.matrices.push()
            context.matrices.translate((cx + 4).toFloat(), (catY + 2).toFloat(), 0f)
            context.matrices.scale(0.6f, 0.6f, 1f)
            context.drawTextWithShadow(textRenderer, cat, 0, 0, if (isActive) 0xFFFFFF else 0x999999)
            context.matrices.pop()
            cx += catW + 2
            if (cx > panelX + panelW - PADDING) break
        }
    }

    // ========== CLICK HANDLING ==========

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Sub-tab clicks
        val tabY = panelY + 2
        if (mouseY >= tabY && mouseY < tabY + 14) {
            if (mouseX >= panelX + PADDING && mouseX < panelX + PADDING + 52) {
                activeSubTab = SubTab.OVERVIEW; return true
            }
            if (mouseX >= panelX + PADDING + 56 && mouseX < panelX + PADDING + 102) {
                activeSubTab = SubTab.RECIPES; scrollOffset = 0; return true
            }
        }

        // Supplier assign/remove clicks (overview tab)
        if (activeSubTab == SubTab.OVERVIEW) {
            for (row in supplierRows) {
                val btnX = panelX + panelW - PADDING - 38
                if (mouseY >= row.y && mouseY < row.y + 16 && mouseX >= btnX && mouseX < btnX + 36) {
                    if (row.skillId == "REMOVE") {
                        // Remove supplier — set to idle (empty assignment)
                        ClientPlayNetworking.send(notlown.cobblebase.core.net.SkillAssignmentC2SPacket(row.pokemonId, ""))
                        AssignmentCache.setAssignment(row.pokemonId, null)
                    } else {
                        // Assign as supplier
                        val supplyAssignment = "craftsman_supply:${row.skillId}"
                        ClientPlayNetworking.send(notlown.cobblebase.core.net.SkillAssignmentC2SPacket(row.pokemonId, supplyAssignment))
                        AssignmentCache.setAssignment(row.pokemonId, supplyAssignment)
                    }
                    ClientPlayNetworking.send(WorkshopRequestC2SPacket())
                    return true
                }
            }
        }

        // Recipe clicks (recipes tab)
        if (activeSubTab == SubTab.RECIPES) {
            // Category clicks
            val catY = panelY + 2 + 14 + 4 + 14
            var cx = panelX + PADDING + panelW / 3 + 8
            for (cat in categories) {
                val catW = (textRenderer.getWidth(cat) * 0.6f).toInt() + 8
                if (mouseX >= cx && mouseX < cx + catW && mouseY >= catY && mouseY < catY + 11) {
                    selectedCategory = cat; scrollOffset = 0; return true
                }
                cx += catW + 2
                if (cx > panelX + panelW - PADDING) break
            }

            // Recipe row clicks
            val craftsmen = getCraftsmanPokemon()
            if (craftsmen.isEmpty()) return false
            val listY = catY + 14
            val listH = panelH - (listY - panelY) - PADDING
            val filtered = getFilteredRecipes()
            val maxVisible = listH / ROW_HEIGHT
            for (i in 0 until maxVisible) {
                val idx = i + scrollOffset
                if (idx >= filtered.size) break
                val rowY = listY + i * ROW_HEIGHT
                if (mouseY >= rowY && mouseY < rowY + ROW_HEIGHT && mouseX >= panelX && mouseX <= panelX + panelW) {
                    val recipe = filtered[idx]
                    val target = craftsmen.firstOrNull { !WorkshopCache.projects.containsKey(it.pokemonId) } ?: craftsmen.first()
                    ClientPlayNetworking.send(WorkshopSelectC2SPacket(target.pokemonId, recipe.recipeId))
                    activeSubTab = SubTab.OVERVIEW
                    ClientPlayNetworking.send(WorkshopRequestC2SPacket())
                    return true
                }
            }
        }

        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean = false
    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean = false

    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (activeSubTab == SubTab.RECIPES && mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + panelH) {
            scrollAccumulator -= verticalAmount
            val whole = scrollAccumulator.toInt()
            scrollAccumulator -= whole.toDouble()
            scrollOffset = (scrollOffset + whole).coerceAtLeast(0)
            val filtered = getFilteredRecipes()
            val listH = panelH - 60
            val maxScroll = (filtered.size - listH / ROW_HEIGHT).coerceAtLeast(0)
            scrollOffset = scrollOffset.coerceAtMost(maxScroll)
            return true
        }
        return false
    }

    // ========== HELPERS ==========

    private fun getFilteredRecipes(): List<RecipeListSyncS2CPacket.RecipeDTO> {
        return WorkshopCache.recipes.filter { recipe ->
            (selectedCategory == "All" || recipe.category == selectedCategory) &&
            (searchText.isBlank() || recipe.outputDisplayName.lowercase().contains(searchText.lowercase()))
        }
    }

    private fun getCraftsmanPokemon(): List<PasturePokemonDataDTO> {
        return pokemonList.filter { AssignmentCache.getAssignment(it.pokemonId) == "cobblebase:craftsman" }
    }

    private fun makeStack(itemId: String): ItemStack {
        return try {
            val parts = itemId.split(":", limit = 2)
            val id = if (parts.size == 2) Identifier.of(parts[0], parts[1]) else Identifier.of("minecraft", itemId)
            val item = Registries.ITEM.get(id)
            if (item == Items.AIR) ItemStack.EMPTY else ItemStack(item)
        } catch (_: Exception) { ItemStack.EMPTY }
    }
}

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
import notlown.cobblebase.core.WorkshopCache
import notlown.cobblebase.core.net.RecipeListSyncS2CPacket
import notlown.cobblebase.core.net.WorkshopRequestC2SPacket
import notlown.cobblebase.core.net.WorkshopSelectC2SPacket
import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket.PasturePokemonDataDTO
import java.util.UUID

/**
 * Workshop tab panel: recipe browser + active project status for Craftsman Pokemon.
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

    private var searchField: TextFieldWidget? = null
    private var searchText = ""
    private var selectedCategory = "All"
    private var scrollOffset = 0
    private var scrollAccumulator = 0.0
    private var dataRequested = false

    // Categories derived from recipes
    private val categories = mutableListOf("All")

    fun init(addWidget: (net.minecraft.client.gui.widget.ClickableWidget) -> Unit) {
        scrollOffset = 0

        // Request data from server
        if (!dataRequested) {
            ClientPlayNetworking.send(WorkshopRequestC2SPacket())
            dataRequested = true
        }

        // Search field
        searchField = TextFieldWidget(textRenderer, panelX + PADDING, panelY + PADDING, panelW / 3, 12, Text.literal(""))
        searchField!!.setMaxLength(64)
        searchField!!.setPlaceholder(Text.literal("Search recipes..."))
        searchField!!.setChangedListener { text -> searchText = text; scrollOffset = 0 }
        addWidget(searchField!!)
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Background
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC1E1E2E.toInt())

        if (!WorkshopCache.recipesLoaded) {
            context.drawTextWithShadow(textRenderer, "Loading recipes...", panelX + PADDING, panelY + panelH / 2, 0x888888)
            return
        }

        // Update categories
        if (categories.size <= 1) {
            val cats = WorkshopCache.recipes.map { it.category }.distinct().sorted()
            categories.clear()
            categories.add("All")
            categories.addAll(cats)
        }

        // --- Top bar: search + category buttons ---
        val catY = panelY + PADDING + 14
        renderCategoryBar(context, mouseX, mouseY, catY)

        // --- Active projects section (if any Craftsman has a project) ---
        val craftsmen = getCraftsmanPokemon()
        val activeProjects = craftsmen.filter { WorkshopCache.projects.containsKey(it.pokemonId) }
        var projectSectionH = 0

        if (activeProjects.isNotEmpty()) {
            val projY = catY + 14
            projectSectionH = renderActiveProjects(context, mouseX, mouseY, projY, activeProjects)
        }

        // --- Supplier suggestions section ---
        var supplierSectionH = 0
        if (activeProjects.isNotEmpty()) {
            val project = WorkshopCache.projects[activeProjects.first().pokemonId]
            if (project != null) {
                val suppY = catY + 14 + projectSectionH
                supplierSectionH = renderSupplierSuggestions(context, mouseX, mouseY, suppY, project)
            }
        }

        // --- Recipe browser ---
        val listY = catY + 14 + projectSectionH + supplierSectionH
        val listH = panelH - (listY - panelY) - PADDING
        val filtered = getFilteredRecipes()

        val maxVisible = listH / ROW_HEIGHT
        val maxScroll = (filtered.size - maxVisible).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        // Column headers
        context.matrices.push()
        context.matrices.translate((panelX + PADDING + 14).toFloat(), (listY - 1).toFloat(), 0f)
        context.matrices.scale(0.6f, 0.6f, 1f)
        context.drawTextWithShadow(textRenderer, "Item", 0, 0, 0x888888)
        context.drawTextWithShadow(textRenderer, "Materials needed", ((panelW * 0.4f) / 0.6f).toInt(), 0, 0x888888)
        context.matrices.pop()

        context.enableScissor(panelX, listY + 6, panelX + panelW, listY + listH)

        for (i in 0 until maxVisible) {
            val idx = i + scrollOffset
            if (idx >= filtered.size) break
            val recipe = filtered[idx]
            val rowY = listY + 6 + i * ROW_HEIGHT
            val isHovered = mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT

            if (isHovered) context.fill(panelX + 2, rowY, panelX + panelW - 2, rowY + ROW_HEIGHT, 0x22FFFFFF)

            // Output item icon
            val outputStack = makeStack(recipe.outputItemId)
            if (!outputStack.isEmpty) {
                context.matrices.push()
                context.matrices.translate((panelX + PADDING).toFloat(), (rowY + 1).toFloat(), 0f)
                context.matrices.scale(ICON_SCALE, ICON_SCALE, 1f)
                context.drawItem(outputStack, 0, 0)
                context.matrices.pop()
            }

            // Output name
            val nameX = panelX + PADDING + 14
            context.matrices.push()
            context.matrices.translate(nameX.toFloat(), (rowY + 5).toFloat(), 0f)
            context.matrices.scale(SCALE, SCALE, 1f)
            val countSuffix = if (recipe.outputCount > 1) " x${recipe.outputCount}" else ""
            context.drawTextWithShadow(textRenderer, "${recipe.outputDisplayName}$countSuffix", 0, 0, 0xFFFFFF)
            context.matrices.pop()

            // Input materials with icons
            val matX = panelX + (panelW * 0.4f).toInt()
            var mx = matX
            for ((inputId, count) in recipe.inputs) {
                val inputStack = makeStack(inputId)
                if (!inputStack.isEmpty) {
                    context.matrices.push()
                    context.matrices.translate(mx.toFloat(), (rowY + 2).toFloat(), 0f)
                    context.matrices.scale(ICON_SCALE * 0.8f, ICON_SCALE * 0.8f, 1f)
                    context.drawItem(inputStack, 0, 0)
                    context.matrices.pop()
                }
                // Count label
                context.matrices.push()
                context.matrices.translate((mx + 8).toFloat(), (rowY + 6).toFloat(), 0f)
                context.matrices.scale(0.6f, 0.6f, 1f)
                context.drawTextWithShadow(textRenderer, "${count}x", 0, 0, 0xAAAAAA)
                context.matrices.pop()
                mx += 28
                if (mx > panelX + panelW - 40) break
            }

            // [Set] button area (right side)
            if (isHovered && craftsmen.isNotEmpty()) {
                val btnX = panelX + panelW - PADDING - 22
                val btnY = rowY + 2
                context.fill(btnX, btnY, btnX + 20, btnY + ROW_HEIGHT - 4, 0xFF4CAF50.toInt())
                context.matrices.push()
                context.matrices.translate((btnX + 3).toFloat(), (btnY + 3).toFloat(), 0f)
                context.matrices.scale(0.6f, 0.6f, 1f)
                context.drawTextWithShadow(textRenderer, "Set", 0, 0, 0xFFFFFF)
                context.matrices.pop()
            }
        }

        context.disableScissor()

        // Scrollbar
        if (filtered.size > maxVisible) {
            val trackX = panelX + panelW - 3
            val thumbH = ((maxVisible.toFloat() / filtered.size) * listH).toInt().coerceAtLeast(10)
            val thumbY = listY + 6 + ((scrollOffset.toFloat() / maxScroll) * (listH - thumbH)).toInt()
            context.fill(trackX, listY + 6, trackX + 2, listY + listH, 0x33FFFFFF)
            context.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xAAFFFFFF.toInt())
        }

        // No craftsman hint
        if (craftsmen.isEmpty()) {
            context.matrices.push()
            context.matrices.translate((panelX + PADDING).toFloat(), (panelY + panelH - 14).toFloat(), 0f)
            context.matrices.scale(0.6f, 0.6f, 1f)
            context.drawTextWithShadow(textRenderer, "Assign a Pokemon the Craftsman job to use the Workshop", 0, 0, 0xFF8888)
            context.matrices.pop()
        }
    }

    private fun renderCategoryBar(context: DrawContext, mouseX: Int, mouseY: Int, catY: Int) {
        var cx = panelX + PADDING + panelW / 3 + 8
        for (cat in categories) {
            val catW = (textRenderer.getWidth(cat) * 0.6f).toInt() + 8
            val isActive = selectedCategory == cat
            val isHovered = mouseX >= cx && mouseX < cx + catW && mouseY >= catY && mouseY < catY + 11
            val bg = when {
                isActive -> 0xFF3A3A6E.toInt()
                isHovered -> 0xFF2A2A4E.toInt()
                else -> 0xFF1A1A2E.toInt()
            }
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

    private fun renderActiveProjects(
        context: DrawContext, mouseX: Int, mouseY: Int, startY: Int,
        activeProjects: List<PasturePokemonDataDTO>
    ): Int {
        var y = startY
        context.fill(panelX + 2, y, panelX + panelW - 2, y + 1, 0xFFFF5722.toInt())
        y += 3

        for (pokemonData in activeProjects) {
            val project = WorkshopCache.projects[pokemonData.pokemonId] ?: continue
            val recipe = WorkshopCache.recipes.find { it.recipeId == project.recipeId }
            val displayName = pokemonData.displayName.string

            // Background card
            val neededSuppliers = if (recipe != null) {
                notlown.cobblebase.core.SupplierHelper.getNeededSuppliers(project.requiredItems)
            } else emptyList()
            val cardH = if (recipe != null) 54 + (if (neededSuppliers.isNotEmpty()) 10 else 0) else 20
            context.fill(panelX + 2, y, panelX + panelW - 2, y + cardH, 0x33FF5722)

            // Row 1: Pokemon name + phase + output icon
            context.matrices.push()
            context.matrices.translate((panelX + PADDING).toFloat(), (y + 3).toFloat(), 0f)
            context.matrices.scale(SCALE, SCALE, 1f)
            val phaseLabel = when (project.phase) {
                "GATHERING" -> "\u00A7e\u00A7lGathering materials..."
                "CRAFTING" -> "\u00A76\u00A7lCrafting..."
                "DEPOSITING" -> "\u00A7a\u00A7lDepositing!"
                else -> "\u00A77Idle — select a recipe below"
            }
            context.drawTextWithShadow(textRenderer, "\u00A7f$displayName \u00A78| $phaseLabel", 0, 0, 0xFFFFFF)
            context.matrices.pop()

            if (recipe != null) {
                // Output icon + name (row 1, right side)
                val outputStack = makeStack(recipe.outputItemId)
                if (!outputStack.isEmpty) {
                    context.matrices.push()
                    context.matrices.translate((panelX + panelW - PADDING - 50).toFloat(), (y + 1).toFloat(), 0f)
                    context.matrices.scale(ICON_SCALE, ICON_SCALE, 1f)
                    context.drawItem(outputStack, 0, 0)
                    context.matrices.pop()
                }
                context.matrices.push()
                context.matrices.translate((panelX + panelW - PADDING - 38).toFloat(), (y + 4).toFloat(), 0f)
                context.matrices.scale(0.6f, 0.6f, 1f)
                context.drawTextWithShadow(textRenderer, recipe.outputDisplayName, 0, 0, 0xFFFFFF)
                context.matrices.pop()

                // Row 2: Material progress with icons
                val matY = y + 14
                context.matrices.push()
                context.matrices.translate((panelX + PADDING).toFloat(), (matY).toFloat(), 0f)
                context.matrices.scale(0.6f, 0.6f, 1f)
                context.drawTextWithShadow(textRenderer, "Materials:", 0, 0, 0x888888)
                context.matrices.pop()

                var mx = panelX + PADDING + 36
                var totalNeeded = 0
                var totalGathered = 0
                for ((itemId, needed) in project.requiredItems) {
                    val gathered = project.gatheredItems[itemId] ?: 0
                    val done = gathered >= needed
                    totalNeeded += needed
                    totalGathered += gathered.coerceAtMost(needed)

                    // Item icon
                    val inputStack = makeStack(itemId)
                    if (!inputStack.isEmpty) {
                        context.matrices.push()
                        context.matrices.translate(mx.toFloat(), (matY - 1).toFloat(), 0f)
                        context.matrices.scale(ICON_SCALE * 0.8f, ICON_SCALE * 0.8f, 1f)
                        context.drawItem(inputStack, 0, 0)
                        context.matrices.pop()
                    }
                    // Count text
                    context.matrices.push()
                    context.matrices.translate((mx + 9).toFloat(), (matY + 1).toFloat(), 0f)
                    context.matrices.scale(0.6f, 0.6f, 1f)
                    val color = if (done) 0xFF55FF55.toInt() else 0xFFFFAA00.toInt()
                    context.drawTextWithShadow(textRenderer, "$gathered/$needed", 0, 0, color)
                    context.matrices.pop()
                    mx += 36
                    if (mx > panelX + panelW - 20) break
                }

                // Row 3: Progress bar
                val barY = matY + 12
                val barX = panelX + PADDING
                val barW = panelW - PADDING * 2
                val barH = 4
                val progress = if (totalNeeded > 0) totalGathered.toFloat() / totalNeeded else 0f
                context.fill(barX, barY, barX + barW, barY + barH, 0xFF333333.toInt())
                val fillW = (barW * progress).toInt()
                val barColor = when (project.phase) {
                    "GATHERING" -> 0xFFFFAA00.toInt()
                    "CRAFTING" -> 0xFFFF5722.toInt()
                    "DEPOSITING" -> 0xFF4CAF50.toInt()
                    else -> 0xFF666666.toInt()
                }
                if (fillW > 0) context.fill(barX, barY, barX + fillW, barY + barH, barColor)

                // Progress percentage
                context.matrices.push()
                context.matrices.translate((barX + barW + 3).toFloat(), (barY - 1).toFloat(), 0f)
                context.matrices.scale(0.5f, 0.5f, 1f)
                context.drawTextWithShadow(textRenderer, "${(progress * 100).toInt()}%", 0, 0, 0xAAAAAA)
                context.matrices.pop()

                // Supplier suggestions
                val suppY = barY + 6
                if (neededSuppliers.isNotEmpty()) {
                    context.matrices.push()
                    context.matrices.translate((panelX + PADDING).toFloat(), suppY.toFloat(), 0f)
                    context.matrices.scale(0.5f, 0.5f, 1f)
                    val suppText = "Needs: " + neededSuppliers.joinToString(", ") { "\u00A7e${it.skillName}\u00A77 (${it.description})" }
                    context.drawTextWithShadow(textRenderer, "\u00A78$suppText", 0, 0, 0x888888)
                    context.matrices.pop()
                }

                // Hint if gathering and nothing found
                if (project.phase == "GATHERING" && totalGathered == 0) {
                    val hintY = suppY + (if (neededSuppliers.isNotEmpty()) 7 else 0)
                    context.matrices.push()
                    context.matrices.translate((panelX + PADDING).toFloat(), hintY.toFloat(), 0f)
                    context.matrices.scale(0.5f, 0.5f, 1f)
                    context.drawTextWithShadow(textRenderer, "Assign supplier Mons or place materials in a nearby chest", 0, 0, 0xFF8888)
                    context.matrices.pop()
                }
            }

            y += cardH + 2
        }

        context.fill(panelX + 2, y, panelX + panelW - 2, y + 1, 0xFF444444.toInt())
        return y - startY + 2
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Category bar clicks
        val catY = panelY + PADDING + 14
        var cx = panelX + PADDING + panelW / 3 + 8
        for (cat in categories) {
            val catW = (textRenderer.getWidth(cat) * 0.6f).toInt() + 8
            if (mouseX >= cx && mouseX < cx + catW && mouseY >= catY && mouseY < catY + 11) {
                selectedCategory = cat
                scrollOffset = 0
                return true
            }
            cx += catW + 2
            if (cx > panelX + panelW - PADDING) break
        }

        // Supplier assign clicks
        for (row in supplierRows) {
            if (mouseY >= row.y && mouseY < row.y + 12 && mouseX >= panelX + panelW - 50 && mouseX <= panelX + panelW) {
                val supplyAssignment = "craftsman_supply:${row.skillId}"
                ClientPlayNetworking.send(notlown.cobblebase.core.net.SkillAssignmentC2SPacket(row.pokemonId, supplyAssignment))
                AssignmentCache.setAssignment(row.pokemonId, supplyAssignment)
                // Refresh workshop state
                ClientPlayNetworking.send(WorkshopRequestC2SPacket())
                return true
            }
        }

        // Recipe row [Set] button clicks
        val craftsmen = getCraftsmanPokemon()
        if (craftsmen.isEmpty()) return false

        val activeProjects = craftsmen.filter { WorkshopCache.projects.containsKey(it.pokemonId) }
        val projectSectionH = if (activeProjects.isNotEmpty()) {
            activeProjects.size * 24 + 4
        } else 0

        val listY = catY + 14 + projectSectionH + 6
        val listH = panelH - (listY - panelY) - PADDING
        val filtered = getFilteredRecipes()
        val maxVisible = listH / ROW_HEIGHT

        for (i in 0 until maxVisible) {
            val idx = i + scrollOffset
            if (idx >= filtered.size) break
            val rowY = listY + i * ROW_HEIGHT
            if (mouseY >= rowY && mouseY < rowY + ROW_HEIGHT && mouseX >= panelX && mouseX <= panelX + panelW) {
                // Click on [Set] area or anywhere on the row
                val btnX = panelX + panelW - PADDING - 22
                if (mouseX >= btnX || button == 0) {
                    val recipe = filtered[idx]
                    // Assign to the first Craftsman that doesn't have a project, or the first one
                    val target = craftsmen.firstOrNull { !WorkshopCache.projects.containsKey(it.pokemonId) }
                        ?: craftsmen.first()
                    ClientPlayNetworking.send(WorkshopSelectC2SPacket(target.pokemonId, recipe.recipeId))
                    // Refresh state
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
        if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + panelH) {
            scrollAccumulator -= verticalAmount
            val whole = scrollAccumulator.toInt()
            scrollAccumulator -= whole.toDouble()
            scrollOffset = (scrollOffset + whole).coerceAtLeast(0)
            val filtered = getFilteredRecipes()
            val listH = panelH - PADDING * 2 - 30
            val maxScroll = (filtered.size - listH / ROW_HEIGHT).coerceAtLeast(0)
            scrollOffset = scrollOffset.coerceAtMost(maxScroll)
            return true
        }
        return false
    }

    // ========== SUPPLIER SUGGESTIONS ==========

    /** Cached supplier row data for click handling */
    private data class SupplierRow(val y: Int, val pokemonId: UUID, val skillId: String)
    private var supplierRows = listOf<SupplierRow>()

    private fun renderSupplierSuggestions(
        context: DrawContext, mouseX: Int, mouseY: Int, startY: Int,
        project: WorkshopCache.ProjectState
    ): Int {
        val neededJobs = notlown.cobblebase.core.SupplierHelper.getNeededSuppliers(project.requiredItems)
        if (neededJobs.isEmpty()) return 0

        var y = startY

        // Header
        context.matrices.push()
        context.matrices.translate((panelX + PADDING).toFloat(), (y + 1).toFloat(), 0f)
        context.matrices.scale(0.6f, 0.6f, 1f)
        context.drawTextWithShadow(textRenderer, "\u00A76\u00A7lSuppliers needed:", 0, 0, 0xFFAA00)
        context.matrices.pop()
        y += 9

        // Find Mons that could fill each needed role
        val rows = mutableListOf<SupplierRow>()
        val nonCraftsmanMons = pokemonList.filter { p ->
            val assignment = AssignmentCache.getAssignment(p.pokemonId)
            assignment != "cobblebase:craftsman"
        }

        for (suggestion in neededJobs) {
            // Find Mons that have this skill
            val matchingMons = nonCraftsmanMons.filter { p ->
                val speciesName = notlown.cobblebase.core.SpeciesSkillRegistry.resolveFormName(
                    p.species.path, p.aspects
                )
                val skills = notlown.cobblebase.core.SpeciesSkillRegistry.getSkills(speciesName)?.skills ?: emptyList()
                skills.any { it.skillId == suggestion.skillId }
            }

            val currentAssignment = if (matchingMons.isNotEmpty()) {
                AssignmentCache.getAssignment(matchingMons.first().pokemonId)
            } else null
            val isAssigned = currentAssignment == "craftsman_supply:${suggestion.skillId}" || currentAssignment == suggestion.skillId

            // Row: [Job Name] - [Mon name or "No mon available"] - [Assign/Assigned button]
            val rowBg = if (isAssigned) 0x224CAF50 else 0x22FF9800
            context.fill(panelX + PADDING, y, panelX + panelW - PADDING, y + 12, rowBg)

            context.matrices.push()
            context.matrices.translate((panelX + PADDING + 2).toFloat(), (y + 2).toFloat(), 0f)
            context.matrices.scale(0.6f, 0.6f, 1f)

            if (matchingMons.isEmpty()) {
                context.drawTextWithShadow(textRenderer, "\u00A7c${suggestion.skillName}\u00A77 — no Pokemon with this skill in pasture", 0, 0, 0xAAAAAA)
            } else {
                val mon = matchingMons.first()
                val monName = mon.displayName.string
                if (isAssigned) {
                    context.drawTextWithShadow(textRenderer, "\u00A7a${suggestion.skillName}\u00A77 — $monName \u00A7a(active)", 0, 0, 0xAAAAAA)
                } else {
                    context.drawTextWithShadow(textRenderer, "\u00A7e${suggestion.skillName}\u00A77 — $monName", 0, 0, 0xAAAAAA)
                }

                // [Assign] button
                if (!isAssigned) {
                    rows.add(SupplierRow(y, mon.pokemonId, suggestion.skillId))
                    val btnText = "Assign"
                    val btnW = (textRenderer.getWidth(btnText) + 6)
                    val btnX = ((panelX + panelW - PADDING - 4) / 0.6f).toInt() - btnW
                    val btnHover = mouseX >= (btnX * 0.6f).toInt() + panelX / 3 && mouseY >= y && mouseY < y + 12
                    context.drawTextWithShadow(textRenderer, "\u00A7a[$btnText]", btnX, 0, if (btnHover) 0x55FF55 else 0x4CAF50)
                }
            }

            context.matrices.pop()
            y += 13
        }

        supplierRows = rows
        return y - startY + 2
    }

    // ========== HELPERS ==========

    private fun getFilteredRecipes(): List<RecipeListSyncS2CPacket.RecipeDTO> {
        return WorkshopCache.recipes.filter { recipe ->
            val matchesCategory = selectedCategory == "All" || recipe.category == selectedCategory
            val matchesSearch = searchText.isBlank() || recipe.outputDisplayName.lowercase().contains(searchText.lowercase())
            matchesCategory && matchesSearch
        }
    }

    private fun getCraftsmanPokemon(): List<PasturePokemonDataDTO> {
        return pokemonList.filter { p ->
            val assignment = AssignmentCache.getAssignment(p.pokemonId)
            assignment == "cobblebase:craftsman"
        }
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

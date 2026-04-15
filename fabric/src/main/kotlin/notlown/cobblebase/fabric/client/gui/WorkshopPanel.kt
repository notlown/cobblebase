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
    private val ROW_HEIGHT = 20
    private val SCALE = 0.75f
    private val ICON_SCALE = 16f / 16f  // full size item icons

    private enum class SubTab { OVERVIEW, RECIPES, PREVIEW }
    private var activeSubTab = SubTab.OVERVIEW
    private var previewRecipe: RecipeListSyncS2CPacket.RecipeDTO? = null

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

    // Scrollbar drag state
    private var isDraggingScrollbar = false
    private var scrollTrackX = 0
    private var scrollTrackY = 0
    private var scrollTrackH = 0
    private var scrollMaxScroll = 0

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
        if (activeSubTab == SubTab.PREVIEW) {
            renderSubTab(context, mouseX, mouseY, "Preview", SubTab.PREVIEW, panelX + PADDING + 106, tabY, 46, tabH)
        }

        searchField?.visible = activeSubTab == SubTab.RECIPES
        val contentY = tabY + tabH + 4

        when (activeSubTab) {
            SubTab.OVERVIEW -> renderOverview(context, mouseX, mouseY, contentY)
            SubTab.RECIPES -> renderRecipeBrowser(context, mouseX, mouseY, contentY)
            SubTab.PREVIEW -> renderPreview(context, mouseX, mouseY, contentY)
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
        context.fill(panelX + 2, y, panelX + panelW - 2, y + 34, 0x33FF5722)
        context.fill(panelX + 2, y, panelX + panelW - 2, y + 1, 0xFFFF5722.toInt())

        // Output item icon + name (LEFT side)
        if (recipe != null) {
            val outputStack = makeStack(recipe.outputItemId)
            if (!outputStack.isEmpty) {
                context.matrices.push()
                context.matrices.translate((panelX + PADDING).toFloat(), (y + 4).toFloat(), 0f)
                context.matrices.scale(1.5f, 1.5f, 1f)
                context.drawItem(outputStack, 0, 0)
                context.matrices.pop()
            }
            context.matrices.push()
            context.matrices.translate((panelX + PADDING + 28).toFloat(), (y + 5).toFloat(), 0f)
            context.matrices.scale(SCALE, SCALE, 1f)
            context.drawTextWithShadow(textRenderer, "\u00A7f\u00A7l${recipe.outputDisplayName}", 0, 0, 0xFFFFFF)
            context.matrices.pop()
        }

        // Phase + craft count (below item name)
        val phaseText = when (project.phase) {
            "GATHERING" -> "\u00A7eGathering materials..."
            "CRAFTING" -> "\u00A76Crafting..."
            "DEPOSITING" -> "\u00A7aDone! Depositing..."
            else -> "\u00A77Waiting..."
        }
        val craftCount = project.craftCount
        val countText = if (craftCount > 0) " \u00A78| \u00A7a${craftCount} crafted" else ""
        context.matrices.push()
        context.matrices.translate((panelX + PADDING + 28).toFloat(), (y + 16).toFloat(), 0f)
        context.matrices.scale(0.6f, 0.6f, 1f)
        context.drawTextWithShadow(textRenderer, "$phaseText$countText", 0, 0, 0xAAAAAA)
        context.matrices.pop()

        // Craftsman sprite (RIGHT side, 3x scale = 48px)
        renderScaledSprite(context, pokemonData.species.path, panelX + panelW - PADDING - 48, y + 1, 3.0f)
        // Mon name under sprite
        context.matrices.push()
        context.matrices.translate((panelX + panelW - PADDING - 48).toFloat(), (y + 26).toFloat(), 0f)
        context.matrices.scale(0.5f, 0.5f, 1f)
        context.drawTextWithShadow(textRenderer, pokemonData.displayName.string, 0, 0, 0xCCCCCC)
        context.matrices.pop()

        y += 36

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
            context.matrices.translate((panelX + PADDING + 20).toFloat(), (y + 4).toFloat(), 0f)
            context.matrices.scale(SCALE, SCALE, 1f)
            context.drawTextWithShadow(textRenderer, itemName, 0, 0, if (done) 0x55FF55 else 0xFFFFFF)
            context.matrices.pop()

            // Progress bar
            val barX = panelX + PADDING + 20 + (panelW * 0.3f).toInt()
            val barW = (panelW * 0.3f).toInt()
            context.fill(barX, y + 5, barX + barW, y + 11, 0xFF333333.toInt())
            val fillW = if (needed > 0) (barW * gathered.toFloat() / needed).toInt() else 0
            if (fillW > 0) context.fill(barX, y + 5, barX + fillW, y + 11, if (done) 0xFF4CAF50.toInt() else 0xFFFF9800.toInt())

            context.matrices.push()
            context.matrices.translate((barX + barW + 4).toFloat(), (y + 4).toFloat(), 0f)
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

            y += 18
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

        // --- Suppliers Section (per Mon, not per skill) ---
        context.fill(panelX + 2, y, panelX + panelW - 2, y + 1, 0xFFFF5722.toInt())
        y += 4
        context.matrices.push()
        context.matrices.translate((panelX + PADDING).toFloat(), y.toFloat(), 0f)
        context.matrices.scale(0.7f, 0.7f, 1f)
        context.drawTextWithShadow(textRenderer, "\u00A76\u00A7lSuppliers", 0, 0, 0xFFAA00)
        context.matrices.pop()
        y += 12

        val rows = mutableListOf<SupplierRow>()
        val nonCraftsmen = pokemonList.filter { AssignmentCache.getAssignment(it.pokemonId) != "cobblebase:craftsman" }
        val shownMons = mutableSetOf<UUID>() // prevent duplicates

        for (mon in nonCraftsmen) {
            if (mon.pokemonId in shownMons) continue
            val speciesName = SpeciesSkillRegistry.resolveFormName(mon.species.path, mon.aspects)
            val monSkills = SpeciesSkillRegistry.getSkills(speciesName)?.skills ?: emptyList()

            // Find which items this Mon can supply for the current project
            val canSupply = mutableListOf<Pair<String, String>>() // itemId -> skillId
            for ((itemId, _) in project.requiredItems) {
                val suppliers = notlown.cobblebase.core.SupplierHelper.getSupplierJobs(itemId)
                for (s in suppliers) {
                    if (monSkills.any { it.skillId == s.skillId } && canSupply.none { it.first == itemId }) {
                        canSupply.add(itemId to s.skillId)
                    }
                }
            }
            if (canSupply.isEmpty()) continue
            shownMons.add(mon.pokemonId)

            val currentAssignment = AssignmentCache.getAssignment(mon.pokemonId)
            val isSupplier = currentAssignment?.startsWith("craftsman_supply:") == true
            val activeSkillId = if (isSupplier) currentAssignment!!.removePrefix("craftsman_supply:") else null

            // Row background
            val rowBg = if (isSupplier) 0x224CAF50 else 0x22444444
            context.fill(panelX + PADDING, y, panelX + panelW - PADDING, y + 20, rowBg)
            if (isSupplier) context.fill(panelX + PADDING, y, panelX + PADDING + 2, y + 20, 0xFF4CAF50.toInt())

            // Pokemon sprite (1.25x scale = 20px)
            renderScaledSprite(context, mon.species.path, panelX + PADDING + 4, y + 1, 1.25f)

            // Mon name
            context.matrices.push()
            context.matrices.translate((panelX + PADDING + 22).toFloat(), (y + 2).toFloat(), 0f)
            context.matrices.scale(0.7f, 0.7f, 1f)
            context.drawTextWithShadow(textRenderer, mon.displayName.string, 0, 0, if (isSupplier) 0xFFFFFF else 0xCCCCCC)
            context.matrices.pop()

            if (isSupplier) {
                // Show what it's producing + cooldown
                val activeEntry = monSkills.find { it.skillId == activeSkillId }
                val activeDef = if (activeEntry != null) notlown.cobblebase.core.SkillRegistry.get(activeEntry.skillId) else null
                val producingItem = notlown.cobblebase.core.executors.SupplierExecutor.findNeededItemPublic(mon.pokemonId)
                val itemLabel = producingItem?.substringAfterLast(":")?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: "?"
                val cdText = if (activeDef != null && activeEntry != null) {
                    val cd = notlown.cobblebase.core.CobblebaseConfig.getEffectiveCooldownTicks(activeDef.cooldownSeconds, activeEntry.proficiency) / 20
                    "~${cd}s"
                } else ""

                context.matrices.push()
                context.matrices.translate((panelX + PADDING + 22).toFloat(), (y + 12).toFloat(), 0f)
                context.matrices.scale(0.5f, 0.5f, 1f)
                context.drawTextWithShadow(textRenderer, "\u00A7a\u2794 $itemLabel \u00A78| $cdText", 0, 0, 0x55FF55)
                context.matrices.pop()

                // Cooldown bar
                if (activeDef != null && activeEntry != null) {
                    val cdTicks = notlown.cobblebase.core.CobblebaseConfig.getEffectiveCooldownTicks(activeDef.cooldownSeconds, activeEntry.proficiency)
                    val cdMs = cdTicks * 50L
                    val progress2 = ((System.currentTimeMillis() % cdMs).toFloat() / cdMs)
                    val bx = panelX + PADDING + 22; val bw = panelX + panelW - PADDING - 44 - bx
                    context.fill(bx, y + 17, bx + bw, y + 19, 0xFF222222.toInt())
                    context.fill(bx, y + 17, bx + (bw * progress2).toInt(), y + 19, 0xFF4CAF50.toInt())
                }

                // [Remove] button
                val btnX = panelX + panelW - PADDING - 38
                val removeHover = mouseX >= btnX && mouseX < btnX + 36 && mouseY >= y && mouseY < y + 20
                context.fill(btnX, y + 3, btnX + 36, y + 17, if (removeHover) 0xFFFF5555.toInt() else 0xFF5A3A3A.toInt())
                context.matrices.push()
                context.matrices.translate((btnX + 3).toFloat(), (y + 6).toFloat(), 0f)
                context.matrices.scale(0.6f, 0.6f, 1f)
                context.drawTextWithShadow(textRenderer, "Remove", 0, 0, 0xFFFFFF)
                context.matrices.pop()
                rows.add(SupplierRow(y, mon.pokemonId, "REMOVE"))
            } else {
                // Show assignable items as small buttons
                context.matrices.push()
                context.matrices.translate((panelX + PADDING + 22).toFloat(), (y + 12).toFloat(), 0f)
                context.matrices.scale(0.5f, 0.5f, 1f)
                val itemNames = canSupply.map { it.first.substringAfterLast(":").replace("_", " ").replaceFirstChar { c -> c.uppercase() } }
                context.drawTextWithShadow(textRenderer, "\u00A78Can supply: ${itemNames.joinToString(", ")}", 0, 0, 0x888888)
                context.matrices.pop()

                // [Assign] button — uses the first matching skill
                val btnX = panelX + panelW - PADDING - 38
                val assignHover = mouseX >= btnX && mouseX < btnX + 36 && mouseY >= y && mouseY < y + 20
                context.fill(btnX, y + 3, btnX + 36, y + 17, if (assignHover) 0xFF4CAF50.toInt() else 0xFF3A6A3A.toInt())
                context.matrices.push()
                context.matrices.translate((btnX + 6).toFloat(), (y + 6).toFloat(), 0f)
                context.matrices.scale(0.6f, 0.6f, 1f)
                context.drawTextWithShadow(textRenderer, "Assign", 0, 0, 0xFFFFFF)
                context.matrices.pop()
                rows.add(SupplierRow(y, mon.pokemonId, canSupply.first().second))
            }

            y += 22
        }

        supplierRows = rows

        if (totalGathered == 0 && rows.isEmpty()) {
            context.matrices.push()
            context.matrices.translate((panelX + PADDING).toFloat(), (y + 4).toFloat(), 0f)
            context.matrices.scale(0.5f, 0.5f, 1f)
            context.drawTextWithShadow(textRenderer, "No compatible Mons in pasture. Place materials in a nearby chest.", 0, 0, 0xFF8888)
            context.matrices.pop()
        }
    }

    // ========== PREVIEW TAB ==========

    private var previewBtnY = 0

    private fun renderPreview(context: DrawContext, mouseX: Int, mouseY: Int, startY: Int) {
        val recipe = previewRecipe ?: run {
            activeSubTab = SubTab.RECIPES
            return
        }
        var y = startY

        // Header: output icon + name (big)
        val outputStack = makeStack(recipe.outputItemId)
        if (!outputStack.isEmpty) {
            context.matrices.push()
            context.matrices.translate((panelX + PADDING).toFloat(), y.toFloat(), 0f)
            context.matrices.scale(1.5f, 1.5f, 1f)
            context.drawItem(outputStack, 0, 0)
            context.matrices.pop()
        }
        context.matrices.push()
        context.matrices.translate((panelX + PADDING + 28).toFloat(), (y + 4).toFloat(), 0f)
        context.matrices.scale(SCALE, SCALE, 1f)
        val countSuffix = if (recipe.outputCount > 1) " x${recipe.outputCount}" else ""
        context.drawTextWithShadow(textRenderer, "\u00A7f\u00A7l${recipe.outputDisplayName}$countSuffix", 0, 0, 0xFFFFFF)
        context.matrices.pop()
        context.matrices.push()
        context.matrices.translate((panelX + PADDING + 28).toFloat(), (y + 14).toFloat(), 0f)
        context.matrices.scale(0.6f, 0.6f, 1f)
        context.drawTextWithShadow(textRenderer, "\u00A78${recipe.category}", 0, 0, 0x888888)
        context.matrices.pop()
        y += 28

        // Materials needed
        context.fill(panelX + 2, y, panelX + panelW - 2, y + 1, 0xFF444444.toInt())
        y += 4
        context.matrices.push()
        context.matrices.translate((panelX + PADDING).toFloat(), y.toFloat(), 0f)
        context.matrices.scale(0.65f, 0.65f, 1f)
        context.drawTextWithShadow(textRenderer, "\u00A78\u00A7lMATERIALS NEEDED", 0, 0, 0x888888)
        context.matrices.pop()
        y += 10

        for ((inputId, count) in recipe.inputs) {
            val stack = makeStack(inputId)
            if (!stack.isEmpty) {
                context.matrices.push()
                context.matrices.translate((panelX + PADDING).toFloat(), y.toFloat(), 0f)
                context.matrices.scale(ICON_SCALE, ICON_SCALE, 1f)
                context.drawItem(stack, 0, 0)
                context.matrices.pop()
            }
            val itemName = inputId.substringAfterLast(":").replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            context.matrices.push()
            context.matrices.translate((panelX + PADDING + 20).toFloat(), (y + 4).toFloat(), 0f)
            context.matrices.scale(SCALE, SCALE, 1f)
            context.drawTextWithShadow(textRenderer, "${count}x $itemName", 0, 0, 0xFFFFFF)
            // Skill suggestion
            val suppliers = notlown.cobblebase.core.SupplierHelper.getSupplierJobs(inputId)
            val skillNames = suppliers.filter { it.skillId != "manual" }.joinToString("/") { it.skillName }
            if (skillNames.isNotEmpty()) {
                val offset = textRenderer.getWidth("${count}x $itemName") + 6
                context.drawTextWithShadow(textRenderer, "\u00A78($skillNames)", offset, 0, 0x666666)
            }
            context.matrices.pop()
            y += 18
        }

        // Available suppliers in pasture
        y += 4
        context.fill(panelX + 2, y, panelX + panelW - 2, y + 1, 0xFF444444.toInt())
        y += 4
        context.matrices.push()
        context.matrices.translate((panelX + PADDING).toFloat(), y.toFloat(), 0f)
        context.matrices.scale(0.65f, 0.65f, 1f)
        context.drawTextWithShadow(textRenderer, "\u00A78\u00A7lAVAILABLE SUPPLIERS", 0, 0, 0x888888)
        context.matrices.pop()
        y += 10

        val nonCraftsmen = pokemonList.filter { AssignmentCache.getAssignment(it.pokemonId) != "cobblebase:craftsman" }
        var hasAny = false
        for (mon in nonCraftsmen) {
            val speciesName = SpeciesSkillRegistry.resolveFormName(mon.species.path, mon.aspects)
            val monSkills = SpeciesSkillRegistry.getSkills(speciesName)?.skills ?: emptyList()
            val canSupply = recipe.inputs.any { (itemId, _) ->
                val suppliers = notlown.cobblebase.core.SupplierHelper.getSupplierJobs(itemId)
                suppliers.any { s -> monSkills.any { it.skillId == s.skillId } }
            }
            if (!canSupply) continue
            hasAny = true
            renderScaledSprite(context, mon.species.path, panelX + PADDING + 4, y, 1.0f)
            context.matrices.push()
            context.matrices.translate((panelX + PADDING + 22).toFloat(), (y + 4).toFloat(), 0f)
            context.matrices.scale(0.65f, 0.65f, 1f)
            context.drawTextWithShadow(textRenderer, mon.displayName.string, 0, 0, 0xCCCCCC)
            context.matrices.pop()
            y += 18
        }
        if (!hasAny) {
            context.matrices.push()
            context.matrices.translate((panelX + PADDING).toFloat(), y.toFloat(), 0f)
            context.matrices.scale(0.55f, 0.55f, 1f)
            context.drawTextWithShadow(textRenderer, "No compatible Pokemon in pasture — you can still place materials manually", 0, 0, 0xFF8888)
            context.matrices.pop()
            y += 10
        }

        // [Start Project] button
        y += 8
        previewBtnY = y
        val craftsmen = getCraftsmanPokemon()
        if (craftsmen.isNotEmpty()) {
            val btnW = 100
            val btnX = panelX + (panelW - btnW) / 2
            val btnHover = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= y && mouseY < y + 16
            context.fill(btnX, y, btnX + btnW, y + 16, if (btnHover) 0xFF4CAF50.toInt() else 0xFF3A6A3A.toInt())
            context.matrices.push()
            context.matrices.translate((btnX + 10).toFloat(), (y + 4).toFloat(), 0f)
            context.matrices.scale(0.7f, 0.7f, 1f)
            context.drawTextWithShadow(textRenderer, "Start Project", 0, 0, 0xFFFFFF)
            context.matrices.pop()
        } else {
            context.matrices.push()
            context.matrices.translate((panelX + PADDING).toFloat(), y.toFloat(), 0f)
            context.matrices.scale(0.55f, 0.55f, 1f)
            context.drawTextWithShadow(textRenderer, "Assign a Craftsman first to start this project", 0, 0, 0xFF8888)
            context.matrices.pop()
        }

        // [Back] button
        context.matrices.push()
        context.matrices.translate((panelX + PADDING).toFloat(), (panelY + panelH - 14).toFloat(), 0f)
        context.matrices.scale(0.6f, 0.6f, 1f)
        context.drawTextWithShadow(textRenderer, "\u00A78< Back to Recipes", 0, 0, 0x888888)
        context.matrices.pop()
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

        // Scrollbar (wider, 6px)
        scrollTrackX = panelX + panelW - 8
        scrollTrackY = listY
        scrollTrackH = listH
        scrollMaxScroll = maxScroll
        if (filtered.size > maxVisible && maxScroll > 0) {
            val thumbH = ((maxVisible.toFloat() / filtered.size) * listH).toInt().coerceAtLeast(14)
            val thumbY = listY + ((scrollOffset.toFloat() / maxScroll) * (listH - thumbH)).toInt()
            context.fill(scrollTrackX, listY, scrollTrackX + 6, listY + listH, 0x33FFFFFF)
            context.fill(scrollTrackX, thumbY, scrollTrackX + 6, thumbY + thumbH, 0xAAFFFFFF.toInt())
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
                activeSubTab = SubTab.RECIPES; scrollOffset = 0; previewRecipe = null; return true
            }
            if (activeSubTab == SubTab.PREVIEW && mouseX >= panelX + PADDING + 106 && mouseX < panelX + PADDING + 152) {
                return true // already on preview
            }
        }

        // Preview tab clicks
        if (activeSubTab == SubTab.PREVIEW && previewRecipe != null) {
            // [Back] link
            if (mouseY >= panelY + panelH - 14 && mouseY < panelY + panelH && mouseX >= panelX + PADDING && mouseX < panelX + 100) {
                activeSubTab = SubTab.RECIPES
                previewRecipe = null
                return true
            }
            // [Start Project] button
            val craftsmen = getCraftsmanPokemon()
            if (craftsmen.isNotEmpty()) {
                val btnW = 100
                val btnX = panelX + (panelW - btnW) / 2
                if (mouseX >= btnX && mouseX < btnX + btnW && mouseY >= previewBtnY && mouseY < previewBtnY + 16) {
                    val target = craftsmen.firstOrNull { !WorkshopCache.projects.containsKey(it.pokemonId) } ?: craftsmen.first()
                    resetStaleSuppliers(previewRecipe!!.recipeId)
                    ClientPlayNetworking.send(WorkshopSelectC2SPacket(target.pokemonId, previewRecipe!!.recipeId))
                    activeSubTab = SubTab.OVERVIEW
                    previewRecipe = null
                    ClientPlayNetworking.send(WorkshopRequestC2SPacket())
                    return true
                }
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

            // Scrollbar click
            if (scrollMaxScroll > 0 && mouseX >= scrollTrackX - 2 && mouseX <= scrollTrackX + 8 &&
                mouseY >= scrollTrackY && mouseY <= scrollTrackY + scrollTrackH) {
                isDraggingScrollbar = true
                val relY = ((mouseY - scrollTrackY) / scrollTrackH.toDouble()).coerceIn(0.0, 1.0)
                scrollOffset = (relY * scrollMaxScroll).toInt()
                return true
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
                    previewRecipe = filtered[idx]
                    activeSubTab = SubTab.PREVIEW
                    return true
                }
            }
        }

        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (isDraggingScrollbar && scrollMaxScroll > 0) {
            val relY = ((mouseY - scrollTrackY) / scrollTrackH.toDouble()).coerceIn(0.0, 1.0)
            scrollOffset = (relY * scrollMaxScroll).toInt()
            return true
        }
        return false
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (isDraggingScrollbar) { isDraggingScrollbar = false; return true }
        return false
    }

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

    /**
     * When switching recipes, free suppliers whose skills don't match the new recipe's needs.
     */
    private fun resetStaleSuppliers(newRecipeId: String) {
        val newRecipe = WorkshopCache.recipes.find { it.recipeId == newRecipeId } ?: return
        val newNeededItems = newRecipe.inputs.map { it.first }.toSet()
        val neededSkills = newNeededItems.flatMap { itemId ->
            notlown.cobblebase.core.SupplierHelper.getSupplierJobs(itemId).map { it.skillId }
        }.toSet()

        // Find all current suppliers and free those whose skill doesn't match
        for (mon in pokemonList) {
            val assignment = AssignmentCache.getAssignment(mon.pokemonId) ?: continue
            if (!assignment.startsWith("craftsman_supply:")) continue
            val supplierSkill = assignment.removePrefix("craftsman_supply:")
            if (supplierSkill !in neededSkills) {
                ClientPlayNetworking.send(notlown.cobblebase.core.net.SkillAssignmentC2SPacket(mon.pokemonId, ""))
                AssignmentCache.setAssignment(mon.pokemonId, null)
            }
        }
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

    /** Render a Pokemon sprite at a custom scale (default 16px, scale 1.5 = 24px) */
    private fun renderScaledSprite(context: DrawContext, species: String, x: Int, y: Int, scale: Float) {
        context.matrices.push()
        context.matrices.translate(x.toFloat(), y.toFloat(), 0f)
        context.matrices.scale(scale, scale, 1f)
        PokemonSpriteHelper.renderSmallIconByName(context, textRenderer, species, 0, 0, 0f)
        context.matrices.pop()
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

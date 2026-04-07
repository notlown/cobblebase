package notlown.cobblebase.fabric.client.gui

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text
import notlown.cobblebase.core.AdminDataCache
import java.util.function.Function

/**
 * Left pane of the Admin GUI: searchable species list.
 */
class AdminSpeciesListPanel(
    private val x: Int,
    private val y: Int,
    private val w: Int,
    private val h: Int,
    private val textRenderer: TextRenderer,
    private val onSelect: (String) -> Unit
) {
    private val ROW_HEIGHT = 18
    private val SEARCH_HEIGHT = 16
    private val PADDING = 4

    private val ROW_EVEN = 0x33FFFFFF.toInt()
    private val ROW_ODD = 0x1AFFFFFF.toInt()
    private val ROW_SELECTED = 0x662A2A5E.toInt()
    private val ROW_HOVER = 0x44333355.toInt()
    private val OVERRIDE_INDICATOR = 0xFFFF9800.toInt()

    private var searchField: TextFieldWidget? = null
    private var filteredSpecies: List<String> = emptyList()
    var selectedSpecies: String? = null
        private set
    private var scrollOffset = 0
    private var isDraggingScrollbar = false

    /** Sort modes cycled by the toggle button next to the "Species" header. */
    private enum class SortMode { POKEDEX, A_Z, Z_A }
    private var sortMode = SortMode.POKEDEX

    /** Cached lookup of national pokedex numbers from Cobblemon's runtime registry. */
    private val pokedexCache = mutableMapOf<String, Int>()
    private fun pokedexNumber(name: String): Int {
        return pokedexCache.getOrPut(name) {
            try {
                val species = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByName(name)
                // nationalPokedexNumber is the canonical field on cobblemon's Species class
                species?.nationalPokedexNumber ?: Int.MAX_VALUE
            } catch (_: Throwable) {
                Int.MAX_VALUE
            }
        }
    }

    private fun applySort(list: List<String>): List<String> = when (sortMode) {
        SortMode.POKEDEX -> list.sortedWith(compareBy({ pokedexNumber(it) }, { it }))
        SortMode.A_Z -> list.sortedBy { it }
        SortMode.Z_A -> list.sortedByDescending { it }
    }

    /**
     * Hit-box of the sort toggle button next to the Species header.
     * Returns (x, y, w, h).
     */
    private fun sortToggleBounds(): IntArray {
        val toggleW = 28
        val toggleH = 10
        val toggleX = x + w - PADDING - toggleW - 4
        val toggleY = y + PADDING + SEARCH_HEIGHT + 3
        return intArrayOf(toggleX, toggleY, toggleW, toggleH)
    }

    fun init(addWidget: Function<TextFieldWidget, TextFieldWidget>) {
        searchField = TextFieldWidget(textRenderer, x + PADDING, y + PADDING, w - PADDING * 2, SEARCH_HEIGHT, Text.literal("Search..."))
        searchField!!.setPlaceholder(Text.literal("\u00A77Search species..."))
        searchField!!.setChangedListener { updateFilter() }
        addWidget.apply(searchField!!)
        updateFilter()
    }

    private var showAddOption = false
    private var addQuery = ""

    private fun updateFilter() {
        val query = searchField?.text?.lowercase()?.trim() ?: ""
        val allSpecies = AdminDataCache.allSpecies
        val matched = if (query.isEmpty()) allSpecies else allSpecies.filter { it.contains(query) }
        filteredSpecies = applySort(matched)
        // Show "Add new species" option when query doesn't match any existing species exactly
        showAddOption = query.isNotEmpty() && query !in allSpecies
        addQuery = query
        scrollOffset = 0
    }

    fun select(species: String) {
        selectedSpecies = species
        onSelect(species)
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Background
        context.fill(x, y, x + w, y + h, 0xCC1A1A2E.toInt())

        // Header — "Species" label + sort toggle button on the right
        val headerY = y + PADDING + SEARCH_HEIGHT + 4
        context.drawTextWithShadow(textRenderer, "\u00A7fSpecies", x + PADDING, headerY, 0xFFFFFF)

        val tb = sortToggleBounds()
        val toggleX = tb[0]; val toggleY = tb[1]; val toggleW = tb[2]; val toggleH = tb[3]
        val toggleHovered = mouseX in toggleX..(toggleX + toggleW) && mouseY in toggleY..(toggleY + toggleH)
        val toggleBg = if (toggleHovered) 0xFF3A3A6C.toInt() else 0xFF252545.toInt()
        context.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, toggleBg)
        context.fill(toggleX, toggleY, toggleX + toggleW, toggleY + 1, 0xFF6A6AFF.toInt())
        val label = when (sortMode) {
            SortMode.POKEDEX -> "\u00A7fDex#"
            SortMode.A_Z -> "\u00A7fA-Z"
            SortMode.Z_A -> "\u00A7fZ-A"
        }
        context.matrices.push()
        context.matrices.translate((toggleX + 4).toFloat(), (toggleY + 2).toFloat(), 0f)
        context.matrices.scale(0.75f, 0.75f, 1f)
        context.drawTextWithShadow(textRenderer, label, 0, 0, 0xFFFFFF)
        context.matrices.pop()

        val listY = y + PADDING + SEARCH_HEIGHT + 16
        val listH = h - PADDING - SEARCH_HEIGHT - 20
        val maxVisible = listH / ROW_HEIGHT

        // Clamp scroll
        val maxScroll = (filteredSpecies.size - maxVisible).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        // Species skill counts from cache
        val speciesSkills = AdminDataCache.speciesSkills
        val overridden = AdminDataCache.overriddenSpecies

        // "Add new species" row at top when search doesn't match
        var addRowOffset = 0
        if (showAddOption) {
            val addRowY = listY
            val isHovered = mouseX in x..(x + w) && mouseY in addRowY..(addRowY + ROW_HEIGHT)
            val bg = if (isHovered) 0x4400AA00.toInt() else 0x33006600.toInt()
            context.fill(x + 2, addRowY, x + w - 2, addRowY + ROW_HEIGHT, bg)
            val scale = 0.75f
            context.matrices.push()
            context.matrices.translate((x + 12).toFloat(), (addRowY + 5).toFloat(), 0f)
            context.matrices.scale(scale, scale, 1f)
            context.drawTextWithShadow(textRenderer, "\u00A7a+ Add \"${addQuery}\"", 0, 0, 0x55FF55)
            context.matrices.pop()
            addRowOffset = 1
        }

        for (i in 0 until maxVisible - addRowOffset) {
            val idx = i + scrollOffset
            if (idx >= filteredSpecies.size) break

            val species = filteredSpecies[idx]
            val rowY = listY + (i + addRowOffset) * ROW_HEIGHT
            val isSelected = species == selectedSpecies
            val isHovered = mouseX in x..(x + w) && mouseY in rowY..(rowY + ROW_HEIGHT)

            val bg = when {
                isSelected -> ROW_SELECTED
                isHovered -> ROW_HOVER
                idx % 2 == 0 -> ROW_EVEN
                else -> ROW_ODD
            }
            context.fill(x + 2, rowY, x + w - 2, rowY + ROW_HEIGHT, bg)

            // Override indicator (orange dot)
            if (overridden.contains(species)) {
                context.fill(x + 4, rowY + 6, x + 8, rowY + 10, OVERRIDE_INDICATOR)
            }

            // Pokemon sprite
            PokemonSpriteHelper.renderSmallIconByName(context, textRenderer, species, x + 10, rowY + 3, delta)

            // Species name (0.75x scaled)
            val nameX = x + 26
            val displayName = species.replaceFirstChar { it.uppercase() }
            val scale = 0.75f
            context.matrices.push()
            context.matrices.translate(nameX.toFloat(), (rowY + 5).toFloat(), 0f)
            context.matrices.scale(scale, scale, 1f)
            context.drawTextWithShadow(textRenderer, displayName, 0, 0, if (isSelected) 0xFFFFFF else 0xCCCCCC)
            context.matrices.pop()

            // Skill count removed — shown in editor panel after lazy load
        }

        // Scrollbar
        if (filteredSpecies.size > maxVisible) {
            val trackX = x + w - 3
            val trackH = listH
            val thumbH = ((maxVisible.toFloat() / filteredSpecies.size) * trackH).toInt().coerceAtLeast(10)
            val thumbY = listY + ((scrollOffset.toFloat() / maxScroll) * (trackH - thumbH)).toInt()
            context.fill(trackX, listY, trackX + 2, listY + trackH, 0x33FFFFFF)
            context.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xAAFFFFFF.toInt())
        }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Sort toggle click — cycle POKEDEX → A_Z → Z_A → POKEDEX
        val tb = sortToggleBounds()
        if (mouseX in tb[0].toDouble()..(tb[0] + tb[2]).toDouble() &&
            mouseY in tb[1].toDouble()..(tb[1] + tb[3]).toDouble()
        ) {
            sortMode = when (sortMode) {
                SortMode.POKEDEX -> SortMode.A_Z
                SortMode.A_Z -> SortMode.Z_A
                SortMode.Z_A -> SortMode.POKEDEX
            }
            updateFilter()
            return true
        }

        val listY = y + PADDING + SEARCH_HEIGHT + 16
        val listH = h - PADDING - SEARCH_HEIGHT - 20

        // Check scrollbar click (8px wide hitbox around the 2px scrollbar track)
        val trackX = x + w - 3
        val maxVisible = listH / ROW_HEIGHT
        val maxScroll = (filteredSpecies.size - maxVisible).coerceAtLeast(0)
        if (maxScroll > 0 && mouseX >= trackX - 4 && mouseX <= trackX + 4 && mouseY >= listY && mouseY <= listY + listH) {
            isDraggingScrollbar = true
            val trackHeight = listH
            val relativeY = ((mouseY - listY) / trackHeight.toDouble()).coerceIn(0.0, 1.0)
            scrollOffset = (relativeY * maxScroll).toInt()
            return true
        }

        if (mouseX >= x && mouseX <= x + w && mouseY >= listY && mouseY <= listY + listH) {
            val row = ((mouseY - listY) / ROW_HEIGHT).toInt()

            // Handle "Add new species" click
            if (showAddOption && row == 0) {
                val newSpecies = addQuery.lowercase().trim().replace(" ", "_")
                if (newSpecies.isNotEmpty()) {
                    // Add to cache so it appears in the list
                    if (newSpecies !in AdminDataCache.allSpecies) {
                        AdminDataCache.allSpecies = (AdminDataCache.allSpecies + newSpecies).sorted()
                    }
                    updateFilter()
                    select(newSpecies)
                }
                return true
            }

            val addOffset = if (showAddOption) 1 else 0
            val idx = (row - addOffset) + scrollOffset
            if (idx in filteredSpecies.indices) {
                select(filteredSpecies[idx])
                return true
            }
        }
        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (isDraggingScrollbar) {
            val listY = y + PADDING + SEARCH_HEIGHT + 16
            val listH = h - PADDING - SEARCH_HEIGHT - 20
            val maxVisible = listH / ROW_HEIGHT
            val maxScroll = (filteredSpecies.size - maxVisible).coerceAtLeast(0)
            val trackHeight = listH
            val relativeY = ((mouseY - listY) / trackHeight.toDouble()).coerceIn(0.0, 1.0)
            scrollOffset = (relativeY * maxScroll).toInt()
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

    private var scrollAccumulator = 0.0

    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
            // Accumulate fractional scroll input (touchpad / smooth scroll mice)
            scrollAccumulator -= verticalAmount
            val whole = scrollAccumulator.toInt()
            scrollAccumulator -= whole.toDouble()
            scrollOffset = (scrollOffset + whole).coerceAtLeast(0)
            val maxScroll = (filteredSpecies.size - ((h - 20) / ROW_HEIGHT)).coerceAtLeast(0)
            scrollOffset = scrollOffset.coerceAtMost(maxScroll)
            return true
        }
        return false
    }
}

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
    private val scrollbar = ScrollbarComponent(trackWidth = 4, minThumbHeight = 12)

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
        // scrollOffset is in PIXELS, not row index. Smooth scroll, no per-tick row skips.
        var addRowOffset = 0
        val addRowHeaderPx: Int
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
            addRowHeaderPx = ROW_HEIGHT
        } else {
            addRowHeaderPx = 0
        }

        val scrollableH = listH - addRowHeaderPx
        val contentPx = filteredSpecies.size * ROW_HEIGHT
        val maxScroll = (contentPx - scrollableH).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        val speciesSkills = AdminDataCache.speciesSkills
        val overridden = AdminDataCache.overriddenSpecies

        val rowsAreaTop = listY + addRowHeaderPx
        val rowsAreaBottom = listY + listH
        val firstIdx = scrollOffset / ROW_HEIGHT

        context.enableScissor(x, rowsAreaTop, x + w, rowsAreaBottom)

        var idx = firstIdx
        while (idx < filteredSpecies.size) {
            val rowY = rowsAreaTop + idx * ROW_HEIGHT - scrollOffset
            if (rowY >= rowsAreaBottom) break

            val species = filteredSpecies[idx]
            val isSelected = species == selectedSpecies
            val isHovered = mouseX in x..(x + w) && mouseY in rowY..(rowY + ROW_HEIGHT) && mouseY in rowsAreaTop..rowsAreaBottom

            val bg = when {
                isSelected -> ROW_SELECTED
                isHovered -> ROW_HOVER
                idx % 2 == 0 -> ROW_EVEN
                else -> ROW_ODD
            }
            context.fill(x + 2, rowY, x + w - 2, rowY + ROW_HEIGHT, bg)

            if (overridden.contains(species)) {
                context.fill(x + 4, rowY + 6, x + 8, rowY + 10, OVERRIDE_INDICATOR)
            }

            PokemonSpriteHelper.renderSmallIconByName(context, textRenderer, species, x + 10, rowY + 3, delta)

            val nameX = x + 26
            val displayName = species.replaceFirstChar { it.uppercase() }
            val scale = 0.75f
            context.matrices.push()
            context.matrices.translate(nameX.toFloat(), (rowY + 5).toFloat(), 0f)
            context.matrices.scale(scale, scale, 1f)
            context.drawTextWithShadow(textRenderer, displayName, 0, 0, if (isSelected) 0xFFFFFF else 0xCCCCCC)
            context.matrices.pop()

            idx++
        }
        context.disableScissor()

        scrollbar.layout(
            trackX = x + w - 4,
            trackY = rowsAreaTop,
            trackHeight = scrollableH,
            contentHeight = contentPx,
            viewportHeight = scrollableH,
            currentScroll = scrollOffset,
        )
        scrollbar.render(context, 0, 0)
        scrollOffset = scrollbar.scroll
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
        val addRowHeaderPx = if (showAddOption) ROW_HEIGHT else 0
        val rowsAreaTop = listY + addRowHeaderPx
        val scrollableH = listH - addRowHeaderPx
        val contentPx = filteredSpecies.size * ROW_HEIGHT
        val maxScroll = (contentPx - scrollableH).coerceAtLeast(0)

        // Scrollbar drag — shared component handles thumb-grab + track-jump.
        if (scrollbar.mouseClicked(mouseX, mouseY)) {
            scrollOffset = scrollbar.scroll
            return true
        }

        // Handle "Add new species" click — sits in the fixed header area above the scroll region.
        if (showAddOption && mouseY in listY.toDouble()..(listY + ROW_HEIGHT).toDouble() && mouseX in x.toDouble()..(x + w).toDouble()) {
            val newSpecies = addQuery.lowercase().trim().replace(" ", "_")
            if (newSpecies.isNotEmpty()) {
                if (newSpecies !in AdminDataCache.allSpecies) {
                    AdminDataCache.allSpecies = (AdminDataCache.allSpecies + newSpecies).sorted()
                }
                updateFilter()
                select(newSpecies)
            }
            return true
        }

        // Row click — derive index from pixel-aware scrollOffset.
        if (mouseX >= x && mouseX <= x + w && mouseY >= rowsAreaTop && mouseY <= rowsAreaTop + scrollableH) {
            val idx = ((mouseY - rowsAreaTop).toInt() + scrollOffset) / ROW_HEIGHT
            if (idx in filteredSpecies.indices) {
                select(filteredSpecies[idx])
                return true
            }
        }
        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (scrollbar.mouseDragged(mouseY)) {
            scrollOffset = scrollbar.scroll
            return true
        }
        return false
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean = scrollbar.mouseReleased()

    /** Pixels scrolled per wheel notch. 9px = half a row — smooth without losing the row rhythm. */
    private val SCROLL_STEP_PX = 9

    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
            val listY = y + PADDING + SEARCH_HEIGHT + 16
            val listH = h - PADDING - SEARCH_HEIGHT - 20
            val addRowHeaderPx = if (showAddOption) ROW_HEIGHT else 0
            val scrollableH = listH - addRowHeaderPx
            val contentPx = filteredSpecies.size * ROW_HEIGHT
            val maxScroll = (contentPx - scrollableH).coerceAtLeast(0)
            scrollOffset = (scrollOffset - (verticalAmount * SCROLL_STEP_PX).toInt()).coerceIn(0, maxScroll)
            return true
        }
        return false
    }
}

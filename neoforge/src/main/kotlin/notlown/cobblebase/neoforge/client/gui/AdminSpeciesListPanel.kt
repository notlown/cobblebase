package notlown.cobblebase.neoforge.client.gui

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

    fun init(addWidget: Function<TextFieldWidget, TextFieldWidget>) {
        searchField = TextFieldWidget(textRenderer, x + PADDING, y + PADDING, w - PADDING * 2, SEARCH_HEIGHT, Text.literal("Search..."))
        searchField!!.setPlaceholder(Text.literal("\u00A77Search species..."))
        searchField!!.setChangedListener { updateFilter() }
        addWidget.apply(searchField!!)
        updateFilter()
    }

    private fun updateFilter() {
        val query = searchField?.text?.lowercase()?.trim() ?: ""
        val allSpecies = AdminDataCache.allSpecies
        filteredSpecies = if (query.isEmpty()) {
            allSpecies
        } else {
            allSpecies.filter { it.contains(query) }
        }
        scrollOffset = 0
    }

    fun select(species: String) {
        selectedSpecies = species
        onSelect(species)
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Background
        context.fill(x, y, x + w, y + h, 0xCC1A1A2E.toInt())

        // Header
        context.drawTextWithShadow(textRenderer, "\u00A7fSpecies", x + PADDING, y + PADDING + SEARCH_HEIGHT + 4, 0xFFFFFF)

        val listY = y + PADDING + SEARCH_HEIGHT + 16
        val listH = h - PADDING - SEARCH_HEIGHT - 20
        val maxVisible = listH / ROW_HEIGHT

        // Clamp scroll
        val maxScroll = (filteredSpecies.size - maxVisible).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        // Species skill counts from cache
        val speciesSkills = AdminDataCache.speciesSkills
        val overridden = AdminDataCache.overriddenSpecies

        for (i in 0 until maxVisible) {
            val idx = i + scrollOffset
            if (idx >= filteredSpecies.size) break

            val species = filteredSpecies[idx]
            val rowY = listY + i * ROW_HEIGHT
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

            // Species name
            val nameX = x + 12
            val displayName = species.replaceFirstChar { it.uppercase() }
            context.drawTextWithShadow(textRenderer, displayName, nameX, rowY + 5, if (isSelected) 0xFFFFFF else 0xCCCCCC)

            // Skill count
            val skills = speciesSkills[species]
            val countText = if (skills != null) "${skills.size}" else "0"
            val countColor = if (skills != null && skills.isNotEmpty()) 0x88FF88 else 0x666666
            context.drawTextWithShadow(textRenderer, countText, x + w - 20, rowY + 5, countColor)
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
        val listY = y + PADDING + SEARCH_HEIGHT + 16
        val listH = h - PADDING - SEARCH_HEIGHT - 20
        val maxVisible = listH / ROW_HEIGHT

        if (mouseX >= x && mouseX <= x + w && mouseY >= listY && mouseY <= listY + listH) {
            val row = ((mouseY - listY) / ROW_HEIGHT).toInt()
            val idx = row + scrollOffset
            if (idx in filteredSpecies.indices) {
                select(filteredSpecies[idx])
                return true
            }
        }
        return false
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
            scrollOffset -= verticalAmount.toInt()
            return true
        }
        return false
    }
}

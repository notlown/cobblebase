package notlown.cobblebase.fabric.client.gui

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier

/**
 * Renders a small Pokemon type-colored icon with species initials.
 * Uses the Pokemon's primary type hue from Cobblemon's ElementalType.
 *
 * This is a lightweight alternative to rendering 3D Pokemon models,
 * suitable for inline icons in scrollable lists.
 */
object PokemonSpriteHelper {

    const val ICON_SIZE = 16
    private const val BORDER_RADIUS_APPROX = 2 // visual padding for the circle approximation

    /**
     * Type color lookup with fallback. Maps type names to ARGB colors.
     * These match Cobblemon's ElementalType hue values.
     */
    private val TYPE_COLORS = mapOf(
        "normal" to 0xFFE8E8DA.toInt(),
        "fire" to 0xFFFF6E21.toInt(),
        "water" to 0xFF3FA5FF.toInt(),
        "grass" to 0xFF62D14F.toInt(),
        "electric" to 0xFFFFD314.toInt(),
        "ice" to 0xFF54F2F2.toInt(),
        "fighting" to 0xFFEF565D.toInt(),
        "poison" to 0xFFD651FF.toInt(),
        "ground" to 0xFFF4A453.toInt(),
        "flying" to 0xFFB8B2FF.toInt(),
        "psychic" to 0xFFFF5E9E.toInt(),
        "bug" to 0xFFD3D319.toInt(),
        "rock" to 0xFFB7A16E.toInt(),
        "ghost" to 0xFF9C80F7.toInt(),
        "dragon" to 0xFF7580FF.toInt(),
        "dark" to 0xFF7E7E8E.toInt(),
        "steel" to 0xFFB8B8CE.toInt(),
        "fairy" to 0xFFFF8AE8.toInt()
    )

    private val DEFAULT_COLOR = 0xFF888888.toInt()

    /**
     * Get the primary type color for a species Identifier.
     */
    fun getTypeColor(species: Identifier): Int {
        val speciesData = PokemonSpecies.getByIdentifier(species) ?: return DEFAULT_COLOR
        val typeName = speciesData.primaryType.name.lowercase()
        return TYPE_COLORS[typeName] ?: DEFAULT_COLOR
    }

    /**
     * Get the first 2 characters of the species display name for the icon label.
     */
    fun getInitials(displayName: String): String {
        val clean = displayName.trim()
        return if (clean.length >= 2) clean.substring(0, 2).uppercase() else clean.uppercase()
    }

    /**
     * Renders a small colored icon with species initials at the given position.
     *
     * @param context    The draw context
     * @param textRenderer The text renderer
     * @param species    The species Identifier (e.g. cobblemon:pikachu)
     * @param displayName The display name of the Pokemon
     * @param x          Left edge of the icon
     * @param y          Top edge of the icon
     */
    fun renderIcon(
        context: DrawContext,
        textRenderer: TextRenderer,
        species: Identifier,
        displayName: String,
        x: Int,
        y: Int
    ) {
        val color = getTypeColor(species)
        val initials = getInitials(displayName)

        // Darker background for contrast
        val bgColor = darkenColor(color, 0.35f)

        // Draw rounded-ish background (main rect + corner fills)
        // Main body
        context.fill(x + 1, y, x + ICON_SIZE - 1, y + ICON_SIZE, bgColor)
        // Top/bottom edge fills for rounded look
        context.fill(x, y + 1, x + 1, y + ICON_SIZE - 1, bgColor)
        context.fill(x + ICON_SIZE - 1, y + 1, x + ICON_SIZE, y + ICON_SIZE - 1, bgColor)

        // Colored border (top, bottom, left, right with 1px corner inset)
        context.fill(x + 1, y, x + ICON_SIZE - 1, y + 1, color) // top
        context.fill(x + 1, y + ICON_SIZE - 1, x + ICON_SIZE - 1, y + ICON_SIZE, color) // bottom
        context.fill(x, y + 1, x + 1, y + ICON_SIZE - 1, color) // left
        context.fill(x + ICON_SIZE - 1, y + 1, x + ICON_SIZE, y + ICON_SIZE - 1, color) // right

        // Draw initials centered
        val textWidth = textRenderer.getWidth(initials)
        val textX = x + (ICON_SIZE - textWidth) / 2
        val textY = y + (ICON_SIZE - 8) / 2 // 8 = font height
        context.drawTextWithShadow(textRenderer, initials, textX, textY, 0xFFFFFF)
    }

    /**
     * Renders a smaller icon (for compact rows like logs).
     * Size: 12x12 pixels.
     */
    fun renderSmallIcon(
        context: DrawContext,
        textRenderer: TextRenderer,
        species: Identifier,
        displayName: String,
        x: Int,
        y: Int
    ) {
        val size = 12
        val color = getTypeColor(species)
        val initial = if (displayName.isNotEmpty()) displayName[0].uppercase() else "?"
        val bgColor = darkenColor(color, 0.35f)

        // Background
        context.fill(x + 1, y, x + size - 1, y + size, bgColor)
        context.fill(x, y + 1, x + 1, y + size - 1, bgColor)
        context.fill(x + size - 1, y + 1, x + size, y + size - 1, bgColor)

        // Border
        context.fill(x + 1, y, x + size - 1, y + 1, color)
        context.fill(x + 1, y + size - 1, x + size - 1, y + size, color)
        context.fill(x, y + 1, x + 1, y + size - 1, color)
        context.fill(x + size - 1, y + 1, x + size, y + size - 1, color)

        // Single initial centered
        val textWidth = textRenderer.getWidth(initial)
        val textX = x + (size - textWidth) / 2
        val textY = y + (size - 8) / 2
        context.drawTextWithShadow(textRenderer, initial, textX, textY, 0xFFFFFF)
    }

    /**
     * Renders a small icon for log entries where we only have the Pokemon name (no species Identifier).
     * Tries to resolve the species from the name, falls back to a name-hash-based color.
     */
    fun renderSmallIconByName(
        context: DrawContext,
        textRenderer: TextRenderer,
        pokemonName: String,
        x: Int,
        y: Int
    ) {
        // Try to look up species by name (lowercase, no spaces)
        val speciesId = resolveSpeciesFromName(pokemonName)
        if (speciesId != null) {
            renderSmallIcon(context, textRenderer, speciesId, pokemonName, x, y)
        } else {
            // Fallback: render with a color derived from the name hash
            renderSmallIconWithColor(context, textRenderer, pokemonName, nameToColor(pokemonName), x, y)
        }
    }

    /**
     * Try to resolve a species Identifier from a display name.
     * Cobblemon species are stored as cobblemon:{species_name}.
     */
    private fun resolveSpeciesFromName(name: String): Identifier? {
        val normalized = name.trim().lowercase().replace(" ", "_").replace("-", "_")
        val id = Identifier.of("cobblemon", normalized)
        return if (PokemonSpecies.getByIdentifier(id) != null) id else null
    }

    /**
     * Generate a consistent color from a Pokemon name hash.
     */
    private fun nameToColor(name: String): Int {
        val hash = name.hashCode()
        val hue = (hash and 0x7FFFFFFF) % 360
        // Convert HSV to RGB with S=0.7, V=0.9
        return hsvToArgb(hue.toFloat(), 0.7f, 0.9f)
    }

    private fun hsvToArgb(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1 - kotlin.math.abs((h / 60) % 2 - 1))
        val m = v - c
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return (0xFF shl 24) or
            (((r + m) * 255).toInt() shl 16) or
            (((g + m) * 255).toInt() shl 8) or
            ((b + m) * 255).toInt()
    }

    /**
     * Renders a small 12x12 icon with a specific color (for name-based fallback).
     */
    private fun renderSmallIconWithColor(
        context: DrawContext,
        textRenderer: TextRenderer,
        displayName: String,
        color: Int,
        x: Int,
        y: Int
    ) {
        val size = 12
        val initial = if (displayName.isNotEmpty()) displayName[0].uppercase() else "?"
        val bgColor = darkenColor(color, 0.35f)

        // Background
        context.fill(x + 1, y, x + size - 1, y + size, bgColor)
        context.fill(x, y + 1, x + 1, y + size - 1, bgColor)
        context.fill(x + size - 1, y + 1, x + size, y + size - 1, bgColor)

        // Border
        context.fill(x + 1, y, x + size - 1, y + 1, color)
        context.fill(x + 1, y + size - 1, x + size - 1, y + size, color)
        context.fill(x, y + 1, x + 1, y + size - 1, color)
        context.fill(x + size - 1, y + 1, x + size, y + size - 1, color)

        // Single initial centered
        val textWidth = textRenderer.getWidth(initial)
        val textX = x + (size - textWidth) / 2
        val textY = y + (size - 8) / 2
        context.drawTextWithShadow(textRenderer, initial, textX, textY, 0xFFFFFF)
    }

    /**
     * Darkens an ARGB color by the given factor (0.0 = black, 1.0 = unchanged).
     */
    private fun darkenColor(color: Int, factor: Float): Int {
        val a = (color shr 24) and 0xFF
        val r = ((color shr 16) and 0xFF) * factor
        val g = ((color shr 8) and 0xFF) * factor
        val b = (color and 0xFF) * factor
        return (a shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }
}

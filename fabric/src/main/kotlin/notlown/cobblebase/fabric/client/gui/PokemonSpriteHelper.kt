package notlown.cobblebase.fabric.client.gui

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID

/**
 * Renders Pokemon portraits using Cobblemon's drawProfilePokemon for 16x16+ icons,
 * and type-colored badge fallbacks for 12x12 small icons.
 *
 * FloatingState instances are cached per-Pokemon to avoid creating them every frame.
 */
object PokemonSpriteHelper {

    const val ICON_SIZE = 16
    private const val BORDER_RADIUS_APPROX = 2

    /** Cached FloatingState per Pokemon UUID to avoid per-frame allocation. */
    private val stateCache = mutableMapOf<String, FloatingState>()

    /** Pre-computed rotation for portrait rendering. */
    private val PORTRAIT_ROTATION: Quaternionf by lazy {
        Quaternionf().fromEulerXYZDegrees(Vector3f(13F, 35F, 0F))
    }

    /**
     * Type color lookup with fallback. Maps type names to ARGB colors.
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
     * Get or create a cached FloatingState for a given cache key.
     */
    private fun getOrCreateState(cacheKey: String, aspects: Set<String>): FloatingState {
        return stateCache.getOrPut(cacheKey) { FloatingState() }.also {
            it.currentAspects = aspects
        }
    }

    /**
     * Clears the state cache. Call when the GUI is closed or the Pokemon list changes.
     */
    fun clearCache() {
        stateCache.clear()
    }

    /**
     * Renders a Pokemon portrait icon at the given position using Cobblemon's 3D renderer.
     * Falls back to the type-colored badge if rendering fails.
     *
     * @param context     The draw context
     * @param textRenderer The text renderer (used for fallback)
     * @param species     The species Identifier (e.g. cobblemon:pikachu)
     * @param displayName The display name of the Pokemon
     * @param aspects     The Pokemon's aspects (shiny, gender, etc.)
     * @param x           Left edge of the icon
     * @param y           Top edge of the icon
     * @param delta        Partial ticks for animation
     */
    fun renderIcon(
        context: DrawContext,
        textRenderer: TextRenderer,
        species: Identifier,
        displayName: String,
        aspects: Set<String>,
        x: Int,
        y: Int,
        delta: Float
    ) {
        val color = getTypeColor(species)
        val bgColor = darkenColor(color, 0.35f)

        // Draw type-colored background behind the portrait
        context.fill(x + 1, y, x + ICON_SIZE - 1, y + ICON_SIZE, bgColor)
        context.fill(x, y + 1, x + 1, y + ICON_SIZE - 1, bgColor)
        context.fill(x + ICON_SIZE - 1, y + 1, x + ICON_SIZE, y + ICON_SIZE - 1, bgColor)

        // Colored border
        context.fill(x + 1, y, x + ICON_SIZE - 1, y + 1, color)
        context.fill(x + 1, y + ICON_SIZE - 1, x + ICON_SIZE - 1, y + ICON_SIZE, color)
        context.fill(x, y + 1, x + 1, y + ICON_SIZE - 1, color)
        context.fill(x + ICON_SIZE - 1, y + 1, x + ICON_SIZE, y + ICON_SIZE - 1, color)

        // Render the 3D Pokemon portrait on top
        try {
            val cacheKey = "${species}_${aspects.sorted().joinToString(",")}"
            val state = getOrCreateState(cacheKey, aspects)
            val matrixStack = context.matrices

            matrixStack.push()
            // drawProfilePokemon renders the Pokemon above the translate point
            matrixStack.translate(
                (x + ICON_SIZE / 2.0),
                (y.toDouble() + 1.0),
                0.0
            )
            matrixStack.scale(1.5F, 1.5F, 1F)

            drawProfilePokemon(
                species = species,
                matrixStack = matrixStack,
                rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(13F, 35F, 0F)),
                state = state,
                partialTicks = delta,
                scale = 4.5F
            )

            matrixStack.pop()
        } catch (_: Exception) {
            // Fallback: draw initials on the existing background
            val initials = getInitials(displayName)
            val textWidth = textRenderer.getWidth(initials)
            val textX = x + (ICON_SIZE - textWidth) / 2
            val textY = y + (ICON_SIZE - 8) / 2
            context.drawTextWithShadow(textRenderer, initials, textX, textY, 0xFFFFFF)
        }
    }

    /**
     * Overload without aspects/delta for backward compatibility (falls back to badge).
     */
    fun renderIcon(
        context: DrawContext,
        textRenderer: TextRenderer,
        species: Identifier,
        displayName: String,
        x: Int,
        y: Int
    ) {
        renderIcon(context, textRenderer, species, displayName, emptySet(), x, y, 0f)
    }

    /**
     * Renders a smaller icon (for compact rows like logs).
     * Size: 12x12 pixels. Uses 3D portrait at smaller scale.
     */
    fun renderSmallIcon(
        context: DrawContext,
        textRenderer: TextRenderer,
        species: Identifier,
        displayName: String,
        x: Int,
        y: Int,
        delta: Float = 0f
    ) {
        val size = 12
        val color = getTypeColor(species)
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

        // Render small 3D portrait
        try {
            val cacheKey = "${species}_small"
            val state = getOrCreateState(cacheKey, emptySet())
            val matrixStack = context.matrices
            matrixStack.push()
            matrixStack.translate((x + size / 2.0), (y.toDouble() + 1.0), 0.0)
            matrixStack.scale(0.9F, 0.9F, 1F)
            drawProfilePokemon(
                species = species,
                matrixStack = matrixStack,
                rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(13F, 35F, 0F)),
                state = state,
                partialTicks = delta,
                scale = 4.5F
            )
            matrixStack.pop()
            return
        } catch (_: Exception) { }

        // Fallback: single initial centered
        val fallbackInitial = if (displayName.isNotEmpty()) displayName[0].uppercase() else "?"
        val textWidth = textRenderer.getWidth(fallbackInitial)
        val textX = x + (size - textWidth) / 2
        val textY = y + (size - 8) / 2
        context.drawTextWithShadow(textRenderer, fallbackInitial, textX, textY, 0xFFFFFF)
    }

    /**
     * Renders a small icon for log entries where we only have the Pokemon name (no species Identifier).
     */
    fun renderSmallIconByName(
        context: DrawContext,
        textRenderer: TextRenderer,
        pokemonName: String,
        x: Int,
        y: Int,
        delta: Float = 0f
    ) {
        val speciesId = resolveSpeciesFromName(pokemonName)
        if (speciesId != null) {
            // Use full-size renderIcon for better visibility (16x16 with 3D portrait)
            renderIcon(context, textRenderer, speciesId, pokemonName, emptySet(), x, y, delta)
        } else {
            renderSmallIconWithColor(context, textRenderer, pokemonName, nameToColor(pokemonName), x, y)
        }
    }

    /**
     * Try to resolve a species Identifier from a display name.
     */
    private fun resolveSpeciesFromName(name: String): Identifier? {
        val normalized = name.trim().lowercase()
            .replace(" ", "_").replace("-", "_")
            .replace(Regex("[^a-z0-9/._-]"), "") // Strip invalid identifier chars
        if (normalized.isEmpty()) return null
        return try {
            val id = Identifier.of("cobblemon", normalized)
            if (PokemonSpecies.getByIdentifier(id) != null) id else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generate a consistent color from a Pokemon name hash.
     */
    private fun nameToColor(name: String): Int {
        val hash = name.hashCode()
        val hue = (hash and 0x7FFFFFFF) % 360
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

        context.fill(x + 1, y, x + size - 1, y + size, bgColor)
        context.fill(x, y + 1, x + 1, y + size - 1, bgColor)
        context.fill(x + size - 1, y + 1, x + size, y + size - 1, bgColor)

        context.fill(x + 1, y, x + size - 1, y + 1, color)
        context.fill(x + 1, y + size - 1, x + size - 1, y + size, color)
        context.fill(x, y + 1, x + 1, y + size - 1, color)
        context.fill(x + size - 1, y + 1, x + size, y + size - 1, color)

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

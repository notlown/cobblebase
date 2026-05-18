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
        // Type-colored background + border removed per user request — the colored
        // squares around every Pokemon icon were noisy across Buffs / Pasture / My
        // Pokemon / Logs and contributed nothing the type-emoji or skill-name color
        // doesn't already convey. The 3D portrait now sits on a transparent backdrop.

        // Anchor: slot center horizontally (with -0.5 px nudge to counter the +35° yaw
        // rotation that shifts visual mass slightly to the right), and `y + 1` vertically
        // — that's where drawProfilePokemon lands the model inside a 16px slot at
        // scale 4.5 (its profileTranslation already does the per-species offset). A
        // bottom-edge anchor (y + ICON_SIZE - 1) overshoots and drops the sprite into
        // the next row.
        try {
            val cacheKey = "${species}_${aspects.sorted().joinToString(",")}"
            val state = getOrCreateState(cacheKey, aspects)
            val matrixStack = context.matrices

            matrixStack.push()
            matrixStack.translate(
                (x + ICON_SIZE / 2.0 - 0.5),
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
     * Portrait-only renderer — draws the 3D Pokemon portrait at the standard 16x16 slot
     * but skips the type-colored background and border. Caller is responsible for
     * providing any cell decoration (e.g. rarity-tinted background in SkillsPanel's
     * My Pokemon / WorkerWiki grid).
     */
    fun renderPortraitOnly(
        context: DrawContext,
        species: Identifier,
        aspects: Set<String>,
        x: Int,
        y: Int,
        delta: Float = 0f
    ) {
        try {
            val cacheKey = "${species}_${aspects.sorted().joinToString(",")}"
            val state = getOrCreateState(cacheKey, aspects)
            val matrixStack = context.matrices
            matrixStack.push()
            // Same anchor convention as renderIcon — see its comment.
            matrixStack.translate(
                (x + ICON_SIZE / 2.0 - 0.5),
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
        } catch (_: Exception) { /* caller's cell bg shows through */ }
    }

    /** Portrait-only variant that takes a display name + optional aspect set. */
    fun renderPortraitOnlyByName(
        context: DrawContext,
        pokemonName: String,
        explicitAspects: Set<String>,
        x: Int,
        y: Int,
        delta: Float = 0f
    ) {
        val (baseName, formAspects) = extractFormAspects(pokemonName)
        val speciesId = resolveSpeciesFromName(baseName) ?: return
        renderPortraitOnly(context, speciesId, formAspects + explicitAspects, x, y, delta)
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
        // Type-colored bg + border removed (see renderIcon above for rationale).

        // Render small 3D portrait
        try {
            val cacheKey = "${species}_small"
            val state = getOrCreateState(cacheKey, emptySet())
            val matrixStack = context.matrices
            matrixStack.push()
            matrixStack.translate(
                (x + size / 2.0 - 0.5),
                (y.toDouble() + 1.0),
                0.0
            )
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
    /**
     * Renders a large zoomed-in Pokemon sprite clipped to a given region.
     * Used for admin GUI headers where we want a big portrait in a narrow row.
     * Uses scissor to clip the sprite to [x, y, w, h].
     */
    fun renderLargeSpriteByName(
        context: DrawContext,
        pokemonName: String,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        delta: Float = 0f,
        scale: Float = 12.0f
    ) {
        val (baseName, aspects) = extractFormAspects(pokemonName)
        val speciesId = resolveSpeciesFromName(baseName) ?: return
        try {
            val cacheKey = "${speciesId}_large_${aspects.joinToString(",")}"
            val state = getOrCreateState(cacheKey, aspects)
            val matrixStack = context.matrices

            // Scissor so the sprite is clipped to the requested region
            context.enableScissor(x, y, x + w, y + h)

            matrixStack.push()
            // Center the sprite horizontally in the region, anchor at bottom of region
            matrixStack.translate(
                (x + w / 2.0),
                (y + h).toDouble(),
                0.0
            )

            drawProfilePokemon(
                species = speciesId,
                matrixStack = matrixStack,
                rotation = PORTRAIT_ROTATION,
                state = state,
                partialTicks = delta,
                scale = scale
            )

            matrixStack.pop()
            context.disableScissor()
        } catch (_: Exception) {
            try { context.disableScissor() } catch (_: Exception) { }
        }
    }

    /**
     * Renders just the 3D Pokemon portrait without the type-colored background box.
     * Used in Workshop tab where the background box doesn't fit the design.
     */
    fun renderPortraitOnly(
        context: DrawContext,
        textRenderer: TextRenderer,
        pokemonName: String,
        x: Int,
        y: Int,
        delta: Float = 0f
    ) {
        val (baseName, aspects) = extractFormAspects(pokemonName)
        val speciesId = resolveSpeciesFromName(baseName) ?: return
        try {
            val cacheKey = "${speciesId}_portrait_${aspects.joinToString(",")}"
            val state = getOrCreateState(cacheKey, aspects)
            context.matrices.push()
            context.matrices.translate(
                (x + ICON_SIZE / 2.0),
                (y.toDouble() + 1.0),
                0.0
            )
            context.matrices.scale(1.5F, 1.5F, 1F)
            drawProfilePokemon(
                species = speciesId,
                matrixStack = context.matrices,
                rotation = org.joml.Quaternionf().fromEulerXYZDegrees(org.joml.Vector3f(13F, 35F, 0F)),
                state = state,
                partialTicks = delta,
                scale = 4.5F
            )
            context.matrices.pop()
        } catch (_: Exception) {
            // Fallback: just draw initials without background
            val initials = getInitials(pokemonName)
            context.drawTextWithShadow(textRenderer, initials, x + 2, y + 4, 0xFFFFFF)
        }
    }

    fun renderSmallIconByName(
        context: DrawContext,
        textRenderer: TextRenderer,
        pokemonName: String,
        x: Int,
        y: Int,
        delta: Float = 0f
    ) {
        // Use the actual 12-px small icon variant — the function name says "small",
        // not "icon", and admin list rows need the compact sprite. (Previously
        // routed to renderIcon which painted a 24-px sprite into a 14-px row slot,
        // overflowing bottom-right.)
        val (baseName, aspects) = extractFormAspects(pokemonName)
        val speciesId = resolveSpeciesFromName(baseName)
        if (speciesId != null) {
            val state = getOrCreateState("${speciesId}_small_${aspects.joinToString(",")}", aspects)
            try {
                val size = 12
                val matrixStack = context.matrices
                matrixStack.push()
                matrixStack.translate(
                    (x + size / 2.0 - 0.5),
                    (y.toDouble() + 1.0),
                    0.0
                )
                matrixStack.scale(0.9F, 0.9F, 1F)
                drawProfilePokemon(
                    species = speciesId,
                    matrixStack = matrixStack,
                    rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(13F, 35F, 0F)),
                    state = state,
                    partialTicks = delta,
                    scale = 4.5F
                )
                matrixStack.pop()
            } catch (_: Exception) {
                renderSmallIconWithColor(context, textRenderer, pokemonName, nameToColor(pokemonName), x, y)
            }
        } else {
            renderSmallIconWithColor(context, textRenderer, pokemonName, nameToColor(pokemonName), x, y)
        }
    }

    /**
     * Overload that accepts explicit aspects (e.g. forwarded from server-side `Pokemon.aspects`).
     * Required for species like Pyroar (female-only sprite needs the `female` aspect) and
     * gender-variant fakemons. Without aspects, Cobblemon's renderer falls back to a placeholder.
     * Form suffixes embedded in the name are still extracted and merged with the supplied set.
     */
    fun renderSmallIconByName(
        context: DrawContext,
        textRenderer: TextRenderer,
        pokemonName: String,
        explicitAspects: Set<String>,
        x: Int,
        y: Int,
        delta: Float = 0f
    ) {
        val (baseName, formAspects) = extractFormAspects(pokemonName)
        val speciesId = resolveSpeciesFromName(baseName)
        val combined = formAspects + explicitAspects
        if (speciesId != null) {
            // 12-px small variant — see the same-named overload above for rationale.
            val state = getOrCreateState("${speciesId}_small_${combined.joinToString(",")}", combined)
            try {
                val size = 12
                val matrixStack = context.matrices
                matrixStack.push()
                matrixStack.translate(
                    (x + size / 2.0 - 0.5),
                    (y.toDouble() + 1.0),
                    0.0
                )
                matrixStack.scale(0.9F, 0.9F, 1F)
                drawProfilePokemon(
                    species = speciesId,
                    matrixStack = matrixStack,
                    rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(13F, 35F, 0F)),
                    state = state,
                    partialTicks = delta,
                    scale = 4.5F
                )
                matrixStack.pop()
            } catch (_: Exception) {
                renderSmallIconWithColor(context, textRenderer, pokemonName, nameToColor(pokemonName), x, y)
            }
        } else {
            renderSmallIconWithColor(context, textRenderer, pokemonName, nameToColor(pokemonName), x, y)
        }
    }

    /**
     * Cache of resolved species ids per normalized name. Resolution iterates
     * the entire Cobblemon registry once per unknown name, so caching is
     * important for the species sidebar (1300+ entries).
     */
    private val speciesIdCache = mutableMapOf<String, Identifier?>()

    /**
     * Try to resolve a species Identifier from a display name.
     *
     * The previous version hardcoded the `cobblemon:` namespace, which meant
     * fakemons (registered under their own mod namespaces like
     * `babylegends:beta`, `extraparadoxmons:saberjaw`, `livelymons:...`,
     * etc.) never matched and always fell back to the colored badge. We now
     * walk Cobblemon's full runtime species registry and pick the species
     * whose `.name` matches the requested display name, regardless of
     * namespace. The result is cached.
     */
    /**
     * Strips a regional-variant suffix from a species name and returns it as an aspect set.
     * Cobblemon stores Hisuian/Alolan/Galarian/Paldean variants as aspects on the BASE species,
     * not as standalone species entries — so "decidueye_hisuian" resolves to species "decidueye"
     * with aspects = {"hisuian"}. Without this stripping, sprite lookup fails for every
     * regional-form Pokemon (Hisuian Zoroark, Galarian Slowking, etc.).
     */
    private fun extractFormAspects(name: String): Pair<String, Set<String>> {
        val lower = name.lowercase()
        val variants = listOf("hisuian", "alolan", "galarian", "paldean")
        for (v in variants) {
            // Match either `*_<variant>` (file naming) or `<variant>_*` (display naming).
            if (lower.endsWith("_$v")) {
                return name.substring(0, name.length - v.length - 1) to setOf(v)
            }
            if (lower.startsWith("${v}_")) {
                return name.substring(v.length + 1) to setOf(v)
            }
        }
        return name to emptySet()
    }

    private fun resolveSpeciesFromName(name: String): Identifier? {
        val raw = name.trim().lowercase()
        if (raw.isEmpty()) return null

        // Cobblemon's species IDs strip ALL punctuation, hyphens, and spaces:
        //   "Wo-Chien"   → "wochien"
        //   "Ho-Oh"      → "hooh"
        //   "Type: Null" → "typenull"
        //   "Mr. Mime"   → "mrmime"
        // The previous normalization mapped hyphens to underscores ("wo_chien"), which
        // doesn't match Cobblemon's registry → the four Treasures of Ruin and other
        // hyphenated species fell back to letter badges. We now try the strict
        // alphanumeric form first, then a hyphen/underscore-preserving form as a
        // secondary match for display-name lookups.
        val strictId = raw.replace(Regex("[^a-z0-9]"), "")
        val withUnderscores = raw.replace(" ", "_").replace("-", "_")
            .replace(Regex("[^a-z0-9/._-]"), "")

        val cacheKey = strictId
        speciesIdCache[cacheKey]?.let { return it }
        if (speciesIdCache.containsKey(cacheKey)) return null

        // 1) Strict-id fast path — matches the Cobblemon ID convention
        if (strictId.isNotEmpty()) {
            try {
                val id = Identifier.of("cobblemon", strictId)
                if (PokemonSpecies.getByIdentifier(id) != null) {
                    speciesIdCache[cacheKey] = id
                    return id
                }
            } catch (_: Exception) { }
        }

        // 2) Underscore fallback — some species (e.g. "porygon_z") use snake_case in their ID
        if (withUnderscores.isNotEmpty() && withUnderscores != strictId) {
            try {
                val id = Identifier.of("cobblemon", withUnderscores)
                if (PokemonSpecies.getByIdentifier(id) != null) {
                    speciesIdCache[cacheKey] = id
                    return id
                }
            } catch (_: Exception) { }
        }

        // 3) PokemonSpecies.getByName walks every namespace by registered name (any mod)
        for (candidate in setOf(strictId, withUnderscores).filter { it.isNotEmpty() }) {
            try {
                val species = PokemonSpecies.getByName(candidate)
                if (species != null) {
                    val id = species.resourceIdentifier
                    speciesIdCache[cacheKey] = id
                    return id
                }
            } catch (_: Exception) { }
        }

        // 4) Last-resort: iterate the whole registry, matching by .name with both
        //    strict-stripped and underscore variants of each registered species
        try {
            for (s in PokemonSpecies.species) {
                val sName = s.name.lowercase()
                val sStrict = sName.replace(Regex("[^a-z0-9]"), "")
                if (sStrict == strictId || sName == withUnderscores) {
                    val id = s.resourceIdentifier
                    speciesIdCache[cacheKey] = id
                    return id
                }
            }
        } catch (_: Exception) { }

        speciesIdCache[cacheKey] = null
        return null
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

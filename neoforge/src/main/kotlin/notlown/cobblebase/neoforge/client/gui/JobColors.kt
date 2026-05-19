package notlown.cobblebase.neoforge.client.gui

/**
 * Per-job accent color — single source of truth for "what color is this skill?".
 *
 * Used by:
 *   * SkillsPanel (Pasture sub-tab): active chip BG + thin underline on muted chips.
 *   * BuffsPanel: passive sub-tab name color (each buff its own hue).
 *   * LogsPanel: subtle row-BG tint per action.
 *
 * Colors picked so each job reads as semantically related to its theme — Mining =
 * stone gray, Fishing = ocean blue, Diving = deep ocean, Healer = soft red, etc. —
 * not just "another color in the palette." Tint variants (with alpha) are derived
 * by [withAlpha] when the panel needs a translucent overlay (e.g. log row BG).
 */
object JobColors {

    /** Full-strength solid ARGB color (0xFFRRGGBB). */
    private val SOLID = mapOf(
        // ── Gathering ──────────────────────────────────────────────
        "item_gatherer"     to 0xFF66CCFF.toInt(),  // sky blue (item beacon)
        "mining"            to 0xFF8B8B8B.toInt(),  // stone gray
        "fishing"           to 0xFF3399FF.toInt(),  // ocean blue
        "diving"            to 0xFF1565C0.toInt(),  // deep ocean blue
        "harvester"         to 0xFF55CC55.toInt(),  // leaf green
        "forager"           to 0xFF7BAA4F.toInt(),  // forest green
        "botanist"          to 0xFFA8C66C.toInt(),  // sage green
        "prospector"        to 0xFFB87333.toInt(),  // ore copper
        "collector"         to 0xFFFFD580.toInt(),  // collector cream
        "scout"             to 0xFFFFD93D.toInt(),  // compass yellow
        "growth_aura"       to 0xFFAAFF66.toInt(),  // crop green

        // ── Generation ─────────────────────────────────────────────
        "producer"          to 0xFFF0E6C8.toInt(),  // wool cream
        "egg_hatcher"       to 0xFFFF99CC.toInt(),  // baby pink
        "gather_items"      to 0xFFFFA040.toInt(),  // gatherer orange

        // ── Combat / Defense ───────────────────────────────────────
        "guard"             to 0xFFFF7733.toInt(),  // flame orange
        "extinguisher"      to 0xFF1976D2.toInt(),  // hose blue
        "armorer"           to 0xFFB0BEC5.toInt(),  // armor steel
        "smith"             to 0xFF6D4C41.toInt(),  // anvil iron

        // ── Support ────────────────────────────────────────────────
        "healer"            to 0xFFFF6666.toInt(),  // soft red
        "mentor"            to 0xFFCC88FF.toInt(),  // XP purple
        "aura_boost"        to 0xFFFFD700.toInt(),  // luck gold
        "aura"              to 0xFFFFD700.toInt(),
        "lucky_charm"       to 0xFFFF99FF.toInt(),  // shiny pink

        // ── Utility / Crafting ─────────────────────────────────────
        "craftsman"         to 0xFFB07050.toInt(),  // workbench brown
        "supplier"          to 0xFF44BB99.toInt(),  // supplier teal
        "builder"           to 0xFFC68642.toInt(),  // build tan
        "furnace_fuel"      to 0xFFFF9800.toInt(),  // furnace orange
        "cauldron_fill"     to 0xFF4DD0E1.toInt(),  // cauldron blue
        "brew_fuel"         to 0xFFE91E63.toInt(),  // brewing magenta
        "irrigate"          to 0xFF42A5F5.toInt(),  // water sky
        "recruiter"         to 0xFFCC66FF.toInt(),  // friendship purple
        "friend_recruiter"  to 0xFFCC66FF.toInt(),

        // ── Passive buffs (also referenced by BuffsPanel) ──────────
        "speed_boost"       to 0xFF66CCFF.toInt(),
        "strength_boost"    to 0xFFFF6666.toInt(),
        "resistance_boost"  to 0xFFCCCCCC.toInt(),
        "night_vision"      to 0xFF9966FF.toInt(),
        "water_breathing"   to 0xFF66E0FF.toInt(),
        "jump_boost"        to 0xFF88FF88.toInt(),
        "haste_boost"       to 0xFFFFCC33.toInt(),
        "saturation_boost"  to 0xFFFFAA66.toInt(),
        "growth"            to 0xFFAAFF66.toInt(),

        // ── Finder family (all share the same hue, finder-blue) ────
        "finder_evo"        to 0xFF7B9EFF.toInt(),
        "finder_hea"        to 0xFFFF8080.toInt(),
        "finder_bui"        to 0xFFC68642.toInt(),
        "finder_ore"        to 0xFFB87333.toInt(),
        "finder_see"        to 0xFF8FCC73.toInt(),
        "finder_bal"        to 0xFFD93636.toInt(),
        "finder_exp"        to 0xFF66E0FF.toInt(),
        "finder_food"       to 0xFFFFC04D.toInt(),
        "finder_stat"       to 0xFFFFD700.toInt(),
        "finder_held"       to 0xFFA68BD9.toInt(),
        "finder_treasure"   to 0xFFD4AF37.toInt(),
        "finder_smith"      to 0xFF6D4C41.toInt(),
        "finder"            to 0xFF7B9EFF.toInt(),

        // ── Action-verb aliases (LogManager logs e.g. "Mined", "Fished",         ─
        //     "Healed" instead of the skill id) ─────────────────────────────────
        "mined"             to 0xFF8B8B8B.toInt(),  // mining
        "fished"            to 0xFF3399FF.toInt(),  // fishing
        "harvested"         to 0xFF55CC55.toInt(),  // harvester / supplier-harvest
        "supplied"          to 0xFF44BB99.toInt(),  // supplier-generate
        "produced"          to 0xFFF0E6C8.toInt(),  // producer
        "crafted"           to 0xFFB07050.toInt(),  // craftsman
        "healed"            to 0xFFFF6666.toInt(),  // healer
        "recruited"         to 0xFFCC66FF.toInt(),  // recruiter
        "sorted"            to 0xFFFFA040.toInt(),  // gatherer
        "hatched"           to 0xFFFF99CC.toInt(),  // egg hatcher
        "spotted"           to 0xFFFFD93D.toInt(),  // scout
        "discovered"        to 0xFFFFD93D.toInt(),  // scout
        "repelled"          to 0xFFFF7733.toInt(),  // guard
        "extinguisher"      to 0xFF1976D2.toInt(),  // extinguisher
        "found"             to 0xFFD4AF37.toInt(),  // finder family (treasure gold)
    )

    /** Sane fallback for skills we haven't assigned a hue yet. */
    private val DEFAULT = 0xFFCCCCCC.toInt()

    /**
     * Resolve a skill's accent color from an id, name, or action string.
     *
     * Accepts:
     *   - "cobblebase:mining" → strips the namespace
     *   - "Mining" → lowercased
     *   - "Egg Hatcher" → spaces → underscores
     */
    fun colorFor(skillIdOrName: String): Int {
        val key = normalize(skillIdOrName)
        return SOLID[key] ?: DEFAULT
    }

    /** Returns the color with the given alpha byte (0x00–0xFF). */
    fun withAlpha(color: Int, alpha: Int): Int {
        return (alpha shl 24) or (color and 0x00FFFFFF)
    }

    /** Convenience: 13% alpha tint for row backgrounds. */
    fun tint(skillIdOrName: String): Int = withAlpha(colorFor(skillIdOrName), 0x22)

    private fun normalize(s: String): String {
        return s.removePrefix("cobblebase:")
            .lowercase()
            .replace(' ', '_')
            .replace('-', '_')
    }
}

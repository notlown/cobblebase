package notlown.cobblebase.fabric.client.gui

/**
 * Design tokens for the Cobblebase GUI. Centralizing these values so font sizes,
 * button heights, paddings, and row heights stay consistent across panels.
 *
 * **Why this exists:** the GUI grew organically — every panel picked its own row
 * height (14/18/20/22/24), its own font scale (0.55/0.6/0.65/0.7/0.75/0.85), its
 * own button height (12/14/16). Result: nothing visually aligned across tabs.
 * Reach for these constants instead of inventing new ones.
 */
object UiTokens {
    // ── Font scales ────────────────────────────────────────────────────────
    /** Tiny text — secondary labels, hints, status strings. */
    const val TEXT_XS = 0.65f
    /** Default small label — used for most chip names, list rows, descriptions. */
    const val TEXT_SM = 0.75f
    /** Comfortable list-row name — Pokemon names, skill names, headers. */
    const val TEXT_MD = 0.9f
    /** Full vanilla font — section titles, prominent labels. */
    const val TEXT_LG = 1.0f

    // ── Row heights ────────────────────────────────────────────────────────
    /** Tight admin list rows (single-line content, 12-px sprite). */
    const val ROW_TIGHT = 14
    /** Standard two-line list rows (sprite + name + lv). */
    const val ROW_STD = 18
    /** Spacious rows when a row also carries chips or extra controls. */
    const val ROW_RELAXED = 22

    // ── Button heights ─────────────────────────────────────────────────────
    /** Standard ButtonWidget height across the GUI (Discord, Mute, Done, Admin). */
    const val BTN_H = 12
    /** Compact square icon buttons (close X, action chips). */
    const val BTN_ICON = 14

    // ── Paddings ───────────────────────────────────────────────────────────
    /** Outer panel padding from the panel edge to content. */
    const val PANEL_PAD = 8
    /** Inner row/cell padding (left/right of text inside a chip/button). */
    const val CELL_PAD = 4
    /** Gap between adjacent buttons in a bar. */
    const val BTN_GAP = 4
    /** Tight gap inside dense lists (skill chips, log rows). */
    const val ROW_GAP = 2

    // ── Bottom bar ─────────────────────────────────────────────────────────
    /** Reserved vertical strip for the persistent bottom bar. Matches button
     * height + 1 px above + 1 px below so the strip looks like a button row,
     * not a wasted-space frame. */
    const val BOTTOM_BAR_H = 14
    /** Padding below the bottom bar (panel edge to button top). */
    const val BOTTOM_BAR_INSET = 1
}

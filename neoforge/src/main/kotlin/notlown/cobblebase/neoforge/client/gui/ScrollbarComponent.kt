package notlown.cobblebase.neoforge.client.gui

import net.minecraft.client.gui.DrawContext

/**
 * Reusable vertical scrollbar widget. One instance per scrollable surface; the host
 * panel calls [layout] once per render (to feed in current geometry + scroll offset),
 * [render] to draw the track + thumb, and delegates [mouseClicked] / [mouseDragged] /
 * [mouseReleased] from its own input handlers.
 *
 * Most panel scrollbars in the mod historically were render-only — they drew a thumb
 * but had no click/drag interaction, so users couldn't drag-scroll. This component
 * fixes that uniformly: any panel that uses it gets working click-on-track-to-jump,
 * click-on-thumb-to-drag, and drag-anywhere-while-held behavior.
 *
 * Usage:
 * ```
 * private val scrollbar = ScrollbarComponent()
 *
 * fun render(...) {
 *     scrollbar.layout(
 *         trackX = panelX + panelW - 8,
 *         trackY = listTop,
 *         trackHeight = listBottom - listTop,
 *         contentHeight = totalContentH,
 *         viewportHeight = listBottom - listTop,
 *         currentScroll = myScrollY,
 *     )
 *     scrollbar.render(context, mouseX, mouseY)
 *     myScrollY = scrollbar.scroll
 *     // ... rest of render using myScrollY
 * }
 *
 * fun mouseClicked(mx: Double, my: Double, button: Int): Boolean {
 *     if (scrollbar.mouseClicked(mx, my)) { myScrollY = scrollbar.scroll; return true }
 *     // ... other clicks
 * }
 *
 * fun mouseDragged(mx: Double, my: Double, ...): Boolean {
 *     if (scrollbar.mouseDragged(my)) { myScrollY = scrollbar.scroll; return true }
 *     return false
 * }
 *
 * fun mouseReleased(...): Boolean = scrollbar.mouseReleased()
 * ```
 *
 * The host panel keeps its own scroll variable as the source of truth; this component
 * is intentionally stateless across frames except for [isDragging]. That lets a panel
 * decide whether to drive scroll from wheel events, layout changes, etc., independently
 * from drag interactions, and avoids the bidirectional-sync pitfalls of an MVC widget.
 */
class ScrollbarComponent(
    /** Track width in pixels. 6 matches the in-game Logs panel look. */
    var trackWidth: Int = 6,
    /** Minimum thumb size — scroll feels broken if the thumb is so small it can't be
     *  grabbed even with sub-pixel precision. 16 px floor. */
    var minThumbHeight: Int = 16,
) {
    /** Effective scroll position. Read after every interaction; written via [layout]. */
    var scroll: Int = 0
        private set

    /** Track origin + size, refreshed every frame by [layout]. */
    var trackX: Int = 0; private set
    var trackY: Int = 0; private set
    var trackHeight: Int = 0; private set

    /** Content metrics, refreshed every frame by [layout]. */
    var contentHeight: Int = 0; private set
    var viewportHeight: Int = 0; private set

    /** True while the user is mid-drag on the thumb. Survives across frames. */
    var isDragging: Boolean = false; private set
    private var dragGrabOffset: Int = 0

    /** Total scrollable range, never negative. */
    val maxScroll: Int
        get() = (contentHeight - viewportHeight).coerceAtLeast(0)

    /** Whether the scrollbar should render at all (content overflows viewport). */
    val needed: Boolean
        get() = contentHeight > viewportHeight && trackHeight > 0

    val thumbHeight: Int
        get() = if (!needed) 0
        else (viewportHeight.toFloat() / contentHeight * trackHeight).toInt()
            .coerceAtLeast(minThumbHeight)
            .coerceAtMost(trackHeight)

    val thumbY: Int
        get() {
            if (!needed) return trackY
            val travel = (trackHeight - thumbHeight).coerceAtLeast(0)
            val progress = if (maxScroll == 0) 0f else scroll.toFloat() / maxScroll
            return trackY + (travel * progress).toInt()
        }

    /**
     * Refresh geometry. Call once per render before [render], with the current scroll
     * offset from the host panel. The component clamps the value to the valid range
     * and re-exposes it via [scroll].
     */
    fun layout(
        trackX: Int,
        trackY: Int,
        trackHeight: Int,
        contentHeight: Int,
        viewportHeight: Int,
        currentScroll: Int,
    ) {
        this.trackX = trackX
        this.trackY = trackY
        this.trackHeight = trackHeight
        this.contentHeight = contentHeight
        this.viewportHeight = viewportHeight
        scroll = currentScroll.coerceIn(0, maxScroll)
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int) {
        if (!needed) return
        // Track (translucent white)
        context.fill(trackX, trackY, trackX + trackWidth, trackY + trackHeight, 0x33FFFFFF)
        // Thumb — brighter on hover or drag
        val ty = thumbY
        val th = thumbHeight
        val hovered = mouseX in trackX..(trackX + trackWidth) && mouseY in ty..(ty + th)
        val color = if (isDragging || hovered) 0xFFDDDDDD.toInt() else 0xFFAAAAAA.toInt()
        context.fill(trackX, ty, trackX + trackWidth, ty + th, color)
    }

    /**
     * Returns true if the click landed on the track and the component consumed it.
     * On a thumb-hit, starts a drag (subsequent [mouseDragged] calls move the thumb).
     * On a track-outside-thumb hit, jumps the thumb so it's centered on the click
     * and starts a drag from there — Windows-style page-jump-then-fine-drag.
     */
    fun mouseClicked(mouseX: Double, mouseY: Double): Boolean {
        if (!needed) return false
        if (mouseX < trackX || mouseX > trackX + trackWidth) return false
        if (mouseY < trackY || mouseY > trackY + trackHeight) return false

        val ty = thumbY
        val th = thumbHeight
        if (mouseY in ty.toDouble()..(ty + th).toDouble()) {
            // Click on thumb itself — start drag from the click offset within the thumb.
            isDragging = true
            dragGrabOffset = (mouseY - ty).toInt()
        } else {
            // Click on track outside the thumb — center thumb on the click, start drag.
            dragGrabOffset = th / 2
            isDragging = true
            setScrollFromMouse(mouseY)
        }
        return true
    }

    fun mouseDragged(mouseY: Double): Boolean {
        if (!isDragging || !needed) return false
        setScrollFromMouse(mouseY)
        return true
    }

    fun mouseReleased(): Boolean {
        if (!isDragging) return false
        isDragging = false
        return true
    }

    private fun setScrollFromMouse(mouseY: Double) {
        val th = thumbHeight
        val travel = (trackHeight - th).coerceAtLeast(0)
        if (travel == 0) {
            scroll = 0
            return
        }
        val newThumbTop = (mouseY - dragGrabOffset).toInt()
            .coerceIn(trackY, trackY + travel)
        val progress = (newThumbTop - trackY).toFloat() / travel
        scroll = (progress * maxScroll).toInt().coerceIn(0, maxScroll)
    }
}

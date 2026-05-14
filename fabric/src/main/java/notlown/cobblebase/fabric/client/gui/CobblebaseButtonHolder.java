package notlown.cobblebase.fabric.client.gui;

import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * Singleton holder for the most-recently-created Cobblebase main button.
 * Used by PCGUIClickPriorityMixin to intercept clicks at the screen level —
 * ensures the Cobblebase button wins over overlapping mod buttons (e.g. Cobbreeding's egg button)
 * regardless of widget iteration order.
 *
 * The {@link #lastRenderTime} acts as a freshness gate: clicks are only routed to the button
 * if it has rendered very recently, preventing stale references from intercepting clicks
 * after the PastureWidget has been closed/replaced.
 */
public class CobblebaseButtonHolder {
    public static volatile ButtonWidget activeButton = null;
    public static volatile long lastRenderTime = 0L;
}

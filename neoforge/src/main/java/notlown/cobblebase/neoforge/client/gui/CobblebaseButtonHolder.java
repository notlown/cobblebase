package notlown.cobblebase.neoforge.client.gui;

import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * Singleton holder for the most-recently-created Cobblebase main button.
 * Used by PCGUIClickPriorityMixin to intercept clicks at the screen level —
 * ensures the Cobblebase button wins over overlapping mod buttons (e.g. Cobbreeding's egg button)
 * regardless of widget iteration order.
 */
public class CobblebaseButtonHolder {
    public static volatile ButtonWidget activeButton = null;
    public static volatile long lastRenderTime = 0L;
}

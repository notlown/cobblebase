package notlown.cobblebase.fabric.client;

import net.minecraft.client.gui.screen.Screen;

/**
 * Simple holder for a pending screen to open.
 * Used by PastureWidgetMixin to defer screen switching to the next client tick,
 * avoiding ConcurrentModificationException when changing screens inside event handlers.
 */
public class PendingScreenHolder {
    public static volatile Screen pendingScreen = null;
}

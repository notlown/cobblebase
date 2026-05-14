package notlown.cobblebase.fabric.mixin;

import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import net.minecraft.client.gui.widget.ButtonWidget;
import notlown.cobblebase.fabric.client.gui.CobblebaseButtonHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts mouse clicks at the PCGUI (parent screen) level so the Cobblebase main button
 * always wins click priority — even if another mod (e.g. Cobbreeding) registers an overlapping
 * "egg" button via its own widget that would normally consume the click first.
 *
 * The visual ordering is already handled in PastureWidgetMixin via a Z-translation of +200
 * during render. This mixin ensures the same priority applies to the clickable area.
 *
 * Freshness gate: the holder's lastRenderTime is updated every frame the Cobblebase button
 * renders. We only route clicks if the button was rendered within the last 200ms, which
 * prevents stale references from a previous PastureWidget instance from hijacking clicks
 * when the user is interacting with a PCGUI tab that doesn't show a pasture.
 */
@Mixin(PCGUI.class)
public abstract class PCGUIClickPriorityMixin {

    private static final org.slf4j.Logger COBBLEBASE_LOG = org.slf4j.LoggerFactory.getLogger("Cobblebase/ClickPriority");

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void cobblebase$priorityClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        ButtonWidget btn = CobblebaseButtonHolder.activeButton;
        if (btn == null) {
            return;
        }
        if (!btn.visible) {
            return;
        }

        long age = System.currentTimeMillis() - CobblebaseButtonHolder.lastRenderTime;
        if (age > 200L) {
            return; // stale reference — button not currently rendered
        }

        if (btn.isMouseOver(mouseX, mouseY)) {
            COBBLEBASE_LOG.debug("Intercepting click at ({}, {}) on Cobblebase main button (priority over PCGUI children)", mouseX, mouseY);
            btn.mouseClicked(mouseX, mouseY, button);
            cir.setReturnValue(true);
        }
    }
}

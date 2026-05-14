package notlown.cobblebase.neoforge.mixin;

import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import net.minecraft.client.gui.widget.ButtonWidget;
import notlown.cobblebase.neoforge.client.gui.CobblebaseButtonHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts mouse clicks at the PCGUI (parent screen) level so the Cobblebase main button
 * always wins click priority — even if another mod (e.g. Cobbreeding) registers an overlapping
 * "egg" button via its own widget that would normally consume the click first.
 */
@Mixin(PCGUI.class)
public abstract class PCGUIClickPriorityMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void cobblebase$priorityClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        ButtonWidget btn = CobblebaseButtonHolder.activeButton;
        if (btn == null || !btn.visible) return;

        long age = System.currentTimeMillis() - CobblebaseButtonHolder.lastRenderTime;
        if (age > 200L) return; // stale reference — button not currently rendered

        if (btn.isMouseOver(mouseX, mouseY)) {
            btn.mouseClicked(mouseX, mouseY, button);
            cir.setReturnValue(true);
        }
    }
}

package notlown.cobblebase.fabric.mixin;

import notlown.cobblebase.fabric.client.gui.CobblebaseScreen;
import notlown.cobblebase.core.net.LogRequestC2SPacket;
import com.cobblemon.mod.common.client.gui.pasture.PasturePCGUIConfiguration;
import com.cobblemon.mod.common.client.gui.pasture.PastureWidget;
import com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.UUID;

@Mixin(PastureWidget.class)
public abstract class PastureWidgetMixin {

    @Shadow @Final private PasturePCGUIConfiguration pasturePCGUIConfiguration;

    @Unique
    private ButtonWidget cobblebase$mainButton;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cobblebase$onInit(
        com.cobblemon.mod.common.client.gui.pc.StorageWidget storageWidget,
        PasturePCGUIConfiguration config,
        int x, int y,
        CallbackInfo ci
    ) {
        cobblebase$mainButton = ButtonWidget.builder(Text.literal("\u00A7bCobblebase"), btn -> {
            cobblebase$openCobblebaseScreen();
        }).dimensions(x + 2, y - 18, 78, 16).build();
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void cobblebase$onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (cobblebase$mainButton != null) {
            cobblebase$mainButton.render(context, mouseX, mouseY, delta);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void cobblebase$onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (cobblebase$mainButton != null && cobblebase$mainButton.isMouseOver(mouseX, mouseY)) {
            cobblebase$openCobblebaseScreen();
            cir.setReturnValue(true);
        }
    }

    @Unique
    private void cobblebase$openCobblebaseScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        List<OpenPasturePacket.PasturePokemonDataDTO> pokemonList =
            pasturePCGUIConfiguration.getPasturedPokemon().get();

        UUID pastureId = pasturePCGUIConfiguration.getPastureId();

        // Request logs from server
        try {
            ClientPlayNetworking.send(new LogRequestC2SPacket(pastureId));
        } catch (Exception ignored) {
            // Packet might not be registered yet on first join
        }

        client.setScreen(new CobblebaseScreen(pokemonList, null, client.currentScreen));
    }
}

package notlown.cobblebase.fabric.mixin;

import notlown.cobblebase.fabric.client.gui.CobblebaseScreen;
import notlown.cobblebase.core.net.LogRequestC2SPacket;
import notlown.cobblebase.core.net.SkillAssignmentRequestC2SPacket;
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

    @Unique
    private int cobblebase$widgetX;
    @Unique
    private int cobblebase$widgetY;

    @Unique
    private static final int COBBLEBASE_BTN_W = 78;
    @Unique
    private static final int COBBLEBASE_BTN_H = 16;


    @Inject(method = "<init>", at = @At("RETURN"))
    private void cobblebase$onInit(
        com.cobblemon.mod.common.client.gui.pc.StorageWidget storageWidget,
        PasturePCGUIConfiguration config,
        int x, int y,
        CallbackInfo ci
    ) {
        cobblebase$widgetX = x;
        cobblebase$widgetY = y;
        cobblebase$mainButton = ButtonWidget.builder(Text.literal("\u00A7bCobblebase"), btn -> {
            cobblebase$requestOpen();
        }).dimensions(x + 2, y - 18, COBBLEBASE_BTN_W, COBBLEBASE_BTN_H).build();
        notlown.cobblebase.fabric.client.gui.CobblebaseButtonHolder.activeButton = cobblebase$mainButton;
    }

    @Unique
    private static final org.slf4j.Logger COBBLEBASE_BTN_LOG = org.slf4j.LoggerFactory.getLogger("Cobblebase/MainButton");

    @Unique
    private notlown.cobblebase.core.MainButtonCorner cobblebase$lastCorner = null;

    @Unique
    private void cobblebase$reposition() {
        if (cobblebase$mainButton == null) return;
        notlown.cobblebase.core.MainButtonCorner corner = notlown.cobblebase.core.CobblebaseConfig.INSTANCE.getMainButtonCorner();
        if (corner != cobblebase$lastCorner) {
            COBBLEBASE_BTN_LOG.info("Cobblebase button corner changed: {} → {}", cobblebase$lastCorner, corner);
            cobblebase$lastCorner = corner;
        }
        // Position the button flush to the corner of the PCGUI window (which is centered on screen).
        // PCGUI's BASE_WIDTH=349, BASE_HEIGHT=205 are public static final constants in Cobblemon.
        // LEFT corners → button hugs the left edge of PCGUI (button right edge = PCGUI left edge)
        // RIGHT corners → button hugs the right edge of PCGUI (button left edge = PCGUI right edge)
        // TOP/BOTTOM → vertical alignment with PCGUI top/bottom
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        int pcW = com.cobblemon.mod.common.client.gui.pc.PCGUI.BASE_WIDTH;
        int pcH = com.cobblemon.mod.common.client.gui.pc.PCGUI.BASE_HEIGHT;
        int pcX = (screenW - pcW) / 2;
        int pcY = (screenH - pcH) / 2;
        // Button anchored OUTSIDE the PCGUI window (above for TOP, below for BOTTOM),
        // horizontally aligned with the matching edge (left or right).
        int bx, by;
        switch (corner) {
            case TOP_LEFT:
                bx = pcX;
                by = pcY - COBBLEBASE_BTN_H;
                break;
            case BOTTOM_LEFT:
                bx = pcX;
                by = pcY + pcH;
                break;
            case BOTTOM_RIGHT:
                bx = pcX + pcW - COBBLEBASE_BTN_W;
                by = pcY + pcH;
                break;
            case TOP_RIGHT:
            default:
                bx = pcX + pcW - COBBLEBASE_BTN_W;
                by = pcY - COBBLEBASE_BTN_H;
                break;
        }
        cobblebase$mainButton.setX(bx);
        cobblebase$mainButton.setY(by);
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void cobblebase$onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (cobblebase$mainButton != null) {
            cobblebase$reposition();
            // Push Z translation to render above Chiselmon's buttons
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 200);
            cobblebase$mainButton.render(context, mouseX, mouseY, delta);
            context.getMatrices().pop();
            // Mark the button as actively rendered so PCGUIClickPriorityMixin trusts the reference
            notlown.cobblebase.fabric.client.gui.CobblebaseButtonHolder.lastRenderTime = System.currentTimeMillis();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void cobblebase$onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (cobblebase$mainButton != null && cobblebase$mainButton.isMouseOver(mouseX, mouseY)) {
            cobblebase$requestOpen();
            cir.setReturnValue(true);
        }
    }

    @Unique
    private void cobblebase$requestOpen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;

        List<OpenPasturePacket.PasturePokemonDataDTO> pokemonList =
            pasturePCGUIConfiguration.getPasturedPokemon().get();

        // Pasture lock: only allow access if the player owns at least one Pokemon in this pasture
        boolean isOwner = pokemonList.stream().anyMatch(dto ->
            client.player.getUuid().equals(dto.getPlayerId())
        );
        if (!isOwner && !pokemonList.isEmpty()) return; // Not the owner — block access

        UUID pastureId = pasturePCGUIConfiguration.getPastureId();

        // Try to recover the pasture block position via the player's last crosshair target
        // (player just right-clicked the pasture, so it's typically still in view).
        net.minecraft.util.math.BlockPos pasturePos = null;
        try {
            net.minecraft.util.hit.HitResult hit = client.crosshairTarget;
            if (hit instanceof net.minecraft.util.hit.BlockHitResult bhr) {
                net.minecraft.util.math.BlockPos hitPos = bhr.getBlockPos();
                if (client.world != null) {
                    net.minecraft.block.entity.BlockEntity be = client.world.getBlockEntity(hitPos);
                    if (be instanceof com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity) {
                        pasturePos = hitPos;
                    }
                }
            }
        } catch (Exception ignored) {}

        // Fallback: scan a small radius around the player for any pasture block entity
        if (pasturePos == null && client.world != null) {
            net.minecraft.util.math.BlockPos playerPos = client.player.getBlockPos();
            outer:
            for (int dy = -2; dy <= 3; dy++) {
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dz = -5; dz <= 5; dz++) {
                        net.minecraft.util.math.BlockPos p = playerPos.add(dx, dy, dz);
                        net.minecraft.block.entity.BlockEntity be = client.world.getBlockEntity(p);
                        if (be instanceof com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity) {
                            pasturePos = p;
                            break outer;
                        }
                    }
                }
            }
        }

        // Request logs and current assignments from server
        try {
            ClientPlayNetworking.send(new LogRequestC2SPacket(pastureId));
            ClientPlayNetworking.send(new SkillAssignmentRequestC2SPacket());
        } catch (Exception ignored) {
            // Packet might not be registered yet on first join
        }

        // Set pending screen — will be opened by END_CLIENT_TICK handler
        // outside the render/event iteration cycle
        notlown.cobblebase.fabric.client.PendingScreenHolder.pendingScreen =
            new CobblebaseScreen(pokemonList, pasturePos, client.currentScreen);
    }
}

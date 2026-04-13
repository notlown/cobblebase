package notlown.cobblebase.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents game crashes from missing Pokemon animation data.
 *
 * Part 1: BedrockAnimationRepository.getAnimation() → delegates to getAnimationOrNull()
 * so missing animations return null instead of throwing IllegalArgumentException.
 *
 * Part 2: VaryingModelRepository.getPoser() → wraps with try/catch so broken
 * animation chains don't propagate up. Returns null on failure, which Cobblebase's
 * own GUI handles gracefully.
 */
@Mixin(
    targets = "com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimationRepository",
    remap = false
)
public class PokemonRenderCrashFixMixin {

    @Inject(method = "getAnimation", at = @At("HEAD"), cancellable = true)
    private void cobblebase$safeGetAnimation(
        String fileName,
        String animationName,
        CallbackInfoReturnable<Object> cir
    ) {
        try {
            Object result = ((com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimationRepository) (Object) this)
                .getAnimationOrNull(fileName, animationName);
            if (result == null) {
                notlown.cobblebase.core.Cobblebase.INSTANCE.getLOGGER().debug(
                    "[Cobblebase] Missing animation: {}.{} — skipped",
                    fileName, animationName
                );
            }
            cir.setReturnValue(result);
        } catch (Exception e) {
            notlown.cobblebase.core.Cobblebase.INSTANCE.getLOGGER().debug(
                "[Cobblebase] Animation lookup failed for {}.{}: {}",
                fileName, animationName, e.getMessage()
            );
            cir.setReturnValue(null);
        }
    }
}

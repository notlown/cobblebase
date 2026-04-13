package notlown.cobblebase.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wraps VaryingModelRepository.getPoser() with error handling.
 *
 * When getAnimation returns null (from PokemonRenderCrashFixMixin), the poser
 * deserialization chain can still crash with NPE or IllegalStateException.
 * This mixin catches those cascading failures so the caller receives null
 * instead of an exception.
 *
 * Uses a ThreadLocal guard to prevent infinite recursion since the mixin
 * intercepts the same method it needs to call.
 */
@Mixin(
    targets = "com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository",
    remap = false
)
public class VaryingModelCrashFixMixin {

    private static final ThreadLocal<Boolean> cobblebase$inGetPoser = ThreadLocal.withInitial(() -> false);

    @Inject(method = "getPoser", at = @At("HEAD"), cancellable = true)
    private void cobblebase$safeGetPoser(
        Object name,
        Object state,
        CallbackInfoReturnable<Object> cir
    ) {
        // Guard: if we're already inside our wrapper, let the original run
        if (cobblebase$inGetPoser.get()) return;

        cobblebase$inGetPoser.set(true);
        try {
            Object result = ((com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository) (Object) this)
                .getPoser(
                    (net.minecraft.util.Identifier) name,
                    (com.cobblemon.mod.common.client.render.models.blockbench.PosableState) state
                );
            cir.setReturnValue(result);
        } catch (Exception e) {
            notlown.cobblebase.core.Cobblebase.INSTANCE.getLOGGER().debug(
                "[Cobblebase] Suppressed getPoser crash for {}: {}",
                name, e.getMessage()
            );
            cir.setReturnValue(null);
        } finally {
            cobblebase$inGetPoser.set(false);
        }
    }
}

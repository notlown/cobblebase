package notlown.cobblebase.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import notlown.cobblebase.core.PastureLeavesTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes leaf block collision for Pokemon entities — but ONLY for leaves
 * that are tracked as being within a Pasture Block's working area.
 *
 * This keeps performance acceptable by filtering via a fast O(1) HashSet
 * lookup: only pasture-area leaves are checked. Leaves elsewhere in the
 * world retain normal collision for all entities.
 */
@Mixin(AbstractBlock.class)
public class PastureLeafCollisionMixin {

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void cobblebase$noCollisionForPasturePokemon(
            BlockState state, BlockView world, BlockPos pos, ShapeContext context,
            CallbackInfoReturnable<VoxelShape> cir
    ) {
        // Fast early return: only care about leaf blocks
        if (!(state.getBlock() instanceof LeavesBlock)) return;
        // Fast early return: only care about Pokemon entity contexts
        if (!(context instanceof EntityShapeContext entityContext)) return;
        if (!(entityContext.getEntity() instanceof PokemonEntity)) return;
        // Only bypass collision if this leaf is tracked as a pasture leaf
        if (!PastureLeavesTracker.INSTANCE.isPastureLeaf(pos)) return;

        cir.setReturnValue(VoxelShapes.empty());
    }
}

package notlown.cobblebase.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes leaf block collision for Pokemon entities.
 * Pokemon can fly/walk through leaves freely — no more getting stuck in trees.
 * Does not affect players or other entities.
 */
@Mixin(AbstractBlock.class)
public class LeavesBlockMixin {

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void cobblebase$noCollisionForPokemon(
            BlockState state, BlockView world, BlockPos pos, ShapeContext context,
            CallbackInfoReturnable<VoxelShape> cir
    ) {
        if (!(state.getBlock() instanceof LeavesBlock)) return;
        if (context instanceof EntityShapeContext entityContext) {
            if (entityContext.getEntity() instanceof PokemonEntity) {
                cir.setReturnValue(VoxelShapes.empty());
            }
        }
    }
}

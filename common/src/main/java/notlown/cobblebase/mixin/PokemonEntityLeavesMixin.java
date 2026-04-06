package notlown.cobblebase.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.block.Block;
import net.minecraft.block.LeavesBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import notlown.cobblebase.core.CobblebaseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Allows Pokemon entities to pass through leaf blocks (no collision).
 * This runs once per Pokemon entity per tick (instead of once per block per
 * collision query like a Block mixin would), so it has minimal TPS impact.
 *
 * Toggleable via the "Leaves Pass-Through" setting.
 */
@Mixin(PokemonEntity.class)
public abstract class PokemonEntityLeavesMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void cobblebase$leavesPassThroughTick(CallbackInfo ci) {
        if (!CobblebaseConfig.INSTANCE.getLeavesPassThrough()) {
            // Make sure noClip stays false when feature is disabled
            PokemonEntity disabled = (PokemonEntity)(Object)this;
            if (disabled.noClip) disabled.noClip = false;
            return;
        }

        PokemonEntity self = (PokemonEntity)(Object)this;
        World world = self.getWorld();
        if (world == null) return;

        // Check if entity bounding box intersects any leaf block — AND no solid blocks
        // (only enable noClip when ONLY leaves are around, prevents falling through ground)
        Box box = self.getBoundingBox();
        int minX = (int) Math.floor(box.minX);
        int minY = (int) Math.floor(box.minY);
        int minZ = (int) Math.floor(box.minZ);
        int maxX = (int) Math.floor(box.maxX);
        int maxY = (int) Math.floor(box.maxY);
        int maxZ = (int) Math.floor(box.maxZ);

        BlockPos.Mutable pos = new BlockPos.Mutable();
        boolean inLeaves = false;
        boolean inSolid = false;

        for (int x = minX; x <= maxX && !inSolid; x++) {
            for (int y = minY; y <= maxY && !inSolid; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    Block block = world.getBlockState(pos).getBlock();
                    if (block instanceof LeavesBlock) {
                        inLeaves = true;
                    } else if (!world.getBlockState(pos).isAir()) {
                        // Found a non-leaf, non-air block — entity is partially in solid terrain
                        inSolid = true;
                        break;
                    }
                }
            }
        }

        // Only enable noClip when surrounded ONLY by leaves (and air), NEVER when touching solid blocks
        // This prevents falling through ground/walls
        self.noClip = inLeaves && !inSolid;
    }
}

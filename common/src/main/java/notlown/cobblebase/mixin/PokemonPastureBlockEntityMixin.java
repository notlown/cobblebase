package notlown.cobblebase.mixin;

import notlown.cobblebase.core.BaseManager;
import notlown.cobblebase.core.Cobblebase;
import notlown.cobblebase.core.PassiveXp;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PokemonPastureBlockEntity.class)
public class PokemonPastureBlockEntityMixin {
    @Inject(at = @At("TAIL"), method = "TICKER$lambda$0")
    private static void cobblebase$tick(World world, BlockPos blockPos, BlockState blockState, PokemonPastureBlockEntity pastureBlock, CallbackInfo ci) {
        if (world.isClient) return;

        List<PokemonPastureBlockEntity.Tethering> tetheredPokemon = pastureBlock.getTetheredPokemon();
        for (PokemonPastureBlockEntity.Tethering tethering : tetheredPokemon) {
            if (tethering == null) continue;

            Pokemon pokemon;
            try {
                pokemon = tethering.getPokemon();
            } catch (Exception e) {
                continue;
            }

            if (pokemon == null || pokemon.isFainted()) continue;

            PokemonEntity pokemonEntity = pokemon.getEntity();
            if (pokemonEntity == null) continue;

            // Passive XP for all pastured Pokemon (even sleeping)
            try {
                PassiveXp.INSTANCE.tick(world, pokemonEntity);
            } catch (Exception ignored) { }

            PoseType poseType = pokemonEntity.getDataTracker().get(PokemonEntity.getPOSE_TYPE());
            if (poseType == PoseType.SLEEP) continue;

            try {
                BaseManager.INSTANCE.tickPokemon(world, blockPos, pokemonEntity);
            } catch (Exception e) {
                Cobblebase.INSTANCE.getLOGGER().error("[Cobblebase] Error ticking Pokemon: {}", e.getMessage());
            }
        }
    }
}

package notlown.cobblebase.mixin;

import notlown.cobblebase.core.BaseManager;
import notlown.cobblebase.core.Cobblebase;
import notlown.cobblebase.core.PassiveXp;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
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

        // Debug log every 5 seconds
        if (world.getTime() % 100 == 0) {
            Cobblebase.INSTANCE.getLOGGER().info("[Cobblebase] Mixin tick at {} | tethered: {}", blockPos, pastureBlock.getTetheredPokemon().size());
        }

        List<PokemonPastureBlockEntity.Tethering> tetheredPokemon = pastureBlock.getTetheredPokemon();
        for (PokemonPastureBlockEntity.Tethering tethering : tetheredPokemon) {
            if (tethering == null) continue;

            Pokemon pokemon;
            try {
                pokemon = tethering.getPokemon();
            } catch (Exception e) {
                if (world.getTime() % 100 == 0) Cobblebase.INSTANCE.getLOGGER().info("[Cobblebase] getPokemon() failed: {}", e.getMessage());
                continue;
            }

            if (pokemon == null) {
                if (world.getTime() % 100 == 0) Cobblebase.INSTANCE.getLOGGER().info("[Cobblebase] pokemon is null");
                continue;
            }
            if (pokemon.isFainted()) {
                if (world.getTime() % 100 == 0) Cobblebase.INSTANCE.getLOGGER().info("[Cobblebase] {} is fainted", pokemon.getSpecies().getName());
                continue;
            }

            PokemonEntity pokemonEntity = pokemon.getEntity();
            if (pokemonEntity == null) {
                if (world.getTime() % 100 == 0) Cobblebase.INSTANCE.getLOGGER().info("[Cobblebase] {} entity is NULL (not spawned in world)", pokemon.getSpecies().getName());
                continue;
            }

            if (world.getTime() % 100 == 0) Cobblebase.INSTANCE.getLOGGER().info("[Cobblebase] Ticking {} at {}", pokemon.getSpecies().getName(), pokemonEntity.getBlockPos());

            // Passive XP for all pastured Pokemon (even sleeping)
            try {
                PassiveXp.INSTANCE.tick(world, pokemonEntity, blockPos);
            } catch (Exception ignored) { }

            try {
                BaseManager.INSTANCE.tickPokemon(world, blockPos, pokemonEntity);
            } catch (Exception e) {
                Cobblebase.INSTANCE.getLOGGER().error("[Cobblebase] Error ticking {}: {}", pokemon.getSpecies().getName(), e.getMessage());
            }
        }
    }
}

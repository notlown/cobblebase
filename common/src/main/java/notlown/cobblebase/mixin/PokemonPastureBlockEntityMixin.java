package notlown.cobblebase.mixin;

import notlown.cobblebase.core.BaseManager;
import notlown.cobblebase.core.Cobblebase;
import notlown.cobblebase.core.LogManager;
import notlown.cobblebase.core.NavigationHelper;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;

@Mixin(PokemonPastureBlockEntity.class)
public class PokemonPastureBlockEntityMixin {

    /**
     * Prevent Cobblemon from teleporting water-capable Pokemon out of water.
     * Saves positions before checkPokemon() and restores them for swimming mons.
     */
    @Inject(at = @At("HEAD"), method = "checkPokemon", remap = false)
    private void cobblebase$beforeCheckPokemon(CallbackInfo ci) {
        PokemonPastureBlockEntity self = (PokemonPastureBlockEntity)(Object)this;
        World world = self.getWorld();
        if (world == null || world.isClient) return;

        Map<UUID, Vec3d> waterPositions = new HashMap<>();
        Cobblebase.INSTANCE.getLOGGER().info("[Cobblebase] checkPokemon HEAD firing");
        for (PokemonPastureBlockEntity.Tethering tethering : self.getTetheredPokemon()) {
            if (tethering == null) continue;
            try {
                Pokemon pokemon = tethering.getPokemon();
                if (pokemon == null) continue;
                PokemonEntity entity = pokemon.getEntity();
                if (entity == null) continue;

                // Save position of mons that can swim and are in/near water
                if (entity.isTouchingWater()) {
                    waterPositions.put(entity.getUuid(), entity.getPos());
                }
            } catch (Exception ignored) { }
        }

        // Store for use in TAIL injection
        cobblebase$savedWaterPositions = waterPositions;
    }

    @org.spongepowered.asm.mixin.Unique
    private static final ThreadLocal<Map<UUID, Vec3d>> cobblebase$threadLocalPositions = new ThreadLocal<>();

    @org.spongepowered.asm.mixin.Unique
    private Map<UUID, Vec3d> cobblebase$savedWaterPositions = new HashMap<>();

    @Inject(at = @At("TAIL"), method = "checkPokemon", remap = false)
    private void cobblebase$afterCheckPokemon(CallbackInfo ci) {
        PokemonPastureBlockEntity self = (PokemonPastureBlockEntity)(Object)this;
        World world = self.getWorld();
        if (world == null || world.isClient) return;

        Map<UUID, Vec3d> saved = cobblebase$savedWaterPositions;
        Cobblebase.INSTANCE.getLOGGER().info("[Cobblebase] checkPokemon TAIL firing, saved {} water positions", saved != null ? saved.size() : 0);
        if (saved == null || saved.isEmpty()) return;

        for (PokemonPastureBlockEntity.Tethering tethering : self.getTetheredPokemon()) {
            if (tethering == null) continue;
            try {
                Pokemon pokemon = tethering.getPokemon();
                if (pokemon == null) continue;
                PokemonEntity entity = pokemon.getEntity();
                if (entity == null) continue;

                Vec3d savedPos = saved.get(entity.getUuid());
                if (savedPos != null) {
                    Vec3d currentPos = entity.getPos();
                    double moved = savedPos.distanceTo(currentPos);
                    if (moved > 1.0) {
                        // Was teleported — restore position
                        entity.refreshPositionAndAngles(savedPos.x, savedPos.y, savedPos.z, entity.getYaw(), entity.getPitch());
                        Cobblebase.INSTANCE.getLOGGER().info("[Cobblebase] RESTORED {} from teleport (moved {})", pokemon.getSpecies().getName(), String.format("%.1f", moved));
                    }
                }
            } catch (Exception ignored) { }
        }

        cobblebase$savedWaterPositions = new HashMap<>();
    }

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

            // Stuck detection: teleport mons that haven't moved for 15+ seconds
            try {
                NavigationHelper.INSTANCE.checkAndUnstick(pokemonEntity, blockPos);
            } catch (Exception ignored) { }

            // Keep mons moving naturally — if idle, wander near the pasture
            if (pokemonEntity.getNavigation().isIdle()) {
                try {
                    NavigationHelper.INSTANCE.wanderNearOrigin(pokemonEntity, blockPos, 15);
                } catch (Exception ignored) { }
            }
        }
    }
}

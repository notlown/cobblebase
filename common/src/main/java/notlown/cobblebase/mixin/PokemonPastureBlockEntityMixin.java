package notlown.cobblebase.mixin;

import notlown.cobblebase.core.AmbientBehavior;
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
                    }
                }
            } catch (Exception ignored) { }
        }

        cobblebase$savedWaterPositions = new HashMap<>();
    }

    @Inject(at = @At("TAIL"), method = "TICKER$lambda$0")
    private static void cobblebase$tick(World world, BlockPos blockPos, BlockState blockState, PokemonPastureBlockEntity pastureBlock, CallbackInfo ci) {
        if (world.isClient) return;

        // Keep entities alive: force Cobblemon to re-check entity spawning frequently
        // This ensures Pokemon entities stay spawned when their owner is offline
        // but another player is nearby loading the chunks
        if (notlown.cobblebase.core.CobblebaseConfig.INSTANCE.getKeepEntitiesAlive()) {
            pastureBlock.setTicksUntilCheck(0);
        }

        List<PokemonPastureBlockEntity.Tethering> tetheredPokemon = pastureBlock.getTetheredPokemon();
        for (PokemonPastureBlockEntity.Tethering tethering : tetheredPokemon) {
            if (tethering == null) continue;

            Pokemon pokemon;
            try {
                pokemon = tethering.getPokemon();
            } catch (Exception e) {
                continue;
            }

            if (pokemon == null) {
                continue;
            }
            if (pokemon.isFainted()) {
                continue;
            }

            PokemonEntity pokemonEntity = pokemon.getEntity();
            if (pokemonEntity == null) {
                // Entity is null (owner offline, chunk unloaded, etc.)
                // Still tick passive buffs using pasture block position
                try {
                    BaseManager.INSTANCE.tickPassiveBuffsWithoutEntity(world, blockPos, pokemon);
                } catch (Exception ignored) { }
                continue;
            }


            // Passive XP for all pastured Pokemon (even sleeping)
            try {
                PassiveXp.INSTANCE.tick(world, pokemonEntity, blockPos);
            } catch (Exception ignored) { }

            // Check if this mon is explicitly set to Idle (no job assigned in GUI)
            String assignment = BaseManager.INSTANCE.getAssignment(pokemonEntity.getPokemon().getUuid());
            boolean isExplicitlyIdle = (assignment == null);

            if (isExplicitlyIdle) {
                // IDLE MON: ambient behaviors (socialize, chase, sit, sleep, etc.)
                boolean isResting = AmbientBehavior.INSTANCE.shouldPreventMovement(pokemonEntity.getPokemon().getUuid());
                boolean inActiveBehavior = AmbientBehavior.INSTANCE.isInActiveBehavior(pokemonEntity.getPokemon().getUuid());

                if (isResting) {
                    // Stationary: stop movement, tick ambient
                    NavigationHelper.INSTANCE.clearTargets(pokemonEntity);
                    pokemonEntity.getNavigation().stop();
                    try {
                        AmbientBehavior.INSTANCE.tickIdle(world, pokemonEntity, blockPos);
                    } catch (Exception ignored) { }
                } else if (inActiveBehavior) {
                    // Moving behavior (chase/flee/follow): tick ambient only
                    try {
                        AmbientBehavior.INSTANCE.tickIdle(world, pokemonEntity, blockPos);
                    } catch (Exception ignored) { }
                } else if (pokemonEntity.getNavigation().isIdle()) {
                    // Pick next behavior or wander slowly
                    try {
                        boolean handled = AmbientBehavior.INSTANCE.tickIdle(world, pokemonEntity, blockPos);
                        if (!handled) {
                            NavigationHelper.INSTANCE.wanderNearOrigin(pokemonEntity, blockPos, 15);
                        }
                    } catch (Exception ignored) { }
                }

                // Escape leaves (idle mons can fly into trees too)
                try {
                    NavigationHelper.INSTANCE.escapeLeaves(pokemonEntity);
                } catch (Exception ignored) { }

                // Passive buffs still run for idle mons
                try {
                    BaseManager.INSTANCE.tickPokemon(world, blockPos, pokemonEntity);
                } catch (Exception ignored) { }
            } else {
                // WORKING MON: normal job execution, no ambient behaviors
                AmbientBehavior.INSTANCE.clearState(pokemonEntity.getPokemon().getUuid());
                try {
                    BaseManager.INSTANCE.tickPokemon(world, blockPos, pokemonEntity);
                } catch (Exception e) {
                    Cobblebase.INSTANCE.getLOGGER().error("[Cobblebase] Error ticking {}: {}", pokemon.getSpecies().getName(), e.getMessage());
                }

                // Escape leaves
                try {
                    NavigationHelper.INSTANCE.escapeLeaves(pokemonEntity);
                } catch (Exception ignored) { }

                // Stuck detection
                try {
                    NavigationHelper.INSTANCE.checkAndUnstick(pokemonEntity, blockPos);
                } catch (Exception ignored) { }

                // Working mons wander when nav is idle (between job cycles)
                if (pokemonEntity.getNavigation().isIdle()) {
                    NavigationHelper.INSTANCE.wanderNearOrigin(pokemonEntity, blockPos, 15);
                }
            }

            // Species-specific idle animations (only for explicitly idle mons)
            if (isExplicitlyIdle) {
                try {
                    AmbientBehavior.INSTANCE.tickSpecialAnimations(world, pokemonEntity);
                } catch (Exception ignored) { }
            }
        }
    }
}

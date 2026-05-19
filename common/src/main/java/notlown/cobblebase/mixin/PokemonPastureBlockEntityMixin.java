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
    /**
     * Immediate nav-state cleanup when a Pokemon is released from the pasture (recall,
     * withdraw, pasture block broken). Without this, per-UUID maps in NavigationHelper
     * (lastPathfindTick, escapeLeaves, water cache, stuck-detection state) only get pruned
     * by the 60s periodic sweep — wasted memory for a release event we can hook directly.
     */
    @Inject(at = @At("HEAD"), method = "releasePokemon", remap = false)
    private void cobblebase$onRelease(java.util.UUID pokemonId, CallbackInfo ci) {
        PokemonPastureBlockEntity self = (PokemonPastureBlockEntity)(Object)this;
        World world = self.getWorld();
        if (world == null || world.isClient) return;
        try {
            PokemonPastureBlockEntity.Tethering tethering = self.getTetheredPokemon().stream()
                .filter(t -> t != null && t.getPokemonId().equals(pokemonId))
                .findFirst().orElse(null);
            if (tethering != null) {
                com.cobblemon.mod.common.pokemon.Pokemon pkm = tethering.getPokemon();
                PokemonEntity entity = (pkm != null) ? pkm.getEntity() : null;
                notlown.cobblebase.core.NavigationHelper.INSTANCE.clearTargets(entity);
            }
            notlown.cobblebase.core.NavigationHelper.INSTANCE.cleanupPokemon(pokemonId);
        } catch (Exception ignored) { }
    }

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

                // Water-type Pokemon are NEVER tethered back — they can swim freely
                boolean isWaterType = false;
                for (com.cobblemon.mod.common.api.types.ElementalType t : pokemon.getTypes()) {
                    if (t.getName().equalsIgnoreCase("water")) {
                        isWaterType = true;
                        break;
                    }
                }
                // Also protect mons on fishing/diving jobs
                if (!isWaterType) {
                    String assignment = notlown.cobblebase.core.BaseManager.INSTANCE.getAssignment(pokemon.getUuid());
                    if (assignment != null && (assignment.contains("fishing") || assignment.contains("diving"))) {
                        isWaterType = true;
                    }
                }
                if (isWaterType) {
                    // Save position with BOTH UUIDs to ensure match in TAIL
                    waterPositions.put(entity.getUuid(), entity.getPos());
                    waterPositions.put(pokemon.getUuid(), entity.getPos());
                }
            } catch (Exception e) {
                notlown.cobblebase.core.Cobblebase.INSTANCE.getLOGGER().debug("[Cobblebase] Tether check error: " + e.getMessage());
            }
        }

        // Store for use in TAIL injection
        cobblebase$savedWaterPositions = waterPositions;
    }

    @org.spongepowered.asm.mixin.Unique
    private static final ThreadLocal<Map<UUID, Vec3d>> cobblebase$threadLocalPositions = new ThreadLocal<>();

    @org.spongepowered.asm.mixin.Unique
    private Map<UUID, Vec3d> cobblebase$savedWaterPositions = new HashMap<>();

    @org.spongepowered.asm.mixin.Unique
    private static final Map<UUID, Vec3d> cobblebase$debugPositions = new HashMap<>();

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

                // Check both entity UUID and pokemon UUID as key
                Vec3d savedPos = saved.get(entity.getUuid());
                if (savedPos == null) {
                    savedPos = saved.get(pokemon.getUuid());
                }
                if (savedPos != null) {
                    // Always restore — water-type mons should never be tethered
                    entity.refreshPositionAndAngles(savedPos.x, savedPos.y, savedPos.z, entity.getYaw(), entity.getPitch());
                }
            } catch (Exception ignored) { }
        }

        cobblebase$savedWaterPositions = new HashMap<>();
    }

    @Inject(at = @At("TAIL"), method = "TICKER$lambda$0")
    private static void cobblebase$tick(World world, BlockPos blockPos, BlockState blockState, PokemonPastureBlockEntity pastureBlock, CallbackInfo ci) {
        if (world.isClient) return;

        long cobblebase$tickStart = System.nanoTime();

        // Keep entities alive: periodically force Cobblemon to re-check entity spawning
        // Only reset every 5 seconds (100 ticks) instead of every tick to avoid
        // triggering Cobblemon's aggressive position correction (makeSuitableY/isSafeFloor)
        // which was tethering water-type Pokemon out of water every tick
        if (notlown.cobblebase.core.CobblebaseConfig.INSTANCE.getKeepEntitiesAlive()) {
            if (pastureBlock.getTicksUntilCheck() > 100) {
                pastureBlock.setTicksUntilCheck(100);
            }
        }

        // Update pasture-area leaf tracking (for PastureLeafCollisionMixin)
        long cobblebase$leavesStart = System.nanoTime();
        if (world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            try {
                notlown.cobblebase.core.PastureLeavesTracker.INSTANCE.updatePasture(serverWorld, blockPos);
            } catch (Exception ignored) { }
            // Refresh per-pasture nearby-player cache so animations + cry packets don't pay
            // for a 128³ AABB scan per call. Cache TTL handled internally (5s).
            try {
                notlown.cobblebase.core.NearbyPlayerCache.INSTANCE.update(serverWorld, blockPos, world.getTime());
            } catch (Exception ignored) { }
        }
        long cobblebase$leavesMs = (System.nanoTime() - cobblebase$leavesStart) / 1_000_000;
        if (cobblebase$leavesMs > 20) {
            notlown.cobblebase.core.Cobblebase.INSTANCE.getLOGGER().warn(
                "[Cobblebase] PERF pasture@" + blockPos.toShortString() + ": leaves-scan took " + cobblebase$leavesMs + "ms"
            );
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
            long cobblebase$xpStart = System.nanoTime();
            try {
                PassiveXp.INSTANCE.tick(world, pokemonEntity, blockPos);
            } catch (Exception ignored) { }
            long cobblebase$xpMs = (System.nanoTime() - cobblebase$xpStart) / 1_000_000;
            if (cobblebase$xpMs > 10) {
                Cobblebase.INSTANCE.getLOGGER().warn(
                    "[Cobblebase] PERF PassiveXp.tick " + pokemon.getSpecies().getName() + " took " + cobblebase$xpMs + "ms"
                );
            }

            // Water-type Pokemon on land: navigate toward water FIRST before any job logic.
            // Types don't change in the pasture context, so cache the boolean per Pokemon.
            boolean isWaterMon = NavigationHelper.INSTANCE.isWaterType(pokemon);
            if (isWaterMon && !pokemonEntity.isTouchingWater() && !pokemonEntity.isSubmergedInWater()) {
                // Water mon is on land — find nearest water and place them in it.
                // Cached lookup: this scan walks 21*21*7 = ~3000 block positions, and used to
                // run every server tick. The cached helper memoizes per Pokemon for 2 seconds.
                net.minecraft.util.math.BlockPos waterPos = NavigationHelper.INSTANCE.findNearbyWaterCached(
                    world, blockPos, 20, pokemon.getUuid(), world.getTime()
                );
                if (waterPos != null) {
                    pokemonEntity.setPosition(
                        waterPos.getX() + 0.5, waterPos.getY() + 0.5, waterPos.getZ() + 0.5
                    );
                }
            }

            // Check if this mon is explicitly set to Idle (no job assigned in GUI)
            String assignment = BaseManager.INSTANCE.getAssignment(pokemonEntity.getPokemon().getUuid());
            boolean isExplicitlyIdle = (assignment == null);

            long cobblebase$ambientStart = System.nanoTime();
            if (isExplicitlyIdle) {
                // IDLE MON: ambient behaviors (socialize, chase, sit, sleep, etc.)
                boolean isResting = AmbientBehavior.INSTANCE.shouldPreventMovement(pokemonEntity.getPokemon().getUuid());
                boolean inActiveBehavior = AmbientBehavior.INSTANCE.isInActiveBehavior(pokemonEntity.getPokemon().getUuid());

                if (isResting) {
                    NavigationHelper.INSTANCE.clearTargets(pokemonEntity);
                    pokemonEntity.getNavigation().stop();
                    // Only freeze velocity for sleeping mons (not socializing/sitting/etc)
                    if (AmbientBehavior.INSTANCE.isSleeping(pokemonEntity.getPokemon().getUuid())) {
                        pokemonEntity.setVelocity(0, Math.min(pokemonEntity.getVelocity().y, 0), 0);
                        pokemonEntity.velocityDirty = true;
                    }
                    try {
                        AmbientBehavior.INSTANCE.tickIdle(world, pokemonEntity, blockPos);
                    } catch (Exception ignored) { }
                } else if (inActiveBehavior) {
                    try {
                        AmbientBehavior.INSTANCE.tickIdle(world, pokemonEntity, blockPos);
                    } catch (Exception ignored) { }
                } else if (pokemonEntity.getNavigation().isIdle()) {
                    try {
                        boolean handled = AmbientBehavior.INSTANCE.tickIdle(world, pokemonEntity, blockPos);
                        if (!handled) {
                            // Idle wander radius tracks the admin Pasture Range slider so
                            // Relax mons roam the full base extent, not a hardcoded bubble.
                            int wanderR = Math.max(5, notlown.cobblebase.core.CobblebaseConfig.INSTANCE.getJobSearchRadius());
                            NavigationHelper.INSTANCE.wanderNearOrigin(pokemonEntity, blockPos, wanderR);
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

                // Escape leaves (instrumented separately so the post-tickPokemon mystery
                // — where ambient block reported 1145ms but tickPokemon was <10ms — gets
                // pinned to a specific helper).
                long cobblebase$elStart = System.nanoTime();
                try {
                    NavigationHelper.INSTANCE.escapeLeaves(pokemonEntity);
                } catch (Exception ignored) { }
                long cobblebase$elMs = (System.nanoTime() - cobblebase$elStart) / 1_000_000;
                if (cobblebase$elMs > 25) {
                    Cobblebase.INSTANCE.getLOGGER().warn(
                        "[Cobblebase] PERF escapeLeaves " + pokemon.getSpecies().getName() + " took " + cobblebase$elMs + "ms"
                    );
                }

                // Stuck detection
                long cobblebase$cuStart = System.nanoTime();
                try {
                    NavigationHelper.INSTANCE.checkAndUnstick(pokemonEntity, blockPos);
                } catch (Exception ignored) { }
                long cobblebase$cuMs = (System.nanoTime() - cobblebase$cuStart) / 1_000_000;
                if (cobblebase$cuMs > 25) {
                    Cobblebase.INSTANCE.getLOGGER().warn(
                        "[Cobblebase] PERF checkAndUnstick " + pokemon.getSpecies().getName() + " took " + cobblebase$cuMs + "ms"
                    );
                }

                // Working mons wander when nav is idle (between job cycles)
                if (pokemonEntity.getNavigation().isIdle()) {
                    long cobblebase$wnoStart = System.nanoTime();
                    NavigationHelper.INSTANCE.wanderNearOrigin(pokemonEntity, blockPos, 15);
                    long cobblebase$wnoMs = (System.nanoTime() - cobblebase$wnoStart) / 1_000_000;
                    if (cobblebase$wnoMs > 25) {
                        Cobblebase.INSTANCE.getLOGGER().warn(
                            "[Cobblebase] PERF wanderNearOrigin " + pokemon.getSpecies().getName() + " took " + cobblebase$wnoMs + "ms"
                        );
                    }
                }
            }

            // Species-specific idle animations (only for explicitly idle mons)
            if (isExplicitlyIdle) {
                try {
                    AmbientBehavior.INSTANCE.tickSpecialAnimations(world, pokemonEntity);
                } catch (Exception ignored) { }
            }
            long cobblebase$ambientMs = (System.nanoTime() - cobblebase$ambientStart) / 1_000_000;
            if (cobblebase$ambientMs > 15) {
                Cobblebase.INSTANCE.getLOGGER().warn(
                    "[Cobblebase] PERF ambient/tickPokemon block " + pokemon.getSpecies().getName() +
                    " (idle=" + isExplicitlyIdle + ", assign=" + assignment + ") took " + cobblebase$ambientMs + "ms"
                );
            }
        }

        long cobblebase$totalMs = (System.nanoTime() - cobblebase$tickStart) / 1_000_000;
        if (cobblebase$totalMs > 20) {
            notlown.cobblebase.core.Cobblebase.INSTANCE.getLOGGER().warn(
                "[Cobblebase] PERF pasture@" + blockPos.toShortString() +
                ": full tick took " + cobblebase$totalMs + "ms (" + tetheredPokemon.size() + " mons) sections: leaves=" + cobblebase$leavesMs + "ms"
            );
        }
    }
}

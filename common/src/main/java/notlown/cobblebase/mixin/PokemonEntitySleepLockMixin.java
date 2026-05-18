package notlown.cobblebase.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.util.math.Vec3d;
import notlown.cobblebase.core.AmbientBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hard-locks the position and AI of a pastured Pokemon while it is in the
 * {@code SLEEPING} ambient state. Solves the long-standing "sleeping mons still
 * twitch / wander" bug that defeated several prior attempts.
 *
 * <h3>Why this mixin lives on {@code PokemonEntity.tick} HEAD, not on the pasture tick</h3>
 *
 * Prior attempts (commits {@code caa48971}, {@code 3ea1c0b6}, {@code 3e3e82c1},
 * {@code 9779f31f}, {@code 3dcdaa1e}) all ran their movement-stop logic at the
 * <em>pasture</em> block-entity tick. Two problems with that:
 *
 * <ol>
 *   <li><b>Race with Cobblemon's Brain.</b> Even if we zeroed velocity and cleared
 *       {@link MemoryModuleType#WALK_TARGET WALK_TARGET} / {@link MemoryModuleType#PATH PATH}
 *       at the pasture-tick TAIL, the entity tick that ran <em>during the same server tick</em>
 *       would have already ticked the brain (which re-populates those memories from goal
 *       selectors), set a path, applied path-following movement, and called
 *       {@code Entity.move}. Our reset arrived after the damage.</li>
 *   <li><b>{@code setAiDisabled(true)} broke animations.</b> The one approach that <em>did</em>
 *       fully stop movement (caa48971) shut down the brain entirely. Cobblemon's animation
 *       system needs the entity to keep ticking, so the sleep pose stopped updating.</li>
 * </ol>
 *
 * Injecting at {@code @At("HEAD")} of {@link PokemonEntity}'s {@code tick} means we run
 * <em>before</em> the brain ticks on every server tick the entity is alive. We clear the
 * three movement-related memory modules (so brain tasks like {@code WalkTowardTargetTask}
 * find nothing to walk toward), stop active navigation, zero horizontal velocity, and pin
 * X/Z to an anchor recorded on the first sleep tick. Y velocity is left untouched so a mon
 * that fell asleep mid-air still settles to the ground naturally.
 *
 * <h3>Anchor pinning vs gravity</h3>
 *
 * The XZ snap-back uses a 1-block radius — anything within drift is left alone (so vanilla
 * physics like swimming-in-place still work) but a hard teleport, knockback, or path-driven
 * shuffle is reverted. Y is never pinned, so:
 * <ul>
 *   <li>Falling off a leaf block to the ground while sleeping: works, mon lands and stops.</li>
 *   <li>Sleeping in water: works, mon floats up to surface or stays at floor (vanilla buoyancy).</li>
 *   <li>Sleeping on a stable block: no movement at all, X/Z/Y all stable.</li>
 * </ul>
 *
 * <h3>Cleanup</h3>
 *
 * The anchor map is keyed by Pokemon UUID. We drop the entry on the first tick the
 * mon is <em>not</em> sleeping (natural awake transition cleans itself), and the periodic
 * 60s sweep in {@code BaseManager.tickPokemon} catches strays from despawned / withdrawn
 * mons that never woke up "the normal way." This map cannot grow without bound — it's
 * capped by the number of Pokemon currently in the SLEEPING state at any moment.
 */
@Mixin(PokemonEntity.class)
public abstract class PokemonEntitySleepLockMixin {

    @Unique
    private static final Map<UUID, Vec3d> cobblebase$sleepAnchors = new ConcurrentHashMap<>();

    @Inject(method = "tick", at = @At("HEAD"))
    private void cobblebase$enforceSleepLock(CallbackInfo ci) {
        PokemonEntity self = (PokemonEntity) (Object) this;
        if (self.getWorld().isClient) return;

        UUID uuid;
        try {
            uuid = self.getPokemon().getUuid();
        } catch (Exception e) {
            return;
        }

        boolean sleeping;
        try {
            sleeping = AmbientBehavior.INSTANCE.isSleeping(uuid);
        } catch (Throwable t) {
            sleeping = false;
        }

        if (!sleeping) {
            cobblebase$sleepAnchors.remove(uuid);
            return;
        }

        // Anchor on first sleep tick. Use entry position so a fall-into-sleep doesn't pin
        // mid-air — the mon falls until the anchor is reset next time isSleeping flips on/off.
        Vec3d anchor = cobblebase$sleepAnchors.computeIfAbsent(uuid, k -> self.getPos());

        // 1. Stop active path
        try {
            if (!self.getNavigation().isIdle()) {
                self.getNavigation().stop();
            }
        } catch (Exception ignored) {}

        // 2. Empty the brain's movement memories BEFORE Brain.tick runs on this server tick.
        // This is the critical fix: prior attempts cleared from the pasture tick TAIL, which
        // happens AFTER the entity tick — too late.
        try {
            Brain<?> brain = self.getBrain();
            brain.forget(MemoryModuleType.WALK_TARGET);
            brain.forget(MemoryModuleType.LOOK_TARGET);
            brain.forget(MemoryModuleType.PATH);
        } catch (Exception ignored) {}

        // 3. Zero horizontal velocity. Y is preserved so falling-while-asleep still resolves
        // naturally via vanilla gravity / buoyancy.
        Vec3d v = self.getVelocity();
        if (v.x != 0.0 || v.z != 0.0) {
            self.setVelocity(0.0, v.y, 0.0);
            self.velocityDirty = true;
        }

        // 4. XZ snap-back if drift exceeded ~1 block. Catches external setPosition calls
        // (water nudges, knockback, mod-driven teleports) without breaking gravity.
        double dx = self.getX() - anchor.x;
        double dz = self.getZ() - anchor.z;
        if (dx * dx + dz * dz > 1.0) {
            self.setPosition(anchor.x, self.getY(), anchor.z);
        }
    }

    /**
     * Drop anchors for Pokemon that are no longer in the SLEEPING state. Called from the
     * periodic 60s sweep in {@code BaseManager.tickPokemon} so a Pokemon that despawned
     * mid-sleep (entity unloaded, owner went offline, world reload) doesn't leak its
     * anchor Vec3d indefinitely. The mixin's per-tick code already removes the anchor on
     * a normal wake-up; this sweep handles the abrupt-stop edge cases.
     */
    public static void cobblebase$cleanupStale() {
        cobblebase$sleepAnchors.keySet().removeIf(id -> {
            try {
                return !AmbientBehavior.INSTANCE.isSleeping(id);
            } catch (Throwable t) {
                return true;
            }
        });
    }
}

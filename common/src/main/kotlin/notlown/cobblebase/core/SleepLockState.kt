package notlown.cobblebase.core

import net.minecraft.util.math.Vec3d
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-Pokemon anchor positions for the sleep-lock enforcement done by
 * `PokemonEntitySleepLockMixin`.
 *
 * Lives in a regular Kotlin object (not the mixin class itself) because Mixin's
 * applicator rejects non-private static methods inside `@Mixin`-annotated classes —
 * they can't be safely transferred to or co-exist with the target class. Putting
 * the state + cleanup helpers here side-steps that restriction and gives non-mixin
 * code (BaseManager's periodic sweep) a clean import path.
 */
object SleepLockState {

    /**
     * Anchor positions captured on the first tick a Pokemon enters SLEEPING state.
     * The mixin's per-tick @Inject reads + writes this; BaseManager's 60s sweep
     * calls [cleanupStale] to drop anchors for Pokemon that despawned mid-sleep.
     */
    private val anchors: MutableMap<UUID, Vec3d> = ConcurrentHashMap()

    /** Returns the existing anchor or stores [default] if none was set. */
    fun computeIfAbsent(id: UUID, default: () -> Vec3d): Vec3d =
        anchors.computeIfAbsent(id) { default() }

    /** Drops the anchor for a Pokemon, called on the first non-sleeping tick. */
    fun forget(id: UUID) {
        anchors.remove(id)
    }

    /**
     * Drops anchors for Pokemon that are no longer in the SLEEPING state. Called
     * from the periodic 60s sweep in `BaseManager.tickPokemon` so a Pokemon that
     * despawned mid-sleep (entity unloaded, owner went offline, world reload)
     * doesn't leak its anchor Vec3d indefinitely. The mixin's per-tick code
     * already removes the anchor on a normal wake-up; this sweep handles the
     * abrupt-stop edge cases.
     */
    fun cleanupStale() {
        anchors.keys.removeIf { id ->
            try {
                !AmbientBehavior.isSleeping(id)
            } catch (_: Throwable) {
                true
            }
        }
    }
}

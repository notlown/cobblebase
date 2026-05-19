package notlown.cobblebase.core

import net.minecraft.util.math.BlockPos

/**
 * Server-side set of pastures whose Show-Radius wireframe is currently visible
 * to everyone. Toggled via [notlown.cobblebase.core.net.RadiusToggleC2SPacket]
 * — only the pasture owner (or an OP) can flip a pasture's state.
 *
 * In-memory only; cleared on server restart. The visible set is broadcast to
 * every connected client so wireframes appear/disappear simultaneously for all
 * players, matching the user's expectation that "everyone sees the same box."
 */
object RadiusVisible {
    private val active = mutableSetOf<BlockPos>()

    fun isActive(pos: BlockPos): Boolean = active.contains(pos)

    /** Toggles the pasture in/out of the visible set. Returns the new state. */
    fun toggle(pos: BlockPos): Boolean {
        val key = pos.toImmutable()
        return if (active.remove(key)) false else { active.add(key); true }
    }

    /** Snapshot — sent to clients on login or after any toggle. */
    fun snapshot(): Set<BlockPos> = active.toSet()

    /** Drop a pasture when its block is broken — caller responsibility. */
    fun forget(pos: BlockPos) { active.remove(pos) }
}

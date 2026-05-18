package notlown.cobblebase.neoforge.client.render

import net.minecraft.util.math.BlockPos

/**
 * NeoForge stub for the Fabric RadiusRenderer. Tracks per-pasture toggle state
 * so the Skills-panel "Show Radius" button stays in sync, but does NOT render
 * the wireframe yet — full implementation would need a NeoForge
 * RenderLevelStageEvent handler. Tracked as TODO for the next pass.
 */
object RadiusRenderer {
    private val active = mutableSetOf<BlockPos>()

    fun isActiveAt(pos: BlockPos): Boolean = active.contains(pos)

    fun toggle(pos: BlockPos, @Suppress("UNUSED_PARAMETER") radius: Int) {
        if (!active.remove(pos)) active.add(pos)
    }
}

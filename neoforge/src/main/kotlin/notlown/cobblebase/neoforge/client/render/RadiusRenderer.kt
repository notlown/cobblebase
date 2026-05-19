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

    /**
     * Replace the active set wholesale. Called by the RadiusVisibleSyncS2CPacket
     * receiver — the visible set is server-authoritative so every player sees
     * the same wireframes.
     */
    fun setActive(positions: Collection<BlockPos>) {
        active.clear()
        for (p in positions) active.add(p.toImmutable())
    }
}

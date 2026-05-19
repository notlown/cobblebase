package notlown.cobblebase.fabric.client.render

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.WorldRenderer
import net.minecraft.util.math.BlockPos

/**
 * Client-side renderer that draws red wireframe boxes around pasture blocks to
 * visualize the effective working radius of each pasture's Pokemon.
 *
 * Toggled per pasture from the Skills tab "Radius" button. Multiple pastures
 * can be shown simultaneously — each one is tracked independently.
 */
object RadiusRenderer {

    // Set of pasture positions that currently show their wireframe. The radius
    // itself is read fresh from CobblebaseConfig.jobSearchRadius at render time,
    // so when the admin changes "Pasture Range" in Server Settings, every active
    // wireframe in every pasture re-snaps to the new value on the next frame.
    private val active = mutableSetOf<BlockPos>()

    /** True if any pasture currently has its radius shown. */
    fun isActive(): Boolean = active.isNotEmpty()

    /** True only if the given [pos] specifically has its radius shown. */
    fun isActiveAt(pos: BlockPos): Boolean = active.contains(pos)

    /** Turn the box on for [pos]. The `radius` parameter is ignored (read from
     * GeneralSettings at render time) but kept for callsite compatibility. */
    @Suppress("UNUSED_PARAMETER")
    fun enable(pos: BlockPos, radius: Int) {
        active.add(pos.toImmutable())
    }

    /** Turn the box off for [pos]. No-op if it was off. */
    fun disable(pos: BlockPos) {
        active.remove(pos)
    }

    /** Clear every active radius. */
    fun disableAll() {
        active.clear()
    }

    /**
     * Toggle the box for [pos]. Returns the new active state for this pasture.
     * Other pastures' radii are never touched.
     */
    @Suppress("UNUSED_PARAMETER")
    fun toggle(pos: BlockPos, radius: Int): Boolean {
        val key = pos.toImmutable()
        return if (active.contains(key)) {
            active.remove(key)
            false
        } else {
            active.add(key)
            true
        }
    }

    fun render(context: WorldRenderContext) {
        if (active.isEmpty()) return

        val cam = context.camera().pos
        val matrices = context.matrixStack() ?: return
        val consumers = context.consumers() ?: return

        matrices.push()
        matrices.translate(-cam.x, -cam.y, -cam.z)

        val buffer = consumers.getBuffer(RenderLayer.getLines())
        val r = 1.0f
        val g = 0.15f
        val b = 0.15f
        val a = 0.9f

        // Re-read every frame so admin/owner slider changes update all boxes
        // instantly. Each pasture uses its OWN per-pasture range + below-reach
        // (owner-picked, clamped to admin caps), falling back to admin max when
        // no override exists. X/Z full range; Y asymmetric (full up, capped down).
        for (pos in active) {
            val radius = notlown.cobblebase.core.CobblebaseConfig.jobSearchRadius(pos).coerceAtLeast(1)
            val downReach = minOf(radius, notlown.cobblebase.core.CobblebaseConfig.belowPastureReach(pos))
            val minX = (pos.x - radius).toDouble()
            val minY = (pos.y - downReach).toDouble()
            val minZ = (pos.z - radius).toDouble()
            val maxX = (pos.x + radius + 1).toDouble()
            val maxY = (pos.y + radius + 1).toDouble()
            val maxZ = (pos.z + radius + 1).toDouble()
            WorldRenderer.drawBox(matrices, buffer, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a)
        }

        matrices.pop()

        // Flush so the box draws on top of the translucent pass instead of queueing.
        if (consumers is VertexConsumerProvider.Immediate) {
            consumers.draw(RenderLayer.getLines())
        }
    }
}

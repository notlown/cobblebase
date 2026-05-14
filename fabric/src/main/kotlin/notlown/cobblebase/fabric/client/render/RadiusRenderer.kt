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

    // pasture position -> radius in blocks
    private val active = mutableMapOf<BlockPos, Int>()

    /** True if any pasture currently has its radius shown. */
    fun isActive(): Boolean = active.isNotEmpty()

    /** True only if the given [pos] specifically has its radius shown. */
    fun isActiveAt(pos: BlockPos): Boolean = active.containsKey(pos)

    /** Turn the box on for [pos] (replaces the radius if already on). */
    fun enable(pos: BlockPos, radius: Int) {
        active[pos.toImmutable()] = radius.coerceAtLeast(1)
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
    fun toggle(pos: BlockPos, radius: Int): Boolean {
        val key = pos.toImmutable()
        return if (active.containsKey(key)) {
            active.remove(key)
            false
        } else {
            active[key] = radius.coerceAtLeast(1)
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
        // Solid red, slightly transparent so the box blends naturally with the world.
        val r = 1.0f
        val g = 0.15f
        val b = 0.15f
        val a = 0.9f

        for ((pos, radius) in active) {
            val minX = (pos.x - radius).toDouble()
            val minY = (pos.y - radius).toDouble()
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

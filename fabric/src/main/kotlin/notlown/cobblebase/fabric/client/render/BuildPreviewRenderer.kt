package notlown.cobblebase.fabric.client.render

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.WorldRenderer
import net.minecraft.util.BlockMirror
import net.minecraft.util.BlockRotation
import net.minecraft.util.math.BlockPos

/**
 * Draws a green wireframe box at the placement target while a build preview is active.
 * Acts as a visual indicator of the structure's footprint plus rotation/mirror feedback.
 *
 * Rendering style mirrors [RadiusRenderer] — uses the lines render layer, applies a
 * camera-relative translation, and forces an immediate flush so the box draws on top
 * of the translucent pass.
 */
object BuildPreviewRenderer {

    fun render(context: WorldRenderContext) {
        if (!BuildPreviewState.isActive()) return
        val bounds = BuildPreviewState.computeBounds() ?: return

        val cam = context.camera().pos
        val matrices = context.matrixStack() ?: return
        val consumers = context.consumers() ?: return

        matrices.push()
        matrices.translate(-cam.x, -cam.y, -cam.z)

        val buffer = consumers.getBuffer(RenderLayer.getLines())

        // Slight outward expansion so the wireframe isn't z-fighting with placed blocks.
        val pad = 0.02
        val minX = bounds[0].toDouble() - pad
        val minY = bounds[1].toDouble() - pad
        val minZ = bounds[2].toDouble() - pad
        val maxX = bounds[3].toDouble() + pad
        val maxY = bounds[4].toDouble() + pad
        val maxZ = bounds[5].toDouble() + pad

        // Bright green outline so it reads against most biomes.
        WorldRenderer.drawBox(matrices, buffer, minX, minY, minZ, maxX, maxY, maxZ, 0.2f, 1.0f, 0.4f, 0.95f)

        // Inner ground rectangle in slightly darker green to make the footprint obvious.
        WorldRenderer.drawBox(
            matrices, buffer,
            minX + pad, minY + pad, minZ + pad,
            maxX - pad, minY + 0.05, maxZ - pad,
            0.1f, 0.7f, 0.25f, 0.6f
        )

        // Ghost-block pass: render each block in the template at its rotated/mirrored world
        // position. Uses the standard cutout layer (looks like a solid mini-build floating in
        // the air). When the block list hasn't arrived yet, we fall back to just the wireframe.
        renderGhostBlocks(context, matrices)

        matrices.pop()

        if (consumers is VertexConsumerProvider.Immediate) {
            consumers.draw(RenderLayer.getLines())
        }
    }

    private fun renderGhostBlocks(context: WorldRenderContext, matrices: net.minecraft.client.util.math.MatrixStack) {
        val blocks = BuildPreviewState.loadedBlocks ?: return
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return
        val blockRenderer = client.blockRenderManager
        val consumers = context.consumers() ?: return
        val template = BuildPreviewState.template ?: return
        val origin = BuildPreviewState.origin
        val rotation = BuildPreviewState.rotation
        val mirror = BuildPreviewState.mirror
        val sizeX = template.sizeX
        val sizeZ = template.sizeZ

        // Hard cap so monstrously large templates don't tank framerate (the GUI rejects
        // anything past this anyway and the wireframe still shows the footprint).
        val cap = 20000
        var rendered = 0

        val buffer = consumers.getBuffer(RenderLayer.getCutout())
        for (b in blocks) {
            if (rendered >= cap) break
            val transformed = transformLocal(BlockPos(b.x, b.y, b.z), rotation, mirror, sizeX, sizeZ)
            val worldPos = origin.add(transformed)
            val rotatedState = b.state.rotate(rotation).mirror(mirror)
            matrices.push()
            matrices.translate(worldPos.x.toDouble(), worldPos.y.toDouble(), worldPos.z.toDouble())
            try {
                blockRenderer.renderBlockAsEntity(rotatedState, matrices, consumers, 0xF000F0, OverlayTexture.DEFAULT_UV)
            } catch (_: Exception) {
                // Some blocks (entities, fluids) can't be rendered through this path — skip silently.
            }
            matrices.pop()
            rendered++
        }
    }

    /** Mirror+rotate transform — must match BuilderExecutor.transformLocal exactly. */
    private fun transformLocal(local: BlockPos, rotation: BlockRotation, mirror: BlockMirror, sizeX: Int, sizeZ: Int): BlockPos {
        var x = local.x
        var z = local.z
        when (mirror) {
            BlockMirror.LEFT_RIGHT -> z = (sizeZ - 1) - z
            BlockMirror.FRONT_BACK -> x = (sizeX - 1) - x
            BlockMirror.NONE -> { }
        }
        return when (rotation) {
            BlockRotation.NONE -> BlockPos(x, local.y, z)
            BlockRotation.CLOCKWISE_90 -> BlockPos((sizeZ - 1) - z, local.y, x)
            BlockRotation.CLOCKWISE_180 -> BlockPos((sizeX - 1) - x, local.y, (sizeZ - 1) - z)
            BlockRotation.COUNTERCLOCKWISE_90 -> BlockPos(z, local.y, (sizeX - 1) - x)
        }
    }
}

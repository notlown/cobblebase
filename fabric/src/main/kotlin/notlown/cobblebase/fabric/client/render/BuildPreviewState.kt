package notlown.cobblebase.fabric.client.render

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.util.BlockMirror
import net.minecraft.util.BlockRotation
import net.minecraft.util.math.BlockPos
import notlown.cobblebase.core.net.BuildPreviewBlocksRequestC2SPacket
import notlown.cobblebase.core.net.BuildPreviewBlocksSyncS2CPacket
import notlown.cobblebase.core.net.StructureTemplateListSyncS2CPacket

/**
 * Client-side state for the active Build Preview.
 *
 * When the player picks a template from the Builder tab, the GUI closes and the
 * preview opens — a wireframe outline of the structure follows the player's gaze
 * until they confirm or cancel.
 *
 * Manipulated by the BuildPreviewKeyHandler (R/M to rotate/mirror, WASD/Q/E to nudge,
 * Enter to send a [BuildJobConfigureC2SPacket], Esc to clear).
 */
object BuildPreviewState {

    /** The pasture this preview is being configured for (so the server can store the job). */
    var pasturePos: BlockPos? = null

    /** The template being placed. Null when no preview is active. */
    var template: StructureTemplateListSyncS2CPacket.TemplateDTO? = null

    /** World position of the template's lower-north-west corner. */
    var origin: BlockPos = BlockPos.ORIGIN

    var rotation: BlockRotation = BlockRotation.NONE
    var mirror: BlockMirror = BlockMirror.NONE

    /**
     * Template-local block list received from the server for the active preview, stored as
     * (local x, y, z, blockState). Null while loading or if no preview is active. The
     * BuildPreviewRenderer applies rotation/mirror/origin transforms at render time.
     */
    @Volatile
    var loadedBlocks: List<Quad>? = null
        private set

    @Volatile
    private var loadedTemplateId: String? = null

    data class Quad(val x: Int, val y: Int, val z: Int, val state: BlockState)

    fun isActive(): Boolean = template != null

    fun start(pasture: BlockPos, template: StructureTemplateListSyncS2CPacket.TemplateDTO, origin: BlockPos) {
        this.pasturePos = pasture.toImmutable()
        this.template = template
        this.origin = origin.toImmutable()
        this.rotation = BlockRotation.NONE
        this.mirror = BlockMirror.NONE
        // Kick off async block-list request — the wireframe shows immediately,
        // ghost blocks materialise when the server response arrives.
        loadedBlocks = null
        loadedTemplateId = template.id
        try {
            ClientPlayNetworking.send(BuildPreviewBlocksRequestC2SPacket(template.id))
        } catch (_: Exception) { /* not connected — wireframe still works */ }
    }

    fun clear() {
        pasturePos = null
        template = null
        origin = BlockPos.ORIGIN
        rotation = BlockRotation.NONE
        mirror = BlockMirror.NONE
        loadedBlocks = null
        loadedTemplateId = null
    }

    /** Called by the S2C handler when block data arrives from the server. */
    fun onBlocksReceived(packet: BuildPreviewBlocksSyncS2CPacket) {
        if (loadedTemplateId != packet.templateId) return  // stale response, ignore
        val list = ArrayList<Quad>(packet.blocks.size)
        for (b in packet.blocks) {
            val state = Block.STATE_IDS.get(b[3]) ?: continue
            list.add(Quad(b[0], b[1], b[2], state))
        }
        loadedBlocks = list
    }

    fun nudge(dx: Int, dy: Int, dz: Int) {
        origin = origin.add(dx, dy, dz)
    }

    fun rotateClockwise() {
        rotation = when (rotation) {
            BlockRotation.NONE -> BlockRotation.CLOCKWISE_90
            BlockRotation.CLOCKWISE_90 -> BlockRotation.CLOCKWISE_180
            BlockRotation.CLOCKWISE_180 -> BlockRotation.COUNTERCLOCKWISE_90
            BlockRotation.COUNTERCLOCKWISE_90 -> BlockRotation.NONE
        }
    }

    fun toggleMirror() {
        mirror = when (mirror) {
            BlockMirror.NONE -> BlockMirror.LEFT_RIGHT
            BlockMirror.LEFT_RIGHT -> BlockMirror.FRONT_BACK
            BlockMirror.FRONT_BACK -> BlockMirror.NONE
        }
    }

    /**
     * Returns the bounding box of the placed template after applying rotation.
     * Used by [BuildPreviewRenderer] to draw the outline.
     */
    fun computeBounds(): IntArray? {
        val t = template ?: return null
        // Rotation swaps X and Z axes when 90/270
        val (sx, sz) = when (rotation) {
            BlockRotation.CLOCKWISE_90, BlockRotation.COUNTERCLOCKWISE_90 -> t.sizeZ to t.sizeX
            else -> t.sizeX to t.sizeZ
        }
        return intArrayOf(
            origin.x, origin.y, origin.z,
            origin.x + sx, origin.y + t.sizeY, origin.z + sz
        )
    }
}

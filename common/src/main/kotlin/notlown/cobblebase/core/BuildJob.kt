package notlown.cobblebase.core

import net.minecraft.util.BlockMirror
import net.minecraft.util.BlockRotation
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

/**
 * A configured build job attached to a pasture.
 *
 * Set by the player via the Builder tab + preview mode (Phase 2). Consumed by
 * [notlown.cobblebase.core.executors.BuilderExecutor] in Phase 3 to drive the
 * Pokemon's block-by-block construction.
 *
 * @property templateId Identifier of the structure template (e.g. `cobbletowns:houses/plains_big`).
 * @property origin World position of the template's lower-north-west corner before rotation.
 * @property rotation Rotation applied to the template at placement.
 * @property mirror Mirror axis applied to the template at placement.
 * @property completed When true the build is finished and the job can be cleared.
 */
data class BuildJob(
    val templateId: Identifier,
    val origin: BlockPos,
    val rotation: BlockRotation = BlockRotation.NONE,
    val mirror: BlockMirror = BlockMirror.NONE,
    val completed: Boolean = false
)

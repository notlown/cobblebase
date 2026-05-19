package notlown.cobblebase.core.executors

import notlown.cobblebase.core.effectiveRadius
import notlown.cobblebase.core.effectiveRadiusFor

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.Registries
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.structure.StructureTemplate
import net.minecraft.util.BlockMirror
import net.minecraft.util.BlockRotation
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.BuildJob
import notlown.cobblebase.core.BuildJobManager
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import notlown.cobblebase.core.StructureTemplateRegistry
import java.util.UUID

/**
 * Builder: places one block per cooldown tick from a [BuildJob] attached to the pasture.
 *
 * Resource flow is "magic": blocks are pulled from a nearby chest and placed at the
 * target position with a brief particle + sound flourish — the Pokemon doesn't physically
 * walk to either point. This keeps the implementation tractable for the MVP and avoids
 * pathfinding through partially-built structures. Movement choreography can be layered
 * on later without changing the executor's contract.
 *
 * **Material handling is tolerant**: when the chest doesn't contain the next block, the
 * tick is a no-op and we retry on the next cooldown — the build pauses but doesn't fail.
 *
 * **Order**: blocks are placed bottom-up (lowest Y first), so each layer rests on the
 * previous one. Within a layer we sort by X then Z for deterministic visual progress.
 */
object BuilderExecutor : SkillExecutor {

    private val cooldowns = mutableMapOf<UUID, Long>()
    private const val STALE_TTL_TICKS = 1200L

    /** Drops cooldown state for Builder Pokemon that have stopped ticking. The planCache
     *  is keyed by pasture origin, not Pokemon UUID, so it's handled separately on pasture
     *  removal. */
    fun cleanupStale(now: Long) {
        val stale = cooldowns.entries.filter { now - it.value > STALE_TTL_TICKS }.map { it.key }
        for (id in stale) cooldowns.remove(id)
    }
    /** Per-pasture cache of placement order (template id + rotation + mirror keys). */
    private data class PlanKey(val templateId: String, val rotation: BlockRotation, val mirror: BlockMirror, val origin: BlockPos)
    private val planCache = mutableMapOf<BlockPos, Pair<PlanKey, List<Placement>>>()

    private data class Placement(val worldPos: BlockPos, val state: BlockState)

    override fun tick(
        world: World,
        origin: BlockPos,
        pokemonEntity: PokemonEntity,
        skill: SkillDef,
        skillEntry: SkillEntry
    ) {
        if (world !is ServerWorld) return
        val pokemonId = pokemonEntity.pokemon.uuid

        // Per-pokemon cooldown matches the skill's configured rate.
        val now = world.time
        val nextTick = cooldowns[pokemonId] ?: 0L
        if (now < nextTick) return
        cooldowns[pokemonId] = now + (skill.cooldownSeconds.coerceAtLeast(1) * 20L)

        val job = BuildJobManager.getJob(origin) ?: return
        if (job.completed) return

        val template = StructureTemplateRegistry.resolve(world.server, job.templateId) ?: run {
            Cobblebase.LOGGER.warn(
                "[Cobblebase] Builder: template ${job.templateId} not found for pasture $origin — clearing job."
            )
            BuildJobManager.clearJob(origin)
            BuildJobManager.save(world)
            return
        }

        val plan = planFor(origin, job, template)
        if (plan.isEmpty()) {
            // Empty template (or pure air) — nothing to do but still mark complete so we stop ticking.
            completeJob(world, origin, job)
            return
        }

        // Find the first placement whose world block doesn't already match the target state.
        val next = plan.firstOrNull { p -> world.getBlockState(p.worldPos) != p.state }
        if (next == null) {
            completeJob(world, origin, job)
            return
        }

        val itemId = Registries.BLOCK.getId(next.state.block)
        val item = Registries.ITEM.get(itemId)
        if (item == net.minecraft.item.Items.AIR) {
            // Block has no item form (e.g. fire, water, end_portal). Skip by placing directly.
            world.setBlockState(next.worldPos, next.state, 3)
            spawnFlourish(world, next.worldPos, pokemonEntity)
            return
        }

        // Look for an item in the player's nearby chests.
        val chestPos = InventoryHelper.findContainerWithItem(
            world, origin, skill.effectiveRadiusFor(origin), itemId.toString()
        ) ?: return  // Chest doesn't have material — wait, retry next tick (tolerant).

        val taken = InventoryHelper.extractItem(world, chestPos, itemId.toString(), 1)
        if (taken.isEmpty || taken.count < 1) return  // Race condition: item gone before extract.

        // Place the block. Flag 3 = notify clients + cause block updates so neighbors react.
        val placed = world.setBlockState(next.worldPos, next.state, 3)
        if (!placed) {
            // Could happen if a player block is in the way and immutable. Don't lose the item.
            world.spawnEntity(net.minecraft.entity.ItemEntity(
                world,
                next.worldPos.x + 0.5, next.worldPos.y + 0.5, next.worldPos.z + 0.5,
                taken
            ))
            return
        }

        spawnFlourish(world, next.worldPos, pokemonEntity)
    }

    /**
     * Returns the deduplicated list of block IDs still needed to complete the build at this
     * pasture, ordered roughly by build-plan order. Used by the BuilderHelperCoordinator to
     * decide which materials helpers should chase. Returns null when there's no active job.
     */
    fun getNeededBlocks(world: ServerWorld, pasture: BlockPos): List<String>? {
        val job = BuildJobManager.getJob(pasture) ?: return null
        if (job.completed) return emptyList()
        val template = notlown.cobblebase.core.StructureTemplateRegistry
            .resolve(world.server, job.templateId) ?: return null
        val plan = planFor(pasture, job, template)
        val seen = linkedSetOf<String>()
        for (p in plan) {
            val current = world.getBlockState(p.worldPos)
            if (current == p.state) continue
            val id = net.minecraft.registry.Registries.BLOCK.getId(p.state.block).toString()
            seen.add(id)
        }
        return seen.toList()
    }

    /**
     * Snapshot of the current state of a pasture's active build job, used by the Builder GUI
     * to render a status panel without polling the per-tick state directly.
     */
    data class JobStatus(
        val templateId: String,
        val displayName: String,
        val totalBlocks: Int,
        val placedBlocks: Int,
        val nextMissingBlockId: String?,
        val completed: Boolean
    )

    /**
     * Returns the current status of the build job at [pasture], or null if no job exists.
     * Iterates the plan and counts matching world blocks — O(plan size); only called from the
     * Builder-tab status request packet handler, so cost is bounded.
     */
    fun getJobStatus(world: ServerWorld, pasture: BlockPos): JobStatus? {
        val job = BuildJobManager.getJob(pasture) ?: return null
        val template = notlown.cobblebase.core.StructureTemplateRegistry
            .resolve(world.server, job.templateId) ?: return null
        val plan = planFor(pasture, job, template)
        val total = plan.size
        var placed = 0
        var nextMissingId: String? = null
        for (p in plan) {
            val current = world.getBlockState(p.worldPos)
            if (current == p.state) {
                placed++
            } else if (nextMissingId == null) {
                nextMissingId = net.minecraft.registry.Registries.BLOCK.getId(p.state.block).toString()
            }
        }
        val templateRef = notlown.cobblebase.core.StructureTemplateRegistry
            .getRef(world.server, job.templateId)
        val display = templateRef?.displayName ?: job.templateId.path.substringAfterLast('/')
        return JobStatus(
            templateId = job.templateId.toString(),
            displayName = display,
            totalBlocks = total,
            placedBlocks = placed,
            nextMissingBlockId = if (job.completed) null else nextMissingId,
            completed = job.completed
        )
    }

    private fun planFor(pasture: BlockPos, job: BuildJob, template: StructureTemplate): List<Placement> {
        val key = PlanKey(job.templateId.toString(), job.rotation, job.mirror, job.origin)
        val cached = planCache[pasture]
        if (cached != null && cached.first == key) return cached.second

        val sizeX = template.size.x
        val sizeZ = template.size.z
        val infos = TemplateBlockReader.getAllBlocks(template)

        val plan = infos.asSequence()
            .filter { info ->
                val block = info.state.block
                block != Blocks.AIR && block != Blocks.STRUCTURE_VOID && block != Blocks.STRUCTURE_BLOCK
            }
            .map { info ->
                val transformedLocal = transformLocal(info.pos, job.rotation, job.mirror, sizeX, sizeZ)
                val worldPos = job.origin.add(transformedLocal)
                val rotatedState = info.state.rotate(job.rotation).mirror(job.mirror)
                Placement(worldPos, rotatedState)
            }
            .sortedWith(compareBy({ it.worldPos.y }, { it.worldPos.x }, { it.worldPos.z }))
            .toList()

        planCache[pasture] = key to plan
        return plan
    }

    private fun transformLocal(
        local: BlockPos,
        rotation: BlockRotation,
        mirror: BlockMirror,
        sizeX: Int,
        sizeZ: Int
    ): BlockPos {
        var x = local.x
        var z = local.z

        // Mirror first (in template-local coords)
        when (mirror) {
            BlockMirror.LEFT_RIGHT -> z = (sizeZ - 1) - z
            BlockMirror.FRONT_BACK -> x = (sizeX - 1) - x
            BlockMirror.NONE -> { /* no-op */ }
        }

        // Then rotation around the bounding-box origin
        return when (rotation) {
            BlockRotation.NONE -> BlockPos(x, local.y, z)
            BlockRotation.CLOCKWISE_90 -> BlockPos((sizeZ - 1) - z, local.y, x)
            BlockRotation.CLOCKWISE_180 -> BlockPos((sizeX - 1) - x, local.y, (sizeZ - 1) - z)
            BlockRotation.COUNTERCLOCKWISE_90 -> BlockPos(z, local.y, (sizeX - 1) - x)
        }
    }

    private fun completeJob(world: ServerWorld, pasture: BlockPos, job: BuildJob) {
        BuildJobManager.setJob(pasture, job.copy(completed = true))
        BuildJobManager.save(world)
        planCache.remove(pasture)
        Cobblebase.LOGGER.debug("[Cobblebase] Builder: job @ $pasture completed (${job.templateId}).")
        // Celebratory burst at the pasture
        world.spawnParticles(
            ParticleTypes.HAPPY_VILLAGER,
            pasture.x + 0.5, pasture.y + 1.5, pasture.z + 0.5,
            18, 0.6, 0.6, 0.6, 0.05
        )
        world.playSound(
            null, pasture.x + 0.5, pasture.y + 1.0, pasture.z + 0.5,
            SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.BLOCKS,
            0.6f, 1.2f
        )
    }

    private fun spawnFlourish(world: ServerWorld, pos: BlockPos, pokemonEntity: PokemonEntity) {
        // Sparkles around the placed block
        world.spawnParticles(
            ParticleTypes.HAPPY_VILLAGER,
            pos.x + 0.5, pos.y + 0.5, pos.z + 0.5,
            6, 0.3, 0.3, 0.3, 0.02
        )
        // Block-place sound
        val state = world.getBlockState(pos)
        val sg = state.soundGroup
        world.playSound(
            null, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5,
            sg.placeSound, SoundCategory.BLOCKS,
            (sg.volume + 1.0f) / 2.0f, sg.pitch * 0.8f
        )
        // Tiny ack particles around the Pokemon so the player can see who built it
        world.spawnParticles(
            ParticleTypes.NOTE,
            pokemonEntity.x, pokemonEntity.y + pokemonEntity.height + 0.3,
            pokemonEntity.z,
            1, 0.0, 0.0, 0.0, 1.0
        )
    }
}

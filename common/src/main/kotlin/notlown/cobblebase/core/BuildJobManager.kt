package notlown.cobblebase.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.BlockMirror
import net.minecraft.util.BlockRotation
import net.minecraft.util.Identifier
import net.minecraft.util.WorldSavePath
import net.minecraft.util.math.BlockPos
import java.io.File

/**
 * Per-pasture active build job. Holds at most one job per pasture position.
 *
 * Persisted to `cobblebase_buildjobs.json` in the world save folder so jobs survive
 * server restarts mid-build.
 */
object BuildJobManager {

    private val jobs = mutableMapOf<BlockPos, BuildJob>()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun setJob(pasture: BlockPos, job: BuildJob) {
        jobs[pasture.toImmutable()] = job
    }

    fun clearJob(pasture: BlockPos) {
        jobs.remove(pasture)
    }

    fun getJob(pasture: BlockPos): BuildJob? = jobs[pasture]

    fun getAll(): Map<BlockPos, BuildJob> = jobs.toMap()

    // -------- Persistence --------

    private data class Persisted(
        val px: Int, val py: Int, val pz: Int,
        val template: String,
        val ox: Int, val oy: Int, val oz: Int,
        val rotation: String,
        val mirror: String,
        val completed: Boolean
    )

    private fun saveFile(world: ServerWorld): File {
        val saveDir = world.server.getSavePath(WorldSavePath.ROOT).toFile()
        return File(saveDir, "cobblebase_buildjobs.json")
    }

    fun save(world: ServerWorld) {
        try {
            val data = jobs.map { (pos, job) ->
                Persisted(
                    pos.x, pos.y, pos.z,
                    job.templateId.toString(),
                    job.origin.x, job.origin.y, job.origin.z,
                    job.rotation.name, job.mirror.name, job.completed
                )
            }
            saveFile(world).writeText(gson.toJson(data))
        } catch (e: Exception) {
            Cobblebase.LOGGER.warn("[Cobblebase] BuildJobManager: save failed: ${e.message}")
        }
    }

    fun load(world: ServerWorld) {
        jobs.clear()
        try {
            val file = saveFile(world)
            if (!file.exists()) return
            val type = object : TypeToken<List<Persisted>>() {}.type
            val data: List<Persisted> = gson.fromJson(file.readText(), type) ?: return
            for (d in data) {
                val templateId = Identifier.tryParse(d.template) ?: continue
                jobs[BlockPos(d.px, d.py, d.pz)] = BuildJob(
                    templateId = templateId,
                    origin = BlockPos(d.ox, d.oy, d.oz),
                    rotation = readRotation(d.rotation),
                    mirror = readMirror(d.mirror),
                    completed = d.completed
                )
            }
            Cobblebase.LOGGER.info("[Cobblebase] BuildJobManager: loaded ${jobs.size} jobs")
        } catch (e: Exception) {
            Cobblebase.LOGGER.warn("[Cobblebase] BuildJobManager: load failed: ${e.message}")
        }
    }

    private fun readRotation(name: String): BlockRotation =
        runCatching { BlockRotation.valueOf(name) }.getOrDefault(BlockRotation.NONE)

    private fun readMirror(name: String): BlockMirror =
        runCatching { BlockMirror.valueOf(name) }.getOrDefault(BlockMirror.NONE)
}

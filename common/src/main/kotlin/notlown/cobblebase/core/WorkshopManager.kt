package notlown.cobblebase.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.WorldSavePath
import java.io.File
import java.util.UUID

/**
 * Manages per-Pokemon workshop state for the Craftsman job.
 * Each Craftsman Pokemon has one active project that it gathers materials for and crafts.
 */
object WorkshopManager {

    enum class Phase { IDLE, GATHERING, CRAFTING, DEPOSITING }

    data class WorkshopProject(
        val recipeId: String,
        val gatheredItems: MutableMap<String, Int> = mutableMapOf(),
        val requiredItems: MutableMap<String, Int> = mutableMapOf(),
        var phase: Phase = Phase.GATHERING,
        var phaseStartTick: Long = 0L
    )

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val projects = mutableMapOf<UUID, WorkshopProject>()
    /** Total items crafted per Pokemon (persisted for display) */
    private val craftCounts = mutableMapOf<UUID, Int>()
    private var dirty = false

    fun getProject(pokemonId: UUID): WorkshopProject? = projects[pokemonId]

    fun setProject(pokemonId: UUID, recipeId: String) {
        if (recipeId.isBlank()) {
            projects.remove(pokemonId)
        } else {
            projects[pokemonId] = WorkshopProject(recipeId)
        }
        dirty = true
    }

    fun clearProject(pokemonId: UUID) {
        projects.remove(pokemonId)
        dirty = true
    }

    fun addGatheredItem(pokemonId: UUID, itemId: String, count: Int = 1) {
        val project = projects[pokemonId] ?: return
        project.gatheredItems[itemId] = (project.gatheredItems[itemId] ?: 0) + count
        dirty = true
    }

    fun setPhase(pokemonId: UUID, phase: Phase, tick: Long) {
        val project = projects[pokemonId] ?: return
        project.phase = phase
        project.phaseStartTick = tick
        dirty = true
    }

    fun resetGathered(pokemonId: UUID) {
        val project = projects[pokemonId] ?: return
        project.gatheredItems.clear()
        dirty = true
    }

    fun getAllProjects(): Map<UUID, WorkshopProject> = projects.toMap()

    fun incrementCraftCount(pokemonId: UUID) {
        craftCounts[pokemonId] = (craftCounts[pokemonId] ?: 0) + 1
        dirty = true
    }

    fun getCraftCount(pokemonId: UUID): Int = craftCounts[pokemonId] ?: 0
    fun getAllCraftCounts(): Map<UUID, Int> = craftCounts.toMap()

    private fun getSaveFile(world: ServerWorld): File {
        val saveDir = world.server.getSavePath(WorldSavePath.ROOT).toFile()
        return File(saveDir, "cobblebase_workshop.json")
    }

    fun save(world: ServerWorld) {
        if (!dirty) return
        try {
            val file = getSaveFile(world)
            val data = projects.mapKeys { it.key.toString() }.mapValues { (_, proj) ->
                mapOf(
                    "recipeId" to proj.recipeId,
                    "gatheredItems" to proj.gatheredItems,
                    "requiredItems" to proj.requiredItems,
                    "phase" to proj.phase.name,
                    "phaseStartTick" to proj.phaseStartTick
                )
            }
            file.writeText(gson.toJson(data))
            dirty = false
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Workshop] Failed to save: ${e.message}")
        }
    }

    fun load(world: ServerWorld) {
        try {
            val file = getSaveFile(world)
            if (!file.exists()) return
            val type = object : TypeToken<Map<String, Map<String, Any>>>() {}.type
            val data: Map<String, Map<String, Any>> = gson.fromJson(file.readText(), type) ?: return
            projects.clear()
            for ((uuidStr, fields) in data) {
                val uuid = try { UUID.fromString(uuidStr) } catch (_: Exception) { continue }
                val recipeId = fields["recipeId"] as? String ?: continue
                @Suppress("UNCHECKED_CAST")
                val gathered = (fields["gatheredItems"] as? Map<String, Double>)
                    ?.mapValues { it.value.toInt() }?.toMutableMap() ?: mutableMapOf()
                @Suppress("UNCHECKED_CAST")
                val required = (fields["requiredItems"] as? Map<String, Double>)
                    ?.mapValues { it.value.toInt() }?.toMutableMap() ?: mutableMapOf()
                val phase = try { Phase.valueOf(fields["phase"] as? String ?: "GATHERING") } catch (_: Exception) { Phase.GATHERING }
                val tick = (fields["phaseStartTick"] as? Double)?.toLong() ?: 0L
                projects[uuid] = WorkshopProject(recipeId, gathered, required, phase, tick)
            }
            Cobblebase.LOGGER.debug("[Workshop] Loaded ${projects.size} active projects")
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Workshop] Failed to load: ${e.message}")
        }
    }
}

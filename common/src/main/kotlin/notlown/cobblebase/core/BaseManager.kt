package notlown.cobblebase.core

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.WorldSavePath
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.io.File
import java.util.UUID

object BaseManager {

    private val gson = Gson()
    private val assignments = mutableMapOf<UUID, String?>()
    private var dirty = false
    private var lastSaveTick = 0L
    private val SAVE_INTERVAL = 600L // save every 30 seconds

    // Unstuck detection: track last known position + time it changed
    private data class PosRecord(val pos: BlockPos, val changedAt: Long)
    private val lastKnownPos = mutableMapOf<UUID, PosRecord>()
    private const val STUCK_TIMEOUT_TICKS = 160L // 8 seconds without movement = stuck

    fun tickPokemon(world: World, pastureOrigin: BlockPos, pokemonEntity: PokemonEntity) {
        val pokemonId: UUID = pokemonEntity.pokemon.uuid
        val speciesName: String = pokemonEntity.pokemon.species.name.lowercase()
        val now = world.time

        // Auto-save periodically
        if (dirty && world is ServerWorld && now - lastSaveTick > SAVE_INTERVAL) {
            save(world)
        }

        // Unstuck check: if Pokemon hasn't moved for 30s, reset its navigation state
        val currentPos = pokemonEntity.blockPos
        val record = lastKnownPos[pokemonId]
        if (record == null || record.pos != currentPos) {
            lastKnownPos[pokemonId] = PosRecord(currentPos.toImmutable(), now)
        } else if (now - record.changedAt > STUCK_TIMEOUT_TICKS) {
            // Pokemon is stuck — clear navigation targets to unstick it
            NavigationHelper.clearTargets(pokemonEntity)
            lastKnownPos[pokemonId] = PosRecord(currentPos.toImmutable(), now) // reset timer
            if (now % 200 == 0L) {
            }
        }

        // Safety: prevent Pokemon from wandering too far from pasture (causes despawning)
        if (CobblebaseConfig.enableSafetyTeleport) {
            val distFromPasture = kotlin.math.sqrt(
                pokemonEntity.squaredDistanceTo(
                    pastureOrigin.x + 0.5, pastureOrigin.y.toDouble(), pastureOrigin.z + 0.5
                )
            )
            if (distFromPasture > CobblebaseConfig.safetyTeleportDistance) {
                val (sx, sz) = getSpawnOffset(world)
                pokemonEntity.setPosition(pastureOrigin.x + sx, pastureOrigin.y + 1.0, pastureOrigin.z + sz)
                NavigationHelper.clearTargets(pokemonEntity)
            }
        }

        // Safety: prevent drowning — if Pokemon is submerged in water, teleport to pasture origin
        if (pokemonEntity.isSubmergedInWater || pokemonEntity.air < 100) {
            val (sx, sz) = getSpawnOffset(world)
            pokemonEntity.setPosition(pastureOrigin.x + sx, pastureOrigin.y + 1.0, pastureOrigin.z + sz)
            pokemonEntity.air = pokemonEntity.maxAir
            NavigationHelper.clearTargets(pokemonEntity)
        }

        val speciesData: SpeciesSkills? = SpeciesSkillRegistry.getSkills(speciesName)
        if (speciesData == null) {
            if (now % 100 == 0L) Cobblebase.log("[BaseManager] $speciesName has NO species skills registered")
            return
        }

        val assignedSkillId: String? = assignments[pokemonId]

        if (assignedSkillId != null) {
            // Check if the assigned skill is still enabled by admin
            if (!JobConfigOverrides.isEnabled(assignedSkillId)) {
                // Skill was disabled by admin — reset to Idle
                assignments.remove(pokemonId)
                dirty = true
            } else if (!isBuffSkill(assignedSkillId)) {
                // Clear ambient behavior when actively working
                AmbientBehavior.clearState(pokemonId)
                val entry: SkillEntry? = speciesData.skills.find { e -> e.skillId == assignedSkillId }
                if (entry == null) {
                } else {
                    executeSkill(world, pastureOrigin, pokemonEntity, entry)
                }
            }
        }
        // null assignment = Idle (do nothing). Only passive buffs run below.

        // Passive buffs: always tick buff skills regardless of job assignment
        for (entry: SkillEntry in speciesData.skills) {
            val skillDef: SkillDef = SkillRegistry.get(entry.skillId) ?: continue
            if (!isBuffSkill(skillDef.executor)) continue
            val exec: SkillExecutor = ExecutorRegistry.get(skillDef.executor) ?: continue
            exec.tick(world, pastureOrigin, pokemonEntity, skillDef, entry)
        }
    }

    private fun isBuffSkill(executorOrSkillId: String): Boolean {
        // Check directly against executor names
        if (executorOrSkillId in BUFF_EXECUTORS) return true
        // Also check if a skillId resolves to a buff executor
        val skillDef = SkillRegistry.get(executorOrSkillId)
        if (skillDef != null && skillDef.executor in BUFF_EXECUTORS) return true
        return false
    }

    private val BUFF_EXECUTORS = listOf(
        "speed_boost", "strength_boost", "resistance_boost",
        "night_vision", "water_breathing", "jump_boost",
        "haste_boost", "saturation_boost",
        "lucky_charm", "aura", "growth"
    )

    private fun executeSkill(world: World, origin: BlockPos, pokemonEntity: PokemonEntity, entry: SkillEntry) {
        val skillDef: SkillDef? = SkillRegistry.getEffective(entry.skillId, origin)
        if (skillDef == null) {
            return
        }
        val exec: SkillExecutor? = ExecutorRegistry.get(skillDef.executor)
        if (exec == null) {
            return
        }
        exec.tick(world, origin, pokemonEntity, skillDef, entry)
    }

    fun assignSkill(pokemonId: UUID, skillId: String?) {
        if (skillId == null) assignments.remove(pokemonId) else assignments[pokemonId] = skillId
        dirty = true
    }

    fun getAssignment(pokemonId: UUID): String? = assignments[pokemonId]

    /**
     * Returns a snapshot of all current assignments for sync packets.
     * Values are skill IDs; null assignments are excluded.
     */
    fun getAllAssignments(): Map<UUID, String> {
        return assignments.filterValues { it != null }.mapValues { it.value!! }
    }

    fun getAvailableSkills(pokemonEntity: PokemonEntity): List<SkillEntry> {
        val speciesName: String = pokemonEntity.pokemon.species.name.lowercase()
        return SpeciesSkillRegistry.getSkills(speciesName)?.skills ?: emptyList()
    }

    /**
     * Check if a skill executor name is a passive buff (not assignable as a job).
     */
    fun isBuffExecutor(executor: String): Boolean {
        return executor in BUFF_EXECUTORS
    }

    /**
     * Returns a random spawn offset (1-2 blocks from center) to avoid spawning directly on the pasture block.
     * Matches Cobblemon's default behavior of placing Pokemon near (not on) the pasture.
     */
    private fun getSpawnOffset(world: World): Pair<Double, Double> {
        val rand = world.random
        val angle = rand.nextDouble() * Math.PI * 2
        val dist = 1.5 + rand.nextDouble() // 1.5-2.5 blocks from center
        return Pair(Math.cos(angle) * dist + 0.5, Math.sin(angle) * dist + 0.5)
    }

    // -- Persistence --

    private fun getSaveFile(world: ServerWorld): File {
        val saveDir = world.server.getSavePath(WorldSavePath.ROOT).toFile()
        return File(saveDir, "cobblebase_assignments.json")
    }

    fun save(world: World) {
        if (world !is ServerWorld) return
        try {
            val file = getSaveFile(world)
            val data = assignments.mapKeys { it.key.toString() }
            file.writeText(gson.toJson(data))
            dirty = false
            lastSaveTick = world.time
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to save assignments: ${e.message}")
        }
    }

    fun load(world: World) {
        if (world !is ServerWorld) return
        try {
            val file = getSaveFile(world)
            if (!file.exists()) return
            val type = object : TypeToken<Map<String, String?>>() {}.type
            val data: Map<String, String?> = gson.fromJson(file.readText(), type)
            assignments.clear()
            for ((key, value) in data) {
                assignments[UUID.fromString(key)] = value
            }
            Cobblebase.LOGGER.info("[Cobblebase] Loaded ${assignments.size} skill assignments")
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to load assignments: ${e.message}")
        }
    }
}

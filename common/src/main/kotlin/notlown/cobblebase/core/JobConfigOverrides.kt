package notlown.cobblebase.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.WorldSavePath
import java.io.File

/**
 * Stores per-job configuration overrides (cooldown, search radius, enabled).
 * When an override exists for a job, its values replace the defaults from JSON.
 * Null fields mean "use the default from SkillDef".
 */
object JobConfigOverrides {

    data class JobOverride(
        val cooldownSeconds: Long? = null,
        val searchRadius: Int? = null,
        val enabled: Boolean = true,
        /**
         * Per-skill tunable parameter overrides (e.g. mentor's xpMultiplier).
         * Keys match the JSON `tuning` map; values replace the JSON `defaultValue`.
         * Entries omitted from this map fall back to the skill's declared default.
         */
        val tuning: Map<String, Double>? = null
    )

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val overrides = mutableMapOf<String, JobOverride>()

    fun getOverride(skillId: String): JobOverride? = overrides[skillId]

    fun getAllOverrides(): Map<String, JobOverride> = overrides.toMap()

    fun setOverride(skillId: String, override: JobOverride) {
        overrides[skillId] = override
    }

    fun removeOverride(skillId: String) {
        overrides.remove(skillId)
    }

    /**
     * Returns the effective cooldown for a skill, checking overrides first.
     */
    fun getEffectiveCooldown(skill: SkillDef): Long {
        val override = overrides[skill.id]
        return override?.cooldownSeconds ?: skill.cooldownSeconds
    }

    /**
     * Returns the effective search radius for a skill, checking overrides first.
     */
    fun getEffectiveRadius(skill: SkillDef): Int {
        val override = overrides[skill.id]
        return override?.searchRadius ?: skill.searchRadius
    }

    /**
     * Returns whether a skill/job is enabled.
     */
    fun isEnabled(skillId: String): Boolean {
        val override = overrides[skillId] ?: return true
        return override.enabled
    }

    /**
     * Returns the effective value for a tunable parameter declared on the skill.
     * Lookup order: admin override → skill's JSON `defaultValue` → caller's fallback.
     */
    fun getEffectiveTuning(skill: SkillDef, key: String, fallback: Double): Double {
        overrides[skill.id]?.tuning?.get(key)?.let { return it }
        skill.tuning?.get(key)?.let { return it.defaultValue }
        return fallback
    }

    /**
     * Bulk-update overrides from a map (used when receiving data from client).
     */
    fun updateAll(newOverrides: Map<String, JobOverride>) {
        overrides.clear()
        overrides.putAll(newOverrides)
    }

    private fun getSaveFile(world: ServerWorld): File {
        val saveDir = world.server.getSavePath(WorldSavePath.ROOT).toFile()
        return File(saveDir, "cobblebase_job_overrides.json")
    }

    fun save(world: ServerWorld) {
        try {
            val file = getSaveFile(world)
            val data = overrides.mapValues { (_, override) ->
                mapOf(
                    "cooldownSeconds" to override.cooldownSeconds,
                    "searchRadius" to override.searchRadius,
                    "enabled" to override.enabled,
                    "tuning" to override.tuning
                )
            }
            file.writeText(gson.toJson(data))
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to save job overrides: ${e.message}")
        }
    }

    fun load(world: ServerWorld) {
        try {
            val file = getSaveFile(world)
            if (!file.exists()) return
            val type = object : TypeToken<Map<String, Map<String, Any?>>>() {}.type
            val data: Map<String, Map<String, Any?>> = gson.fromJson(file.readText(), type) ?: return
            overrides.clear()
            for ((skillId, fields) in data) {
                val cooldown = (fields["cooldownSeconds"] as? Double)?.toLong()
                val radius = (fields["searchRadius"] as? Double)?.toInt()
                val enabled = (fields["enabled"] as? Boolean) ?: true
                @Suppress("UNCHECKED_CAST")
                val tuningRaw = fields["tuning"] as? Map<String, Any?>
                val tuning = tuningRaw?.mapNotNull { (k, v) ->
                    val d = (v as? Number)?.toDouble() ?: return@mapNotNull null
                    k to d
                }?.toMap()
                overrides[skillId] = JobOverride(cooldown, radius, enabled, tuning?.takeIf { it.isNotEmpty() })
            }
            Cobblebase.LOGGER.info("[Cobblebase] Loaded ${overrides.size} job config overrides")
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to load job overrides: ${e.message}")
        }
    }
}

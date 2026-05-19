package notlown.cobblebase.core

import com.google.gson.Gson
import java.io.InputStreamReader

/**
 * Registry that loads and holds all skill definitions from JSON resources.
 * Skill JSON files are located in data/cobblebase/skills/ and indexed by _index.txt.
 */
object SkillRegistry {
    private val gson = Gson()
    private val skills = mutableMapOf<String, SkillDef>()

    fun init() {
        loadFromResources()
        Cobblebase.LOGGER.debug("SkillRegistry: ${skills.size} skills registered")
    }

    fun get(id: String): SkillDef? = skills[id]
    fun getAll(): Map<String, SkillDef> = skills.toMap()
    fun register(skill: SkillDef) { skills[skill.id] = skill }

    /**
     * Returns a SkillDef with overridden values applied from JobConfigOverrides.
     * This allows executors to use the effective values without changes.
     */
    fun getEffective(id: String): SkillDef? {
        val base = skills[id] ?: return null
        val override = JobConfigOverrides.getOverride(id) ?: return base
        return base.copy(
            cooldownSeconds = override.cooldownSeconds ?: base.cooldownSeconds,
            searchRadius = override.searchRadius ?: base.searchRadius
        )
    }

    /**
     * Returns the effective cooldown for a skill, checking overrides first.
     */
    fun getEffectiveCooldown(skillId: String): Long {
        val skill = skills[skillId] ?: return 60
        return JobConfigOverrides.getEffectiveCooldown(skill)
    }

    /**
     * The effective search radius for a job.
     *
     * - **Active jobs** (Mining, Fishing, Egg Hatcher, Craftsman, Healer, …) →
     *   ALWAYS the admin's "Pasture Range" stepper. Per-skill JSON values are
     *   ignored; admins control the one number and every executor obeys it.
     * - **Auras / passive buffs** (Speed Boost, Strength Boost, Growth Aura,
     *   Aura Boost, …) → use the per-skill JSON radius (or admin per-skill
     *   override) so balance-tuned aura reach isn't broken by the global cap.
     *
     * Unknown skill ids fall through to the admin radius.
     */
    fun getEffectiveRadius(skillId: String): Int {
        val skill = skills[skillId] ?: return CobblebaseConfig.jobSearchRadius
        return if (BaseManager.isBuffExecutor(skill.executor)) {
            JobConfigOverrides.getEffectiveRadius(skill)
        } else {
            CobblebaseConfig.jobSearchRadius
        }
    }

    /**
     * Returns the effective value of a tunable parameter for a skill.
     * Admin override wins, then the JSON `defaultValue`, then the caller's fallback.
     *
     * Executors should call this with the same fallback they'd use if the skill
     * file had been authored without the tuning declaration — keeps the executor
     * working even on skills that haven't opted in to per-job tuning.
     */
    fun getEffectiveTuning(skillId: String, key: String, fallback: Double): Double {
        val skill = skills[skillId] ?: return fallback
        return JobConfigOverrides.getEffectiveTuning(skill, key, fallback)
    }

    private fun loadFromResources() {
        val indexPath = "/data/cobblebase/skills/_index.txt"
        val indexStream = Cobblebase::class.java.getResourceAsStream(indexPath)
        if (indexStream == null) {
            Cobblebase.LOGGER.warn("[Cobblebase] Skill index not found at $indexPath")
            return
        }

        val fileNames = indexStream.bufferedReader().use { it.readLines() }
            .map { it.trim() }
            .filter { it.endsWith(".json") }

        for (fileName in fileNames) {
            try {
                val resourcePath = "/data/cobblebase/skills/$fileName"
                val stream = Cobblebase::class.java.getResourceAsStream(resourcePath)
                if (stream == null) {
                    Cobblebase.LOGGER.warn("[Cobblebase] Skill file not found: $resourcePath")
                    continue
                }
                val skillDef = stream.use { s ->
                    InputStreamReader(s, Charsets.UTF_8).use { reader ->
                        gson.fromJson(reader, SkillDef::class.java)
                    }
                }
                if (skillDef != null) {
                    register(skillDef)
                } else {
                    Cobblebase.LOGGER.warn("[Cobblebase] Failed to parse skill from $fileName")
                }
            } catch (e: Exception) {
                Cobblebase.LOGGER.error("[Cobblebase] Error loading skill $fileName: ${e.message}")
            }
        }
    }
}

/**
 * Convenience accessor — every executor should use this in place of
 * `skill.searchRadius`. Routes through [SkillRegistry.getEffectiveRadius] so the
 * admin's "Pasture Range" cap applies uniformly.
 */
val SkillDef.effectiveRadius: Int
    get() = SkillRegistry.getEffectiveRadius(this.id)

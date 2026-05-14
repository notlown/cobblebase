package notlown.cobblebase.core

/**
 * Represents a skill/job definition loaded from JSON.
 * Skills are generic templates - Pokemon species reference skills by ID.
 *
 * Example JSON (data/cobblebase/skills/fishing.json):
 * {
 *   "id": "cobblebase:fishing",
 *   "name": "Fishing",
 *   "description": "Catches fish from nearby water",
 *   "category": "gathering",
 *   "cooldownSeconds": 60,
 *   "searchRadius": 10,
 *   "executor": "fishing",
 *   "effectType": "water",
 *   "lootTable": "minecraft:gameplay/fishing",
 *   "targetBlock": "minecraft:water",
 *   "requiresNearby": true,
 *   "xpReward": 0,
 *   "icon": "cobblebase:textures/skill/fishing.png"
 * }
 */
data class SkillDef(
    val id: String,
    val name: String,
    val description: String = "",
    val category: String = "general",
    val cooldownSeconds: Long = 60,
    val searchRadius: Int = 10,
    val executor: String = "generic",
    val effectType: String = "default",
    val lootTable: String? = null,
    val targetBlock: String? = null,
    val requiresNearby: Boolean = false,
    val xpReward: Int = 0,
    val icon: String? = null,
    /**
     * Per-skill tunable parameters surfaced in the Admin → Jobs GUI.
     * Each key is a stable identifier consumed by the executor (e.g. "xpMultiplier").
     * The map's iteration order is preserved (LinkedHashMap from Gson) so the GUI
     * renders fields in the order they appear in JSON.
     */
    val tuning: Map<String, TuningField>? = null
)

/**
 * Describes a single configurable parameter exposed in the Admin → Jobs GUI.
 * The executor reads the effective value via `SkillRegistry.getTuning(skillId, key, default)`.
 *
 * Example JSON snippet in a skill file:
 *   "tuning": {
 *     "xpMultiplier": { "label": "XP Multiplier", "defaultValue": 1.0, "min": 0.0, "max": 5.0, "step": 0.1 }
 *   }
 */
data class TuningField(
    val label: String,
    val defaultValue: Double,
    val min: Double = 0.0,
    val max: Double = 10.0,
    val step: Double = 0.1,
    /** Optional unit suffix shown in the GUI ("x", "%", "blocks"). */
    val unit: String? = null,
    /** Optional tooltip explaining the parameter. */
    val tooltip: String? = null
)

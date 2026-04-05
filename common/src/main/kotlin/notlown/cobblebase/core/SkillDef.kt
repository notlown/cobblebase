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
    val icon: String? = null
)

package notlown.cobblebase.core

/**
 * Defines which skills a Pokemon species can perform and at what proficiency.
 *
 * Example JSON (data/cobblebase/species_skills/nidoking.json):
 * {
 *   "species": "nidoking",
 *   "skills": [
 *     { "skill": "cobblebase:fossil_digger", "proficiency": 3 },
 *     { "skill": "cobblebase:guard", "proficiency": 2 },
 *     { "skill": "cobblebase:earth_shaker", "proficiency": 1 }
 *   ]
 * }
 *
 * Proficiency (1-5):
 *   1 = Novice (slow, lower output)
 *   2 = Apprentice
 *   3 = Skilled (default)
 *   4 = Expert
 *   5 = Master (fast, higher output)
 *
 * Proficiency affects cooldown multiplier and loot quality.
 */
data class SpeciesSkills(
    val species: String,
    val skills: List<SkillEntry>
)

data class SkillEntry(
    val skillId: String,
    val proficiency: Int = 3
)

package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import notlown.cobblebase.core.effects.SkillEffects
import java.util.UUID

/**
 * Recruiter executor: periodically attracts a random rare Pokemon to spawn nearby.
 * Instead of modifying spawn rates, it directly spawns a wild Pokemon from
 * Cobblemon's spawn pool near the working Pokemon.
 */
object RecruiterExecutor : SkillExecutor {

    private val lastRecruitTime = mutableMapOf<UUID, Long>()

    // Curated list of desirable Pokemon that can be "recruited"
    // Organized by rarity tier
    private val COMMON_RECRUITS = listOf(
        "eevee", "pichu", "cleffa", "togepi", "riolu", "happiny",
        "mime_jr", "bonsly", "munchlax", "chingling", "budew"
    )

    private val UNCOMMON_RECRUITS = listOf(
        "dratini", "larvitar", "bagon", "beldum", "gible", "deino",
        "goomy", "jangmo-o", "dreepy", "frigibax", "ralts",
        "zorua", "axew", "litwick", "honedge", "phantump"
    )

    private val RARE_RECRUITS = listOf(
        "lapras", "snorlax", "chansey", "togetic", "lucario",
        "gardevoir", "salamence", "metagross", "garchomp",
        "hydreigon", "goodra", "kommo-o", "dragapult",
        "arcanine", "milotic", "absol"
    )

    override fun tick(
        world: World,
        origin: BlockPos,
        pokemonEntity: PokemonEntity,
        skill: SkillDef,
        skillEntry: SkillEntry
    ) {
        if (world !is ServerWorld) return
        val pokemonId = pokemonEntity.pokemon.uuid
        val now = world.time
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(skill.cooldownSeconds, skillEntry.proficiency)

        val lastTime = lastRecruitTime[pokemonId] ?: 0L
        if (now - lastTime < cooldownTicks) {
            // Working particles while waiting
            if (now % 40 == 0L) {
                SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
            }
            return
        }

        lastRecruitTime[pokemonId] = now

        // Pick a random Pokemon based on proficiency
        val speciesName = pickRecruit(world, skillEntry.proficiency)
        if (speciesName == null) return

        // Find a spawn position nearby (within skill radius)
        val spawnPos = findSpawnPos(world, origin, skill.searchRadius)
        if (spawnPos == null) return

        // Spawn the wild Pokemon
        try {
            val species = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByName(speciesName)
            if (species == null) {
                Cobblebase.LOGGER.warn("[Recruiter] Unknown species: $speciesName")
                return
            }

            val pokemon = com.cobblemon.mod.common.pokemon.Pokemon()
            pokemon.species = species

            // Level based on area average (use the recruiter's level as reference, scaled down)
            val baseLevel = pokemonEntity.pokemon.level
            val level = (baseLevel * 0.6 + world.random.nextInt(10) - 5).toInt().coerceIn(5, baseLevel)
            pokemon.level = level

            pokemon.initialize()

            val entity = pokemon.sendOut(world, spawnPos.toCenterPos(), null) {
                Cobblebase.LOGGER.info("[Recruiter] ${pokemonEntity.pokemon.species.name} recruited a wild $speciesName (Lv.$level)!")
            }

            SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Recruiter] Failed to spawn $speciesName: ${e.message}")
        }
    }

    private fun pickRecruit(world: World, proficiency: Int): String? {
        val rand = world.random.nextInt(100)

        // Higher proficiency = better chance at rare recruits
        val rareChance = proficiency * 5      // 5-25%
        val uncommonChance = 30 + proficiency * 5  // 35-55%
        // Rest is common

        val pool = when {
            rand < rareChance -> RARE_RECRUITS
            rand < rareChance + uncommonChance -> UNCOMMON_RECRUITS
            else -> COMMON_RECRUITS
        }

        return pool.randomOrNull()
    }

    private fun findSpawnPos(world: ServerWorld, origin: BlockPos, radius: Int): BlockPos? {
        // Try a few random positions nearby
        for (i in 0..10) {
            val x = origin.x + world.random.nextInt(radius * 2) - radius
            val z = origin.z + world.random.nextInt(radius * 2) - radius
            val y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, x, z)
            val pos = BlockPos(x, y, z)

            // Check if it's a valid spawn position (not inside a block)
            if (world.getBlockState(pos).isAir && world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
                return pos
            }
        }
        return null
    }
}

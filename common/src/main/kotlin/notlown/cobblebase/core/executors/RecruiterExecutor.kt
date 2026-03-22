package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.Heightmap
import net.minecraft.world.World
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import notlown.cobblebase.core.effects.SkillEffects
import java.util.UUID

/**
 * Friend Recruiter: spawns wild Pokemon of the SAME TYPE as the recruiter.
 * Higher proficiency = better chance at rarer spawns.
 * The spawned Pokemon shares one of the recruiter's types.
 */
object RecruiterExecutor : SkillExecutor {

    private val lastRecruitTime = mutableMapOf<UUID, Long>()
    // Track recruited Pokemon entity IDs for sparkle effect
    private val recruitedEntities = mutableSetOf<Int>()

    // Cache: type -> list of species names (built once on first use)
    private var speciesByType: Map<String, List<String>>? = null

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
        // Use config cooldown instead of skill definition (so it can be changed in settings)
        val baseCooldown = if (skill.id == "cobblebase:recruiter")
            CobblebaseConfig.legendaryRecruiterCooldownSeconds
        else
            CobblebaseConfig.friendRecruiterCooldownSeconds
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(baseCooldown, skillEntry.proficiency)

        // Sparkle effect on all recruited Pokemon every 2 seconds
        if (now % 40 == 0L) {
            tickRecruitedSparkles(world)
        }

        val lastTime = lastRecruitTime[pokemonId] ?: 0L
        if (now - lastTime < cooldownTicks) {
            if (now % 40 == 0L) {
                SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
            }
            return
        }

        lastRecruitTime[pokemonId] = now

        // Pick one of the recruiter's types randomly
        val recruiterTypes = pokemonEntity.pokemon.types.toList()
        if (recruiterTypes.isEmpty()) return
        val chosenType = recruiterTypes[world.random.nextInt(recruiterTypes.size)]

        // Find a Pokemon of that type to spawn
        val speciesName = pickSpeciesByType(world, chosenType.name, skillEntry.proficiency) ?: return

        // Spawn directly next to the recruiter Pokemon, not at the pasture
        val spawnPos = findSpawnPos(world, pokemonEntity.blockPos, 3) ?: return

        try {
            val species = PokemonSpecies.getByName(speciesName) ?: return

            val pokemon = Pokemon()
            pokemon.species = species

            val baseLevel = pokemonEntity.pokemon.level
            val level = (baseLevel * 0.6 + world.random.nextInt(10) - 5).toInt().coerceIn(5, baseLevel)
            pokemon.level = level
            pokemon.initialize()

            pokemon.sendOut(world, spawnPos.toCenterPos(), null) {
                Cobblebase.LOGGER.info("[Recruiter] ${pokemonEntity.pokemon.species.name} recruited a wild $speciesName (Lv.$level) [${chosenType.name} type]")
            }

            // Spawn effects on the recruited Pokemon
            val sx = spawnPos.x + 0.5
            val sy = spawnPos.y + 1.0
            val sz = spawnPos.z + 0.5
            world.spawnParticles(ParticleTypes.ENCHANT, sx, sy + 1.0, sz, 30, 0.5, 0.5, 0.5, 0.8)
            world.spawnParticles(ParticleTypes.END_ROD, sx, sy, sz, 15, 0.4, 0.4, 0.4, 0.03)
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, sx, sy + 0.5, sz, 10, 0.3, 0.3, 0.3, 0.02)

            // Play cry on the recruited mon via animation packet
            SkillEffects.sendAnimationPublic(world, pokemonEntity, "cry")

            // Track for ongoing sparkle effect
            pokemon.entity?.let { recruitedEntities.add(it.id) }

            SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Recruiter] Failed to spawn $speciesName: ${e.message}")
        }
    }

    private fun pickSpeciesByType(world: World, typeName: String, proficiency: Int): String? {
        val typeMap = getOrBuildTypeMap()
        val candidates = typeMap[typeName.lowercase()] ?: return null
        if (candidates.isEmpty()) return null

        // Just pick a random one from the type pool
        // The pool itself naturally contains common and rare species
        // Higher proficiency = we try multiple times and pick the "best" (rarest = later in the list)
        if (proficiency >= 4) {
            // Pick best of 3 random choices (tends toward rarer species)
            val picks = (1..3).map { candidates[world.random.nextInt(candidates.size)] }
            return picks.maxByOrNull { it.length } // rough proxy: longer names tend to be evolutions
        }

        return candidates[world.random.nextInt(candidates.size)]
    }

    private fun getOrBuildTypeMap(): Map<String, List<String>> {
        speciesByType?.let { return it }

        val map = mutableMapOf<String, MutableList<String>>()
        try {
            for (species in PokemonSpecies.species) {
                val name = species.name.lowercase()
                for (type in species.types) {
                    map.getOrPut(type.name.lowercase()) { mutableListOf() }.add(name)
                }
            }
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Recruiter] Failed to build type map: ${e.message}")
        }

        speciesByType = map
        Cobblebase.LOGGER.info("[Recruiter] Built type map: ${map.size} types, ${map.values.sumOf { it.size }} entries")
        return map
    }

    /**
     * Tick sparkle particles on all recruited Pokemon that are still alive.
     */
    private fun tickRecruitedSparkles(world: ServerWorld) {
        val toRemove = mutableSetOf<Int>()
        for (entityId in recruitedEntities) {
            val entity = world.getEntityById(entityId)
            if (entity == null || !entity.isAlive) {
                toRemove.add(entityId)
                continue
            }
            world.spawnParticles(
                ParticleTypes.ENCHANT,
                entity.x, entity.y + entity.height + 0.5, entity.z,
                3, 0.2, 0.3, 0.2, 0.3
            )
        }
        recruitedEntities.removeAll(toRemove)
    }

    private fun findSpawnPos(world: ServerWorld, origin: BlockPos, radius: Int): BlockPos? {
        for (i in 0..10) {
            val x = origin.x + world.random.nextInt(radius * 2) - radius
            val z = origin.z + world.random.nextInt(radius * 2) - radius
            val y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z)
            val pos = BlockPos(x, y, z)

            if (world.getBlockState(pos).isAir && world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
                return pos
            }
        }
        return null
    }
}

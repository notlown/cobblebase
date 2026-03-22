package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.entity.SpawnReason
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.Box
import net.minecraft.util.math.BlockPos
import net.minecraft.world.Heightmap
import net.minecraft.world.World
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import notlown.cobblebase.core.SpawnData
import notlown.cobblebase.core.effects.SkillEffects
import java.util.UUID

/**
 * Friend Recruiter: spawns wild Pokemon of the SAME TYPE as the recruiter.
 * Uses Cobblemon's official spawn bucket data for rarity rates.
 *
 * Proficiency scaling (1-5):
 *   Level 1: base rates (Common 93.8%, Uncommon 5%, Rare 1%, Ultra-Rare 0.2%)
 *   Level 5: uncommon/rare/ultra-rare rates doubled, common reduced to compensate
 */
object RecruiterExecutor : SkillExecutor {

    private val lastRecruitTime = mutableMapOf<UUID, Long>()
    private val recruitedEntities = mutableSetOf<Int>()

    // Cache: type name -> list of species names (built once)
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

        // Sparkle on recruited mons
        if (now % 40 == 0L) {
            tickRecruitedSparkles(world)
        }

        val baseCooldown = if (skill.id == "cobblebase:recruiter")
            CobblebaseConfig.legendaryRecruiterCooldownSeconds
        else
            CobblebaseConfig.friendRecruiterCooldownSeconds
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(baseCooldown, skillEntry.proficiency)

        val lastTime = lastRecruitTime[pokemonId] ?: 0L
        if (now - lastTime < cooldownTicks) {
            if (now % 40 == 0L) {
                SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
            }
            return
        }

        lastRecruitTime[pokemonId] = now

        // Pick a type from the recruiter
        val recruiterTypes = pokemonEntity.pokemon.types.toList()
        if (recruiterTypes.isEmpty()) return
        val chosenType = recruiterTypes[world.random.nextInt(recruiterTypes.size)]

        // Roll for rarity bucket based on proficiency
        val bucket = rollBucket(world, skillEntry.proficiency)

        // Find a species of that type in that bucket
        val speciesName = pickSpecies(world, chosenType.name, bucket) ?: return

        // Spawn next to recruiter
        val spawnPos = findSpawnPos(world, pokemonEntity.blockPos, 3) ?: return

        try {
            val species = PokemonSpecies.getByName(speciesName) ?: return

            // Create a proper wild Pokemon
            val pokemon = Pokemon()
            pokemon.species = species
            val baseLevel = pokemonEntity.pokemon.level
            val level = (baseLevel * 0.6 + world.random.nextInt(10) - 5).toInt().coerceIn(5, baseLevel)
            pokemon.level = level
            pokemon.initialize()

            // Spawn as a real wild entity (not sendOut which is for owned Pokemon)
            val entity = PokemonEntity(world, pokemon)
            entity.refreshPositionAndAngles(
                spawnPos.x + 0.5, spawnPos.y.toDouble(), spawnPos.z + 0.5,
                world.random.nextFloat() * 360f, 0f
            )
            world.spawnEntity(entity)

            Cobblebase.LOGGER.info("[Recruiter] ${pokemonEntity.pokemon.species.name} found a ${bucket.name} $speciesName (Lv.$level) [${chosenType.name}]")

            // Spawn effects
            val sx = spawnPos.x + 0.5; val sy = spawnPos.y + 1.0; val sz = spawnPos.z + 0.5
            world.spawnParticles(ParticleTypes.ENCHANT, sx, sy + 1.0, sz, 30, 0.5, 0.5, 0.5, 0.8)
            world.spawnParticles(ParticleTypes.END_ROD, sx, sy, sz, 15, 0.4, 0.4, 0.4, 0.03)
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, sx, sy + 0.5, sz, 10, 0.3, 0.3, 0.3, 0.02)

            recruitedEntities.add(entity.id)
            SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)

            // Notify nearby players
            val bucketColor = when (bucket) {
                SpawnData.Bucket.ULTRA_RARE -> Formatting.GOLD
                SpawnData.Bucket.RARE -> Formatting.LIGHT_PURPLE
                SpawnData.Bucket.UNCOMMON -> Formatting.GREEN
                else -> Formatting.WHITE
            }
            val bucketLabel = when (bucket) {
                SpawnData.Bucket.ULTRA_RARE -> "Ultra Rare"
                SpawnData.Bucket.RARE -> "Rare"
                SpawnData.Bucket.UNCOMMON -> "Uncommon"
                else -> "Common"
            }
            val message = Text.literal("")
                .append(Text.literal("[Cobblebase] ").formatted(Formatting.AQUA))
                .append(Text.literal("${pokemonEntity.pokemon.species.name}").formatted(Formatting.YELLOW))
                .append(Text.literal(" found a ").formatted(Formatting.GRAY))
                .append(Text.literal(bucketLabel).formatted(bucketColor, Formatting.BOLD))
                .append(Text.literal(" $speciesName").formatted(Formatting.WHITE, Formatting.BOLD))
                .append(Text.literal(" (Lv.$level)!").formatted(Formatting.GRAY))

            val nearbyPlayers = world.getEntitiesByClass(ServerPlayerEntity::class.java, Box.of(pokemonEntity.pos, 128.0, 128.0, 128.0)) { true }
            for (player in nearbyPlayers) {
                player.sendMessage(message, false)
                // Extra ding sound for rare+
                if (bucket.ordinal >= SpawnData.Bucket.RARE.ordinal) {
                    player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f)
                }
            }
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Recruiter] Failed to spawn $speciesName: ${e.message}")
        }
    }

    /**
     * Roll which rarity bucket to spawn from.
     * Proficiency 1 = base rates. Proficiency 5 = rare rates doubled.
     * Scale factor: 1.0 at prof 1, up to 2.0 at prof 5.
     */
    private fun rollBucket(world: World, proficiency: Int): SpawnData.Bucket {
        val scale = 1.0 + (proficiency - 1) * 0.25 // 1.0, 1.25, 1.5, 1.75, 2.0

        val ultraRare = CobblebaseConfig.recruiterUltraRareRate * scale
        val rare = CobblebaseConfig.recruiterRareRate * scale
        val uncommon = CobblebaseConfig.recruiterUncommonRate * scale
        // Common gets whatever is left
        val total = ultraRare + rare + uncommon
        val common = (100.0 - total).coerceAtLeast(0.0)

        val roll = world.random.nextDouble() * 100.0

        return when {
            roll < ultraRare -> SpawnData.Bucket.ULTRA_RARE
            roll < ultraRare + rare -> SpawnData.Bucket.RARE
            roll < ultraRare + rare + uncommon -> SpawnData.Bucket.UNCOMMON
            else -> SpawnData.Bucket.COMMON
        }
    }

    /**
     * Pick a random species of the given type that matches the target bucket.
     * Falls back to any bucket if no match found.
     */
    private fun pickSpecies(world: World, typeName: String, targetBucket: SpawnData.Bucket): String? {
        val typeMap = getOrBuildTypeMap()
        val candidates = typeMap[typeName.lowercase()] ?: return null
        if (candidates.isEmpty()) return null

        // Filter by bucket
        val matching = candidates.filter { SpawnData.getBucket(it) == targetBucket }
        if (matching.isNotEmpty()) {
            return matching[world.random.nextInt(matching.size)]
        }

        // Fallback: try lower buckets
        for (fallback in SpawnData.Bucket.entries.reversed()) {
            val fallbackMatching = candidates.filter { SpawnData.getBucket(it) == fallback }
            if (fallbackMatching.isNotEmpty()) {
                return fallbackMatching[world.random.nextInt(fallbackMatching.size)]
            }
        }

        return candidates[world.random.nextInt(candidates.size)]
    }

    private fun getOrBuildTypeMap(): Map<String, List<String>> {
        speciesByType?.let { return it }

        val map = mutableMapOf<String, MutableList<String>>()
        try {
            for (species in PokemonSpecies.species) {
                val name = species.name.lowercase()
                // Only include Pokemon that exist in our spawn CSV (confirmed in Cobblemon)
                if (!SpawnData.exists(name)) continue
                for (type in species.types) {
                    map.getOrPut(type.name.lowercase()) { mutableListOf() }.add(name)
                }
            }
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Recruiter] Failed to build type map: ${e.message}")
        }

        speciesByType = map
        Cobblebase.LOGGER.info("[Recruiter] Type map: ${map.size} types, ${map.values.sumOf { it.size }} entries")
        return map
    }

    private fun tickRecruitedSparkles(world: ServerWorld) {
        val toRemove = mutableSetOf<Int>()
        for (entityId in recruitedEntities) {
            val entity = world.getEntityById(entityId)
            if (entity == null || !entity.isAlive) {
                toRemove.add(entityId)
                continue
            }
            world.spawnParticles(ParticleTypes.ENCHANT, entity.x, entity.y + entity.height + 0.5, entity.z, 3, 0.2, 0.3, 0.2, 0.3)
        }
        recruitedEntities.removeAll(toRemove)
    }

    private fun findSpawnPos(world: ServerWorld, origin: BlockPos, radius: Int): BlockPos? {
        for (i in 0..10) {
            val x = origin.x + world.random.nextInt(radius * 2 + 1) - radius
            val z = origin.z + world.random.nextInt(radius * 2 + 1) - radius
            val y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z)
            val pos = BlockPos(x, y, z)
            if (world.getBlockState(pos).isAir && world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
                return pos
            }
        }
        return null
    }
}

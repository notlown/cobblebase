package notlown.cobblebase.core.executors

import notlown.cobblebase.core.LogManager
import com.cobblemon.mod.common.CobblemonEntities
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
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
    private val pendingCry = mutableMapOf<Int, Long>() // entityId -> tick when to cry

    // Cache: type name -> list of species names (built once)
    private var speciesByType: Map<String, List<String>>? = null
    // Cache: all legendary/mythical/ultra beast species (for Prof 5 recruiter)
    private var legendarySpecies: List<String>? = null

    override fun tick(
        world: World,
        origin: BlockPos,
        pokemonEntity: PokemonEntity,
        skill: SkillDef,
        skillEntry: SkillEntry
    ) {
        if (world !is ServerWorld) return
        val now = world.time

        // Sparkle on recruited mons (every 2 seconds)
        if (now % 40 == 0L) {
            tickRecruitedSparkles(world)
        }

        // Throttle: Recruiter has minute-long cooldowns, no need to check every tick
        if (now % 20L != 0L) return

        val pokemonId = pokemonEntity.pokemon.uuid

        // Prof 5 = Legendary Recruiter with much higher cooldown (30 min base, no prof reduction)
        val isLegendaryMode = skillEntry.proficiency >= 5
        val cooldownTicks = if (isLegendaryMode) {
            LEGENDARY_COOLDOWN_SECONDS * 20L // Fixed, no proficiency scaling
        } else {
            CobblebaseConfig.getEffectiveCooldownTicks(skill.cooldownSeconds, skillEntry.proficiency)
        }

        val lastTime = lastRecruitTime[pokemonId] ?: now.also { lastRecruitTime[pokemonId] = now }
        if (now - lastTime < cooldownTicks) {
            if (now % 40 == 0L) {
                SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
            }
            return
        }

        val speciesName: String
        val bucket: SpawnData.Bucket
        val chosenType: com.cobblemon.mod.common.api.types.ElementalType

        if (isLegendaryMode) {
            // Prof 5: summon a random legendary regardless of type
            val legendaries = getOrBuildLegendaryList()
            if (legendaries.isEmpty()) return
            speciesName = legendaries[world.random.nextInt(legendaries.size)]
            bucket = SpawnData.Bucket.ULTRA_RARE
            chosenType = pokemonEntity.pokemon.types.first()
        } else {
            // Normal recruiter: pick by type + rarity
            val recruiterTypes = pokemonEntity.pokemon.types.toList()
            if (recruiterTypes.isEmpty()) return
            chosenType = recruiterTypes[world.random.nextInt(recruiterTypes.size)]
            bucket = rollBucket(world, skillEntry.proficiency)
            val typeKey = chosenType.name
            speciesName = pickSpecies(world, typeKey, bucket) ?: run {
                // Don't reset cooldown on failed species pick — retry sooner
                val typeMap = getOrBuildTypeMap()
                val availableForType = typeMap[typeKey.lowercase()]?.size ?: 0
                Cobblebase.LOGGER.warn("[Recruiter] ${pokemonEntity.pokemon.species.name} (type=$typeKey) failed to find ${bucket.name} species. Available ${typeKey.lowercase()} species in map: $availableForType, total types: ${typeMap.size}")
                return
            }
        }

        // Spawn near the pasture block — water-type recruiters can also spawn in/on water
        val isWaterRecruiter = pokemonEntity.pokemon.types.any { it.name.lowercase() == "water" }
        val spawnPos = findSpawnPos(world, origin, 5, isWaterRecruiter) ?: run {
            // Don't reset cooldown on failed spawn position — retry sooner
            Cobblebase.log("[Recruiter] ${pokemonEntity.pokemon.species.name} failed to find spawn position near $origin")
            return
        }

        try {
            val species = PokemonSpecies.getByName(speciesName) ?: run {
                Cobblebase.log("[Recruiter] ${pokemonEntity.pokemon.species.name} species '$speciesName' not found in registry")
                return
            }

            // Only reset cooldown AFTER all validation passed
            lastRecruitTime[pokemonId] = now

            // Create a proper wild Pokemon
            val pokemon = Pokemon()
            pokemon.species = species
            val baseLevel = pokemonEntity.pokemon.level
            val minLevel = minOf(5, baseLevel)
            val level = (baseLevel * 0.6 + world.random.nextInt(10) - 5).toInt().coerceIn(minLevel, maxOf(baseLevel, minLevel))
            pokemon.level = level
            pokemon.initialize()

            // Create a proper wild Pokemon entity using Cobblemon's entity type
            val entity = PokemonEntity(world, pokemon, CobblemonEntities.POKEMON)
            entity.refreshPositionAndAngles(
                spawnPos.x + 0.5, spawnPos.y.toDouble(), spawnPos.z + 0.5,
                world.random.nextFloat() * 360f, 0f
            )
            // Mark as wild spawn so Cobblemon treats it normally
            entity.countsTowardsSpawnCap = false
            entity.tickSpawned = world.server?.ticks ?: 0
            world.spawnEntity(entity)

            Cobblebase.log("[Recruiter] ${pokemonEntity.pokemon.species.name} found a ${bucket.name} $speciesName (Lv.$level) [${chosenType.name}]")

            // Track for sparkles and delayed cry (entity needs a tick to sync to clients)
            recruitedEntities.add(entity.id)
            pendingCry[entity.id] = world.time + 20L // cry after 1 second

            // Notify nearby players
            val bucketColor: Formatting
            val bucketLabel: String
            if (isLegendaryMode) {
                bucketColor = Formatting.LIGHT_PURPLE
                bucketLabel = "Legendary"
            } else {
                bucketColor = if (bucket == SpawnData.Bucket.ULTRA_RARE) Formatting.GOLD
                    else if (bucket == SpawnData.Bucket.RARE) Formatting.LIGHT_PURPLE
                    else if (bucket == SpawnData.Bucket.UNCOMMON) Formatting.GREEN
                    else Formatting.WHITE
                bucketLabel = if (bucket == SpawnData.Bucket.ULTRA_RARE) "Ultra Rare"
                    else if (bucket == SpawnData.Bucket.RARE) "Rare"
                    else if (bucket == SpawnData.Bucket.UNCOMMON) "Uncommon"
                    else "Common"
            }
            val message = Text.literal("")
                .append(Text.literal("[Cobblebase] ").formatted(Formatting.AQUA))
                .append(Text.literal("${pokemonEntity.pokemon.species.name}").formatted(Formatting.YELLOW))
                .append(Text.literal(if (isLegendaryMode) " summoned a " else " found a ").formatted(Formatting.GRAY))
                .append(Text.literal(bucketLabel).formatted(bucketColor, Formatting.BOLD))
                .append(Text.literal(" $speciesName").formatted(Formatting.WHITE, Formatting.BOLD))
                .append(Text.literal(" (Lv.$level)!").formatted(Formatting.GRAY))

            // Send message only to the owner — no fallback
            val ownerUuid = pokemonEntity.pokemon.getOwnerUUID() ?: return
            val targetPlayers = world.players.filter { it.uuid == ownerUuid }
            for (player in targetPlayers) {
                player.sendMessage(message, false)
                if (isLegendaryMode || bucket.ordinal >= SpawnData.Bucket.RARE.ordinal) {
                    player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f)
                }
            }

            // Log to activity log
            val logRarity = if (bucket == SpawnData.Bucket.ULTRA_RARE) LogManager.Rarity.ULTRA_RARE
                else if (bucket == SpawnData.Bucket.RARE) LogManager.Rarity.RARE
                else if (bucket == SpawnData.Bucket.UNCOMMON) LogManager.Rarity.UNCOMMON
                else LogManager.Rarity.COMMON
            LogManager.log(origin, world.time, pokemonEntity.pokemon.species.name, "Recruited", "$speciesName (Lv.$level)", logRarity)
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

        // Filter by bucket (species with spawn data)
        val matching = candidates.filter { SpawnData.getBucket(it) == targetBucket }
        if (matching.isNotEmpty()) {
            return matching[world.random.nextInt(matching.size)]
        }

        // Fallback: try MORE COMMON buckets first (never fall UP to rarer)
        for (fallback in SpawnData.Bucket.entries) {
            if (fallback.ordinal > targetBucket.ordinal) continue
            val fallbackMatching = candidates.filter { SpawnData.getBucket(it) == fallback }
            if (fallbackMatching.isNotEmpty()) {
                return fallbackMatching[world.random.nextInt(fallbackMatching.size)]
            }
        }

        // Final fallback: pick any random species of this type (includes fakemons without spawn data)
        return candidates[world.random.nextInt(candidates.size)]
    }

    private val EXCLUDED_LABELS = setOf("legendary", "mythical", "ultra_beast")
    private const val LEGENDARY_COOLDOWN_SECONDS = 1800L // 30 minutes — no proficiency reduction

    /**
     * Build or return cached list of all legendary/mythical/ultra beast species.
     * Used by Prof 5 recruiter to summon legendaries regardless of type.
     */
    private fun getOrBuildLegendaryList(): List<String> {
        legendarySpecies?.let { return it }

        val list = mutableListOf<String>()
        try {
            for (species in PokemonSpecies.species) {
                if (species.labels.any { it.lowercase() in EXCLUDED_LABELS }) {
                    list.add(species.name.lowercase())
                }
            }
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Recruiter] Failed to build legendary list: ${e.message}")
        }

        legendarySpecies = list
        Cobblebase.log("[Recruiter] Legendary list: ${list.size} species")
        return list
    }

    private var typeMapBuiltAt: Long = 0L

    private fun getOrBuildTypeMap(): Map<String, List<String>> {
        // Rebuild every 5 minutes to pick up late-loaded species from addon mods
        val now = System.currentTimeMillis()
        if (speciesByType != null && now - typeMapBuiltAt < 300_000L) {
            return speciesByType!!
        }

        val map = mutableMapOf<String, MutableList<String>>()
        var skipped = 0
        try {
            // Use ALL loaded Cobblemon species (includes installed fakemon packs)
            // but exclude legendaries, mythicals, and ultra beasts
            for (species in PokemonSpecies.species) {
                if (species.labels.any { it.lowercase() in EXCLUDED_LABELS }) {
                    skipped++
                    continue
                }
                val name = species.name.lowercase()
                for (type in species.types) {
                    map.getOrPut(type.name.lowercase()) { mutableListOf() }.add(name)
                }
            }
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Recruiter] Failed to build type map: ${e.message}")
        }

        speciesByType = map
        typeMapBuiltAt = now
        // Also rebuild legendary list
        legendarySpecies = null
        Cobblebase.log("[Recruiter] Type map: ${map.size} types, ${map.values.sumOf { it.size }} entries (excluded $skipped legendary/mythical/ultra beast)")
        return map
    }

    private fun tickRecruitedSparkles(world: ServerWorld) {
        val toRemove = mutableSetOf<Int>()
        for (entityId in recruitedEntities) {
            val entity = world.getEntityById(entityId)
            if (entity == null || !entity.isAlive) {
                toRemove.add(entityId)
                pendingCry.remove(entityId)
                continue
            }
            // Delayed cry - wait for entity to sync to clients
            val cryTick = pendingCry[entityId]
            if (cryTick != null && world.time >= cryTick && entity is PokemonEntity) {
                SkillEffects.sendAnimationPublic(world, entity, "cry")
                pendingCry.remove(entityId)
            }
        }
        recruitedEntities.removeAll(toRemove)
    }

    private fun findSpawnPos(world: ServerWorld, origin: BlockPos, radius: Int, isWaterType: Boolean = false): BlockPos? {
        for (i in 0..10) {
            val x = origin.x + world.random.nextInt(radius * 2 + 1) - radius
            val z = origin.z + world.random.nextInt(radius * 2 + 1) - radius
            val y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z)
            val pos = BlockPos(x, y, z)

            // Standard ground spawn: air above solid block
            if (world.getBlockState(pos).isAir && world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
                return pos
            }

            // Water spawn: for water-type recruiters, allow spawning in/on water
            if (isWaterType) {
                val below = world.getBlockState(pos.down())
                // Spawn on water surface (air above water)
                if (world.getBlockState(pos).isAir && below.fluidState.isStill) {
                    return pos
                }
                // Spawn in water (water block with more water or solid below)
                if (world.getBlockState(pos).fluidState.isStill) {
                    return pos
                }
                // Also check at origin Y level for underwater pastures
                val underwaterPos = BlockPos(x, origin.y, z)
                if (world.getBlockState(underwaterPos).fluidState.isStill) {
                    return underwaterPos
                }
            }
        }
        return null
    }
}

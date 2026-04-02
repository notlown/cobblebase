package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import net.minecraft.world.biome.BiomeKeys
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.DiscoveryRegistry
import notlown.cobblebase.core.LogManager
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import notlown.cobblebase.core.effects.SkillEffects
import notlown.cobblebase.core.NavigationHelper
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Scout: Pokemon explores the area to discover wild Pokemon, structures, and biomes.
 * Cooldown-based — each tick past cooldown runs a scout action.
 *
 * Discovery types unlock by proficiency:
 *   Prof 1-2: Wild Pokemon only
 *   Prof 3+: Wild Pokemon + Structures
 *   Prof 4+: All types (+ Biomes)
 *
 * Proficiency also affects search radius and cooldown.
 */
object ScoutExecutor : SkillExecutor {

    private val lastScoutTime = mutableMapOf<UUID, Long>()

    // Structure tag keys for locateStructure
    private val STRUCTURE_TAGS = listOf(
        TagKey.of(RegistryKeys.STRUCTURE, Identifier.ofVanilla("village")),
        TagKey.of(RegistryKeys.STRUCTURE, Identifier.ofVanilla("mineshaft")),
        TagKey.of(RegistryKeys.STRUCTURE, Identifier.ofVanilla("ruined_portal")),
        TagKey.of(RegistryKeys.STRUCTURE, Identifier.ofVanilla("shipwreck")),
        TagKey.of(RegistryKeys.STRUCTURE, Identifier.ofVanilla("ocean_ruin"))
    )

    private val STRUCTURE_DISPLAY_NAMES = mapOf(
        "village" to "Village",
        "mineshaft" to "Mineshaft",
        "ruined_portal" to "Ruined Portal",
        "shipwreck" to "Shipwreck",
        "ocean_ruin" to "Ocean Ruin"
    )

    // Biomes to search for (interesting/rare ones)
    private val INTERESTING_BIOMES = listOf(
        BiomeKeys.MUSHROOM_FIELDS,
        BiomeKeys.ICE_SPIKES,
        BiomeKeys.BAMBOO_JUNGLE,
        BiomeKeys.CHERRY_GROVE,
        BiomeKeys.DEEP_DARK,
        BiomeKeys.LUSH_CAVES
    )

    private val BIOME_DISPLAY_NAMES = mapOf(
        "mushroom_fields" to "Mushroom Fields",
        "ice_spikes" to "Ice Spikes",
        "bamboo_jungle" to "Bamboo Jungle",
        "cherry_grove" to "Cherry Grove",
        "deep_dark" to "Deep Dark",
        "lush_caves" to "Lush Caves"
    )

    private const val MAX_DISTANCE_FROM_PASTURE = 5.0
    private const val TELEPORT_DISTANCE = 10.0

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

        // Safety: keep Scout near the pasture — teleport back if too far
        val distFromOrigin = sqrt(
            pokemonEntity.squaredDistanceTo(
                origin.x + 0.5, origin.y.toDouble(), origin.z + 0.5
            )
        )
        if (distFromOrigin > TELEPORT_DISTANCE) {
            pokemonEntity.setPosition(origin.x + 0.5, origin.y + 1.0, origin.z + 0.5)
            NavigationHelper.clearTargets(pokemonEntity)
            Cobblebase.LOGGER.info("[Scout] ${pokemonEntity.pokemon.species.name} was too far (${distFromOrigin.toInt()} blocks) — teleported back to pasture")
        } else if (distFromOrigin > MAX_DISTANCE_FROM_PASTURE) {
            // Gently walk back towards pasture
            NavigationHelper.navigateTo(pokemonEntity, origin, 0.6)
        }

        // Cooldown: proficiency reduces cooldown
        // Prof 1: 300s, Prof 2: 240s, Prof 3: 180s, Prof 4: 180s, Prof 5: 150s
        val baseCooldown = skill.cooldownSeconds
        val cooldownTicks = if (CobblebaseConfig.devMode) 100L
            else (baseCooldown * 20L * (7 - skillEntry.proficiency) / 4)

        val lastTime = lastScoutTime[pokemonId] ?: now.also { lastScoutTime[pokemonId] = now }
        if (now - lastTime < cooldownTicks) {
            // Working particles — lookout effect (play frequently so it looks active)
            if (now % 40 == 0L) {
                SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
                // "Looking around" animation
                SkillEffects.sendAnimationPublic(world, pokemonEntity, "cry", "special")
                // Extra eye particles for "searching"
                val x = pokemonEntity.x
                val y = pokemonEntity.y + pokemonEntity.height.toDouble()
                val z = pokemonEntity.z
                world.spawnParticles(ParticleTypes.ENCHANT, x, y + 0.5, z, 5, 0.3, 0.3, 0.3, 0.5)
                world.spawnParticles(ParticleTypes.END_ROD, x, y + 0.8, z, 3, 0.5, 0.3, 0.5, 0.02)
            }
            return
        }

        lastScoutTime[pokemonId] = now

        // Cleanup old sightings periodically
        if (now % 1200 == 0L) {
            DiscoveryRegistry.cleanupOldSightings()
        }

        // Search radius based on proficiency
        val searchRadius = getSearchRadius(skillEntry.proficiency)
        val scoutName = pokemonEntity.pokemon.species.name

        // Roll for discovery type based on proficiency
        val roll = world.random.nextInt(100)
        val prof = skillEntry.proficiency

        // Prof 1-2: only wild Pokemon
        // Prof 3: 80% wild, 20% structure
        // Prof 4: 65% wild, 25% structure, 10% biome
        // Prof 5: 55% wild, 30% structure, 15% biome
        val structureChance = if (prof >= 3) (prof - 2) * 10 else 0  // 0, 0, 10, 20, 30
        val biomeChance = if (prof >= 4) (prof - 3) * 10 else 0       // 0, 0, 0, 10, 20 -- adjusted: 0,0,0,5,15

        val biomeThreshold = if (prof >= 4) (prof - 3) * 5 else 0
        val structureThreshold = biomeThreshold + structureChance

        val success = when {
            roll < biomeThreshold -> scoutBiome(world, pokemonEntity, origin, searchRadius, scoutName)
            roll < structureThreshold -> scoutStructure(world, pokemonEntity, origin, searchRadius, scoutName)
            else -> scoutWildPokemon(world, pokemonEntity, origin, searchRadius, scoutName)
        }

        if (success) {
            SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
            // "Look around" animation
            SkillEffects.sendAnimationPublic(world, pokemonEntity, "cry", "special")
        }
    }

    /**
     * Search for wild Pokemon entities nearby.
     */
    private fun scoutWildPokemon(
        world: ServerWorld,
        pokemonEntity: PokemonEntity,
        origin: BlockPos,
        searchRadius: Int,
        scoutName: String
    ): Boolean {
        val pos = pokemonEntity.pos
        val searchBox = Box(
            pos.x - searchRadius, pos.y - searchRadius / 2.0, pos.z - searchRadius,
            pos.x + searchRadius, pos.y + searchRadius / 2.0, pos.z + searchRadius
        )

        val wildPokemon = world.getEntitiesByClass(PokemonEntity::class.java, searchBox) { entity ->
            // Only wild Pokemon (no owner), not the scout itself, and alive
            entity.pokemon.getOwnerPlayer() == null &&
                entity.id != pokemonEntity.id &&
                entity.isAlive
        }

        if (wildPokemon.isEmpty()) return false

        // Pick a random wild Pokemon that hasn't been reported recently
        val unreported = wildPokemon.filter { entity ->
            !DiscoveryRegistry.isAlreadyDiscovered(
                entity.blockX, entity.blockZ, entity.pokemon.species.name
            )
        }

        val target = if (unreported.isNotEmpty()) {
            unreported[world.random.nextInt(unreported.size)]
        } else {
            return false
        }

        val targetName = target.pokemon.species.name
        val targetLevel = target.pokemon.level
        val targetX = target.blockX
        val targetZ = target.blockZ

        // Determine rarity based on Pokemon level — skip commons entirely
        val rarity = when {
            targetLevel >= 60 -> LogManager.Rarity.ULTRA_RARE
            targetLevel >= 45 -> LogManager.Rarity.RARE
            targetLevel >= 25 -> LogManager.Rarity.UNCOMMON
            else -> return false // Skip common Pokemon — nobody cares about Lv.5 Rattata
        }

        val discovery = DiscoveryRegistry.Discovery(
            type = DiscoveryRegistry.DiscoveryType.WILD_POKEMON,
            name = "$targetName (Lv.$targetLevel)",
            x = targetX, y = target.blockY, z = targetZ,
            discoveredBy = scoutName,
            timestamp = System.currentTimeMillis(),
            rarity = rarity
        )

        if (!DiscoveryRegistry.addDiscovery(discovery)) return false

        // Log
        LogManager.log(
            origin, world.time,
            scoutName, "Spotted",
            "$targetName Lv.$targetLevel at ($targetX, $targetZ)",
            rarity
        )

        // Chat notification with clickable coordinates
        val rarityColor = when (rarity) {
            LogManager.Rarity.ULTRA_RARE -> Formatting.GOLD
            LogManager.Rarity.RARE -> Formatting.BLUE
            else -> Formatting.GREEN
        }
        val coordText = Text.literal("[$targetX, $targetZ]")
            .styled { it
                .withColor(Formatting.AQUA)
                .withUnderline(true)
                .withClickEvent(net.minecraft.text.ClickEvent(net.minecraft.text.ClickEvent.Action.SUGGEST_COMMAND, "/tp @s $targetX ~ $targetZ"))
                .withHoverEvent(net.minecraft.text.HoverEvent(net.minecraft.text.HoverEvent.Action.SHOW_TEXT, Text.literal("Click to copy /tp command")))
            }

        val message = Text.literal("")
            .append(Text.literal("[Scout] ").formatted(Formatting.AQUA))
            .append(Text.literal(scoutName).formatted(Formatting.YELLOW))
            .append(Text.literal(" spotted a wild ").formatted(Formatting.GRAY))
            .append(Text.literal(targetName).formatted(rarityColor, Formatting.BOLD))
            .append(Text.literal(" (Lv.$targetLevel) ").formatted(Formatting.GRAY))
            .append(Text.literal("at ").formatted(Formatting.GRAY))
            .append(coordText)

        notifyNearbyPlayers(world, pokemonEntity, message)
        return true
    }

    /**
     * Search for nearby structures using world.getChunkManager().locateStructure().
     */
    private fun scoutStructure(
        world: ServerWorld,
        pokemonEntity: PokemonEntity,
        origin: BlockPos,
        searchRadius: Int,
        scoutName: String
    ): Boolean {
        val pos = pokemonEntity.blockPos

        // Pick a random structure tag to search for
        val shuffledTags = STRUCTURE_TAGS.toList().shuffled()

        for (tag in shuffledTags) {
            try {
                val result = world.locateStructure(
                    tag, pos, searchRadius / 16, false
                )
                if (result != null) {
                    val structX = result.x
                    val structZ = result.z

                    // Get display name from tag
                    val tagName = tag.id.path
                    val displayName = STRUCTURE_DISPLAY_NAMES[tagName] ?: tagName.replace("_", " ")
                        .replaceFirstChar { it.uppercase() }

                    if (DiscoveryRegistry.isAlreadyDiscovered(structX, structZ, displayName)) continue

                    val discovery = DiscoveryRegistry.Discovery(
                        type = DiscoveryRegistry.DiscoveryType.STRUCTURE,
                        name = displayName,
                        x = structX, y = 64, z = structZ,
                        discoveredBy = scoutName,
                        timestamp = System.currentTimeMillis(),
                        rarity = LogManager.Rarity.RARE
                    )

                    if (!DiscoveryRegistry.addDiscovery(discovery)) continue

                    // Log
                    LogManager.log(
                        origin, world.time,
                        scoutName, "Discovered",
                        "$displayName at ($structX, $structZ)",
                        LogManager.Rarity.RARE
                    )

                    // Chat notification with clickable coordinates
                    val structCoord = Text.literal("[$structX, $structZ]")
                        .styled { it
                            .withColor(Formatting.AQUA)
                            .withUnderline(true)
                            .withClickEvent(net.minecraft.text.ClickEvent(net.minecraft.text.ClickEvent.Action.SUGGEST_COMMAND, "/tp @s $structX ~ $structZ"))
                            .withHoverEvent(net.minecraft.text.HoverEvent(net.minecraft.text.HoverEvent.Action.SHOW_TEXT, Text.literal("Click to copy /tp command")))
                        }
                    val message = Text.literal("")
                        .append(Text.literal("[Scout] ").formatted(Formatting.AQUA))
                        .append(Text.literal(scoutName).formatted(Formatting.YELLOW))
                        .append(Text.literal(" discovered a ").formatted(Formatting.GRAY))
                        .append(Text.literal(displayName).formatted(Formatting.BLUE, Formatting.BOLD))
                        .append(Text.literal(" at ").formatted(Formatting.GRAY))
                        .append(structCoord)

                    notifyNearbyPlayers(world, pokemonEntity, message)
                    return true
                }
            } catch (e: Exception) {
                Cobblebase.LOGGER.debug("[Scout] Error locating structure: ${e.message}")
            }
        }

        // Fallback: try wild Pokemon instead
        return scoutWildPokemon(world, pokemonEntity, origin, searchRadius, scoutName)
    }

    /**
     * Search for interesting biomes nearby.
     */
    private fun scoutBiome(
        world: ServerWorld,
        pokemonEntity: PokemonEntity,
        origin: BlockPos,
        searchRadius: Int,
        scoutName: String
    ): Boolean {
        val pos = pokemonEntity.blockPos

        // Pick a random interesting biome to search for
        val shuffledBiomes = INTERESTING_BIOMES.shuffled(java.util.Random(world.random.nextLong()))

        for (biomeKey in shuffledBiomes) {
            try {
                val biomeName = biomeKey.value.path
                val displayName = BIOME_DISPLAY_NAMES[biomeName] ?: biomeName.replace("_", " ")
                    .replaceFirstChar { it.uppercase() }

                val result = world.locateBiome(
                    { it.matchesKey(biomeKey) },
                    pos, searchRadius, 32, 64
                )

                if (result != null) {
                    val biomePos = result.getFirst()
                    val bx = biomePos.x
                    val bz = biomePos.z

                    if (DiscoveryRegistry.isAlreadyDiscovered(bx, bz, displayName)) continue

                    val discovery = DiscoveryRegistry.Discovery(
                        type = DiscoveryRegistry.DiscoveryType.BIOME,
                        name = displayName,
                        x = bx, y = biomePos.y, z = bz,
                        discoveredBy = scoutName,
                        timestamp = System.currentTimeMillis(),
                        rarity = LogManager.Rarity.ULTRA_RARE
                    )

                    if (!DiscoveryRegistry.addDiscovery(discovery)) continue

                    // Log
                    LogManager.log(
                        origin, world.time,
                        scoutName, "Discovered",
                        "$displayName biome at ($bx, $bz)",
                        LogManager.Rarity.ULTRA_RARE
                    )

                    // Chat notification with clickable coordinates
                    val biomeCoord = Text.literal("[$bx, $bz]")
                        .styled { it
                            .withColor(Formatting.AQUA)
                            .withUnderline(true)
                            .withClickEvent(net.minecraft.text.ClickEvent(net.minecraft.text.ClickEvent.Action.SUGGEST_COMMAND, "/tp @s $bx ~ $bz"))
                            .withHoverEvent(net.minecraft.text.HoverEvent(net.minecraft.text.HoverEvent.Action.SHOW_TEXT, Text.literal("Click to copy /tp command")))
                        }
                    val message = Text.literal("")
                        .append(Text.literal("[Scout] ").formatted(Formatting.AQUA))
                        .append(Text.literal(scoutName).formatted(Formatting.YELLOW))
                        .append(Text.literal(" discovered ").formatted(Formatting.GRAY))
                        .append(Text.literal("$displayName biome").formatted(Formatting.GOLD, Formatting.BOLD))
                        .append(Text.literal(" at ").formatted(Formatting.GRAY))
                        .append(biomeCoord)

                    notifyNearbyPlayers(world, pokemonEntity, message)

                    // Extra ding for ultra rare
                    val nearbyPlayers = world.getEntitiesByClass(
                        ServerPlayerEntity::class.java,
                        Box.of(pokemonEntity.pos, 128.0, 128.0, 128.0)
                    ) { true }
                    for (player in nearbyPlayers) {
                        player.playSound(
                            net.minecraft.sound.SoundEvents.ENTITY_PLAYER_LEVELUP,
                            0.5f, 1.5f
                        )
                    }

                    return true
                }
            } catch (e: Exception) {
                Cobblebase.LOGGER.debug("[Scout] Error locating biome: ${e.message}")
            }
        }

        // Fallback: try structure, then wild Pokemon
        return scoutStructure(world, pokemonEntity, origin, searchRadius, scoutName)
    }

    private fun getSearchRadius(proficiency: Int): Int {
        return when (proficiency) {
            1 -> 50
            2 -> 100
            3 -> 150
            4 -> 200
            else -> 200
        }
    }

    /**
     * Calculate compass direction from one position to another.
     */
    private fun getDirection(fromX: Double, fromZ: Double, toX: Double, toZ: Double): String {
        val dx = toX - fromX
        val dz = toZ - fromZ
        val angle = Math.toDegrees(atan2(dz, dx))
        // MC: +X = East, +Z = South
        // angle 0 = East, 90 = South, -90 = North, 180/-180 = West
        return when {
            angle >= -22.5 && angle < 22.5 -> "East"
            angle >= 22.5 && angle < 67.5 -> "SE"
            angle >= 67.5 && angle < 112.5 -> "South"
            angle >= 112.5 && angle < 157.5 -> "SW"
            angle >= 157.5 || angle < -157.5 -> "West"
            angle >= -157.5 && angle < -112.5 -> "NW"
            angle >= -112.5 && angle < -67.5 -> "North"
            angle >= -67.5 && angle < -22.5 -> "NE"
            else -> "nearby"
        }
    }

    private fun notifyNearbyPlayers(world: ServerWorld, pokemonEntity: PokemonEntity, message: Text) {
        val nearbyPlayers = world.getEntitiesByClass(
            ServerPlayerEntity::class.java,
            Box.of(pokemonEntity.pos, 128.0, 128.0, 128.0)
        ) { true }
        for (player in nearbyPlayers) {
            player.sendMessage(message, false)
        }
    }
}

package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import java.util.UUID

/**
 * Lucky Charm: follows the pasture OWNER wherever they are on the map
 * and boosts the shiny chance of wild Pokemon near them.
 *
 * Each wild Pokemon is only processed once (tracked by UUID).
 * Prof 1: 1.4x shiny rate, Prof 5: 3.0x shiny rate.
 *
 * Default Cobblemon shiny rate is 1/8192.
 * At 3x that becomes 3/8192 (~1/2731).
 */
object LuckyCharmExecutor : SkillExecutor {

    // Track which wild Pokemon we've already processed (UUID set)
    private val processedPokemon = mutableSetOf<UUID>()
    private val lastCleanup = mutableMapOf<UUID, Long>()

    // Base shiny chance is 1/8192
    private const val BASE_SHINY_CHANCE = 1.0 / 8192.0

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

        // Only check every 2 seconds (40 ticks)
        if (now % 40 != 0L) return

        // Cleanup processed set every 5 minutes to prevent memory leak
        val lastClean = lastCleanup[pokemonId] ?: 0L
        if (now - lastClean > 6000L) {
            processedPokemon.clear()
            lastCleanup[pokemonId] = now
        }

        val prof = skillEntry.proficiency.coerceIn(1, 5)
        val shinyMultiplier = getShinyMultiplier(prof)
        val range = getRange(prof)

        // Always scan around the OWNER player (not the pasture)
        val ownerUuid = pokemonEntity.pokemon.getOwnerUUID() ?: return
        val owner = world.players.find { it.uuid == ownerUuid } ?: return
        val searchBox = Box.of(owner.pos, range * 2, range * 2, range * 2)
        val wildMons = world.getEntitiesByClass(PokemonEntity::class.java, searchBox) { entity ->
            entity.pokemon.isWild() && entity.isAlive && !entity.pokemon.shiny
                && entity.pokemon.uuid !in processedPokemon
        }

        for (wildMon in wildMons) {
            processedPokemon.add(wildMon.pokemon.uuid)

            // Roll for shiny with boosted odds
            // Extra chance = (multiplier - 1) * base chance
            // We only add the BONUS chance since the mon already had its initial roll
            val bonusChance = (shinyMultiplier - 1.0) * BASE_SHINY_CHANCE
            if (world.random.nextDouble() < bonusChance) {
                wildMon.pokemon.shiny = true

                // Visual celebration
                val sx = wildMon.x; val sy = wildMon.y + 1.0; val sz = wildMon.z
                world.spawnParticles(ParticleTypes.END_ROD, sx, sy, sz, 30, 0.5, 0.8, 0.5, 0.05)
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, sx, sy + 0.5, sz, 15, 0.4, 0.4, 0.4, 0.02)

                // Notify nearby players
                val speciesName = wildMon.pokemon.species.name
                val message = Text.literal("")
                    .append(Text.literal("[Cobblebase] ").formatted(Formatting.AQUA))
                    .append(Text.literal("${pokemonEntity.pokemon.species.name}").formatted(Formatting.YELLOW))
                    .append(Text.literal("'s Lucky Charm turned a wild ").formatted(Formatting.GRAY))
                    .append(Text.literal(speciesName).formatted(Formatting.GOLD, Formatting.BOLD))
                    .append(Text.literal(" shiny!").formatted(Formatting.GOLD))

                val nearbyPlayers = world.getEntitiesByClass(ServerPlayerEntity::class.java,
                    Box.of(wildMon.pos, 128.0, 128.0, 128.0)) { true }
                for (player in nearbyPlayers) {
                    player.sendMessage(message, false)
                    player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.8f, 1.8f)
                }

                Cobblebase.LOGGER.info("[LuckyCharm] ${pokemonEntity.pokemon.species.name} turned wild $speciesName shiny! (prof=$prof, mult=${shinyMultiplier}x)")
            }
        }

        // Subtle sparkle particles at pasture origin every 5 seconds
        if (now % 100 == 0L) {
            val x = origin.x + 0.5; val y = origin.y + 1.0; val z = origin.z + 0.5
            world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 2, 0.3, 0.3, 0.3, 0.01)
        }
    }

    /**
     * Shiny rate multiplier based on proficiency.
     * Prof 1: 1.4x, Prof 2: 1.8x, Prof 3: 2.2x, Prof 4: 2.6x, Prof 5: 3.0x
     */
    private fun getShinyMultiplier(prof: Int): Double {
        return 1.0 + prof * 0.4 // 1.4, 1.8, 2.2, 2.6, 3.0
    }

    /**
     * Scan range around the owner player.
     * Kept small so it only affects Pokemon the player actually encounters.
     */
    private fun getRange(prof: Int): Double {
        if (prof == 1) return 10.0
        if (prof == 2) return 12.0
        if (prof == 3) return 15.0
        if (prof == 4) return 18.0
        return 20.0 // prof 5
    }
}

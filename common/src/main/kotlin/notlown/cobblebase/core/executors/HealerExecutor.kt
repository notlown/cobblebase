package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.effects.SkillEffects
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import java.util.UUID

/**
 * Healer: heals players, Pokemon entities, AND revives fainted team Pokemon.
 * Priority: 1. Fainted team Pokemon, 2. Injured entities (lowest HP%)
 */
object HealerExecutor : SkillExecutor {

    private val lastHealTime = mutableMapOf<UUID, Long>()

    override fun tick(
        world: World,
        origin: BlockPos,
        pokemonEntity: PokemonEntity,
        skill: SkillDef,
        skillEntry: SkillEntry
    ) {
        val pokemonId = pokemonEntity.pokemon.uuid
        val now = world.time

        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(5, skillEntry.proficiency)
        val lastTime = lastHealTime[pokemonId] ?: now.also { lastHealTime[pokemonId] = now }
        if (now - lastTime < cooldownTicks) {
            if (now % 40 == 0L) {
                SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
            }
            return
        }

        val radius = skill.searchRadius.toDouble()
        val searchBox = Box.of(origin.toCenterPos(), radius * 2, radius * 2, radius * 2)

        // Find nearby players
        val nearbyPlayers = world.getEntitiesByClass(ServerPlayerEntity::class.java, searchBox) { true }

        // PRIORITY 1: Check team Pokemon of nearby players for fainted mons
        for (player in nearbyPlayers) {
            val faintedMon = findFaintedTeamPokemon(player)
            if (faintedMon != null) {
                // Navigate to the player (the fainted mon is in their team, not in the world)
                NavigationHelper.navigateTo(pokemonEntity, player.blockPos)

                if (NavigationHelper.isPokemonAtPosition(pokemonEntity, player.blockPos, 3.0)) {
                    reviveTeamPokemon(faintedMon, skillEntry.proficiency)
                    lastHealTime[pokemonId] = now
                    SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
                    Cobblebase.LOGGER.info("[Healer] ${pokemonEntity.pokemon.species.name} revived ${faintedMon.species.name} for ${player.name.string}")
                }
                return
            }
        }

        // PRIORITY 2: Heal injured players
        val injuredPlayer = nearbyPlayers
            .filter { it.health < it.maxHealth }
            .minByOrNull { it.health / it.maxHealth }

        // PRIORITY 3: Heal injured Pokemon entities in the world
        val injuredMon = world.getEntitiesByClass(PokemonEntity::class.java, searchBox) {
            it != pokemonEntity && it.isAlive && it.health < it.maxHealth
        }.minByOrNull { it.health / it.maxHealth }

        // Also check team Pokemon that are injured but not fainted
        var injuredTeamMon: Pokemon? = null
        var injuredTeamPlayer: ServerPlayerEntity? = null
        for (player in nearbyPlayers) {
            val mon = findInjuredTeamPokemon(player)
            if (mon != null) {
                injuredTeamMon = mon
                injuredTeamPlayer = player
                break
            }
        }

        // Pick the best target
        val target: LivingEntity? = listOfNotNull(injuredPlayer, injuredMon)
            .minByOrNull { it.health / it.maxHealth }

        // Compare world entity target vs team Pokemon
        if (injuredTeamMon != null && injuredTeamPlayer != null) {
            val teamHpPercent = injuredTeamMon.currentHealth.toFloat() / injuredTeamMon.maxHealth.toFloat()
            val entityHpPercent = target?.let { it.health / it.maxHealth } ?: 1f

            if (teamHpPercent < entityHpPercent) {
                // Heal team Pokemon instead
                NavigationHelper.navigateTo(pokemonEntity, injuredTeamPlayer.blockPos)
                if (NavigationHelper.isPokemonAtPosition(pokemonEntity, injuredTeamPlayer.blockPos, 3.0)) {
                    healTeamPokemon(injuredTeamMon, skillEntry.proficiency)
                    lastHealTime[pokemonId] = now
                    SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
                }
                return
            }
        }

        if (target == null) return

        // Heal world entity
        NavigationHelper.navigateTo(pokemonEntity, target.blockPos)

        if (NavigationHelper.isPokemonAtPosition(pokemonEntity, target.blockPos, 2.0)) {
            val healPercent = getHealPercent(skillEntry.proficiency)
            val healAmount = target.maxHealth * healPercent
            target.heal(healAmount)

            if (target is PokemonEntity) {
                val newHp = (target.pokemon.currentHealth + (target.pokemon.maxHealth * healPercent).toInt())
                    .coerceAtMost(target.pokemon.maxHealth)
                target.pokemon.currentHealth = newHp
            }

            lastHealTime[pokemonId] = now
            SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
        }
    }

    private fun findFaintedTeamPokemon(player: ServerPlayerEntity): Pokemon? {
        try {
            val party = Cobblemon.storage.getParty(player)
            return party.firstOrNull { it.isFainted() }
        } catch (_: Exception) { return null }
    }

    private fun findInjuredTeamPokemon(player: ServerPlayerEntity): Pokemon? {
        try {
            val party = Cobblemon.storage.getParty(player)
            return party.filter { !it.isFainted() && it.currentHealth < it.maxHealth }
                .minByOrNull { it.currentHealth.toFloat() / it.maxHealth.toFloat() }
        } catch (_: Exception) { return null }
    }

    private fun reviveTeamPokemon(pokemon: Pokemon, proficiency: Int) {
        val healPercent = getHealPercent(proficiency)
        val newHp = (pokemon.maxHealth * healPercent).toInt().coerceAtLeast(1)
        pokemon.currentHealth = newHp
    }

    private fun healTeamPokemon(pokemon: Pokemon, proficiency: Int) {
        val healPercent = getHealPercent(proficiency)
        val healAmount = (pokemon.maxHealth * healPercent).toInt()
        pokemon.currentHealth = (pokemon.currentHealth + healAmount).coerceAtMost(pokemon.maxHealth)
    }

    private fun getHealPercent(proficiency: Int): Float {
        return when (proficiency) {
            1 -> 0.05f
            2 -> 0.08f
            3 -> 0.12f
            4 -> 0.18f
            5 -> 0.25f
            else -> 0.05f
        }
    }
}

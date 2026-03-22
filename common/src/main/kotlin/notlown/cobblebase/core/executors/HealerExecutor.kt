package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
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
 * Healer: navigates to player, heals team in 3 visual ticks (1 sec apart).
 * Each tick heals 33% -> 66% -> 100% and plays cry + heart particles.
 *
 * Prof determines how many mons get healed per cycle:
 * Prof 1: 1 mon, Prof 2: 2, Prof 3: 3, Prof 4: 4, Prof 5: all 6
 * Fixed 3 minute cooldown for all proficiency levels.
 */
object HealerExecutor : SkillExecutor {

    private val lastHealTime = mutableMapOf<UUID, Long>()

    // Active healing state: healer UUID -> HealingSession
    private val activeSessions = mutableMapOf<UUID, HealingSession>()

    private class HealingSession(
        val targetPlayer: ServerPlayerEntity,
        val monsToHeal: List<Pokemon>,
        val healPlayerHp: Boolean,
        var ticksRemaining: Int = 3, // 3 pulses
        var nextPulseTick: Long = 0
    )

    override fun tick(
        world: World,
        origin: BlockPos,
        pokemonEntity: PokemonEntity,
        skill: SkillDef,
        skillEntry: SkillEntry
    ) {
        val pokemonId = pokemonEntity.pokemon.uuid
        val now = world.time

        // Active healing session - do the 3-pulse animation
        val session = activeSessions[pokemonId]
        if (session != null) {
            if (now >= session.nextPulseTick) {
                doPulse(world, pokemonEntity, session)
                session.ticksRemaining--
                session.nextPulseTick = now + 20L // next pulse in 1 second

                if (session.ticksRemaining <= 0) {
                    activeSessions.remove(pokemonId)
                    lastHealTime[pokemonId] = now
                }
            }
            return
        }

        // Cooldown
        val cooldownTicks = if (CobblebaseConfig.devMode) 100L else 180L * 20L
        val lastTime = lastHealTime[pokemonId] ?: now.also { lastHealTime[pokemonId] = now }
        if (now - lastTime < cooldownTicks) {
            if (now % 40 == 0L) {
                SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
            }
            return
        }

        // Find player that needs healing
        val radius = skill.searchRadius.toDouble()
        val searchBox = Box.of(origin.toCenterPos(), radius * 2, radius * 2, radius * 2)

        val allPlayers = world.getEntitiesByClass(ServerPlayerEntity::class.java, searchBox) { true }
        if (now % 100 == 0L) {
            Cobblebase.LOGGER.info("[Healer] ${pokemonEntity.pokemon.species.name} searching... players=${allPlayers.size}")
            for (p in allPlayers) {
                Cobblebase.LOGGER.info("[Healer]   ${p.name.string}: hp=${p.health}/${p.maxHealth} needsHeal=${playerNeedsHealing(p)}")
            }
        }

        val target = allPlayers
            .filter { playerNeedsHealing(it) }
            .minByOrNull { it.squaredDistanceTo(pokemonEntity.pos) }
            ?: return

        // Navigate to player
        NavigationHelper.navigateTo(pokemonEntity, target.blockPos)

        if (NavigationHelper.isPokemonAtPosition(pokemonEntity, target.blockPos, 3.0)) {
            // Start healing session
            val monsCount = if (skillEntry.proficiency >= 5) 6 else skillEntry.proficiency
            val monsToHeal = selectMonsToHeal(target, monsCount)
            val healPlayer = target.health < target.maxHealth

            if (monsToHeal.isNotEmpty() || healPlayer) {
                activeSessions[pokemonId] = HealingSession(
                    targetPlayer = target,
                    monsToHeal = monsToHeal,
                    healPlayerHp = healPlayer,
                    ticksRemaining = 3,
                    nextPulseTick = now
                )
                Cobblebase.LOGGER.info("[Healer] ${pokemonEntity.pokemon.species.name} starting heal on ${target.name.string} (${monsToHeal.size} mons)")
            }
        }
    }

    /**
     * One heal pulse: heals 33% of each mon's missing HP and plays effects.
     */
    private fun doPulse(world: World, healer: PokemonEntity, session: HealingSession) {
        // Heal player HP (33% per pulse = 100% after 3)
        if (session.healPlayerHp) {
            val missing = session.targetPlayer.maxHealth - session.targetPlayer.health
            session.targetPlayer.heal(missing / session.ticksRemaining.coerceAtLeast(1))
        }

        // Heal/revive team mons (33% per pulse)
        for (mon in session.monsToHeal) {
            if (mon.isFainted()) {
                // Revive: bring to 33% -> 66% -> 100% over 3 pulses
                val targetHp = mon.maxHealth * (4 - session.ticksRemaining) / 3
                mon.currentHealth = targetHp.coerceAtLeast(1)
            } else {
                // Heal: fill missing HP in 3 steps
                val targetHp = mon.maxHealth * (4 - session.ticksRemaining) / 3
                if (mon.currentHealth < targetHp) {
                    mon.currentHealth = targetHp.coerceAtMost(mon.maxHealth)
                }
            }
        }

        // Visual: cry + heart particles every pulse
        SkillEffects.playSuccess(world, healer, "heal")
    }

    private fun playerNeedsHealing(player: ServerPlayerEntity): Boolean {
        if (player.health < player.maxHealth) return true
        try {
            val party = Cobblemon.storage.getParty(player)
            return party.any { it.isFainted() || it.currentHealth < it.maxHealth }
        } catch (_: Exception) { return false }
    }

    private fun selectMonsToHeal(player: ServerPlayerEntity, count: Int): List<Pokemon> {
        try {
            val party = Cobblemon.storage.getParty(player)
            // Fainted first, then most injured
            val fainted = party.filter { it.isFainted() }
            val injured = party.filter { !it.isFainted() && it.currentHealth < it.maxHealth }
                .sortedBy { it.currentHealth.toFloat() / it.maxHealth.toFloat() }

            val result = mutableListOf<Pokemon>()
            for (mon in fainted + injured) {
                if (result.size >= count) break
                result.add(mon)
            }
            return result
        } catch (_: Exception) { return emptyList() }
    }
}

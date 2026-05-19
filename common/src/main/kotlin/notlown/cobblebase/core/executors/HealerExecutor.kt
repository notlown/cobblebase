package notlown.cobblebase.core.executors

import notlown.cobblebase.core.effectiveRadius
import notlown.cobblebase.core.effectiveRadiusFor
import notlown.cobblebase.core.effectiveRadiusFor

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
import notlown.cobblebase.core.NavigationHelper
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
    private val lastScanTime = mutableMapOf<UUID, Long>()
    private const val SCAN_INTERVAL_TICKS = 20L  // throttle player search when cooldown has elapsed but nobody needs healing
    private const val STALE_TTL_TICKS = 1200L

    /** Drops per-Pokemon state for healers that have stopped ticking (recalled / despawned). */
    fun cleanupStale(now: Long) {
        val stale = lastScanTime.entries.filter { now - it.value > STALE_TTL_TICKS }.map { it.key }
        for (id in stale) {
            lastScanTime.remove(id)
            lastHealTime.remove(id)
            activeSessions.remove(id)
            navStartTime.remove(id)
        }
    }

    // Active healing state: healer UUID -> HealingSession
    private val activeSessions = mutableMapOf<UUID, HealingSession>()
    private val navStartTime = mutableMapOf<UUID, Long>()  // track when healer started navigating to player

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
                session.ticksRemaining--
                val isFinalPulse = session.ticksRemaining <= 0
                doPulse(world, pokemonEntity, session, isFinalPulse, origin)
                session.nextPulseTick = now + 20L

                if (isFinalPulse) {
                    activeSessions.remove(pokemonId)
                    lastHealTime[pokemonId] = now
                    // Log healing action
                    notlown.cobblebase.core.LogManager.log(
                        origin, now,
                        pokemonEntity.pokemon.species.name,
                        "Healed",
                        "${session.monsToHeal.size} Pokemon fully restored",
                        notlown.cobblebase.core.LogManager.Rarity.UNCOMMON
                    )
                }
            }
            return
        }

        // Cooldown - first heal triggers immediately (lastTime defaults to 0, not now)
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(skill.cooldownSeconds, skillEntry.proficiency, skill.id)
        val lastTime = lastHealTime[pokemonId] ?: 0L
        if (now - lastTime < cooldownTicks) return

        // Even though the heal cooldown has elapsed, we don't want to scan for players every
        // tick when nobody needs healing (the cooldown only advances on successful heal).
        // Throttle the search to once per second.
        val lastScan = lastScanTime[pokemonId] ?: 0L
        if (now - lastScan < SCAN_INTERVAL_TICKS) return
        lastScanTime[pokemonId] = now

        // Find player that needs healing
        val radius = skill.effectiveRadiusFor(origin).toDouble()
        val searchBox = Box.of(origin.toCenterPos(), radius * 2, radius * 2, radius * 2)

        val allPlayers = world.getEntitiesByClass(ServerPlayerEntity::class.java, searchBox) { true }

        val target = allPlayers
            .filter { playerNeedsHealing(it) }
            .minByOrNull { it.squaredDistanceTo(pokemonEntity.pos) }
            ?: return

        // Navigate to player — use generous range check (5 blocks) and auto-heal after 5s timeout
        NavigationHelper.navigateTo(pokemonEntity, target.blockPos)
        val distToPlayer = pokemonEntity.squaredDistanceTo(target)
        val closeEnough = distToPlayer < 36.0 // 6 blocks squared

        // Track navigation time for timeout
        val navKey = pokemonEntity.pokemon.uuid
        val navStarted = navStartTime.getOrPut(navKey) { now }
        val navTimedOut = now - navStarted > 100L // 5 seconds timeout

        if (closeEnough || navTimedOut) {
            navStartTime.remove(navKey)
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
            }
        }
    }

    /**
     * One heal pulse: heals 33% of each mon's missing HP and plays effects.
     */
    private fun doPulse(world: World, healer: PokemonEntity, session: HealingSession, isFinalPulse: Boolean, origin: BlockPos) {
        // Heal player HP (33% per pulse = 100% after 3)
        if (session.healPlayerHp) {
            val missing = session.targetPlayer.maxHealth - session.targetPlayer.health
            session.targetPlayer.heal(missing / session.ticksRemaining.coerceAtLeast(1))
        }

        // Heal/revive team mons (33% -> 66% -> 100% over 3 pulses)
        for (mon in session.monsToHeal) {
            val targetHp = mon.maxHealth * (4 - session.ticksRemaining) / 3
            mon.currentHealth = targetHp.coerceIn(1, mon.maxHealth)
        }

        // On final pulse: full restore for the selected mons only
        if (isFinalPulse) {
            for (mon in session.monsToHeal) {
                mon.currentHealth = mon.maxHealth
                mon.status = null
                mon.moveSet.heal()
            }
        }

        // Visual: cry + heart particles every pulse
        SkillEffects.playSuccess(world, healer, "heal", origin)
    }

    private fun playerNeedsHealing(player: ServerPlayerEntity): Boolean {
        if (player.health < player.maxHealth) return true
        try {
            val party = Cobblemon.storage.getParty(player)
            // Only trigger on HP loss, fainted, or status - NOT PP (would cause nonstop healing)
            return party.any { mon ->
                mon.isFainted() ||
                mon.currentHealth < mon.maxHealth ||
                mon.status != null
            }
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Healer] Error checking party: ${e.message}")
            return false
        }
    }

    private fun selectMonsToHeal(player: ServerPlayerEntity, count: Int): List<Pokemon> {
        try {
            val party = Cobblemon.storage.getParty(player)

            // Priority: fainted first (by party slot order), then sort remaining by:
            // 1. Lowest HP% first
            // 2. If equal HP%, prefer ones with status effects
            // 3. If still tied, prefer earlier party slot (natural list order)
            val fainted = party.filter { it.isFainted() }
            val needsHeal = party.filter { mon ->
                !mon.isFainted() && (
                    mon.currentHealth < mon.maxHealth ||
                    mon.status != null
                )
            }.sortedWith(compareBy<Pokemon> {
                it.currentHealth.toFloat() / it.maxHealth.toFloat() // lowest HP% first
            }.thenByDescending {
                if (it.status != null) 1 else 0 // prefer status-afflicted on ties
            })

            val result = mutableListOf<Pokemon>()
            for (mon in fainted + needsHeal) {
                if (result.size >= count) break
                result.add(mon)
            }
            return result
        } catch (_: Exception) { return emptyList() }
    }
}

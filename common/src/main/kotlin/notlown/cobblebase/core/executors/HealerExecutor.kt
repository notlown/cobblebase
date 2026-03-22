package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
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
 * Healer: navigates to nearby player and fully heals + revives their team Pokemon.
 * Also heals the player's HP.
 *
 * Prof 1: heals/revives 1 Pokemon + player
 * Prof 2: heals/revives 2 Pokemon + player
 * Prof 3: heals/revives 3 Pokemon + player
 * Prof 4: heals/revives 4 Pokemon + player
 * Prof 5: heals/revives entire team (6) + player
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

        // Cooldown: 30 seconds base, slightly reduced by proficiency
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(30, skillEntry.proficiency)
        val lastTime = lastHealTime[pokemonId] ?: now.also { lastHealTime[pokemonId] = now }
        if (now - lastTime < cooldownTicks) {
            if (now % 40 == 0L) {
                SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
            }
            return
        }

        val radius = skill.searchRadius.toDouble()
        val searchBox = Box.of(origin.toCenterPos(), radius * 2, radius * 2, radius * 2)

        // Find nearest player that needs healing (team has fainted/injured mons OR player is injured)
        val target = world.getEntitiesByClass(ServerPlayerEntity::class.java, searchBox) { true }
            .filter { playerNeedsHealing(it) }
            .minByOrNull { it.squaredDistanceTo(pokemonEntity.pos) }
            ?: return

        // Navigate to player
        NavigationHelper.navigateTo(pokemonEntity, target.blockPos)

        if (NavigationHelper.isPokemonAtPosition(pokemonEntity, target.blockPos, 3.0)) {
            val monsToHeal = if (skillEntry.proficiency >= 5) 6 else skillEntry.proficiency

            healPlayer(target, monsToHeal)
            lastHealTime[pokemonId] = now
            SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
            Cobblebase.LOGGER.info("[Healer] ${pokemonEntity.pokemon.species.name} healed ${target.name.string}'s team ($monsToHeal mons)")
        }
    }

    private fun playerNeedsHealing(player: ServerPlayerEntity): Boolean {
        if (player.health < player.maxHealth) return true
        try {
            val party = Cobblemon.storage.getParty(player)
            return party.any { it.isFainted() || it.currentHealth < it.maxHealth }
        } catch (_: Exception) { return false }
    }

    private fun healPlayer(player: ServerPlayerEntity, monsToHeal: Int) {
        // Heal the player fully
        player.health = player.maxHealth

        // Heal/revive team Pokemon
        try {
            val party = Cobblemon.storage.getParty(player)

            // Fainted first, then injured, up to monsToHeal count
            val fainted = party.filter { it.isFainted() }
            val injured = party.filter { !it.isFainted() && it.currentHealth < it.maxHealth }

            var healed = 0
            for (mon in fainted) {
                if (healed >= monsToHeal) break
                mon.currentHealth = mon.maxHealth
                healed++
            }
            for (mon in injured) {
                if (healed >= monsToHeal) break
                mon.currentHealth = mon.maxHealth
                healed++
            }
        } catch (_: Exception) { }
    }
}

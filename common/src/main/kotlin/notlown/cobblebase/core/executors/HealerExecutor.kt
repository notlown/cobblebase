package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.effects.SkillEffects
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import java.util.UUID

/**
 * Healer: navigates to injured players AND Pokemon and gives regeneration.
 * Prioritizes whoever has the lowest HP percentage.
 * Duration and amplifier scale with proficiency.
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

        // Find injured players
        val injuredPlayers = world.getEntitiesByClass(PlayerEntity::class.java, searchBox) {
            it.health < it.maxHealth
        }

        // Find injured or fainted Pokemon (not the healer itself)
        val needsHealPokemon = world.getEntitiesByClass(PokemonEntity::class.java, searchBox) {
            it != pokemonEntity && it.isAlive &&
            (it.pokemon.isFainted() || it.health < it.maxHealth)
        }

        // Prioritize: fainted Pokemon first, then lowest HP percentage
        val faintedMon = needsHealPokemon.firstOrNull { it.pokemon.isFainted() }
        val injuredPlayer = injuredPlayers.minByOrNull { it.health / it.maxHealth }
        val injuredMon = needsHealPokemon.filter { !it.pokemon.isFainted() }
            .minByOrNull { it.health / it.maxHealth }

        // Fainted mons get top priority, then whoever has lowest HP%
        val target: LivingEntity = faintedMon
            ?: listOfNotNull(injuredPlayer, injuredMon)
                .minByOrNull { it.health / it.maxHealth }
            ?: return

        // Navigate to target
        NavigationHelper.navigateTo(pokemonEntity, target.blockPos)

        if (NavigationHelper.isPokemonAtPosition(pokemonEntity, target.blockPos, 2.0)) {
            // Heal percentage scales with proficiency:
            // Prof 1: 5%, Prof 2: 8%, Prof 3: 12%, Prof 4: 18%, Prof 5: 25%
            val healPercent = when (skillEntry.proficiency) {
                1 -> 0.05f
                2 -> 0.08f
                3 -> 0.12f
                4 -> 0.18f
                5 -> 0.25f
                else -> 0.05f
            }

            if (target is PokemonEntity && target.pokemon.isFainted()) {
                // Revive: set HP to healPercent then apply heal
                val maxHp = target.pokemon.maxHealth
                target.pokemon.currentHealth = (maxHp * healPercent).toInt().coerceAtLeast(1)
                target.health = target.maxHealth * healPercent
            } else {
                // Direct heal: X% of max HP instantly
                val healAmount = target.maxHealth * healPercent
                target.heal(healAmount)

                // Also heal Cobblemon HP if it's a Pokemon
                if (target is PokemonEntity) {
                    val newHp = (target.pokemon.currentHealth + (target.pokemon.maxHealth * healPercent).toInt())
                        .coerceAtMost(target.pokemon.maxHealth)
                    target.pokemon.currentHealth = newHp
                }
            }

            lastHealTime[pokemonId] = now
            SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
        }
    }
}

package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
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
            it.health < it.maxHealth && !it.hasStatusEffect(StatusEffects.REGENERATION)
        }

        // Find injured Pokemon (not the healer itself, not fainted)
        val injuredPokemon = world.getEntitiesByClass(PokemonEntity::class.java, searchBox) {
            it != pokemonEntity && it.health < it.maxHealth && it.isAlive &&
            !it.hasStatusEffect(StatusEffects.REGENERATION)
        }

        // Pick the target with the lowest HP percentage (players and Pokemon together)
        val allTargets = mutableListOf<LivingEntity>()
        allTargets.addAll(injuredPlayers)
        allTargets.addAll(injuredPokemon)

        val target = allTargets
            .minByOrNull { it.health / it.maxHealth }
            ?: return

        // Navigate to target
        NavigationHelper.navigateTo(pokemonEntity, target.blockPos)

        if (NavigationHelper.isPokemonAtPosition(pokemonEntity, target.blockPos, 2.0)) {
            val regenSeconds = 10 + (skillEntry.proficiency * 4)
            val amplifier = if (skillEntry.proficiency >= 4) 1 else 0

            target.addStatusEffect(
                StatusEffectInstance(
                    StatusEffects.REGENERATION,
                    regenSeconds * 20,
                    amplifier
                )
            )
            lastHealTime[pokemonId] = now
            SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
        }
    }
}

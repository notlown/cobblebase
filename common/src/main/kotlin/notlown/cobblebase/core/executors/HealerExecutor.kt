package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
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
 * Healer: navigates to injured players and gives regeneration.
 * Duration and amplifier scale with proficiency.
 * Only heals players that are missing HP and don't already have regen.
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

        // Cooldown between heals: ~5 seconds at prof 3
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(5, skillEntry.proficiency)
        val lastTime = lastHealTime[pokemonId] ?: 0L
        if (now - lastTime < cooldownTicks) return

        // Find nearby players who need healing
        val radius = skill.searchRadius.toDouble()
        val searchBox = Box.of(origin.toCenterPos(), radius * 2, radius * 2, radius * 2)
        val nearbyPlayers = world.getEntitiesByClass(PlayerEntity::class.java, searchBox) { true }

        // Find closest player that needs healing (missing HP + no regen)
        val target = nearbyPlayers
            .filter { it.health < it.maxHealth && !it.hasStatusEffect(StatusEffects.REGENERATION) }
            .minByOrNull { it.squaredDistanceTo(pokemonEntity.pos) }
            ?: return

        // Navigate to player
        NavigationHelper.navigateTo(pokemonEntity, target.blockPos)

        if (NavigationHelper.isPokemonNearPlayer(pokemonEntity, target)) {
            // Proficiency affects duration and amplifier
            val regenSeconds = 10 + (skillEntry.proficiency * 4) // 14s at prof1, 30s at prof5
            val amplifier = if (skillEntry.proficiency >= 4) 1 else 0 // Regen II at prof 4+

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

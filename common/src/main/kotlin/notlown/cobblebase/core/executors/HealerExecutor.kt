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
 * Healer executor: gives regeneration effect to nearby players that need healing.
 * Navigates to injured players and applies regeneration on arrival.
 */
object HealerExecutor : SkillExecutor {

    private val lastHealTime = mutableMapOf<UUID, Long>()
    private val playerTarget = mutableMapOf<UUID, UUID>()
    private const val REGEN_DURATION_SECONDS = 10
    private const val REGEN_AMPLIFIER = 0

    override fun tick(
        world: World,
        origin: BlockPos,
        pokemonEntity: PokemonEntity,
        skill: SkillDef,
        skillEntry: SkillEntry
    ) {
        val pokemonId = pokemonEntity.pokemon.uuid
        val now = world.time

        // Use a short cooldown between heal attempts (every 3 seconds base, adjusted by proficiency)
        val cooldownTicks = if (skill.cooldownSeconds > 0) {
            CobblebaseConfig.getEffectiveCooldownTicks(skill.cooldownSeconds, skillEntry.proficiency)
        } else {
            60L * (6 - skillEntry.proficiency) / 3 // ~3 seconds at prof 3
        }

        val lastTime = lastHealTime[pokemonId] ?: 0L
        if (now - lastTime < cooldownTicks) return

        // Find nearby players who need healing
        val radius = skill.searchRadius.toDouble()
        val searchBox = Box.of(origin.toCenterPos(), radius * 2, radius * 2, radius * 2)
        val nearbyPlayers = world.getEntitiesByClass(PlayerEntity::class.java, searchBox) { true }
        if (nearbyPlayers.isEmpty()) {
            playerTarget.remove(pokemonId)
            return
        }

        // Find closest player who needs healing
        val closestPlayer = nearbyPlayers
            .filter { !it.hasStatusEffect(StatusEffects.REGENERATION) && it.health < it.maxHealth }
            .minByOrNull { it.squaredDistanceTo(pokemonEntity.pos) }
            ?: return

        // Navigate to the player
        navigateToPlayer(pokemonEntity, closestPlayer)

        if (isNearPlayer(pokemonEntity, closestPlayer)) {
            closestPlayer.addStatusEffect(
                StatusEffectInstance(
                    StatusEffects.REGENERATION,
                    REGEN_DURATION_SECONDS * 20,
                    REGEN_AMPLIFIER
                )
            )
            lastHealTime[pokemonId] = now
            playerTarget.remove(pokemonId)
            SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
        }
    }


    private fun navigateToPlayer(pokemonEntity: PokemonEntity, player: PlayerEntity) {
        NavigationHelper.navigateTo(pokemonEntity, player.blockPos)
    }

    private fun isNearPlayer(pokemonEntity: PokemonEntity, player: PlayerEntity): Boolean {
        return NavigationHelper.isPokemonNearPlayer(pokemonEntity, player)
    }
}

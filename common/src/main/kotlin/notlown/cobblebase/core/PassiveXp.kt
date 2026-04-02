package notlown.cobblebase.core

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.experience.ExperienceSource
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.executors.MentorExecutor
import java.util.UUID

object PassiveXp {
    private val lastXpTick = mutableMapOf<UUID, Long>()
    private val maxedOut = mutableSetOf<UUID>() // Pokemon at level cap, skip forever

    private val intervalTicks get() = CobblebaseConfig.passiveXpIntervalSeconds * 20L

    @JvmOverloads
    fun tick(world: World, pokemonEntity: PokemonEntity, pastureOrigin: BlockPos? = null) {
        if (!CobblebaseConfig.passiveXpEnabled) return

        val pokemonId = pokemonEntity.pokemon.uuid

        // Skip permanently if already at cap
        if (pokemonId in maxedOut) return

        val pokemon = pokemonEntity.pokemon
        val maxLevel = Cobblemon.config.maxPokemonLevel

        // Mark as maxed and never try again
        if (pokemon.level >= maxLevel || !pokemon.canLevelUpFurther()) {
            maxedOut.add(pokemonId)
            return
        }

        val now = world.time
        val lastTime = lastXpTick[pokemonId] ?: now.also { lastXpTick[pokemonId] = it }

        if (now - lastTime < intervalTicks) return
        lastXpTick[pokemonId] = now

        // Percentage-based: give X% of XP needed for next level each tick
        val xpToNextLevel = pokemon.getExperienceToNextLevel()
        val baseXpGain = (xpToNextLevel * CobblebaseConfig.passiveXpPercent / 100.0).toInt().coerceAtLeast(1)

        // Apply mentor XP multiplier if a mentor is active in the same pasture
        val mentorMultiplier = if (pastureOrigin != null) MentorExecutor.getXpMultiplier(pastureOrigin) else 1.0
        val xpGain = (baseXpGain * mentorMultiplier).toInt().coerceAtLeast(1)

        pokemon.addExperience(CobblebaseExperienceSource, xpGain)

        if (pokemon.level >= maxLevel) {
            maxedOut.add(pokemonId)
        }
    }
}

object CobblebaseExperienceSource : ExperienceSource {
    override fun isSidemod() = true
}

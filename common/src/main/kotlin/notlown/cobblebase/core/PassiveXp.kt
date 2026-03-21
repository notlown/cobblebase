package notlown.cobblebase.core

import com.cobblemon.mod.common.api.pokemon.experience.ExperienceSource
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.World
import java.util.UUID

/**
 * Passive XP for all pastured Pokemon.
 * Default: 125 XP every 60 seconds (about 1 level per in-game day at mid-levels).
 */
object PassiveXp {
    private val lastXpTick = mutableMapOf<UUID, Long>()

    // Configurable later via config
    var enabled = true
    var xpAmount = 125
    var intervalSeconds = 60L

    private val intervalTicks get() = intervalSeconds * 20L

    fun tick(world: World, pokemonEntity: PokemonEntity) {
        if (!enabled) return

        val pokemonId = pokemonEntity.pokemon.uuid
        val now = world.time
        val lastTime = lastXpTick[pokemonId] ?: now.also { lastXpTick[pokemonId] = it }

        if (now - lastTime < intervalTicks) return

        lastXpTick[pokemonId] = now

        val pokemon = pokemonEntity.pokemon
        if (pokemon.canLevelUpFurther()) {
            pokemon.addExperience(CobblebaseExperienceSource, xpAmount)
        }
    }
}

object CobblebaseExperienceSource : ExperienceSource {
    override fun isSidemod() = true
}

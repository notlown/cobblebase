package notlown.cobblebase.core

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.experience.ExperienceSource
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.World
import java.util.UUID

object PassiveXp {
    private val lastXpTick = mutableMapOf<UUID, Long>()
    private val maxedOut = mutableSetOf<UUID>() // Pokemon at level cap, skip forever

    private val intervalTicks get() = CobblebaseConfig.passiveXpIntervalSeconds * 20L

    fun tick(world: World, pokemonEntity: PokemonEntity) {
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

        pokemon.addExperience(CobblebaseExperienceSource, CobblebaseConfig.passiveXpAmount)

        // Check if just hit cap after adding XP
        if (pokemon.level >= maxLevel) {
            maxedOut.add(pokemonId)
        }
    }
}

object CobblebaseExperienceSource : ExperienceSource {
    override fun isSidemod() = true
}

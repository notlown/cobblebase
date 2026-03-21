package notlown.cobblebase.core

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.experience.ExperienceSource
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.World
import java.util.UUID

object PassiveXp {
    private val lastXpTick = mutableMapOf<UUID, Long>()

    private val intervalTicks get() = CobblebaseConfig.passiveXpIntervalSeconds * 20L

    fun tick(world: World, pokemonEntity: PokemonEntity) {
        if (!CobblebaseConfig.passiveXpEnabled) return

        val pokemonId = pokemonEntity.pokemon.uuid
        val now = world.time
        val lastTime = lastXpTick[pokemonId] ?: now.also { lastXpTick[pokemonId] = it }

        if (now - lastTime < intervalTicks) return
        lastXpTick[pokemonId] = now

        val pokemon = pokemonEntity.pokemon
        if (pokemon.level >= Cobblemon.config.maxPokemonLevel) return
        if (pokemon.canLevelUpFurther()) {
            pokemon.addExperience(CobblebaseExperienceSource, CobblebaseConfig.passiveXpAmount)
        }
    }
}

object CobblebaseExperienceSource : ExperienceSource {
    override fun isSidemod() = true
}

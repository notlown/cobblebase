package notlown.cobblebase.core.effects

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import notlown.cobblebase.core.Cobblebase
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import net.minecraft.util.math.Box
import net.minecraft.world.World

/**
 * Visual and audio effects for skill execution.
 * Sends animation packets server-side using CobblemonNetwork (same as battle system).
 */
object SkillEffects {

    fun sendAnimationPublic(world: World, pokemonEntity: PokemonEntity, vararg names: String) =
        sendAnimation(world, pokemonEntity, *names)

    private fun sendAnimation(world: World, pokemonEntity: PokemonEntity, vararg names: String) {
        if (world !is ServerWorld) return
        // Also add bedrock-format variants "speciesname:animname" as fallback
        val speciesName = pokemonEntity.pokemon.species.name.lowercase().replace(" ", "_").replace("-", "_")
        val allNames = mutableSetOf<String>()
        for (name in names) {
            allNames.add(name)
            allNames.add("${speciesName}:${name}")
        }
        val packet = PlayPosableAnimationPacket(pokemonEntity.id, allNames, emptyList())
        val box = Box.of(pokemonEntity.pos, 128.0, 128.0, 128.0)
        val players = world.getEntitiesByClass(ServerPlayerEntity::class.java, box) { true }
        for (player in players) {
            CobblemonNetwork.sendPacketToPlayer(player, packet)
        }
    }

    /**
     * Play a success effect based on skill effect type.
     * Called when a skill action completes (harvest, catch, repel, etc.)
     */
    fun playSuccess(world: World, pokemonEntity: PokemonEntity, effectType: String) {
        if (world !is ServerWorld) return
        val x = pokemonEntity.x
        val y = pokemonEntity.y
        val h = pokemonEntity.height.toDouble()
        val z = pokemonEntity.z

        // Play cry sound from Cobblebase's own sound pack (all 1025 Pokemon)
        val speciesName = pokemonEntity.pokemon.species.name.lowercase().replace(" ", "_").replace("-", "_")
        val cryId = Identifier.of("cobblebase", "pokemon.${speciesName}.cry")
        val soundEvent = Registries.SOUND_EVENT.get(cryId)
        if (soundEvent != null) {
            world.playSound(null, pokemonEntity.x, pokemonEntity.y, pokemonEntity.z, soundEvent, SoundCategory.NEUTRAL, 0.8f, 1.0f)
        }

        when (effectType) {
            "harvest" -> {
                sendAnimation(world, pokemonEntity, "tackle", "scratch", "pound", "physical")
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y + h, z, 15, 0.5, 0.3, 0.5, 0.03)
                world.spawnParticles(ParticleTypes.COMPOSTER, x, y + h * 0.5, z, 10, 0.3, 0.2, 0.3, 0.05)
            }
            "water" -> {
                sendAnimation(world, pokemonEntity, "watergun", "bubble", "spray", "special")
                world.spawnParticles(ParticleTypes.SPLASH, x, y, z, 40, 0.5, 0.3, 0.5, 0.3)
                world.spawnParticles(ParticleTypes.FISHING, x, y, z, 15, 0.5, 0.0, 0.5, 0.05)
                world.spawnParticles(ParticleTypes.BUBBLE_POP, x, y + 0.5, z, 10, 0.3, 0.3, 0.3, 0.05)
            }
            "fire" -> {
                sendAnimation(world, pokemonEntity, "ember", "flamethrower", "flame", "special")
                world.spawnParticles(ParticleTypes.FLAME, x, y + h * 0.5, z, 25, 0.4, 0.3, 0.4, 0.05)
                world.spawnParticles(ParticleTypes.LAVA, x, y + h * 0.3, z, 8, 0.3, 0.2, 0.3, 0.0)
                world.spawnParticles(ParticleTypes.SMOKE, x, y + h, z, 10, 0.3, 0.2, 0.3, 0.02)
            }
            "combat" -> {
                sendAnimation(world, pokemonEntity, "tackle", "bite", "crunch", "physical")
                world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, x, y + h, z, 8, 0.4, 0.3, 0.4, 0.02)
                world.spawnParticles(ParticleTypes.CRIT, x, y + h * 0.5, z, 15, 0.5, 0.3, 0.5, 0.1)
                world.spawnParticles(ParticleTypes.SMOKE, x, y, z, 10, 0.4, 0.2, 0.4, 0.03)
            }
            "heal" -> {
                sendAnimation(world, pokemonEntity, "wish", "special")
                world.spawnParticles(ParticleTypes.HEART, x, y + h, z, 15, 0.6, 0.4, 0.6, 0.03)
            }
            "special" -> {
                sendAnimation(world, pokemonEntity, "special")
                world.spawnParticles(ParticleTypes.ENCHANT, x, y + h + 0.5, z, 30, 0.5, 0.5, 0.5, 0.8)
                world.spawnParticles(ParticleTypes.END_ROD, x, y + h, z, 8, 0.3, 0.3, 0.3, 0.02)
            }
            else -> {
                sendAnimation(world, pokemonEntity, "special", "physical")
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y + h, z, 12, 0.5, 0.3, 0.5, 0.03)
                world.spawnParticles(ParticleTypes.ENCHANT, x, y + h + 0.5, z, 20, 0.4, 0.3, 0.4, 0.5)
            }
        }
    }

    /**
     * Play working particles - called periodically while a skill is on cooldown.
     */
    fun playWorking(world: World, pokemonEntity: PokemonEntity, effectType: String) {
        if (world !is ServerWorld) return
        val x = pokemonEntity.x
        val y = pokemonEntity.y
        val h = pokemonEntity.height.toDouble()
        val z = pokemonEntity.z

        when (effectType) {
            "water" -> {
                world.spawnParticles(ParticleTypes.FISHING, x, y, z, 5, 0.5, 0.0, 0.5, 0.02)
                world.spawnParticles(ParticleTypes.BUBBLE_POP, x, y + 0.3, z, 3, 0.3, 0.2, 0.3, 0.01)
                world.spawnParticles(ParticleTypes.SPLASH, x, y, z, 8, 0.4, 0.1, 0.4, 0.05)
            }
            "fire" -> {
                world.spawnParticles(ParticleTypes.FLAME, x, y + h * 0.5, z, 4, 0.2, 0.1, 0.2, 0.02)
                world.spawnParticles(ParticleTypes.SMOKE, x, y + h, z, 2, 0.2, 0.1, 0.2, 0.01)
            }
            "combat" -> {
                world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, x, y + h, z, 2, 0.2, 0.1, 0.2, 0.01)
            }
            "heal" -> {
                world.spawnParticles(ParticleTypes.HEART, x, y + h, z, 2, 0.3, 0.2, 0.3, 0.01)
            }
            "harvest" -> {
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y + h, z, 3, 0.3, 0.2, 0.3, 0.02)
            }
            "special" -> {
                world.spawnParticles(ParticleTypes.ENCHANT, x, y + h + 0.3, z, 5, 0.3, 0.3, 0.3, 0.3)
            }
            else -> {
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y + h, z, 2, 0.2, 0.1, 0.2, 0.01)
            }
        }
    }
}

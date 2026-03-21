package notlown.cobblebase.core.effects

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import notlown.cobblebase.core.Cobblebase
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Box
import net.minecraft.world.World

/**
 * Visual and audio effects for skill execution.
 * Sends animation packets server-side using CobblemonNetwork (same as battle system).
 */
object SkillEffects {

    private fun sendAnimation(world: World, pokemonEntity: PokemonEntity, vararg names: String) {
        if (world !is ServerWorld) return
        val packet = PlayPosableAnimationPacket(pokemonEntity.id, names.toSet(), emptyList())
        val box = Box.of(pokemonEntity.pos, 128.0, 128.0, 128.0)
        for (player in world.getEntitiesByClass(ServerPlayerEntity::class.java, box) { true }) {
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

        // Cry + attack animation in ONE packet so they don't override each other
        when (effectType) {
            "harvest" -> {
                sendAnimation(world, pokemonEntity, "cry", "tackle", "scratch", "pound", "physical")
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y + h, z, 15, 0.5, 0.3, 0.5, 0.03)
                world.spawnParticles(ParticleTypes.COMPOSTER, x, y + h * 0.5, z, 10, 0.3, 0.2, 0.3, 0.05)
            }
            "water" -> {
                sendAnimation(world, pokemonEntity, "cry", "watergun", "bubble", "spray", "special")
                world.spawnParticles(ParticleTypes.SPLASH, x, y, z, 40, 0.5, 0.3, 0.5, 0.3)
                world.spawnParticles(ParticleTypes.FISHING, x, y, z, 15, 0.5, 0.0, 0.5, 0.05)
                world.spawnParticles(ParticleTypes.BUBBLE_POP, x, y + 0.5, z, 10, 0.3, 0.3, 0.3, 0.05)
            }
            "fire" -> {
                sendAnimation(world, pokemonEntity, "cry", "ember", "flamethrower", "flame", "special")
                world.spawnParticles(ParticleTypes.FLAME, x, y + h * 0.5, z, 25, 0.4, 0.3, 0.4, 0.05)
                world.spawnParticles(ParticleTypes.LAVA, x, y + h * 0.3, z, 8, 0.3, 0.2, 0.3, 0.0)
                world.spawnParticles(ParticleTypes.SMOKE, x, y + h, z, 10, 0.3, 0.2, 0.3, 0.02)
            }
            "combat" -> {
                sendAnimation(world, pokemonEntity, "cry", "tackle", "bite", "crunch", "physical")
                world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, x, y + h, z, 8, 0.4, 0.3, 0.4, 0.02)
                world.spawnParticles(ParticleTypes.CRIT, x, y + h * 0.5, z, 15, 0.5, 0.3, 0.5, 0.1)
                world.spawnParticles(ParticleTypes.SMOKE, x, y, z, 10, 0.4, 0.2, 0.4, 0.03)
            }
            "heal" -> {
                sendAnimation(world, pokemonEntity, "cry", "wish", "special")
                world.spawnParticles(ParticleTypes.HEART, x, y + h, z, 10, 0.5, 0.3, 0.5, 0.02)
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y + h * 0.5, z, 15, 0.4, 0.3, 0.4, 0.02)
            }
            "special" -> {
                sendAnimation(world, pokemonEntity, "cry", "special")
                world.spawnParticles(ParticleTypes.ENCHANT, x, y + h + 0.5, z, 30, 0.5, 0.5, 0.5, 0.8)
                world.spawnParticles(ParticleTypes.END_ROD, x, y + h, z, 8, 0.3, 0.3, 0.3, 0.02)
            }
            else -> {
                sendAnimation(world, pokemonEntity, "cry", "special", "physical")
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

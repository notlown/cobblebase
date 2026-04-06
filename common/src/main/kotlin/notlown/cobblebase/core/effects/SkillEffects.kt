package notlown.cobblebase.core.effects

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.Cobblebase
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import java.util.UUID
import net.minecraft.util.math.Box
import net.minecraft.world.World

/**
 * Visual and audio effects for skill execution.
 * Sends animation packets server-side using CobblemonNetwork (same as battle system).
 */
object SkillEffects {

    private val lastCryTime = mutableMapOf<UUID, Long>()
    private const val CRY_COOLDOWN_TICKS = 1200L // 60 seconds between cries per Pokemon

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
     *
     * Particles disabled for performance — only "heal" effect keeps particles
     * since healing is rare. Cries and animations still play for all types.
     */
    fun playSuccess(world: World, pokemonEntity: PokemonEntity, effectType: String) {
        if (world !is ServerWorld) return

        // Play cry sound — max once per 60 seconds per Pokemon to avoid spam
        if (CobblebaseConfig.cryEnabled && CobblebaseConfig.cryVolume > 0) {
            val pokemonId = pokemonEntity.pokemon.uuid
            val now = world.time
            val lastCry = lastCryTime[pokemonId] ?: 0L
            if (now - lastCry >= CRY_COOLDOWN_TICKS) {
                lastCryTime[pokemonId] = now
                val speciesName = pokemonEntity.pokemon.species.name.lowercase().replace(" ", "_").replace("-", "_")
                val cryId = Identifier.of("cobblebase", "pokemon.${speciesName}.cry")
                val soundEvent = Registries.SOUND_EVENT.get(cryId)
                if (soundEvent != null) {
                    val volume = CobblebaseConfig.cryVolume / 100f
                    world.playSound(null, pokemonEntity.x, pokemonEntity.y, pokemonEntity.z, soundEvent, SoundCategory.NEUTRAL, volume, 1.0f)
                }
            }
        }

        // Animations only — no particles (TPS optimization)
        when (effectType) {
            "harvest" -> sendAnimation(world, pokemonEntity, "tackle", "scratch", "pound", "physical")
            "water" -> sendAnimation(world, pokemonEntity, "watergun", "bubble", "spray", "special")
            "fire" -> sendAnimation(world, pokemonEntity, "ember", "flamethrower", "flame", "special")
            "combat" -> sendAnimation(world, pokemonEntity, "tackle", "bite", "crunch", "physical")
            "heal" -> {
                // Heal is rare — keep particles
                sendAnimation(world, pokemonEntity, "wish", "special")
                val x = pokemonEntity.x
                val y = pokemonEntity.y
                val h = pokemonEntity.height.toDouble()
                val z = pokemonEntity.z
                world.spawnParticles(ParticleTypes.HEART, x, y + h, z, 15, 0.6, 0.4, 0.6, 0.03)
            }
            "special" -> sendAnimation(world, pokemonEntity, "special")
            else -> sendAnimation(world, pokemonEntity, "special", "physical")
        }
    }

    /**
     * Play working particles - called periodically while a skill is on cooldown.
     */
    fun playWorking(world: World, pokemonEntity: PokemonEntity, effectType: String) {
        // Working particles disabled for performance — only heal keeps subtle particles
        if (world !is ServerWorld) return
        if (effectType == "heal") {
            val x = pokemonEntity.x
            val y = pokemonEntity.y
            val h = pokemonEntity.height.toDouble()
            val z = pokemonEntity.z
            world.spawnParticles(ParticleTypes.HEART, x, y + h, z, 2, 0.3, 0.2, 0.3, 0.01)
        }
    }
}

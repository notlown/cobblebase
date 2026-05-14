package notlown.cobblebase.core.effects

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.NearbyPlayerCache
import notlown.cobblebase.core.PlatformPacketSender
import notlown.cobblebase.core.net.PlayCryS2CPacket
import java.util.UUID

/**
 * Visual and audio effects for skill execution.
 *
 * Two-channel design:
 *   - Animations are sent via CobblemonNetwork (vanilla pose system).
 *   - Cries are sent via Cobblebase's own [PlayCryS2CPacket] so client-side cryEnabled /
 *     cryVolume cloth-config actually applies. Server-side `world.playSound(null, ...)`
 *     ignores per-player config and was the reason the mute toggle felt broken.
 *
 * Both channels broadcast through [NearbyPlayerCache] so the 128³ AABB scan only runs
 * once per pasture per cache TTL instead of per-animation.
 */
object SkillEffects {

    private val lastCryTime = mutableMapOf<UUID, Long>()
    private const val CRY_COOLDOWN_TICKS = 1200L // 60 seconds between cries per Pokemon

    /** Animation broadcast keyed on a pasture position so [NearbyPlayerCache] can be reused. */
    fun sendAnimationPublic(world: World, pokemonEntity: PokemonEntity, pasturePos: BlockPos, vararg names: String) =
        sendAnimation(world, pokemonEntity, pasturePos, *names)

    /** Backward-compat overload — uses the Pokemon's own block position as the cache key. */
    fun sendAnimationPublic(world: World, pokemonEntity: PokemonEntity, vararg names: String) =
        sendAnimation(world, pokemonEntity, pokemonEntity.blockPos, *names)

    private fun sendAnimation(world: World, pokemonEntity: PokemonEntity, pasturePos: BlockPos, vararg names: String) {
        if (world !is ServerWorld) return
        val speciesName = pokemonEntity.pokemon.species.name.lowercase().replace(" ", "_").replace("-", "_")
        val allNames = mutableSetOf<String>()
        for (name in names) {
            allNames.add(name)
            allNames.add("${speciesName}:${name}")
        }
        val packet = PlayPosableAnimationPacket(pokemonEntity.id, allNames, emptyList())
        for (player in NearbyPlayerCache.getPlayers(pasturePos)) {
            CobblemonNetwork.sendPacketToPlayer(player, packet)
        }
    }

    /** Cry-only — no animation, no success marking. */
    fun playCryOnly(world: World, pokemonEntity: PokemonEntity, pasturePos: BlockPos = pokemonEntity.blockPos) {
        if (world !is ServerWorld) return
        playCry(world, pokemonEntity, pasturePos)
    }

    private fun playCry(world: ServerWorld, pokemonEntity: PokemonEntity, pasturePos: BlockPos) {
        val pokemonId = pokemonEntity.pokemon.uuid
        val now = world.time
        val lastCry = lastCryTime[pokemonId] ?: 0L
        if (now - lastCry < CRY_COOLDOWN_TICKS) return
        lastCryTime[pokemonId] = now
        val speciesName = pokemonEntity.pokemon.species.name.lowercase().replace(" ", "_").replace("-", "_")
        val packet = PlayCryS2CPacket(speciesName, pokemonEntity.x, pokemonEntity.y, pokemonEntity.z)
        for (player in NearbyPlayerCache.getPlayers(pasturePos)) {
            PlatformPacketSender.sendS2C(player, packet)
        }
    }

    fun playSuccess(world: World, pokemonEntity: PokemonEntity, effectType: String, pasturePos: BlockPos = pokemonEntity.blockPos) {
        if (world !is ServerWorld) return
        playCry(world, pokemonEntity, pasturePos)

        when (effectType) {
            "harvest" -> sendAnimation(world, pokemonEntity, pasturePos, "tackle", "scratch", "pound", "physical")
            "water" -> sendAnimation(world, pokemonEntity, pasturePos, "watergun", "bubble", "spray", "special")
            "fire" -> sendAnimation(world, pokemonEntity, pasturePos, "ember", "flamethrower", "flame", "special")
            "combat" -> sendAnimation(world, pokemonEntity, pasturePos, "tackle", "bite", "crunch", "physical")
            "heal" -> {
                sendAnimation(world, pokemonEntity, pasturePos, "wish", "special")
                val x = pokemonEntity.x
                val y = pokemonEntity.y
                val h = pokemonEntity.height.toDouble()
                val z = pokemonEntity.z
                world.spawnParticles(ParticleTypes.HEART, x, y + h, z, 15, 0.6, 0.4, 0.6, 0.03)
            }
            "special" -> sendAnimation(world, pokemonEntity, pasturePos, "special")
            else -> sendAnimation(world, pokemonEntity, pasturePos, "special", "physical")
        }
    }

    /** Periodic working-particle tick. Disabled for everything except heal (TPS). */
    fun playWorking(world: World, pokemonEntity: PokemonEntity, effectType: String) {
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

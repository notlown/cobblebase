package notlown.cobblebase.neoforge

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import net.minecraft.util.math.BlockPos
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler
import net.neoforged.neoforge.network.registration.PayloadRegistrar
import notlown.cobblebase.core.BaseManager
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.DiscoveryRegistry
import notlown.cobblebase.core.LogManager
import notlown.cobblebase.core.SpeciesSkillOverrides
import notlown.cobblebase.core.SpeciesSkillRegistry
import notlown.cobblebase.core.net.AdminSpeciesRequestC2SPacket
import notlown.cobblebase.core.net.AdminSpeciesSyncS2CPacket
import notlown.cobblebase.core.net.AdminSpeciesUpdateC2SPacket
import notlown.cobblebase.core.net.DiscoveryRequestC2SPacket
import notlown.cobblebase.core.net.DiscoverySyncS2CPacket
import notlown.cobblebase.core.net.LogRequestC2SPacket
import notlown.cobblebase.core.net.LogSyncS2CPacket
import notlown.cobblebase.core.net.SkillAssignmentC2SPacket

@Mod("cobblebase")
class CobblebaseNeoForge(modBus: IEventBus) {

    init {
        Cobblebase.init()

        // Register mod bus events (networking)
        modBus.addListener(::onRegisterPayloads)

        // Register game events on the NeoForge event bus
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(::onServerStopping)
    }

    private fun onRegisterPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar: PayloadRegistrar = event.registrar("cobblebase")

        // C2S: Skill assignment
        registrar.playToServer(
            SkillAssignmentC2SPacket.ID,
            SkillAssignmentC2SPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                packet.handle(context.player() as net.minecraft.server.network.ServerPlayerEntity)
            }
        }

        // C2S: Log request
        registrar.playToServer(
            LogRequestC2SPacket.ID,
            LogRequestC2SPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                handleLogRequest(context.player() as net.minecraft.server.network.ServerPlayerEntity, packet)
            }
        }

        // S2C: Log sync
        registrar.playToClient(
            LogSyncS2CPacket.ID,
            LogSyncS2CPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                LogManager.setClientLogs(packet.entries)
            }
        }

        // C2S: Discovery request
        registrar.playToServer(
            DiscoveryRequestC2SPacket.ID,
            DiscoveryRequestC2SPacket.CODEC
        ) { _, context ->
            context.enqueueWork {
                val player = context.player() as net.minecraft.server.network.ServerPlayerEntity
                val discoveries = DiscoveryRegistry.getAllForSync()
                context.reply(DiscoverySyncS2CPacket(discoveries))
            }
        }

        // S2C: Discovery sync
        registrar.playToClient(
            DiscoverySyncS2CPacket.ID,
            DiscoverySyncS2CPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                DiscoveryRegistry.setClientDiscoveries(packet.discoveries)
            }
        }

        // C2S: Admin species request
        registrar.playToServer(
            AdminSpeciesRequestC2SPacket.ID,
            AdminSpeciesRequestC2SPacket.CODEC
        ) { _, context ->
            context.enqueueWork {
                val player = context.player() as net.minecraft.server.network.ServerPlayerEntity
                if (!player.hasPermissionLevel(2)) return@enqueueWork
                handleAdminSpeciesRequest(player, context)
            }
        }

        // S2C: Admin species sync
        registrar.playToClient(
            AdminSpeciesSyncS2CPacket.ID,
            AdminSpeciesSyncS2CPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                notlown.cobblebase.core.AdminDataCache.update(
                    packet.allSpecies,
                    packet.speciesSkills,
                    packet.overriddenSpecies
                )
            }
        }

        // C2S: Admin species update
        registrar.playToServer(
            AdminSpeciesUpdateC2SPacket.ID,
            AdminSpeciesUpdateC2SPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                packet.handle(context.player() as net.minecraft.server.network.ServerPlayerEntity)
            }
        }
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        val world = event.server.overworld
        BaseManager.load(world)
        LogManager.load(world)
        DiscoveryRegistry.load(world)
        SpeciesSkillOverrides.load(world)
    }

    private fun onServerStopping(event: ServerStoppingEvent) {
        val world = event.server.overworld
        BaseManager.save(world)
        LogManager.save(world)
        DiscoveryRegistry.save(world)
        SpeciesSkillOverrides.save(world)
    }

    /**
     * Handles a log request from the client.
     * Finds the nearest pasture block entity to the player and sends its logs.
     */
    private fun handleLogRequest(player: net.minecraft.server.network.ServerPlayerEntity, packet: LogRequestC2SPacket) {
        val world = player.serverWorld
        val playerPos = player.blockPos

        var nearestPos: BlockPos? = null
        var nearestDist = Double.MAX_VALUE
        val searchRadius = 16

        for (x in -searchRadius..searchRadius) {
            for (y in -searchRadius..searchRadius) {
                for (z in -searchRadius..searchRadius) {
                    val pos = playerPos.add(x, y, z)
                    val blockEntity = world.getBlockEntity(pos)
                    if (blockEntity is PokemonPastureBlockEntity) {
                        val dist = pos.getSquaredDistance(playerPos)
                        if (dist < nearestDist) {
                            nearestDist = dist
                            nearestPos = pos
                        }
                    }
                }
            }
        }

        val pasturePos = nearestPos
        if (pasturePos == null) {
            player.server?.execute {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, LogSyncS2CPacket(emptyList()))
            }
            return
        }

        val entries = LogManager.getEntries(pasturePos)
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, LogSyncS2CPacket(entries))
    }

    /**
     * Handles an admin species request. Gathers all Cobblemon species,
     * skill assignments, and override info, then sends to the requesting player.
     */
    private fun handleAdminSpeciesRequest(
        player: net.minecraft.server.network.ServerPlayerEntity,
        context: net.neoforged.neoforge.network.handling.IPayloadContext
    ) {
        val allSpecies = try {
            val cobblemonSpecies = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.species
                .map { it.name.lowercase() }
            val overrideSpecies = SpeciesSkillOverrides.getAllOverriddenSpecies()
            (cobblemonSpecies + overrideSpecies).distinct().sorted()
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to get Cobblemon species: ${e.message}")
            emptyList()
        }

        val assigned = SpeciesSkillRegistry.getAllAssigned()
        val speciesSkills = assigned.mapValues { (_, v) -> v.skills }
        val overridden = SpeciesSkillOverrides.getAllOverriddenSpecies()

        context.reply(AdminSpeciesSyncS2CPacket(allSpecies, speciesSkills, overridden))
    }
}

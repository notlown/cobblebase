package notlown.cobblebase.fabric

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.util.math.BlockPos
import notlown.cobblebase.core.BaseManager
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.DiscoveryRegistry
import notlown.cobblebase.core.LogManager
import notlown.cobblebase.core.JobConfigOverrides
import notlown.cobblebase.core.SkillRegistry
import notlown.cobblebase.core.SpeciesSkillOverrides
import notlown.cobblebase.core.SpeciesSkillRegistry
import notlown.cobblebase.core.net.AdminJobsRequestC2SPacket
import notlown.cobblebase.core.net.AdminJobsSyncS2CPacket
import notlown.cobblebase.core.net.AdminJobsUpdateC2SPacket
import notlown.cobblebase.core.net.AdminSpeciesRequestC2SPacket
import notlown.cobblebase.core.net.AdminSpeciesSyncS2CPacket
import notlown.cobblebase.core.net.AdminSpeciesUpdateC2SPacket
import notlown.cobblebase.core.net.DiscoveryRequestC2SPacket
import notlown.cobblebase.core.net.DiscoverySyncS2CPacket
import notlown.cobblebase.core.net.LogRequestC2SPacket
import notlown.cobblebase.core.net.LogSyncS2CPacket
import notlown.cobblebase.core.net.SkillAssignmentC2SPacket
import notlown.cobblebase.core.net.SkillAssignmentRequestC2SPacket
import notlown.cobblebase.core.net.SkillAssignmentSyncS2CPacket
import notlown.cobblebase.core.net.VersionHandshakeC2SPacket
import notlown.cobblebase.core.VersionChecker
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

object CobblebaseFabric : ModInitializer {
    override fun onInitialize() {
        Cobblebase.init()

        // Register C2S packet for version handshake
        PayloadTypeRegistry.playC2S().register(VersionHandshakeC2SPacket.ID, VersionHandshakeC2SPacket.CODEC)
        ServerPlayNetworking.registerGlobalReceiver(VersionHandshakeC2SPacket.ID) { packet, context ->
            context.server().execute {
                VersionChecker.onHandshake(context.player(), packet.version)
            }
        }

        // Register player join/leave events for version handshake
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            VersionChecker.onPlayerJoin(handler.player)
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            VersionChecker.onPlayerLeave(handler.player.uuid)
        }

        // Register C2S packet for skill assignments
        PayloadTypeRegistry.playC2S().register(SkillAssignmentC2SPacket.ID, SkillAssignmentC2SPacket.CODEC)
        ServerPlayNetworking.registerGlobalReceiver(SkillAssignmentC2SPacket.ID) { packet, context ->
            context.server().execute {
                packet.handle(context.player())
                // Broadcast updated assignments to all online players
                broadcastAssignmentSync(context.server())
            }
        }

        // Register S2C packet for skill assignment sync
        PayloadTypeRegistry.playS2C().register(SkillAssignmentSyncS2CPacket.ID, SkillAssignmentSyncS2CPacket.CODEC)

        // Register C2S packet for skill assignment requests (client opens GUI)
        PayloadTypeRegistry.playC2S().register(SkillAssignmentRequestC2SPacket.ID, SkillAssignmentRequestC2SPacket.CODEC)
        ServerPlayNetworking.registerGlobalReceiver(SkillAssignmentRequestC2SPacket.ID) { _, context ->
            context.server().execute {
                val syncPacket = SkillAssignmentSyncS2CPacket(BaseManager.getAllAssignments())
                ServerPlayNetworking.send(context.player(), syncPacket)
            }
        }

        // Register C2S packet for log requests
        PayloadTypeRegistry.playC2S().register(LogRequestC2SPacket.ID, LogRequestC2SPacket.CODEC)
        ServerPlayNetworking.registerGlobalReceiver(LogRequestC2SPacket.ID) { packet, context ->
            context.server().execute {
                handleLogRequest(context.player(), packet)
            }
        }

        // Register S2C packet for log sync
        PayloadTypeRegistry.playS2C().register(LogSyncS2CPacket.ID, LogSyncS2CPacket.CODEC)

        // Register C2S packet for discovery requests
        PayloadTypeRegistry.playC2S().register(DiscoveryRequestC2SPacket.ID, DiscoveryRequestC2SPacket.CODEC)
        ServerPlayNetworking.registerGlobalReceiver(DiscoveryRequestC2SPacket.ID) { _, context ->
            context.server().execute {
                val discoveries = DiscoveryRegistry.getAllForSync()
                ServerPlayNetworking.send(context.player(), DiscoverySyncS2CPacket(discoveries))
            }
        }

        // Register S2C packet for discovery sync
        PayloadTypeRegistry.playS2C().register(DiscoverySyncS2CPacket.ID, DiscoverySyncS2CPacket.CODEC)

        // Register C2S packet for admin species requests
        PayloadTypeRegistry.playC2S().register(AdminSpeciesRequestC2SPacket.ID, AdminSpeciesRequestC2SPacket.CODEC)
        ServerPlayNetworking.registerGlobalReceiver(AdminSpeciesRequestC2SPacket.ID) { _, context ->
            context.server().execute {
                val player = context.player()
                if (!player.hasPermissionLevel(2)) return@execute
                handleAdminSpeciesRequest(player)
            }
        }

        // Register S2C packet for admin species sync
        PayloadTypeRegistry.playS2C().register(AdminSpeciesSyncS2CPacket.ID, AdminSpeciesSyncS2CPacket.CODEC)

        // Register C2S packet for admin species updates
        PayloadTypeRegistry.playC2S().register(AdminSpeciesUpdateC2SPacket.ID, AdminSpeciesUpdateC2SPacket.CODEC)
        ServerPlayNetworking.registerGlobalReceiver(AdminSpeciesUpdateC2SPacket.ID) { packet, context ->
            context.server().execute {
                packet.handle(context.player())
            }
        }

        // Register C2S packet for admin jobs requests
        PayloadTypeRegistry.playC2S().register(AdminJobsRequestC2SPacket.ID, AdminJobsRequestC2SPacket.CODEC)
        ServerPlayNetworking.registerGlobalReceiver(AdminJobsRequestC2SPacket.ID) { _, context ->
            context.server().execute {
                val player = context.player()
                if (!player.hasPermissionLevel(2)) return@execute
                handleAdminJobsRequest(player)
            }
        }

        // Register S2C packet for admin jobs sync
        PayloadTypeRegistry.playS2C().register(AdminJobsSyncS2CPacket.ID, AdminJobsSyncS2CPacket.CODEC)

        // Register C2S packet for admin jobs updates
        PayloadTypeRegistry.playC2S().register(AdminJobsUpdateC2SPacket.ID, AdminJobsUpdateC2SPacket.CODEC)
        ServerPlayNetworking.registerGlobalReceiver(AdminJobsUpdateC2SPacket.ID) { packet, context ->
            context.server().execute {
                packet.handle(context.player())
            }
        }

        // Load assignments, logs, discoveries, and overrides when world starts
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            val world = server.overworld
            BaseManager.load(world)
            LogManager.load(world)
            DiscoveryRegistry.load(world)
            SpeciesSkillOverrides.load(world)
            JobConfigOverrides.load(world)
        }

        // Save assignments, logs, discoveries, and overrides when world stops
        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            val world = server.overworld
            BaseManager.save(world)
            LogManager.save(world)
            DiscoveryRegistry.save(world)
            SpeciesSkillOverrides.save(world)
            JobConfigOverrides.save(world)
        }
    }

    /**
     * Broadcasts current skill assignments to all online players.
     */
    private fun broadcastAssignmentSync(server: net.minecraft.server.MinecraftServer) {
        val syncPacket = SkillAssignmentSyncS2CPacket(BaseManager.getAllAssignments())
        for (player in server.playerManager.playerList) {
            ServerPlayNetworking.send(player, syncPacket)
        }
    }

    /**
     * Handles a log request from the client.
     * Finds the nearest pasture block entity to the player and sends its logs.
     */
    private fun handleLogRequest(player: net.minecraft.server.network.ServerPlayerEntity, packet: LogRequestC2SPacket) {
        val world = player.serverWorld
        val playerPos = player.blockPos

        // Search nearby block entities for a PokemonPastureBlockEntity
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
            // No pasture found, send empty logs
            ServerPlayNetworking.send(player, LogSyncS2CPacket(emptyList()))
            return
        }

        val entries = LogManager.getEntries(pasturePos)
        ServerPlayNetworking.send(player, LogSyncS2CPacket(entries))
    }

    /**
     * Handles an admin jobs request. Sends all job definitions and overrides to the requesting player.
     */
    private fun handleAdminJobsRequest(player: net.minecraft.server.network.ServerPlayerEntity) {
        val allJobs = SkillRegistry.getAll()
        val overrides = JobConfigOverrides.getAllOverrides()
        ServerPlayNetworking.send(player, AdminJobsSyncS2CPacket(allJobs, overrides))
    }

    /**
     * Handles an admin species request. Gathers all Cobblemon species,
     * skill assignments, and override info, then sends to the requesting player.
     */
    private fun handleAdminSpeciesRequest(player: net.minecraft.server.network.ServerPlayerEntity) {
        // Get all species from Cobblemon + any custom overrides (fakemons etc.)
        val allSpecies = try {
            val cobblemonSpecies = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.species
                .map { it.name.lowercase() }
            val overrideSpecies = SpeciesSkillOverrides.getAllOverriddenSpecies()
            (cobblemonSpecies + overrideSpecies).distinct().sorted()
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to get Cobblemon species: ${e.message}")
            emptyList()
        }

        // Get all assigned species skills
        val assigned = SpeciesSkillRegistry.getAllAssigned()
        val speciesSkills = assigned.mapValues { (_, v) -> v.skills }

        // Get overridden species
        val overridden = SpeciesSkillOverrides.getAllOverriddenSpecies()

        ServerPlayNetworking.send(player, AdminSpeciesSyncS2CPacket(allSpecies, speciesSkills, overridden))
    }
}

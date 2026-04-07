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
import notlown.cobblebase.core.ContainerHelperRegistry
import notlown.cobblebase.core.VersionChecker
import net.neoforged.neoforge.event.entity.player.PlayerEvent

@Mod("cobblebase")
class CobblebaseNeoForge(modBus: IEventBus) {

    init {
        Cobblebase.init()

        // Register platform-specific container helper (NeoForge Capabilities)
        ContainerHelperRegistry.instance = NeoForgeContainerHelper()

        // Register mod bus events (networking)
        modBus.addListener(::onRegisterPayloads)

        // Register game events on the NeoForge event bus
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(::onServerStopping)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(::onPlayerLoggedOut)
    }

    private fun onRegisterPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar: PayloadRegistrar = event.registrar("cobblebase")

        // C2S: Version handshake
        registrar.playToServer(
            VersionHandshakeC2SPacket.ID,
            VersionHandshakeC2SPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                VersionChecker.onHandshake(context.player() as net.minecraft.server.network.ServerPlayerEntity, packet.version)
            }
        }

        // C2S: Skill assignment
        registrar.playToServer(
            SkillAssignmentC2SPacket.ID,
            SkillAssignmentC2SPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                val player = context.player() as net.minecraft.server.network.ServerPlayerEntity
                packet.handle(player)
                // Broadcast updated assignments to all online players
                broadcastAssignmentSync(player.server)
            }
        }

        // S2C: Skill assignment sync
        registrar.playToClient(
            SkillAssignmentSyncS2CPacket.ID,
            SkillAssignmentSyncS2CPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                notlown.cobblebase.core.AssignmentCache.update(
                    packet.assignments.mapValues { (_, v) -> if (v.isEmpty()) null else v }
                )
            }
        }

        // C2S: Skill assignment request (client opens GUI)
        registrar.playToServer(
            SkillAssignmentRequestC2SPacket.ID,
            SkillAssignmentRequestC2SPacket.CODEC
        ) { _, context ->
            context.enqueueWork {
                val player = context.player() as net.minecraft.server.network.ServerPlayerEntity
                val syncPacket = SkillAssignmentSyncS2CPacket(BaseManager.getAllAssignments())
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, syncPacket)
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
                    packet.overriddenSpecies
                )
            }
        }

        // C2S: Lazy-load skills request
        registrar.playToServer(
            notlown.cobblebase.core.net.AdminSpeciesSkillsRequestC2SPacket.ID,
            notlown.cobblebase.core.net.AdminSpeciesSkillsRequestC2SPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                val player = context.player() as net.minecraft.server.network.ServerPlayerEntity
                if (!player.hasPermissionLevel(2)) return@enqueueWork
                val skills = SpeciesSkillRegistry.getSkills(packet.species)?.skills ?: emptyList()
                context.reply(notlown.cobblebase.core.net.AdminSpeciesSkillsResponseS2CPacket(packet.species, skills))
            }
        }

        // S2C: Lazy-load skills response
        registrar.playToClient(
            notlown.cobblebase.core.net.AdminSpeciesSkillsResponseS2CPacket.ID,
            notlown.cobblebase.core.net.AdminSpeciesSkillsResponseS2CPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                notlown.cobblebase.core.AdminDataCache.setSpeciesSkills(packet.species, packet.skills)
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

        // C2S: Admin jobs request
        registrar.playToServer(
            AdminJobsRequestC2SPacket.ID,
            AdminJobsRequestC2SPacket.CODEC
        ) { _, context ->
            context.enqueueWork {
                val player = context.player() as net.minecraft.server.network.ServerPlayerEntity
                if (!player.hasPermissionLevel(2)) return@enqueueWork
                handleAdminJobsRequest(player, context)
            }
        }

        // S2C: Admin jobs sync
        registrar.playToClient(
            AdminJobsSyncS2CPacket.ID,
            AdminJobsSyncS2CPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                notlown.cobblebase.core.AdminJobDataCache.update(
                    packet.jobs,
                    packet.overrides
                )
            }
        }

        // C2S: Admin jobs update
        registrar.playToServer(
            AdminJobsUpdateC2SPacket.ID,
            AdminJobsUpdateC2SPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                packet.handle(context.player() as net.minecraft.server.network.ServerPlayerEntity)
            }
        }

        // C2S: Admin loot request
        registrar.playToServer(
            notlown.cobblebase.core.net.AdminLootRequestC2SPacket.ID,
            notlown.cobblebase.core.net.AdminLootRequestC2SPacket.CODEC
        ) { _, context ->
            context.enqueueWork {
                val player = context.player() as net.minecraft.server.network.ServerPlayerEntity
                if (!player.hasPermissionLevel(2)) return@enqueueWork
                val (tables, defaults) = notlown.cobblebase.core.LootSyncBuilder.buildSnapshot()
                val overridden = notlown.cobblebase.core.LootOverrides.getAll().keys.toSet()
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    player,
                    notlown.cobblebase.core.net.AdminLootSyncS2CPacket(tables, overridden, defaults)
                )
            }
        }

        // S2C: Admin loot sync
        registrar.playToClient(
            notlown.cobblebase.core.net.AdminLootSyncS2CPacket.ID,
            notlown.cobblebase.core.net.AdminLootSyncS2CPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                notlown.cobblebase.core.AdminLootDataCache.update(
                    packet.tables, packet.overriddenIds, packet.defaultItemIds
                )
            }
        }

        // C2S: Admin loot update
        registrar.playToServer(
            notlown.cobblebase.core.net.AdminLootUpdateC2SPacket.ID,
            notlown.cobblebase.core.net.AdminLootUpdateC2SPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                val player = context.player() as net.minecraft.server.network.ServerPlayerEntity
                packet.handle(player)
                val (tables, defaults) = notlown.cobblebase.core.LootSyncBuilder.buildSnapshot()
                val overridden = notlown.cobblebase.core.LootOverrides.getAll().keys.toSet()
                val sync = notlown.cobblebase.core.net.AdminLootSyncS2CPacket(tables, overridden, defaults)
                for (p in player.server.playerManager.playerList) {
                    if (p.hasPermissionLevel(2)) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, sync)
                    }
                }
            }
        }

        // S2C: General settings sync (Discord URL, enabled)
        registrar.playToClient(
            notlown.cobblebase.core.net.GeneralSettingsSyncS2CPacket.ID,
            notlown.cobblebase.core.net.GeneralSettingsSyncS2CPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                notlown.cobblebase.core.GeneralSettingsCache.update(packet.discordUrl, packet.discordEnabled)
            }
        }

        // C2S: General settings update (admin only)
        registrar.playToServer(
            notlown.cobblebase.core.net.GeneralSettingsUpdateC2SPacket.ID,
            notlown.cobblebase.core.net.GeneralSettingsUpdateC2SPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                val player = context.player() as net.minecraft.server.network.ServerPlayerEntity
                if (!player.hasPermissionLevel(2)) return@enqueueWork
                val newSettings = notlown.cobblebase.core.GeneralSettings.Settings(
                    discordUrl = packet.discordUrl,
                    discordEnabled = packet.discordEnabled
                )
                notlown.cobblebase.core.GeneralSettings.setSettings(newSettings)
                notlown.cobblebase.core.GeneralSettings.save(player.serverWorld)
                val syncPacket = notlown.cobblebase.core.net.GeneralSettingsSyncS2CPacket(
                    packet.discordUrl, packet.discordEnabled
                )
                for (p in player.server.playerManager.playerList) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, syncPacket)
                }
            }
        }
    }

    private fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? net.minecraft.server.network.ServerPlayerEntity ?: return
        VersionChecker.onPlayerJoin(player)
        // Sync general settings to joining player
        val s = notlown.cobblebase.core.GeneralSettings.getSettings()
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            player,
            notlown.cobblebase.core.net.GeneralSettingsSyncS2CPacket(s.discordUrl, s.discordEnabled)
        )
    }

    private fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? net.minecraft.server.network.ServerPlayerEntity ?: return
        VersionChecker.onPlayerLeave(player.uuid)
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        val world = event.server.overworld
        BaseManager.load(world)
        LogManager.load(world)
        DiscoveryRegistry.load(world)
        SpeciesSkillOverrides.load(world)
        JobConfigOverrides.load(world)
        notlown.cobblebase.core.GeneralSettings.load(world)
        notlown.cobblebase.core.LootOverrides.load(world)
        notlown.cobblebase.core.SpawnData.loadFromCobblemonSpawnPool()
    }

    private fun onServerStopping(event: ServerStoppingEvent) {
        val world = event.server.overworld
        BaseManager.save(world)
        LogManager.save(world)
        DiscoveryRegistry.save(world)
        SpeciesSkillOverrides.save(world)
        JobConfigOverrides.save(world)
        notlown.cobblebase.core.GeneralSettings.save(world)
        notlown.cobblebase.core.LootOverrides.save(world)
    }

    /**
     * Broadcasts current skill assignments to all online players.
     */
    private fun broadcastAssignmentSync(server: net.minecraft.server.MinecraftServer) {
        val syncPacket = SkillAssignmentSyncS2CPacket(BaseManager.getAllAssignments())
        for (player in server.playerManager.playerList) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, syncPacket)
        }
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
     * Handles an admin jobs request. Sends all job definitions and overrides to the requesting player.
     */
    private fun handleAdminJobsRequest(
        player: net.minecraft.server.network.ServerPlayerEntity,
        context: net.neoforged.neoforge.network.handling.IPayloadContext
    ) {
        val allJobs = SkillRegistry.getAll()
        val overrides = JobConfigOverrides.getAllOverrides()
        context.reply(AdminJobsSyncS2CPacket(allJobs, overrides))
    }

    /**
     * Handles an admin species request. Gathers all Cobblemon species,
     * skill assignments, and override info, then sends to the requesting player.
     */
    private fun handleAdminSpeciesRequest(
        player: net.minecraft.server.network.ServerPlayerEntity,
        context: net.neoforged.neoforge.network.handling.IPayloadContext
    ) {
        // Filter against Cobblemon's runtime species registry so the admin GUI
        // only lists mons that are actually installed on this server (so e.g.
        // fakemons from addons you don't have are hidden). Override entries
        // are always kept regardless of registry presence.
        val allSpecies = try {
            val installed = notlown.cobblebase.core.CobblemonSpeciesHelper.getInstalledSpeciesNames()
            val registrySpecies = SpeciesSkillRegistry.getAllAssigned().keys.toList()
            val overrideSpecies = SpeciesSkillOverrides.getAllOverriddenSpecies()
            val merged = (registrySpecies + overrideSpecies).distinct()
            if (installed.isEmpty()) merged.sorted()
            else merged.filter { it.lowercase() in installed || it in overrideSpecies }.sorted()
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to get species list: ${e.message}")
            emptyList()
        }

        val overridden = SpeciesSkillOverrides.getAllOverriddenSpecies()
        context.reply(AdminSpeciesSyncS2CPacket(allSpecies, overridden))
    }
}

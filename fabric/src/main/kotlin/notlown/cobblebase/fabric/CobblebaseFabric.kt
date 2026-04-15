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
import notlown.cobblebase.core.SpawnData
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
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

object CobblebaseFabric : ModInitializer {
    override fun onInitialize() {
        Cobblebase.init()

        // Register platform-specific container helper (Fabric Transfer API)
        ContainerHelperRegistry.instance = FabricContainerHelper()

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
                // Re-broadcast every override to every player so the Pasture
                // Skills tab picks up the change without a reconnect.
                val sync = notlown.cobblebase.core.net.SpeciesOverrideSyncS2CPacket(
                    SpeciesSkillOverrides.getAllOverriddenSpecies()
                        .associateWith { SpeciesSkillOverrides.getOverride(it) ?: emptyList() }
                )
                for (p in context.player().server.playerManager.playerList) {
                    ServerPlayNetworking.send(p, sync)
                }
            }
        }

        // Register S2C packet for species override sync (sent on join + after admin updates)
        PayloadTypeRegistry.playS2C().register(
            notlown.cobblebase.core.net.SpeciesOverrideSyncS2CPacket.ID,
            notlown.cobblebase.core.net.SpeciesOverrideSyncS2CPacket.CODEC
        )

        // Register S2C packet for job override sync (sent on join + after admin updates)
        PayloadTypeRegistry.playS2C().register(
            notlown.cobblebase.core.net.JobOverrideSyncS2CPacket.ID,
            notlown.cobblebase.core.net.JobOverrideSyncS2CPacket.CODEC
        )

        // Register C2S packet for lazy-loading individual species skills
        PayloadTypeRegistry.playC2S().register(notlown.cobblebase.core.net.AdminSpeciesSkillsRequestC2SPacket.ID, notlown.cobblebase.core.net.AdminSpeciesSkillsRequestC2SPacket.CODEC)
        ServerPlayNetworking.registerGlobalReceiver(notlown.cobblebase.core.net.AdminSpeciesSkillsRequestC2SPacket.ID) { packet, context ->
            context.server().execute {
                val player = context.player()
                if (!player.hasPermissionLevel(2)) return@execute
                val skills = SpeciesSkillRegistry.getSkills(packet.species)?.skills ?: emptyList()
                val produce = notlown.cobblebase.core.executors.ProducerExecutor.getProduceEntry(packet.species)
                ServerPlayNetworking.send(player, notlown.cobblebase.core.net.AdminSpeciesSkillsResponseS2CPacket(
                    packet.species, skills,
                    produce?.itemId, produce?.count ?: 0, produce?.displayName,
                    produce?.cooldownSeconds ?: 0
                ))
            }
        }

        // Register S2C packet for lazy-loading skills response
        PayloadTypeRegistry.playS2C().register(notlown.cobblebase.core.net.AdminSpeciesSkillsResponseS2CPacket.ID, notlown.cobblebase.core.net.AdminSpeciesSkillsResponseS2CPacket.CODEC)

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
                // Re-broadcast every job override to every player so the
                // Pasture Skills tab hides jobs the admin just disabled.
                val sync = notlown.cobblebase.core.net.JobOverrideSyncS2CPacket(
                    JobConfigOverrides.getAllOverrides()
                )
                for (p in context.player().server.playerManager.playerList) {
                    ServerPlayNetworking.send(p, sync)
                }
            }
        }

        // Register loot table admin packets
        PayloadTypeRegistry.playC2S().register(
            notlown.cobblebase.core.net.AdminLootRequestC2SPacket.ID,
            notlown.cobblebase.core.net.AdminLootRequestC2SPacket.CODEC
        )
        PayloadTypeRegistry.playS2C().register(
            notlown.cobblebase.core.net.AdminLootSyncS2CPacket.ID,
            notlown.cobblebase.core.net.AdminLootSyncS2CPacket.CODEC
        )
        PayloadTypeRegistry.playC2S().register(
            notlown.cobblebase.core.net.AdminLootUpdateC2SPacket.ID,
            notlown.cobblebase.core.net.AdminLootUpdateC2SPacket.CODEC
        )
        ServerPlayNetworking.registerGlobalReceiver(notlown.cobblebase.core.net.AdminLootRequestC2SPacket.ID) { _, context ->
            context.server().execute {
                val player = context.player()
                if (!player.hasPermissionLevel(2)) return@execute
                val (tables, defaults) = notlown.cobblebase.core.LootSyncBuilder.buildSnapshot()
                val overridden = notlown.cobblebase.core.LootOverrides.getAll().keys.toSet()
                ServerPlayNetworking.send(player, notlown.cobblebase.core.net.AdminLootSyncS2CPacket(tables, overridden, defaults))
            }
        }
        ServerPlayNetworking.registerGlobalReceiver(notlown.cobblebase.core.net.AdminLootUpdateC2SPacket.ID) { packet, context ->
            context.server().execute {
                packet.handle(context.player())
                // Re-broadcast updated state to all OPs viewing the GUI (cheap and rare)
                val (tables, defaults) = notlown.cobblebase.core.LootSyncBuilder.buildSnapshot()
                val overridden = notlown.cobblebase.core.LootOverrides.getAll().keys.toSet()
                val sync = notlown.cobblebase.core.net.AdminLootSyncS2CPacket(tables, overridden, defaults)
                for (p in context.player().server.playerManager.playerList) {
                    if (p.hasPermissionLevel(2)) ServerPlayNetworking.send(p, sync)
                }
            }
        }

        // Register general settings packets
        PayloadTypeRegistry.playS2C().register(
            notlown.cobblebase.core.net.GeneralSettingsSyncS2CPacket.ID,
            notlown.cobblebase.core.net.GeneralSettingsSyncS2CPacket.CODEC
        )
        PayloadTypeRegistry.playC2S().register(
            notlown.cobblebase.core.net.GeneralSettingsUpdateC2SPacket.ID,
            notlown.cobblebase.core.net.GeneralSettingsUpdateC2SPacket.CODEC
        )
        ServerPlayNetworking.registerGlobalReceiver(notlown.cobblebase.core.net.GeneralSettingsUpdateC2SPacket.ID) { packet, context ->
            context.server().execute {
                val player = context.player()
                if (!player.hasPermissionLevel(2)) return@execute
                val newSettings = notlown.cobblebase.core.GeneralSettings.Settings(
                    discordUrl = packet.discordUrl,
                    discordEnabled = packet.discordEnabled
                )
                notlown.cobblebase.core.GeneralSettings.setSettings(newSettings)
                notlown.cobblebase.core.GeneralSettings.save(player.serverWorld)
                // Broadcast updated settings to ALL players
                for (p in player.server.playerManager.playerList) {
                    ServerPlayNetworking.send(p, notlown.cobblebase.core.net.GeneralSettingsSyncS2CPacket(
                        packet.discordUrl, packet.discordEnabled
                    ))
                }
            }
        }

        // --- Workshop packets ---

        // C2S: Request recipe list + workshop state
        PayloadTypeRegistry.playC2S().register(
            notlown.cobblebase.core.net.WorkshopRequestC2SPacket.ID,
            notlown.cobblebase.core.net.WorkshopRequestC2SPacket.CODEC
        )
        ServerPlayNetworking.registerGlobalReceiver(notlown.cobblebase.core.net.WorkshopRequestC2SPacket.ID) { _, context ->
            context.server().execute {
                val player = context.player()
                val world = player.serverWorld
                // Send recipe list
                val recipes = notlown.cobblebase.core.RecipeHelper.getAllSimplifiedRecipes(world)
                val recipeDTOs = recipes.map { r ->
                    notlown.cobblebase.core.net.RecipeListSyncS2CPacket.RecipeDTO(
                        r.recipeId, r.outputItemId, r.outputCount, r.outputDisplayName, r.inputs, r.category
                    )
                }
                ServerPlayNetworking.send(player, notlown.cobblebase.core.net.RecipeListSyncS2CPacket(recipeDTOs))
                // Send workshop state
                val projects = notlown.cobblebase.core.WorkshopManager.getAllProjects()
                val projectDTOs = projects.mapValues { (_, proj) ->
                    val recipe = notlown.cobblebase.core.RecipeHelper.getRecipeById(world, proj.recipeId)
                    val required = if (recipe != null) {
                        notlown.cobblebase.core.RecipeHelper.getRequiredMaterials(recipe)
                            .map { (item, count) -> net.minecraft.registry.Registries.ITEM.getId(item).toString() to count }
                            .toMap()
                    } else emptyMap()
                    notlown.cobblebase.core.net.WorkshopSyncS2CPacket.ProjectDTO(
                        proj.recipeId, proj.gatheredItems, proj.phase.name, required
                    )
                }
                ServerPlayNetworking.send(player, notlown.cobblebase.core.net.WorkshopSyncS2CPacket(projectDTOs))
            }
        }

        // S2C: Recipe list
        PayloadTypeRegistry.playS2C().register(
            notlown.cobblebase.core.net.RecipeListSyncS2CPacket.ID,
            notlown.cobblebase.core.net.RecipeListSyncS2CPacket.CODEC
        )

        // S2C: Workshop state sync
        PayloadTypeRegistry.playS2C().register(
            notlown.cobblebase.core.net.WorkshopSyncS2CPacket.ID,
            notlown.cobblebase.core.net.WorkshopSyncS2CPacket.CODEC
        )

        // C2S: Select project
        PayloadTypeRegistry.playC2S().register(
            notlown.cobblebase.core.net.WorkshopSelectC2SPacket.ID,
            notlown.cobblebase.core.net.WorkshopSelectC2SPacket.CODEC
        )
        ServerPlayNetworking.registerGlobalReceiver(notlown.cobblebase.core.net.WorkshopSelectC2SPacket.ID) { packet, context ->
            context.server().execute {
                packet.handle(context.player())
            }
        }

        // Send general settings to player on join
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val s = notlown.cobblebase.core.GeneralSettings.getSettings()
            ServerPlayNetworking.send(handler.player, notlown.cobblebase.core.net.GeneralSettingsSyncS2CPacket(
                s.discordUrl, s.discordEnabled
            ))
            // Sync all species skill overrides so the client's Pasture Skills
            // tab sees the admin-set skill set instead of the bundled default.
            val overrideSync = notlown.cobblebase.core.net.SpeciesOverrideSyncS2CPacket(
                SpeciesSkillOverrides.getAllOverriddenSpecies()
                    .associateWith { SpeciesSkillOverrides.getOverride(it) ?: emptyList() }
            )
            ServerPlayNetworking.send(handler.player, overrideSync)
            // Sync all job config overrides so disabled jobs are hidden from
            // the Pasture Skills tab.
            ServerPlayNetworking.send(
                handler.player,
                notlown.cobblebase.core.net.JobOverrideSyncS2CPacket(JobConfigOverrides.getAllOverrides())
            )
        }

        // Load assignments, logs, discoveries, and overrides when world starts
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            val world = server.overworld
            BaseManager.load(world)
            LogManager.load(world)
            DiscoveryRegistry.load(world)
            SpeciesSkillOverrides.load(world)
            JobConfigOverrides.load(world)
            notlown.cobblebase.core.ProducerOverrides.load(world)
            notlown.cobblebase.core.WorkshopManager.load(world)
            notlown.cobblebase.core.GeneralSettings.load(world)
            notlown.cobblebase.core.LootOverrides.load(world)
            // Load spawn buckets from Cobblemon's actual spawn pool
            SpawnData.loadFromCobblemonSpawnPool()
        }

        // Save assignments, logs, discoveries, and overrides when world stops
        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            val world = server.overworld
            BaseManager.save(world)
            LogManager.save(world)
            DiscoveryRegistry.save(world)
            SpeciesSkillOverrides.save(world)
            JobConfigOverrides.save(world)
            notlown.cobblebase.core.ProducerOverrides.save(world)
            notlown.cobblebase.core.WorkshopManager.save(world)
            notlown.cobblebase.core.GeneralSettings.save(world)
            notlown.cobblebase.core.LootOverrides.save(world)
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
        // Lazy loading: only send species names + override flags upfront
        // Skills are loaded on demand via AdminSpeciesSkillsRequestC2SPacket
        // Filter against Cobblemon's runtime species registry so the admin GUI
        // only lists mons that are actually installed on this server (so e.g.
        // fakemons from addons you don't have are hidden). Override entries
        // are always kept regardless of registry presence.
        val allSpecies = try {
            val installed = notlown.cobblebase.core.CobblemonSpeciesHelper.getInstalledSpeciesNames()
            val registrySpecies = SpeciesSkillRegistry.getAllAssigned().keys.toList()
            val overrideSpecies = SpeciesSkillOverrides.getAllOverriddenSpecies()
            val merged = (registrySpecies + overrideSpecies).distinct()
            // If we somehow couldn't get the installed set, fall back to showing everything
            if (installed.isEmpty()) merged.sorted()
            else merged.filter { it.lowercase() in installed || it in overrideSpecies }.sorted()
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to get species list: ${e.message}")
            emptyList()
        }

        val overridden = SpeciesSkillOverrides.getAllOverriddenSpecies()
        ServerPlayNetworking.send(player, AdminSpeciesSyncS2CPacket(allSpecies, overridden))
    }
}

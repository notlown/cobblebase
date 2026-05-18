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

        // S2C: Cry playback — client-side decision so cryVolume/cryEnabled actually apply.
        registrar.playToClient(
            notlown.cobblebase.core.net.PlayCryS2CPacket.ID,
            notlown.cobblebase.core.net.PlayCryS2CPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                if (!notlown.cobblebase.core.CobblebaseConfig.cryEnabled) return@enqueueWork
                val volume = notlown.cobblebase.core.CobblebaseConfig.cryVolume
                if (volume <= 0) return@enqueueWork
                val cryId = net.minecraft.util.Identifier.of("cobblebase", "pokemon.${packet.speciesName}.cry")
                val soundEvent = net.minecraft.registry.Registries.SOUND_EVENT.get(cryId) ?: return@enqueueWork
                val mc = net.minecraft.client.MinecraftClient.getInstance()
                val world = mc.world ?: return@enqueueWork
                world.playSound(
                    packet.x, packet.y, packet.z,
                    soundEvent,
                    net.minecraft.sound.SoundCategory.NEUTRAL,
                    volume / 100f, 1.0f, false
                )
            }
        }
        // Common-side platform sender hook — SkillEffects dispatches through this.
        notlown.cobblebase.core.PlatformPacketSender.sendS2C = { player, payload ->
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload)
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
                val produce = notlown.cobblebase.core.executors.ProducerExecutor.getProduceEntry(packet.species)
                context.reply(notlown.cobblebase.core.net.AdminSpeciesSkillsResponseS2CPacket(
                    packet.species, skills,
                    produce?.itemId, produce?.count ?: 0, produce?.displayName,
                    produce?.cooldownSeconds ?: 0
                ))
            }
        }

        // S2C: Lazy-load skills response
        registrar.playToClient(
            notlown.cobblebase.core.net.AdminSpeciesSkillsResponseS2CPacket.ID,
            notlown.cobblebase.core.net.AdminSpeciesSkillsResponseS2CPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                notlown.cobblebase.core.AdminDataCache.setSpeciesSkills(packet.species, packet.skills)
                val pItemId = packet.producerItemId
                if (pItemId != null) {
                    notlown.cobblebase.core.AdminDataCache.setSpeciesProducer(packet.species,
                        notlown.cobblebase.core.AdminDataCache.ProducerData(pItemId, packet.producerCount, packet.producerDisplayName ?: "", packet.producerCooldown))
                } else {
                    notlown.cobblebase.core.AdminDataCache.setSpeciesProducer(packet.species, null)
                }
            }
        }

        // C2S: Admin species update
        registrar.playToServer(
            AdminSpeciesUpdateC2SPacket.ID,
            AdminSpeciesUpdateC2SPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                val player = context.player() as net.minecraft.server.network.ServerPlayerEntity
                packet.handle(player)
                // Re-broadcast every override to every player so their Pasture
                // Skills tab picks up the change without a reconnect.
                val sync = notlown.cobblebase.core.net.SpeciesOverrideSyncS2CPacket(
                    SpeciesSkillOverrides.getAllOverriddenSpecies()
                        .associateWith { SpeciesSkillOverrides.getOverride(it) ?: emptyList() }
                )
                for (p in player.server.playerManager.playerList) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, sync)
                }
            }
        }

        // S2C: Species override sync
        registrar.playToClient(
            notlown.cobblebase.core.net.SpeciesOverrideSyncS2CPacket.ID,
            notlown.cobblebase.core.net.SpeciesOverrideSyncS2CPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                for ((species, skills) in packet.overrides) {
                    notlown.cobblebase.core.SpeciesSkillRegistry.register(
                        notlown.cobblebase.core.SpeciesSkills(species, skills)
                    )
                }
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
                val player = context.player() as net.minecraft.server.network.ServerPlayerEntity
                packet.handle(player)
                // Re-broadcast every job override to every player so the
                // Pasture Skills tab hides jobs the admin just disabled.
                val sync = notlown.cobblebase.core.net.JobOverrideSyncS2CPacket(
                    JobConfigOverrides.getAllOverrides()
                )
                for (p in player.server.playerManager.playerList) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, sync)
                }
            }
        }

        // S2C: Job override sync
        registrar.playToClient(
            notlown.cobblebase.core.net.JobOverrideSyncS2CPacket.ID,
            notlown.cobblebase.core.net.JobOverrideSyncS2CPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                notlown.cobblebase.core.JobConfigOverrides.updateAll(packet.overrides)
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
                notlown.cobblebase.core.GeneralSettingsCache.update(
                    packet.discordUrl, packet.discordEnabled, packet.pokeWikiEnabled,
                    packet.pastureRange, packet.maxWorkingPokemonPerPasture
                )
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
                    discordEnabled = packet.discordEnabled,
                    pokeWikiEnabled = packet.pokeWikiEnabled,
                    pastureRange = packet.pastureRange.coerceIn(5, 30),
                    maxWorkingPokemonPerPasture = packet.maxWorkingPokemonPerPasture.coerceIn(0, 64)
                )
                notlown.cobblebase.core.GeneralSettings.setSettings(newSettings)
                notlown.cobblebase.core.GeneralSettings.save(player.serverWorld)
                val syncPacket = notlown.cobblebase.core.net.GeneralSettingsSyncS2CPacket(
                    newSettings.discordUrl, newSettings.discordEnabled, newSettings.pokeWikiEnabled,
                    newSettings.pastureRange, newSettings.maxWorkingPokemonPerPasture
                )
                for (p in player.server.playerManager.playerList) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, syncPacket)
                }
            }
        }
        // --- Workshop packets ---

        // C2S: Request recipe list + workshop state
        registrar.playToServer(
            notlown.cobblebase.core.net.WorkshopRequestC2SPacket.ID,
            notlown.cobblebase.core.net.WorkshopRequestC2SPacket.CODEC
        ) { _, context ->
            context.enqueueWork {
                val player = context.player() as net.minecraft.server.network.ServerPlayerEntity
                val world = player.serverWorld
                val recipes = notlown.cobblebase.core.RecipeHelper.getAllSimplifiedRecipes(world)
                val recipeDTOs = recipes.map { r ->
                    notlown.cobblebase.core.net.RecipeListSyncS2CPacket.RecipeDTO(
                        r.recipeId, r.outputItemId, r.outputCount, r.outputDisplayName, r.inputs, r.category
                    )
                }
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    notlown.cobblebase.core.net.RecipeListSyncS2CPacket(recipeDTOs))
                val projects = notlown.cobblebase.core.WorkshopManager.getAllProjects()
                val projectDTOs = projects.mapValues { entry ->
                    val pokemonId = entry.key
                    val proj = entry.value
                    val recipe = notlown.cobblebase.core.RecipeHelper.getRecipeById(world, proj.recipeId)
                    val required = if (recipe != null) {
                        notlown.cobblebase.core.RecipeHelper.getRequiredMaterials(recipe)
                            .map { (item, count) -> net.minecraft.registry.Registries.ITEM.getId(item).toString() to count }
                            .toMap()
                    } else emptyMap()
                    notlown.cobblebase.core.net.WorkshopSyncS2CPacket.ProjectDTO(
                        proj.recipeId, proj.gatheredItems, proj.phase.name, required,
                        notlown.cobblebase.core.WorkshopManager.getCraftCount(pokemonId)
                    )
                }
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    notlown.cobblebase.core.net.WorkshopSyncS2CPacket(projectDTOs))
            }
        }

        // S2C: Recipe list
        registrar.playToClient(
            notlown.cobblebase.core.net.RecipeListSyncS2CPacket.ID,
            notlown.cobblebase.core.net.RecipeListSyncS2CPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                notlown.cobblebase.core.WorkshopCache.updateRecipes(packet.recipes)
            }
        }

        // S2C: Workshop state sync
        registrar.playToClient(
            notlown.cobblebase.core.net.WorkshopSyncS2CPacket.ID,
            notlown.cobblebase.core.net.WorkshopSyncS2CPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                val states = packet.projects.mapValues { (_, dto) ->
                    notlown.cobblebase.core.WorkshopCache.ProjectState(
                        dto.recipeId, dto.gatheredItems, dto.phase, dto.requiredItems, dto.craftCount
                    )
                }
                notlown.cobblebase.core.WorkshopCache.updateProjects(states)
            }
        }

        // C2S: Select project
        registrar.playToServer(
            notlown.cobblebase.core.net.WorkshopSelectC2SPacket.ID,
            notlown.cobblebase.core.net.WorkshopSelectC2SPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                packet.handle(context.player() as net.minecraft.server.network.ServerPlayerEntity)
            }
        }

        // --- Builder packets ---

        // C2S: client requests the template list
        registrar.playToServer(
            notlown.cobblebase.core.net.StructureTemplateListRequestC2SPacket.ID,
            notlown.cobblebase.core.net.StructureTemplateListRequestC2SPacket.CODEC
        ) { _, context ->
            context.enqueueWork {
                val player = context.player() as net.minecraft.server.network.ServerPlayerEntity
                val templates = notlown.cobblebase.core.StructureTemplateRegistry
                    .getAll(player.server)
                    .map {
                        notlown.cobblebase.core.net.StructureTemplateListSyncS2CPacket.TemplateDTO(
                            it.id.toString(), it.displayName, it.sizeX, it.sizeY, it.sizeZ,
                            it.topBlocks, it.totalBlockCount
                        )
                    }
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    notlown.cobblebase.core.net.StructureTemplateListSyncS2CPacket(templates))
            }
        }

        // S2C: template list
        registrar.playToClient(
            notlown.cobblebase.core.net.StructureTemplateListSyncS2CPacket.ID,
            notlown.cobblebase.core.net.StructureTemplateListSyncS2CPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                notlown.cobblebase.core.BuilderCache.update(packet.templates)
            }
        }

        // C2S: confirm/clear a build job for a pasture
        registrar.playToServer(
            notlown.cobblebase.core.net.BuildJobConfigureC2SPacket.ID,
            notlown.cobblebase.core.net.BuildJobConfigureC2SPacket.CODEC
        ) { packet, context ->
            context.enqueueWork {
                packet.handle(context.player() as net.minecraft.server.network.ServerPlayerEntity)
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
            notlown.cobblebase.core.net.GeneralSettingsSyncS2CPacket(
                s.discordUrl, s.discordEnabled, s.pokeWikiEnabled,
                s.pastureRange, s.maxWorkingPokemonPerPasture
            )
        )
        // Sync all species skill overrides so the Pasture Skills tab sees
        // the admin-set skill set instead of the bundled default.
        val overrideSync = notlown.cobblebase.core.net.SpeciesOverrideSyncS2CPacket(
            SpeciesSkillOverrides.getAllOverriddenSpecies()
                .associateWith { SpeciesSkillOverrides.getOverride(it) ?: emptyList() }
        )
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, overrideSync)
        // Sync all job config overrides so disabled jobs are hidden from
        // the Pasture Skills tab.
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            player,
            notlown.cobblebase.core.net.JobOverrideSyncS2CPacket(JobConfigOverrides.getAllOverrides())
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
        notlown.cobblebase.core.ProducerOverrides.load(world)
        notlown.cobblebase.core.WorkshopManager.load(world)
        notlown.cobblebase.core.GeneralSettings.load(world)
        notlown.cobblebase.core.LootOverrides.load(world)
        notlown.cobblebase.core.SpawnData.loadFromCobblemonSpawnPool()
        notlown.cobblebase.core.StructureTemplateRegistry.refresh(event.server)
        notlown.cobblebase.core.BuildJobManager.load(world)
    }

    private fun onServerStopping(event: ServerStoppingEvent) {
        val world = event.server.overworld
        BaseManager.save(world)
        LogManager.save(world)
        DiscoveryRegistry.save(world)
        SpeciesSkillOverrides.save(world)
        JobConfigOverrides.save(world)
        notlown.cobblebase.core.ProducerOverrides.save(world)
        notlown.cobblebase.core.WorkshopManager.save(world)
        notlown.cobblebase.core.GeneralSettings.save(world)
        notlown.cobblebase.core.LootOverrides.save(world)
        notlown.cobblebase.core.BuildJobManager.save(world)
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
     *
     * Resolves the player's open pasture by matching the packet's pastureId
     * (== Cobblemon's pcId) against the tetherings of nearby pasture blocks.
     * Earlier versions picked the geometrically nearest pasture, which sent
     * the wrong pasture's logs whenever two pastures sat within the search
     * radius — e.g. Pasture A's logs leaking into Pasture B's view (TobiNF12,
     * 1.5.5-beta.1). The Pos-keyed log store was correct; only the lookup was wrong.
     *
     * Falls back to the nearest pasture for the edge case where every tethering
     * has just been withdrawn (no pcId match, but historical logs still exist
     * keyed by that BlockPos).
     */
    private fun handleLogRequest(player: net.minecraft.server.network.ServerPlayerEntity, packet: LogRequestC2SPacket) {
        val world = player.serverWorld
        val playerPos = player.blockPos
        val searchRadius = 16

        var matchedPos: BlockPos? = null
        var nearestPos: BlockPos? = null
        var nearestDist = Double.MAX_VALUE

        outer@ for (x in -searchRadius..searchRadius) {
            for (y in -searchRadius..searchRadius) {
                for (z in -searchRadius..searchRadius) {
                    val pos = playerPos.add(x, y, z)
                    val blockEntity = world.getBlockEntity(pos)
                    if (blockEntity is PokemonPastureBlockEntity) {
                        if (blockEntity.tetheredPokemon.any { it.pcId == packet.pastureId }) {
                            matchedPos = pos.toImmutable()
                            break@outer
                        }
                        val dist = pos.getSquaredDistance(playerPos)
                        if (dist < nearestDist) {
                            nearestDist = dist
                            nearestPos = pos.toImmutable()
                        }
                    }
                }
            }
        }

        val pasturePos = matchedPos ?: nearestPos
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

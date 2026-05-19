package notlown.cobblebase.fabric.client

import me.shedaniel.autoconfig.AutoConfig
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import notlown.cobblebase.fabric.client.render.RadiusRenderer
import notlown.cobblebase.core.AdminDataCache
import notlown.cobblebase.core.AdminJobDataCache
import notlown.cobblebase.core.AssignmentCache
import notlown.cobblebase.core.CobblebaseClothConfig
import notlown.cobblebase.core.DiscoveryRegistry
import notlown.cobblebase.core.LogManager
import notlown.cobblebase.core.VersionChecker
import notlown.cobblebase.core.net.AdminJobsRequestC2SPacket
import notlown.cobblebase.core.net.AdminJobsSyncS2CPacket
import notlown.cobblebase.core.net.AdminSpeciesRequestC2SPacket
import notlown.cobblebase.core.net.AdminSpeciesSyncS2CPacket
import notlown.cobblebase.core.net.SkillAssignmentSyncS2CPacket
import notlown.cobblebase.core.net.SkillAssignmentRequestC2SPacket
import notlown.cobblebase.core.net.VersionHandshakeC2SPacket
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import notlown.cobblebase.core.net.DiscoverySyncS2CPacket
import notlown.cobblebase.core.net.LogSyncS2CPacket
import notlown.cobblebase.fabric.client.gui.AdminScreen
import org.lwjgl.glfw.GLFW

object CobblebaseFabricClient : ClientModInitializer {

    private lateinit var settingsKey: KeyBinding
    private var pendingAdminScreen = false

    fun requestAdminScreen() {
        pendingAdminScreen = true
        ClientPlayNetworking.send(AdminSpeciesRequestC2SPacket())
        ClientPlayNetworking.send(AdminJobsRequestC2SPacket())
    }

    override fun onInitializeClient() {
        // Register keybinding: K = open Cobblebase settings
        settingsKey = KeyBindingHelper.registerKeyBinding(KeyBinding(
            "key.cobblebase.settings",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "Cobblebase"
        ))

        // Listen for key press
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (settingsKey.wasPressed()) {
                val screen = AutoConfig.getConfigScreen(CobblebaseClothConfig::class.java, client.currentScreen).get()
                client.setScreen(screen)
            }

            // Open admin screen once data arrives
            if (pendingAdminScreen && AdminDataCache.allSpecies.isNotEmpty()) {
                pendingAdminScreen = false
                client.setScreen(AdminScreen())
            }

            // Open Cobblebase screen from pasture (deferred from PastureWidgetMixin)
            val pendingScreen = PendingScreenHolder.pendingScreen
            if (pendingScreen != null) {
                PendingScreenHolder.pendingScreen = null
                client.setScreen(pendingScreen)
            }
        }

        // Send version handshake + pre-fetch assignments when joining a server
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            ClientPlayNetworking.send(VersionHandshakeC2SPacket(VersionChecker.MOD_VERSION))
            ClientPlayNetworking.send(SkillAssignmentRequestC2SPacket())
        }

        // Register S2C log sync packet receiver
        ClientPlayNetworking.registerGlobalReceiver(LogSyncS2CPacket.ID) { packet, context ->
            context.client().execute {
                LogManager.setClientLogs(packet.entries)
            }
        }

        // Register S2C discovery sync packet receiver
        ClientPlayNetworking.registerGlobalReceiver(DiscoverySyncS2CPacket.ID) { packet, context ->
            context.client().execute {
                DiscoveryRegistry.setClientDiscoveries(packet.discoveries)
            }
        }

        // Register S2C skill assignment sync packet receiver
        ClientPlayNetworking.registerGlobalReceiver(SkillAssignmentSyncS2CPacket.ID) { packet, context ->
            context.client().execute {
                AssignmentCache.update(packet.assignments.mapValues { (_, v) -> if (v.isEmpty()) null else v })
            }
        }

        // Register S2C admin species sync packet receiver
        ClientPlayNetworking.registerGlobalReceiver(AdminSpeciesSyncS2CPacket.ID) { packet, context ->
            context.client().execute {
                AdminDataCache.update(packet.allSpecies, packet.overriddenSpecies)
            }
        }

        // Register S2C general settings sync (Discord URL, enabled, etc.)
        ClientPlayNetworking.registerGlobalReceiver(notlown.cobblebase.core.net.GeneralSettingsSyncS2CPacket.ID) { packet, context ->
            context.client().execute {
                notlown.cobblebase.core.GeneralSettingsCache.update(
                    packet.discordUrl, packet.discordEnabled, packet.pokeWikiEnabled,
                    packet.pastureRangeMax, packet.maxWorkingPokemonPerPasture,
                    packet.belowPastureReachMax,
                    packet.pastureRangeMin, packet.belowPastureReachMin
                )
            }
        }
        // Per-pasture override sync — full replace.
        ClientPlayNetworking.registerGlobalReceiver(notlown.cobblebase.core.net.PastureSettingsSyncS2CPacket.ID) { packet, context ->
            context.client().execute {
                notlown.cobblebase.core.PastureSettingsCache.replaceAll(packet.entries)
            }
        }

        // Cry playback — server-side `world.playSound(null, ...)` ignored per-player config,
        // so cryEnabled/cryVolume are checked HERE on the receiving client before playing.
        ClientPlayNetworking.registerGlobalReceiver(notlown.cobblebase.core.net.PlayCryS2CPacket.ID) { packet, context ->
            context.client().execute {
                if (!notlown.cobblebase.core.CobblebaseConfig.cryEnabled) return@execute
                val volume = notlown.cobblebase.core.CobblebaseConfig.cryVolume
                if (volume <= 0) return@execute
                val cryId = net.minecraft.util.Identifier.of("cobblebase", "pokemon.${packet.speciesName}.cry")
                val soundEvent = net.minecraft.registry.Registries.SOUND_EVENT.get(cryId) ?: return@execute
                val world = context.client().world ?: return@execute
                world.playSound(
                    packet.x, packet.y, packet.z,
                    soundEvent,
                    net.minecraft.sound.SoundCategory.NEUTRAL,
                    volume / 100f, 1.0f, false
                )
            }
        }

        // Lazy-loaded skills response
        ClientPlayNetworking.registerGlobalReceiver(notlown.cobblebase.core.net.AdminSpeciesSkillsResponseS2CPacket.ID) { packet, context ->
            context.client().execute {
                AdminDataCache.setSpeciesSkills(packet.species, packet.skills)
                val pItemId = packet.producerItemId
                if (pItemId != null) {
                    AdminDataCache.setSpeciesProducer(packet.species,
                        AdminDataCache.ProducerData(pItemId, packet.producerCount, packet.producerDisplayName ?: "", packet.producerCooldown))
                } else {
                    AdminDataCache.setSpeciesProducer(packet.species, null)
                }
            }
        }

        // Workshop: recipe list sync
        ClientPlayNetworking.registerGlobalReceiver(notlown.cobblebase.core.net.RecipeListSyncS2CPacket.ID) { packet, context ->
            context.client().execute {
                notlown.cobblebase.core.WorkshopCache.updateRecipes(packet.recipes)
            }
        }

        // Workshop: project state sync
        ClientPlayNetworking.registerGlobalReceiver(notlown.cobblebase.core.net.WorkshopSyncS2CPacket.ID) { packet, context ->
            context.client().execute {
                val states = packet.projects.mapValues { (_, dto) ->
                    notlown.cobblebase.core.WorkshopCache.ProjectState(
                        dto.recipeId, dto.gatheredItems, dto.phase, dto.requiredItems, dto.craftCount
                    )
                }
                notlown.cobblebase.core.WorkshopCache.updateProjects(states)
            }
        }

        // Register S2C admin jobs sync packet receiver
        ClientPlayNetworking.registerGlobalReceiver(AdminJobsSyncS2CPacket.ID) { packet, context ->
            context.client().execute {
                AdminJobDataCache.update(packet.jobs, packet.overrides)
            }
        }

        // Register S2C recipe-override sync packet receiver — populates the client cache
        // the Admin → Jobs → Craftsman → Recipes tab reads from.
        ClientPlayNetworking.registerGlobalReceiver(notlown.cobblebase.core.net.RecipeOverridesSyncS2CPacket.ID) { packet, context ->
            context.client().execute {
                notlown.cobblebase.core.RecipeOverridesCache.setSnapshot(packet.disabledRecipeIds)
                // Keep the AdminRecipesCache disabled-set in sync too — admin panel may
                // be open when an admin in another client toggles a recipe.
                val current = notlown.cobblebase.core.AdminRecipesCache.getAll()
                if (current.isNotEmpty()) {
                    notlown.cobblebase.core.AdminRecipesCache.set(
                        notlown.cobblebase.core.net.AdminRecipesSyncS2CPacket(current, packet.disabledRecipeIds)
                    )
                }
            }
        }

        // Register S2C admin recipes sync (full list incl. disabled, for the admin Recipes tab).
        ClientPlayNetworking.registerGlobalReceiver(notlown.cobblebase.core.net.AdminRecipesSyncS2CPacket.ID) { packet, context ->
            context.client().execute {
                notlown.cobblebase.core.AdminRecipesCache.set(packet)
            }
        }

        // Register S2C admin loot sync packet receiver
        ClientPlayNetworking.registerGlobalReceiver(notlown.cobblebase.core.net.AdminLootSyncS2CPacket.ID) { packet, context ->
            context.client().execute {
                notlown.cobblebase.core.AdminLootDataCache.update(
                    packet.tables, packet.overriddenIds, packet.defaultItemIds
                )
            }
        }

        // Register S2C species override sync — applies admin-set skill overrides
        // to the client's SpeciesSkillRegistry so they show up in the Pasture
        // Skills / Buffs tabs.
        ClientPlayNetworking.registerGlobalReceiver(notlown.cobblebase.core.net.SpeciesOverrideSyncS2CPacket.ID) { packet, context ->
            context.client().execute {
                for ((species, skills) in packet.overrides) {
                    notlown.cobblebase.core.SpeciesSkillRegistry.register(
                        notlown.cobblebase.core.SpeciesSkills(species, skills)
                    )
                }
            }
        }

        // Register S2C job override sync — applies admin-set job config
        // (cooldown / radius / enabled) to the client's JobConfigOverrides so
        // disabled jobs disappear from the Pasture Skills tab.
        ClientPlayNetworking.registerGlobalReceiver(notlown.cobblebase.core.net.JobOverrideSyncS2CPacket.ID) { packet, context ->
            context.client().execute {
                notlown.cobblebase.core.JobConfigOverrides.updateAll(packet.overrides)
            }
        }

        // Radius visualization: draw red wireframe box after translucent pass
        WorldRenderEvents.AFTER_TRANSLUCENT.register { context ->
            RadiusRenderer.render(context)
        }

        // Hatchery log sync receiver
        ClientPlayNetworking.registerGlobalReceiver(
            notlown.cobblebase.core.net.HatchLogSyncS2CPacket.ID
        ) { packet, context ->
            context.client().execute {
                notlown.cobblebase.fabric.client.HatchLogCache.update(packet)
            }
        }

        // My Pokemon sync receiver
        ClientPlayNetworking.registerGlobalReceiver(
            notlown.cobblebase.core.net.MyPokemonSyncS2CPacket.ID
        ) { packet, context ->
            context.client().execute {
                notlown.cobblebase.fabric.client.MyPokemonCache.update(packet)
            }
        }

        // Register /cobblebase admin client command
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommandManager.literal("cobblebase")
                    .then(ClientCommandManager.literal("admin").executes { _ ->
                        pendingAdminScreen = true
                        ClientPlayNetworking.send(AdminSpeciesRequestC2SPacket())
                        ClientPlayNetworking.send(AdminJobsRequestC2SPacket())
                        1
                    })
            )
        }
    }
}

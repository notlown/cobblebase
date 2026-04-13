package notlown.cobblebase.fabric.client

import me.shedaniel.autoconfig.AutoConfig
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
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
                notlown.cobblebase.core.GeneralSettingsCache.update(packet.discordUrl, packet.discordEnabled)
            }
        }

        // Lazy-loaded skills response
        ClientPlayNetworking.registerGlobalReceiver(notlown.cobblebase.core.net.AdminSpeciesSkillsResponseS2CPacket.ID) { packet, context ->
            context.client().execute {
                AdminDataCache.setSpeciesSkills(packet.species, packet.skills)
            }
        }

        // Register S2C admin jobs sync packet receiver
        ClientPlayNetworking.registerGlobalReceiver(AdminJobsSyncS2CPacket.ID) { packet, context ->
            context.client().execute {
                AdminJobDataCache.update(packet.jobs, packet.overrides)
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

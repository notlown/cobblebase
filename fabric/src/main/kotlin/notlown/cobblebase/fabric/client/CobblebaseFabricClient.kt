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
                AdminDataCache.update(packet.allSpecies, packet.speciesSkills, packet.overriddenSpecies)
            }
        }

        // Register S2C admin jobs sync packet receiver
        ClientPlayNetworking.registerGlobalReceiver(AdminJobsSyncS2CPacket.ID) { packet, context ->
            context.client().execute {
                AdminJobDataCache.update(packet.jobs, packet.overrides)
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

package notlown.cobblebase.fabric.client

import me.shedaniel.autoconfig.AutoConfig
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import notlown.cobblebase.core.CobblebaseClothConfig
import notlown.cobblebase.core.DiscoveryRegistry
import notlown.cobblebase.core.LogManager
import notlown.cobblebase.core.net.DiscoverySyncS2CPacket
import notlown.cobblebase.core.net.LogSyncS2CPacket
import org.lwjgl.glfw.GLFW

object CobblebaseFabricClient : ClientModInitializer {

    private lateinit var settingsKey: KeyBinding

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
    }
}

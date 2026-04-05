package notlown.cobblebase.neoforge.client

import me.shedaniel.autoconfig.AutoConfig
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.network.PacketDistributor
import notlown.cobblebase.core.AdminDataCache
import notlown.cobblebase.core.VersionChecker
import notlown.cobblebase.core.net.VersionHandshakeC2SPacket
import notlown.cobblebase.core.CobblebaseClothConfig
import notlown.cobblebase.core.net.AdminJobsRequestC2SPacket
import notlown.cobblebase.core.net.AdminSpeciesRequestC2SPacket
import notlown.cobblebase.neoforge.client.gui.AdminScreen
import org.lwjgl.glfw.GLFW
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.server.command.ServerCommandSource

@EventBusSubscriber(modid = "cobblebase", value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.GAME)
object CobblebaseNeoForgeClient {

    val settingsKey: KeyBinding = KeyBinding(
        "key.cobblebase.settings",
        InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_K,
        "Cobblebase"
    )

    private var pendingAdminScreen = false

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        val client = net.minecraft.client.MinecraftClient.getInstance()
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

    @SubscribeEvent
    fun onPlayerLoggedIn(event: ClientPlayerNetworkEvent.LoggingIn) {
        PacketDistributor.sendToServer(VersionHandshakeC2SPacket(VersionChecker.MOD_VERSION))
    }

    @SubscribeEvent
    fun onRegisterClientCommands(event: RegisterClientCommandsEvent) {
        event.dispatcher.register(
            LiteralArgumentBuilder.literal<ServerCommandSource>("cobblebase")
                .then(LiteralArgumentBuilder.literal<ServerCommandSource>("admin").executes { _ ->
                    pendingAdminScreen = true
                    PacketDistributor.sendToServer(AdminSpeciesRequestC2SPacket())
                    PacketDistributor.sendToServer(AdminJobsRequestC2SPacket())
                    1
                })
        )
    }
}

@EventBusSubscriber(modid = "cobblebase", value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.MOD)
object CobblebaseNeoForgeClientModEvents {

    @SubscribeEvent
    fun onRegisterKeyMappings(event: RegisterKeyMappingsEvent) {
        event.register(CobblebaseNeoForgeClient.settingsKey)
    }
}

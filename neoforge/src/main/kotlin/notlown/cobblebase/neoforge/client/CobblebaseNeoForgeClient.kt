package notlown.cobblebase.neoforge.client

import me.shedaniel.autoconfig.AutoConfig
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import notlown.cobblebase.core.CobblebaseClothConfig
import org.lwjgl.glfw.GLFW

@EventBusSubscriber(modid = "cobblebase", value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.GAME)
object CobblebaseNeoForgeClient {

    val settingsKey: KeyBinding = KeyBinding(
        "key.cobblebase.settings",
        InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_K,
        "Cobblebase"
    )

    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        val client = net.minecraft.client.MinecraftClient.getInstance()
        if (settingsKey.wasPressed()) {
            val screen = AutoConfig.getConfigScreen(CobblebaseClothConfig::class.java, client.currentScreen).get()
            client.setScreen(screen)
        }
    }
}

@EventBusSubscriber(modid = "cobblebase", value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.MOD)
object CobblebaseNeoForgeClientModEvents {

    @SubscribeEvent
    @JvmStatic
    fun onRegisterKeyMappings(event: RegisterKeyMappingsEvent) {
        event.register(CobblebaseNeoForgeClient.settingsKey)
    }
}

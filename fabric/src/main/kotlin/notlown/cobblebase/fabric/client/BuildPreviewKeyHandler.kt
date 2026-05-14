package notlown.cobblebase.fabric.client

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.util.InputUtil
import notlown.cobblebase.core.net.BuildJobConfigureC2SPacket
import notlown.cobblebase.fabric.client.render.BuildPreviewState
import org.lwjgl.glfw.GLFW

/**
 * Listens for keyboard input while a build preview is active. Drives nudging,
 * rotation/mirror, confirmation and cancellation.
 *
 * We poll directly via [InputUtil.isKeyPressed] inside the client tick rather than
 * registering KeyBindings — these inputs are only relevant during preview mode and
 * shouldn't pollute the keybindings menu or conflict with vanilla `R` (swap-hands).
 */
object BuildPreviewKeyHandler {

    private val recentlyPressed = mutableSetOf<Int>()

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            tick(client)
        }
    }

    private fun tick(client: MinecraftClient) {
        if (!BuildPreviewState.isActive()) {
            recentlyPressed.clear()
            return
        }
        // Don't intercept while a screen is open — the player is in a GUI.
        if (client.currentScreen != null) return
        val handle = client.window.handle

        // Edge-triggered keys: rotate, mirror, confirm, cancel
        edge(handle, GLFW.GLFW_KEY_R) { BuildPreviewState.rotateClockwise() }
        edge(handle, GLFW.GLFW_KEY_M) { BuildPreviewState.toggleMirror() }
        edge(handle, GLFW.GLFW_KEY_ENTER) { confirm(client) }
        edge(handle, GLFW.GLFW_KEY_KP_ENTER) { confirm(client) }
        edge(handle, GLFW.GLFW_KEY_ESCAPE) {
            BuildPreviewState.clear()
        }

        // Edge-triggered nudge keys (hold-to-repeat would be too aggressive).
        edge(handle, GLFW.GLFW_KEY_W) { BuildPreviewState.nudge(0, 0, -1) }
        edge(handle, GLFW.GLFW_KEY_S) { BuildPreviewState.nudge(0, 0, 1) }
        edge(handle, GLFW.GLFW_KEY_A) { BuildPreviewState.nudge(-1, 0, 0) }
        edge(handle, GLFW.GLFW_KEY_D) { BuildPreviewState.nudge(1, 0, 0) }
        edge(handle, GLFW.GLFW_KEY_E) { BuildPreviewState.nudge(0, 1, 0) }
        edge(handle, GLFW.GLFW_KEY_Q) { BuildPreviewState.nudge(0, -1, 0) }
    }

    private fun edge(handle: Long, key: Int, action: () -> Unit) {
        val pressed = InputUtil.isKeyPressed(handle, key)
        if (pressed) {
            if (recentlyPressed.add(key)) action()
        } else {
            recentlyPressed.remove(key)
        }
    }

    private fun confirm(client: MinecraftClient) {
        val pasture = BuildPreviewState.pasturePos
        val template = BuildPreviewState.template
        if (pasture == null || template == null) {
            BuildPreviewState.clear()
            return
        }
        ClientPlayNetworking.send(BuildJobConfigureC2SPacket(
            pasturePos = pasture,
            templateId = template.id,
            originX = BuildPreviewState.origin.x,
            originY = BuildPreviewState.origin.y,
            originZ = BuildPreviewState.origin.z,
            rotationName = BuildPreviewState.rotation.name,
            mirrorName = BuildPreviewState.mirror.name
        ))
        client.player?.sendMessage(net.minecraft.text.Text.literal(
            "\u00A7a[Cobblebase] Build job confirmed — Builder Pokemon will start once Phase 3 lands."
        ), true)
        BuildPreviewState.clear()
    }
}

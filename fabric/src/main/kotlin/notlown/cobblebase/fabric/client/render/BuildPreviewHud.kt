package notlown.cobblebase.fabric.client.render

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext

/**
 * Bottom-of-screen HUD overlay shown while a build preview is active.
 * Lists the active template, current rotation/mirror, and the keybindings.
 */
object BuildPreviewHud {

    fun render(context: DrawContext, tickDelta: Float) {
        if (!BuildPreviewState.isActive()) return
        val client = MinecraftClient.getInstance()
        val tr = client.textRenderer
        val template = BuildPreviewState.template ?: return

        val w = client.window.scaledWidth
        val h = client.window.scaledHeight

        val title = "\u00A7aBuild Preview\u00A7r — \u00A7e${template.displayName}\u00A7r" +
            " \u00A77(${template.sizeX}\u00D7${template.sizeY}\u00D7${template.sizeZ})\u00A7r"
        val state = "Rotation: \u00A7b${BuildPreviewState.rotation.name}\u00A7r" +
            "  Mirror: \u00A7b${BuildPreviewState.mirror.name}\u00A7r" +
            "  Origin: \u00A77${BuildPreviewState.origin.x},${BuildPreviewState.origin.y},${BuildPreviewState.origin.z}\u00A7r"
        val controls = "\u00A7eWASD\u00A7r move  \u00A7eQ/E\u00A7r down/up  \u00A7eR\u00A7r rotate  \u00A7eM\u00A7r mirror  \u00A7aEnter\u00A7r confirm  \u00A7cEsc\u00A7r cancel"

        val lines = listOf(title, state, controls)
        val padding = 6
        val lineHeight = 11
        val boxH = padding * 2 + lineHeight * lines.size
        val boxW = lines.maxOf { tr.getWidth(it) } + padding * 2

        val boxX = (w - boxW) / 2
        val boxY = h - boxH - 12

        // semi-transparent dark backdrop
        context.fill(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, 0xFF1E2A1E.toInt())
        context.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xCC0F1A0F.toInt())

        for ((i, line) in lines.withIndex()) {
            val x = boxX + padding
            val y = boxY + padding + i * lineHeight
            context.drawTextWithShadow(tr, line, x, y, 0xFFFFFF)
        }
    }
}

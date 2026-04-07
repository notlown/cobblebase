package notlown.cobblebase.fabric.client.gui

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.text.Text
import net.minecraft.util.Util
import java.net.URI
import java.util.function.Function

/**
 * Admin GUI "Wiki" tab — curated outbound links to the Cobblebase docs,
 * species database, datapack generator, and community channels.
 *
 * Pure resource list, no server state. Buttons open the URL in the
 * user's default browser via Util.getOperatingSystem().
 */
class AdminWikiPanel(
    private val x: Int,
    private val y: Int,
    private val w: Int,
    private val h: Int,
    private val textRenderer: TextRenderer
) {
    private val PADDING = 8

    private data class WikiLink(
        val title: String,
        val description: String,
        val url: String,
        val accent: Int
    )

    private val links = listOf(
        WikiLink(
            "Documentation",
            "Full guides for jobs, proficiency, GUI, admin commands and datapacks.",
            "https://notlown.github.io/cobblebase-web/docs/",
            0xFF4CAF50.toInt()
        ),
        WikiLink(
            "Species Database",
            "Browse all Pokemon with their skill assignments and proficiency.",
            "https://notlown.github.io/cobblebase-web/database/",
            0xFF2196F3.toInt()
        ),
        WikiLink(
            "Datapack Generator",
            "Create custom species skill datapacks without editing JSON.",
            "https://notlown.github.io/cobblebase-web/generator/",
            0xFFFF9800.toInt()
        ),
        WikiLink(
            "Job Reference",
            "List of every job, what it does, and the proficiency scaling.",
            "https://notlown.github.io/cobblebase-web/docs/jobs.html",
            0xFFE91E9E.toInt()
        ),
        WikiLink(
            "Loot Tables",
            "Documentation of all loot tables for gatherer, mining, fishing, etc.",
            "https://notlown.github.io/cobblebase-web/docs/loot.html",
            0xFFFFD700.toInt()
        ),
        WikiLink(
            "GitHub",
            "Source code, issue tracker, and releases.",
            "https://github.com/notlown/cobblebase",
            0xFFFFFFFF.toInt()
        ),
        WikiLink(
            "Modrinth",
            "Download the latest version of Cobblebase.",
            "https://modrinth.com/project/cobblebase",
            0xFF00AF5C.toInt()
        ),
        WikiLink(
            "Discord",
            "Join the community for support, suggestions and updates.",
            "https://discord.gg/6As3sVZgVT",
            0xFF5865F2.toInt()
        )
    )

    fun init(addWidget: Function<ClickableWidget, ClickableWidget>) {
        val btnW = 60
        val btnH = 14
        val rowH = 28
        for ((i, link) in links.withIndex()) {
            val btnX = x + w - PADDING - btnW
            val btnY = y + PADDING + 22 + i * rowH + 6
            val url = link.url
            val btn = ButtonWidget.builder(Text.literal("\u00A7bOpen")) {
                try { Util.getOperatingSystem().open(URI(url)) } catch (_: Exception) {}
            }.dimensions(btnX, btnY, btnW, btnH).build()
            addWidget.apply(btn)
        }
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Background
        context.fill(x, y, x + w, y + h, 0xCC1E1E2E.toInt())

        // Header
        context.drawTextWithShadow(
            textRenderer, "\u00A7f\u00A7lCobblebase Wiki & Resources",
            x + PADDING, y + PADDING, 0xFFFFFF
        )
        context.drawTextWithShadow(
            textRenderer, "\u00A77Documentation, tools and community links",
            x + PADDING, y + PADDING + 11, 0x8B8B99
        )

        // Link list
        val rowH = 28
        for ((i, link) in links.withIndex()) {
            val rowY = y + PADDING + 22 + i * rowH

            // Accent bar
            context.fill(x + PADDING, rowY + 2, x + PADDING + 3, rowY + rowH - 4, link.accent)

            // Title
            context.drawTextWithShadow(
                textRenderer, "\u00A7f${link.title}",
                x + PADDING + 10, rowY + 4, 0xFFFFFF
            )
            // Description (scaled down for compactness)
            context.matrices.push()
            context.matrices.translate((x + PADDING + 10).toFloat(), (rowY + 15).toFloat(), 0f)
            context.matrices.scale(0.75f, 0.75f, 1f)
            context.drawTextWithShadow(textRenderer, "\u00A77${link.description}", 0, 0, 0x8B8B99)
            context.matrices.pop()
        }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = false
    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean = false
    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean = false
    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean = false
}

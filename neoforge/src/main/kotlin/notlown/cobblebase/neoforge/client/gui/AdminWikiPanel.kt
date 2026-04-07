package notlown.cobblebase.neoforge.client.gui

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
 * Compact scrollable list. Buttons are repositioned each render to follow
 * the scroll offset, and clipped (visible=false) when off-screen so they
 * still respect mouse hit-testing.
 */
class AdminWikiPanel(
    private val x: Int,
    private val y: Int,
    private val w: Int,
    private val h: Int,
    private val textRenderer: TextRenderer
) {
    private val PADDING = 6
    private val ROW_H = 18
    private val HEADER_H = 18
    private val BTN_W = 38
    private val BTN_H = 12
    private val SCALE_TITLE = 0.85f
    private val SCALE_DESC = 0.65f

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

    private val openButtons = mutableListOf<ButtonWidget>()
    private var scrollOffset = 0
    private var isDraggingScrollbar = false

    fun init(addWidget: Function<ClickableWidget, ClickableWidget>) {
        openButtons.clear()
        for (link in links) {
            val url = link.url
            val btn = ButtonWidget.builder(Text.literal("\u00A7bOpen")) {
                try { Util.getOperatingSystem().open(URI(url)) } catch (_: Exception) {}
            }.dimensions(0, 0, BTN_W, BTN_H).build()
            openButtons.add(btn)
            addWidget.apply(btn)
        }
    }

    private fun listAreaY(): Int = y + HEADER_H
    private fun listAreaH(): Int = h - HEADER_H - PADDING

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Background
        context.fill(x, y, x + w, y + h, 0xCC1E1E2E.toInt())

        // Header (single compact row)
        drawScaled(context, "\u00A7f\u00A7lCobblebase Wiki & Resources", x + PADDING, y + PADDING, 0xFFFFFF, SCALE_TITLE)
        drawScaled(context, "\u00A77Docs, tools and community links", x + PADDING, y + PADDING + 9, 0x8B8B99, SCALE_DESC)

        val listY = listAreaY()
        val listH = listAreaH()
        val maxVisible = listH / ROW_H
        val maxScroll = (links.size - maxVisible).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        // Separator under header
        context.fill(x + PADDING, listY - 2, x + w - PADDING, listY - 1, 0xFF3A3A5C.toInt())

        context.enableScissor(x, listY, x + w, listY + listH)

        for ((i, link) in links.withIndex()) {
            val visualIdx = i - scrollOffset
            val rowY = listY + visualIdx * ROW_H

            val visible = visualIdx in 0 until maxVisible
            val btn = openButtons[i]
            if (visible) {
                btn.visible = true
                btn.x = x + w - PADDING - BTN_W - 6 // leave room for scrollbar
                btn.y = rowY + (ROW_H - BTN_H) / 2

                // Accent bar
                context.fill(x + PADDING, rowY + 2, x + PADDING + 3, rowY + ROW_H - 2, link.accent)

                // Title
                drawScaled(context, "\u00A7f${link.title}", x + PADDING + 8, rowY + 2, 0xFFFFFF, SCALE_TITLE)
                // Description
                drawScaled(context, "\u00A77${link.description}", x + PADDING + 8, rowY + 10, 0x8B8B99, SCALE_DESC)
            } else {
                btn.visible = false
            }
        }

        context.disableScissor()

        // Scrollbar
        if (links.size > maxVisible) {
            val trackX = x + w - 4
            val trackH = listH
            val thumbH = ((maxVisible.toFloat() / links.size) * trackH).toInt().coerceAtLeast(10)
            val thumbY = listY + ((scrollOffset.toFloat() / maxScroll) * (trackH - thumbH)).toInt()
            context.fill(trackX, listY, trackX + 2, listY + trackH, 0x33FFFFFF)
            context.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xAAFFFFFF.toInt())
        }
    }

    private fun drawScaled(context: DrawContext, text: String, px: Int, py: Int, color: Int, scale: Float) {
        context.matrices.push()
        context.matrices.translate(px.toFloat(), py.toFloat(), 0f)
        context.matrices.scale(scale, scale, 1f)
        context.drawTextWithShadow(textRenderer, text, 0, 0, color)
        context.matrices.pop()
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val listY = listAreaY()
        val listH = listAreaH()
        val maxVisible = listH / ROW_H
        val maxScroll = (links.size - maxVisible).coerceAtLeast(0)
        val trackX = x + w - 4
        if (maxScroll > 0 &&
            mouseX in (trackX - 4).toDouble()..(trackX + 4).toDouble() &&
            mouseY in listY.toDouble()..(listY + listH).toDouble()
        ) {
            isDraggingScrollbar = true
            val rel = ((mouseY - listY) / listH.toDouble()).coerceIn(0.0, 1.0)
            scrollOffset = (rel * maxScroll).toInt()
            return true
        }
        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (isDraggingScrollbar) {
            val listY = listAreaY()
            val listH = listAreaH()
            val maxVisible = listH / ROW_H
            val maxScroll = (links.size - maxVisible).coerceAtLeast(0)
            val rel = ((mouseY - listY) / listH.toDouble()).coerceIn(0.0, 1.0)
            scrollOffset = (rel * maxScroll).toInt()
            return true
        }
        return false
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false
            return true
        }
        return false
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (mouseX in x.toDouble()..(x + w).toDouble() && mouseY in y.toDouble()..(y + h).toDouble()) {
            val listH = listAreaH()
            val maxVisible = listH / ROW_H
            val maxScroll = (links.size - maxVisible).coerceAtLeast(0)
            scrollOffset = (scrollOffset - vertical.toInt() * 2).coerceIn(0, maxScroll)
            return true
        }
        return false
    }
}

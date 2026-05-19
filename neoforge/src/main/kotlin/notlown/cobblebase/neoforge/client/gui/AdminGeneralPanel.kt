package notlown.cobblebase.neoforge.client.gui

import net.neoforged.neoforge.network.PacketDistributor
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text
import notlown.cobblebase.core.GeneralSettingsCache
import notlown.cobblebase.core.net.GeneralSettingsUpdateC2SPacket
import java.util.function.Function

/**
 * Admin GUI "General" tab — server-side settings for things like the in-game
 * Discord button URL and whether it's shown at all.
 */
class AdminGeneralPanel(
    private val x: Int,
    private val y: Int,
    private val w: Int,
    private val h: Int,
    private val textRenderer: TextRenderer
) {
    private val PADDING = 10

    private var discordUrlField: TextFieldWidget? = null
    private var discordEnabledBtn: ButtonWidget? = null
    private var saveBtn: ButtonWidget? = null

    private var discordEnabled: Boolean = GeneralSettingsCache.discordEnabled
    private var dirty = false

    fun init(addWidget: Function<net.minecraft.client.gui.widget.ClickableWidget, net.minecraft.client.gui.widget.ClickableWidget>) {
        discordEnabled = GeneralSettingsCache.discordEnabled

        // Discord URL text field
        val field = TextFieldWidget(
            textRenderer,
            x + PADDING,
            y + 60,
            w - PADDING * 2,
            16,
            Text.literal("Discord URL")
        )
        field.setMaxLength(256)
        field.text = GeneralSettingsCache.discordUrl
        field.setChangedListener { dirty = true }
        discordUrlField = field
        addWidget.apply(field)

        // Discord enabled toggle
        discordEnabledBtn = ButtonWidget.builder(Text.literal(getToggleLabel())) { btn ->
            discordEnabled = !discordEnabled
            btn.message = Text.literal(getToggleLabel())
            dirty = true
        }.dimensions(x + PADDING, y + 100, 140, 18).build()
        addWidget.apply(discordEnabledBtn!!)

        // Save button
        saveBtn = ButtonWidget.builder(Text.literal("\u00A72Save")) { saveSettings() }
            .dimensions(x + w - 64, y + h - 20, 54, 16).build()
        addWidget.apply(saveBtn!!)
    }

    private fun getToggleLabel(): String {
        return if (discordEnabled) "Discord Button: \u00A7aON" else "Discord Button: \u00A7cOFF"
    }

    private fun saveSettings() {
        val url = discordUrlField?.text ?: return
        // PokeWiki toggle is edited in AdminWikiPanel; keep its current value when saving here.
        val cachedDownLimit = notlown.cobblebase.core.GeneralSettingsCache.harvesterDownwardLimit
        PacketDistributor.sendToServer(GeneralSettingsUpdateC2SPacket(
            url, discordEnabled,
            notlown.cobblebase.core.GeneralSettingsCache.pokeWikiEnabled,
            notlown.cobblebase.core.GeneralSettingsCache.pastureRange,
            notlown.cobblebase.core.GeneralSettingsCache.maxWorkingPokemonPerPasture,
            if (cachedDownLimit in 0..30) cachedDownLimit else 6
        ))
        dirty = false
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Background
        context.fill(x, y, x + w, y + h, 0xCC1E1E2E.toInt())

        // Header
        context.drawTextWithShadow(
            textRenderer, "\u00A7f\u00A7lGeneral Settings",
            x + PADDING, y + PADDING, 0xFFFFFF
        )
        context.drawTextWithShadow(
            textRenderer, "\u00A77Server-wide settings synced to all players",
            x + PADDING, y + PADDING + 12, 0x8B8B99
        )

        // Discord URL label
        context.drawTextWithShadow(
            textRenderer, "\u00A7eDiscord Button URL",
            x + PADDING, y + 46, 0xFFFF55
        )

        // Discord enabled label
        if (dirty) {
            context.drawTextWithShadow(
                textRenderer, "\u00A7e*unsaved changes",
                x + PADDING, y + h - 32, 0xFFFF55
            )
        }

        // Description/help text
        context.drawTextWithShadow(
            textRenderer, "\u00A78When disabled, the Discord icon is hidden from the Skills GUI.",
            x + PADDING + 144, y + 105, 0x8B8B99
        )
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = false
    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean = false
    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean = false
    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean = false
}

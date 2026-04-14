package notlown.cobblebase.fabric.mixin;

import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import com.cobblemon.mod.common.client.gui.pc.StorageSlot;
import com.cobblemon.mod.common.client.gui.pc.StorageWidget;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import notlown.cobblebase.core.SkillDef;
import notlown.cobblebase.core.SkillEntry;
import notlown.cobblebase.core.SkillRegistry;
import notlown.cobblebase.core.SpeciesSkillRegistry;
import notlown.cobblebase.core.SpeciesSkills;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Renders Cobblebase skill tooltips on the PCGUI screen.
 * Uses remap=true (default) so Minecraft's render() method is properly found.
 * Iterates over StorageWidget children to find hovered Pokemon slots.
 */
@Mixin(PCGUI.class)
public abstract class PCGUISkillTooltipMixin extends Screen {

    @Shadow private StorageWidget storageWidget;

    private PCGUISkillTooltipMixin() { super(Text.empty()); }

    @Inject(method = "render", at = @At("TAIL"))
    private void cobblebase$drawSkillTooltip(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            if (storageWidget == null) return;

            // Check all children of the screen for StorageSlot under mouse
            for (var child : this.children()) {
                if (child instanceof StorageWidget) {
                    // StorageWidget contains the slots but we can't easily access boxSlots
                    // Instead iterate the screen's drawables
                    break;
                }
            }

            // Simpler approach: iterate ALL drawables on the screen
            // StorageSlots are added as children of the Screen
            Pokemon hoveredPokemon = null;
            for (var drawable : this.children()) {
                if (drawable instanceof StorageSlot) {
                    StorageSlot slot = (StorageSlot) drawable;
                    Pokemon pokemon = slot.getPokemon();
                    if (pokemon == null) continue;

                    int slotX = slot.getX();
                    int slotY = slot.getY();
                    int size = StorageSlot.SIZE;

                    if (mouseX >= slotX && mouseX <= slotX + size &&
                        mouseY >= slotY && mouseY <= slotY + size) {
                        hoveredPokemon = pokemon;
                        break;
                    }
                }
            }

            if (hoveredPokemon == null) return;

            String speciesName = hoveredPokemon.getSpecies().getName().toLowerCase();
            Set<String> aspects = hoveredPokemon.getAspects();
            String resolvedName = SpeciesSkillRegistry.INSTANCE.resolveFormName(speciesName, aspects);

            SpeciesSkills speciesSkills = SpeciesSkillRegistry.INSTANCE.getSkills(resolvedName);
            if (speciesSkills == null) {
                speciesSkills = SpeciesSkillRegistry.INSTANCE.getSkills(speciesName);
            }
            if (speciesSkills == null || speciesSkills.getSkills().isEmpty()) return;

            List<Text> lines = new ArrayList<>();
            lines.add(Text.literal("\u00A7b\u00A7lCobblebase Skills"));

            List<SkillEntry> sorted = new ArrayList<>(speciesSkills.getSkills());
            sorted.sort(Comparator.comparingInt(SkillEntry::getProficiency).reversed());

            for (SkillEntry entry : sorted) {
                SkillDef def = SkillRegistry.INSTANCE.get(entry.getSkillId());
                if (def == null) continue;
                String stars = cobblebase$getStars(entry.getProficiency());
                String color = cobblebase$getProficiencyColor(entry.getProficiency());
                lines.add(Text.literal(color + stars + " \u00A7f" + def.getName()));
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                context.getMatrices().push();
                context.getMatrices().translate(0, 0, 400);
                context.drawTooltip(client.textRenderer, lines, mouseX, mouseY);
                context.getMatrices().pop();
            }
        } catch (Exception ignored) {}
    }

    @Unique
    private static String cobblebase$getStars(int proficiency) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < proficiency && i < 5; i++) sb.append("\u2605");
        for (int i = proficiency; i < 5; i++) sb.append("\u2606");
        return sb.toString();
    }

    @Unique
    private static String cobblebase$getProficiencyColor(int proficiency) {
        switch (proficiency) {
            case 5: return "\u00A76";
            case 4: return "\u00A7a";
            case 3: return "\u00A7e";
            case 2: return "\u00A77";
            default: return "\u00A78";
        }
    }
}

package notlown.cobblebase.fabric.mixin;

import com.cobblemon.mod.common.client.gui.pc.StorageSlot;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import notlown.cobblebase.core.SkillDef;
import notlown.cobblebase.core.SkillEntry;
import notlown.cobblebase.core.SkillRegistry;
import notlown.cobblebase.core.SpeciesSkillRegistry;
import notlown.cobblebase.core.SpeciesSkills;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Adds hover tooltip to Pokemon storage slots showing Cobblebase skills.
 * Extends ClickableWidget so getX/getY/getWidth/getHeight are properly remapped.
 */
@Mixin(StorageSlot.class)
public abstract class StorageSlotTooltipMixin extends ClickableWidget {

    private StorageSlotTooltipMixin() { super(0, 0, 0, 0, Text.empty()); }

    @Inject(
        method = "renderSlot(Lnet/minecraft/class_332;IIF)V",
        at = @At("TAIL"),
        remap = false
    )
    private void cobblebase$renderSkillTooltip(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            StorageSlot self = (StorageSlot) (Object) this;
            Pokemon pokemon = self.getPokemon();
            if (pokemon == null) return;

            // Use inherited ClickableWidget methods (properly remapped via extends)
            int slotX = this.getX();
            int slotY = this.getY();
            int slotW = this.getWidth();
            int slotH = this.getHeight();

            // Check hover bounds
            if (mouseX < slotX || mouseX > slotX + slotW ||
                mouseY < slotY || mouseY > slotY + slotH) return;

            String speciesName = pokemon.getSpecies().getName().toLowerCase();
            Set<String> aspects = pokemon.getAspects();
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

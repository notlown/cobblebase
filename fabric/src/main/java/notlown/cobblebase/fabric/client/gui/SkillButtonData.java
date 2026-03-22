package notlown.cobblebase.fabric.client.gui;

import java.util.UUID;

/**
 * Simple POJO for skill button state. Using Java class to avoid Fabric ClassLoader issues with Kotlin data classes.
 */
public class SkillButtonData {
    public UUID pokemonId;
    public String skillId;
    public String displayName;
    public int proficiency;
    public String category;
    public int baseX;
    public int baseY;
    public boolean selected;

    public SkillButtonData(UUID pokemonId, String skillId, String displayName, int proficiency, String category, int baseX, int baseY, boolean selected) {
        this.pokemonId = pokemonId;
        this.skillId = skillId;
        this.displayName = displayName;
        this.proficiency = proficiency;
        this.category = category;
        this.baseX = baseX;
        this.baseY = baseY;
        this.selected = selected;
    }
}

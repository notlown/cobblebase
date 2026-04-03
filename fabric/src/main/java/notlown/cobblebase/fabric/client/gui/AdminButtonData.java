package notlown.cobblebase.fabric.client.gui;

/**
 * Simple POJO for admin skill toggle state. Using Java class to avoid Fabric ClassLoader issues with Kotlin data classes.
 */
public class AdminButtonData {
    public String skillId;
    public String displayName;
    public String category;
    public boolean assigned;
    public int proficiency;

    public AdminButtonData(String skillId, String displayName, String category, boolean assigned, int proficiency) {
        this.skillId = skillId;
        this.displayName = displayName;
        this.category = category;
        this.assigned = assigned;
        this.proficiency = proficiency;
    }
}

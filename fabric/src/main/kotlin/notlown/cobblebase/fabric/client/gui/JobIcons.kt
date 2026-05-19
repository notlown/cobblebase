package notlown.cobblebase.fabric.client.gui

import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier

/**
 * Single source of truth for the vanilla-item icon shown next to each Cobblebase skill.
 *
 * Used by the Admin GUI job-overview tiles, the in-game Skills tab, the Buffs tab, and the
 * AdminScreen / CobblebaseScreen top-level tab bars (which pick representative icons per
 * panel). Keeping the mapping in one place ensures admins and players see the same icon
 * for a given skill everywhere.
 */
object JobIcons {

    /**
     * Cobbreeding's `pokemon_egg` item if installed, otherwise vanilla `minecraft:egg`.
     * Public so other parts of the GUI (e.g. CobblebaseScreen's Hatchery tab) can use the
     * same Pokemon-themed icon.
     */
    val POKEMON_EGG: Item by lazy {
        val parsed = Identifier.tryParse("cobbreeding:pokemon_egg")
        if (parsed != null) {
            val item = Registries.ITEM.get(parsed)
            if (item != Items.AIR) return@lazy item
        }
        Items.EGG
    }

    /**
     * Cobblemon's `poke_ball` item if installed, otherwise vanilla `minecraft:slime_ball` as
     * a round-ish stand-in. Used wherever a Pokeball best represents a concept (Species tab,
     * Collector skill, etc.).
     */
    val POKE_BALL: Item by lazy {
        val parsed = Identifier.tryParse("cobblemon:poke_ball")
        if (parsed != null) {
            val item = Registries.ITEM.get(parsed)
            if (item != Items.AIR) return@lazy item
        }
        Items.SLIME_BALL
    }

    /** Skill-id → vanilla item. Lazy so Items.* resolves only after Minecraft is initialized. */
    private val JOBS: Map<String, Item> by lazy {
        mapOf(
            "cobblebase:mining" to Items.DIAMOND_PICKAXE,
            "cobblebase:fishing" to Items.FISHING_ROD,
            "cobblebase:diving" to Items.TRIDENT,
            "cobblebase:harvester" to Items.WHEAT,
            "cobblebase:archeologist" to Items.BRUSH,
            "cobblebase:producer" to Items.EGG,
            "cobblebase:gatherer" to Items.CHEST,
            "cobblebase:scout" to Items.SPYGLASS,
            "cobblebase:irrigator" to Items.WATER_BUCKET,
            "cobblebase:extinguisher" to Items.WET_SPONGE,
            "cobblebase:guard" to Items.IRON_SWORD,
            "cobblebase:healer" to Items.GOLDEN_APPLE,
            "cobblebase:mentor" to Items.ENCHANTED_BOOK,
            "cobblebase:builder" to Items.BRICKS,
            "cobblebase:craftsman" to Items.CRAFTING_TABLE,
            "cobblebase:furnace_fuel" to Items.COAL,
            "cobblebase:brew_fuel" to Items.BLAZE_POWDER,
            "cobblebase:water_fill" to Items.WATER_BUCKET,
            "cobblebase:lava_fill" to Items.LAVA_BUCKET,
            "cobblebase:snow_fill" to Items.POWDER_SNOW_BUCKET,
            "cobblebase:friend_recruiter" to Items.ECHO_SHARD,
            "cobblebase:recruiter" to Items.NETHER_STAR,
            "cobblebase:lucky_charm" to Items.GOLD_NUGGET,
            // Passive buff icons — semantic match for what the buff DOES, not the
            // arcane vanilla brewing ingredient. Haste = mining speed → golden
            // pickaxe; Jump = bouncing → slime block; Aura = effect beam → beacon.
            "cobblebase:growth_aura" to Items.BONE_MEAL,                // bone meal = grow plants ✓
            "cobblebase:aura_boost" to Items.BEACON,                    // beacon emits an aura beam
            "cobblebase:speed_boost" to Items.FEATHER,                  // feather = light + swift
            "cobblebase:strength_boost" to Items.IRON_SWORD,            // sword = raw damage
            "cobblebase:resistance_boost" to Items.NETHERITE_INGOT,     // tough armor ingot
            "cobblebase:night_vision" to Items.GLOW_INK_SAC,            // glow = see in dark
            "cobblebase:water_breathing" to Items.TURTLE_HELMET,        // vanilla water-breathing item
            "cobblebase:jump_boost" to Items.SLIME_BLOCK,               // slime = bounce/jump
            "cobblebase:haste_boost" to Items.GOLDEN_PICKAXE,           // haste = mining speed
            "cobblebase:saturation_boost" to Items.BREAD,               // bread = staple food / saturation
            "cobblebase:egg_hatcher" to POKEMON_EGG,
            "cobblebase:finder_bal" to POKE_BALL,
            "cobblebase:finder_evo" to Items.AMETHYST_CLUSTER,
            "cobblebase:finder_hea" to Items.GLISTERING_MELON_SLICE,
            "cobblebase:finder_food" to Items.BREAD,
            "cobblebase:finder_bui" to Items.STONE_BRICKS,
            "cobblebase:finder_ore" to Items.RAW_IRON,
            "cobblebase:finder_see" to Items.WHEAT_SEEDS,
            "cobblebase:finder_exp" to Items.EXPERIENCE_BOTTLE,
            "cobblebase:finder_stat" to Items.GLOW_BERRIES,
            "cobblebase:finder_held" to Items.LEATHER_HELMET,
            "cobblebase:finder_treasure" to Items.GOLD_INGOT,
            "cobblebase:finder_smith" to Items.SMITHING_TABLE,
        )
    }

    /** Returns the icon item for [skillId], or PAPER for skills we haven't mapped yet. */
    fun itemFor(skillId: String): Item = JOBS[skillId] ?: Items.PAPER

    /** Returns a 1-count ItemStack — what DrawContext.drawItem expects. */
    fun stackFor(skillId: String): ItemStack = ItemStack(itemFor(skillId))
}

package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.BaseManager
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.LogManager
import notlown.cobblebase.core.ProducerOverrides
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import notlown.cobblebase.core.effects.SkillEffects
import java.util.UUID

/**
 * Producer executor — species-specific passive item production.
 * Each Pokemon species produces a unique item based on its lore.
 * If the species is not in the produce map, the executor silently skips.
 */
object ProducerExecutor : SkillExecutor {

    private val lastProduceTime = mutableMapOf<UUID, Long>()
    private const val STALE_TTL_TICKS = 1200L

    fun cleanupStale(now: Long) {
        val stale = lastProduceTime.entries.filter { now - it.value > STALE_TTL_TICKS }.map { it.key }
        for (id in stale) lastProduceTime.remove(id)
    }

    data class ProduceEntry(
        val itemId: String,
        val count: Int,
        val displayName: String,
        val cooldownSeconds: Long? = null  // null = use default from skill def
    )

    private val produceMap: Map<String, ProduceEntry> = mapOf(
        // Textiles & Fibers
        "wooloo" to ProduceEntry("white_wool", 1, "White Wool"),
        "dubwool" to ProduceEntry("white_wool", 2, "White Wool"),
        "mareep" to ProduceEntry("white_wool", 1, "White Wool"),
        "flaaffy" to ProduceEntry("white_wool", 1, "White Wool"),
        "cottonee" to ProduceEntry("string", 2, "String"),
        "whimsicott" to ProduceEntry("string", 3, "String"),
        "spinarak" to ProduceEntry("string", 2, "String"),
        "ariados" to ProduceEntry("string", 3, "String"),
        "joltik" to ProduceEntry("string", 1, "String"),
        "galvantula" to ProduceEntry("string", 2, "String"),
        "sewaddle" to ProduceEntry("string", 1, "String"),
        "leavanny" to ProduceEntry("string", 2, "String"),
        "snom" to ProduceEntry("string", 1, "String"),
        "frosmoth" to ProduceEntry("string", 2, "String"),
        "caterpie_valencian" to ProduceEntry("string", 1, "String"),
        "metapod_valencian" to ProduceEntry("string", 1, "String"),

        // Animal Products
        "miltank" to ProduceEntry("cobblemon:moomoo_milk", 2, "Moomoo Milk"),
        "gogoat" to ProduceEntry("cobblemon:moomoo_milk", 1, "Moomoo Milk"),
        "skiddo" to ProduceEntry("cobblemon:moomoo_milk", 1, "Moomoo Milk"),
        "chansey" to ProduceEntry("egg", 1, "Egg"),
        "blissey" to ProduceEntry("egg", 2, "Egg"),
        "happiny" to ProduceEntry("egg", 1, "Egg"),
        "exeggcute" to ProduceEntry("egg", 1, "Egg"),
        "torchic" to ProduceEntry("egg", 1, "Egg"),
        "blaziken" to ProduceEntry("egg", 1, "Egg"),
        "combee" to ProduceEntry("honeycomb", 1, "Honeycomb"),
        "vespiquen" to ProduceEntry("honeycomb", 2, "Honeycomb"),
        "ribombee" to ProduceEntry("honey_bottle", 1, "Honey Bottle"),
        "cutiefly" to ProduceEntry("honey_bottle", 1, "Honey Bottle"),

        // Valuables
        "meowth" to ProduceEntry("gold_nugget", 1, "Gold Nugget"),
        "meowth_alolan" to ProduceEntry("gold_nugget", 1, "Gold Nugget"),
        "meowth_galarian" to ProduceEntry("iron_nugget", 1, "Iron Nugget"),
        "persian" to ProduceEntry("gold_nugget", 2, "Gold Nugget"),
        "persian_alolan" to ProduceEntry("gold_nugget", 2, "Gold Nugget"),
        "perrserker" to ProduceEntry("gold_nugget", 2, "Gold Nugget"),
        "gholdengo" to ProduceEntry("gold_nugget", 3, "Gold Nugget"),
        "gimmighoul" to ProduceEntry("gold_nugget", 1, "Gold Nugget"),
        "sableye" to ProduceEntry("amethyst_shard", 1, "Amethyst Shard"),
        "carbink" to ProduceEntry("diamond", 1, "Diamond"),
        "diancie" to ProduceEntry("diamond", 1, "Diamond"),
        "clamperl" to ProduceEntry("prismarine_shard", 2, "Prismarine Shard"),

        // Food
        "tropius" to ProduceEntry("apple", 2, "Apple"),
        "applin" to ProduceEntry("apple", 1, "Apple"),
        "flapple" to ProduceEntry("apple", 2, "Apple"),
        "appletun" to ProduceEntry("apple", 2, "Apple"),
        "bounsweet" to ProduceEntry("sweet_berries", 2, "Sweet Berries"),
        "steenee" to ProduceEntry("sweet_berries", 2, "Sweet Berries"),
        "tsareena" to ProduceEntry("sweet_berries", 3, "Sweet Berries"),
        "cherubi" to ProduceEntry("sweet_berries", 1, "Sweet Berries"),
        "cherrim" to ProduceEntry("sweet_berries", 2, "Sweet Berries"),
        "delibird" to ProduceEntry("cookie", 2, "Cookie"),

        // Materials
        "goomy" to ProduceEntry("slime_ball", 1, "Slime Ball"),
        "sliggoo" to ProduceEntry("slime_ball", 2, "Slime Ball"),
        "goodra" to ProduceEntry("slime_ball", 3, "Slime Ball"),
        "slugma" to ProduceEntry("magma_cream", 1, "Magma Cream"),
        "magcargo" to ProduceEntry("magma_cream", 2, "Magma Cream"),
        "torkoal" to ProduceEntry("charcoal", 2, "Charcoal"),
        "coalossal" to ProduceEntry("charcoal", 3, "Charcoal"),
        "rolycoly" to ProduceEntry("charcoal", 1, "Charcoal"),
        "carkol" to ProduceEntry("charcoal", 2, "Charcoal"),
        "koffing" to ProduceEntry("gunpowder", 1, "Gunpowder"),
        "weezing" to ProduceEntry("gunpowder", 2, "Gunpowder"),
        "weezing_galarian" to ProduceEntry("gunpowder", 2, "Gunpowder"),
        "voltorb" to ProduceEntry("gunpowder", 1, "Gunpowder"),
        "voltorb_hisuian" to ProduceEntry("gunpowder", 1, "Gunpowder"),
        "electrode" to ProduceEntry("gunpowder", 2, "Gunpowder"),
        "electrode_hisuian" to ProduceEntry("gunpowder", 2, "Gunpowder"),
        "magnemite" to ProduceEntry("iron_nugget", 1, "Iron Nugget"),
        "magneton" to ProduceEntry("iron_nugget", 2, "Iron Nugget"),
        "magnezone" to ProduceEntry("iron_nugget", 3, "Iron Nugget"),

        // Ink
        "octillery" to ProduceEntry("ink_sac", 2, "Ink Sac"),
        "inkay" to ProduceEntry("ink_sac", 1, "Ink Sac"),
        "malamar" to ProduceEntry("ink_sac", 2, "Ink Sac"),
        "tentacool" to ProduceEntry("ink_sac", 1, "Ink Sac"),
        "tentacruel" to ProduceEntry("ink_sac", 2, "Ink Sac"),

        // Bones & Feathers
        "cubone" to ProduceEntry("bone", 1, "Bone"),
        "marowak" to ProduceEntry("bone", 2, "Bone"),
        "mandibuzz" to ProduceEntry("bone", 1, "Bone"),
        "vullaby" to ProduceEntry("bone", 1, "Bone"),
        "pidgeot" to ProduceEntry("feather", 2, "Feather"),
        "staraptor" to ProduceEntry("feather", 2, "Feather"),
        "ho-oh" to ProduceEntry("feather", 1, "Feather"),

        // Energy
        "pikachu" to ProduceEntry("glowstone_dust", 1, "Glowstone Dust"),
        "raichu" to ProduceEntry("glowstone_dust", 2, "Glowstone Dust"),
        "raichu_alolan" to ProduceEntry("glowstone_dust", 2, "Glowstone Dust"),
        "jolteon" to ProduceEntry("glowstone_dust", 2, "Glowstone Dust"),
        "luxray" to ProduceEntry("glowstone_dust", 2, "Glowstone Dust"),

        // Slime (additional)
        "ditto" to ProduceEntry("slime_ball", 2, "Slime Ball"),
        "grimer" to ProduceEntry("slime_ball", 1, "Slime Ball"),
        "grimer_alolan" to ProduceEntry("slime_ball", 1, "Slime Ball"),
        "muk" to ProduceEntry("slime_ball", 2, "Slime Ball"),
        "muk_alolan" to ProduceEntry("slime_ball", 2, "Slime Ball"),
        "sliggoo_hisuian" to ProduceEntry("slime_ball", 2, "Slime Ball"),
        "goodra_hisuian" to ProduceEntry("slime_ball", 3, "Slime Ball"),

        // Shells & Sea
        "shellder" to ProduceEntry("prismarine_shard", 1, "Prismarine Shard"),
        "cloyster" to ProduceEntry("prismarine_shard", 2, "Prismarine Shard"),
        "corsola" to ProduceEntry("prismarine_shard", 1, "Prismarine Shard"),
        "krabby" to ProduceEntry("nautilus_shell", 1, "Nautilus Shell"),
        "kingler" to ProduceEntry("nautilus_shell", 1, "Nautilus Shell"),
        "crabrawler" to ProduceEntry("nautilus_shell", 1, "Nautilus Shell"),
        "crabominable" to ProduceEntry("nautilus_shell", 1, "Nautilus Shell"),

        // Stink & Gas
        "stunky" to ProduceEntry("gunpowder", 1, "Gunpowder"),
        "skuntank" to ProduceEntry("gunpowder", 2, "Gunpowder"),

        // Leather
        "tauros" to ProduceEntry("leather", 2, "Leather"),
        "bouffalant" to ProduceEntry("leather", 2, "Leather"),

        // Clay & Earth
        "mudbray" to ProduceEntry("clay_ball", 2, "Clay Ball"),
        "mudsdale" to ProduceEntry("clay_ball", 3, "Clay Ball"),
        "sandygast" to ProduceEntry("sand", 2, "Sand"),
        "palossand" to ProduceEntry("sand", 3, "Sand"),

        // Wood & Logs
        "komala" to ProduceEntry("oak_log", 1, "Oak Log"),
        "timburr" to ProduceEntry("oak_planks", 2, "Oak Planks"),
        "gurdurr" to ProduceEntry("spruce_log", 1, "Spruce Log"),
        "conkeldurr" to ProduceEntry("dark_oak_log", 1, "Dark Oak Log"),
        "phantump" to ProduceEntry("oak_log", 1, "Oak Log"),
        "trevenant" to ProduceEntry("dark_oak_log", 1, "Dark Oak Log"),
        "exeggutor" to ProduceEntry("jungle_log", 1, "Jungle Log"),
        "exeggutor_alolan" to ProduceEntry("jungle_log", 2, "Jungle Log"),
        "sudowoodo" to ProduceEntry("stick", 3, "Stick"),

        // Snow & Ice
        "snover" to ProduceEntry("snowball", 2, "Snowball"),
        "abomasnow" to ProduceEntry("snowball", 3, "Snowball"),
        "vanillite" to ProduceEntry("snowball", 1, "Snowball"),
        "vanillish" to ProduceEntry("snowball", 2, "Snowball"),
        "vanilluxe" to ProduceEntry("snowball", 3, "Snowball"),

        // Pumpkin & Mushroom
        "pumpkaboo" to ProduceEntry("pumpkin", 1, "Pumpkin"),
        "gourgeist" to ProduceEntry("pumpkin", 1, "Pumpkin"),
        "foongus" to ProduceEntry("brown_mushroom", 1, "Brown Mushroom"),
        "amoonguss" to ProduceEntry("brown_mushroom", 2, "Brown Mushroom"),
        "oddish" to ProduceEntry("red_mushroom", 1, "Red Mushroom"),
        "gloom" to ProduceEntry("red_mushroom", 1, "Red Mushroom"),
        "vileplume" to ProduceEntry("red_mushroom", 2, "Red Mushroom"),

        // Fire
        "arcanine" to ProduceEntry("blaze_powder", 1, "Blaze Powder"),

        // Candle
        "litwick" to ProduceEntry("candle", 1, "Candle"),
        "lampent" to ProduceEntry("candle", 2, "Candle"),
        "chandelure" to ProduceEntry("candle", 2, "Candle"),

        // Brewing Ingredients
        "salandit" to ProduceEntry("spider_eye", 1, "Spider Eye"),
        "salazzle" to ProduceEntry("spider_eye", 2, "Spider Eye"),
        "skorupi" to ProduceEntry("spider_eye", 1, "Spider Eye"),
        "drapion" to ProduceEntry("spider_eye", 2, "Spider Eye"),
        "litleo" to ProduceEntry("blaze_powder", 1, "Blaze Powder"),
        "pyroar" to ProduceEntry("blaze_powder", 2, "Blaze Powder"),
        "ponyta" to ProduceEntry("blaze_powder", 1, "Blaze Powder"),
        "rapidash" to ProduceEntry("blaze_powder", 1, "Blaze Powder"),
        "buneary" to ProduceEntry("rabbit_foot", 1, "Rabbit's Foot"),
        "lopunny" to ProduceEntry("rabbit_foot", 1, "Rabbit's Foot"),
        "bunnelby" to ProduceEntry("rabbit_foot", 1, "Rabbit's Foot"),
        "diggersby" to ProduceEntry("rabbit_foot", 1, "Rabbit's Foot"),
        "scorbunny" to ProduceEntry("rabbit_foot", 1, "Rabbit's Foot"),
        "raboot" to ProduceEntry("rabbit_foot", 1, "Rabbit's Foot"),
        "drifloon" to ProduceEntry("phantom_membrane", 1, "Phantom Membrane"),
        "drifblim" to ProduceEntry("phantom_membrane", 1, "Phantom Membrane"),
        "gastly" to ProduceEntry("ghast_tear", 1, "Ghast Tear"),
        "haunter" to ProduceEntry("ghast_tear", 1, "Ghast Tear"),
        "gengar" to ProduceEntry("ghast_tear", 1, "Ghast Tear"),

        // Plants & Nature
        "bulbasaur" to ProduceEntry("lead", 1, "Lead"),
        "ivysaur" to ProduceEntry("lead", 1, "Lead"),
        "venusaur" to ProduceEntry("lead", 2, "Lead"),
        "tangela" to ProduceEntry("lead", 1, "Lead"),
        "tangrowth" to ProduceEntry("lead", 2, "Lead"),
        "bellsprout" to ProduceEntry("kelp", 2, "Kelp"),
        "weepinbell" to ProduceEntry("kelp", 2, "Kelp"),
        "roselia" to ProduceEntry("poppy", 1, "Poppy"),
        "roserade" to ProduceEntry("poppy", 2, "Poppy"),
        "sunkern" to ProduceEntry("sunflower", 1, "Sunflower"),
        "sunflora" to ProduceEntry("sunflower", 2, "Sunflower"),
        "cacnea" to ProduceEntry("cactus", 1, "Cactus"),
        "cacturne" to ProduceEntry("cactus", 2, "Cactus"),
        "petilil" to ProduceEntry("lily_of_the_valley", 1, "Lily of the Valley"),
        "lilligant" to ProduceEntry("lily_of_the_valley", 2, "Lily of the Valley"),

        // Redstone & Tech
        "rotom" to ProduceEntry("redstone", 2, "Redstone"),
        "elekid" to ProduceEntry("redstone", 1, "Redstone"),
        "electabuzz" to ProduceEntry("redstone", 2, "Redstone"),
        "electivire" to ProduceEntry("redstone", 3, "Redstone"),
        "beldum" to ProduceEntry("iron_nugget", 1, "Iron Nugget"),
        "metang" to ProduceEntry("iron_nugget", 2, "Iron Nugget"),
        "metagross" to ProduceEntry("iron_nugget", 3, "Iron Nugget"),
        "aron" to ProduceEntry("iron_nugget", 1, "Iron Nugget"),
        "lairon" to ProduceEntry("iron_nugget", 2, "Iron Nugget"),
        "aggron" to ProduceEntry("iron_nugget", 3, "Iron Nugget"),
        "bronzor" to ProduceEntry("raw_copper", 1, "Raw Copper"),
        "bronzong" to ProduceEntry("raw_copper", 2, "Raw Copper"),

        // Gems & Crystals
        "staryu" to ProduceEntry("prismarine_crystals", 1, "Prismarine Crystals"),
        "starmie" to ProduceEntry("prismarine_crystals", 2, "Prismarine Crystals"),
        "espeon" to ProduceEntry("amethyst_shard", 1, "Amethyst Shard"),
        "minior" to ProduceEntry("amethyst_shard", 2, "Amethyst Shard"),

        // Souls & Dark
        "absol" to ProduceEntry("echo_shard", 1, "Echo Shard"),
        "duskull" to ProduceEntry("soul_sand", 1, "Soul Sand"),
        "dusclops" to ProduceEntry("soul_sand", 1, "Soul Sand"),
        "dusknoir" to ProduceEntry("soul_sand", 2, "Soul Sand"),
        "yamask" to ProduceEntry("gold_nugget", 1, "Gold Nugget"),
        "cofagrigus" to ProduceEntry("gold_nugget", 2, "Gold Nugget"),

        // Sea & Water
        "dhelmise" to ProduceEntry("kelp", 2, "Kelp"),
        "binacle" to ProduceEntry("clay_ball", 1, "Clay Ball"),
        "barbaracle" to ProduceEntry("clay_ball", 2, "Clay Ball"),

        // Stone & Metal
        "geodude" to ProduceEntry("cobblestone", 1, "Cobblestone"),
        "graveler" to ProduceEntry("cobblestone", 2, "Cobblestone"),
        "golem" to ProduceEntry("cobblestone", 3, "Cobblestone"),
        "onix" to ProduceEntry("iron_nugget", 1, "Iron Nugget"),
        "steelix" to ProduceEntry("iron_nugget", 3, "Iron Nugget"),
        "skarmory" to ProduceEntry("iron_nugget", 2, "Iron Nugget"),

        // Sweets & Baking
        "alcremie" to ProduceEntry("cake", 1, "Cake"),
        "milcery" to ProduceEntry("sugar", 1, "Sugar"),
        "swirlix" to ProduceEntry("sugar", 1, "Sugar"),
        "slurpuff" to ProduceEntry("sugar", 2, "Sugar"),

        // Object-Pokemon
        "varoom" to ProduceEntry("iron_ingot", 1, "Iron Ingot"),
        "revaroom" to ProduceEntry("iron_ingot", 2, "Iron Ingot"),
        "trubbish" to ProduceEntry("rotten_flesh", 1, "Rotten Flesh"),
        "garbodor" to ProduceEntry("bone_meal", 2, "Bone Meal"),
        "klefki" to ProduceEntry("iron_nugget", 1, "Iron Nugget"),
        "honedge" to ProduceEntry("iron_ingot", 1, "Iron Ingot"),
        "doublade" to ProduceEntry("iron_ingot", 1, "Iron Ingot"),
        "aegislash" to ProduceEntry("iron_ingot", 2, "Iron Ingot"),
        "sinistea" to ProduceEntry("clay_ball", 1, "Clay Ball"),
        "polteageist" to ProduceEntry("clay_ball", 2, "Clay Ball"),
        "falinks" to ProduceEntry("iron_nugget", 2, "Iron Nugget"),
        "klink" to ProduceEntry("iron_nugget", 1, "Iron Nugget"),
        "klang" to ProduceEntry("iron_nugget", 2, "Iron Nugget"),
        "klinklang" to ProduceEntry("iron_nugget", 3, "Iron Nugget"),

        // Water Plants
        "lotad" to ProduceEntry("lily_pad", 1, "Lily Pad"),
        "lombre" to ProduceEntry("lily_pad", 1, "Lily Pad"),
        "ludicolo" to ProduceEntry("lily_pad", 2, "Lily Pad"),

        // Ores & Metals
        "roggenrola" to ProduceEntry("raw_iron", 1, "Raw Iron"),
        "boldore" to ProduceEntry("raw_iron", 2, "Raw Iron"),
        "gigalith" to ProduceEntry("raw_iron", 3, "Raw Iron"),
        "ferroseed" to ProduceEntry("raw_iron", 1, "Raw Iron"),
        "ferrothorn" to ProduceEntry("raw_iron", 2, "Raw Iron"),
        "durant" to ProduceEntry("raw_iron", 1, "Raw Iron"),
        "nosepass" to ProduceEntry("raw_iron", 1, "Raw Iron"),
        "probopass" to ProduceEntry("raw_iron", 2, "Raw Iron"),
        "shieldon" to ProduceEntry("raw_iron", 1, "Raw Iron"),
        "bastiodon" to ProduceEntry("raw_iron", 2, "Raw Iron"),
        "copperajah" to ProduceEntry("raw_copper", 3, "Raw Copper"),
        "cufant" to ProduceEntry("raw_copper", 1, "Raw Copper"),
        "drilbur" to ProduceEntry("raw_copper", 1, "Raw Copper"),
        "excadrill" to ProduceEntry("raw_gold", 2, "Raw Gold"),
        "sandslash" to ProduceEntry("raw_gold", 1, "Raw Gold"),
        "rhyhorn" to ProduceEntry("cobblestone", 1, "Cobblestone"),
        "rhydon" to ProduceEntry("cobblestone", 2, "Cobblestone"),
        "rhyperior" to ProduceEntry("cobblestone", 3, "Cobblestone"),
        "larvitar" to ProduceEntry("cobblestone", 1, "Cobblestone"),
        "pupitar" to ProduceEntry("cobblestone", 1, "Cobblestone"),
        "tyranitar" to ProduceEntry("cobblestone", 2, "Cobblestone"),
        "cranidos" to ProduceEntry("cobblestone", 1, "Cobblestone"),
        "rampardos" to ProduceEntry("cobblestone", 2, "Cobblestone"),

        // Special
        "shuckle" to ProduceEntry("fermented_spider_eye", 1, "Fermented Spider Eye"),
        "lapras" to ProduceEntry("blue_ice", 1, "Blue Ice"),
        "swinub" to ProduceEntry("snowball", 2, "Snowball"),
        "piloswine" to ProduceEntry("snowball", 3, "Snowball")
    )

    override fun tick(
        world: World,
        origin: BlockPos,
        pokemonEntity: PokemonEntity,
        skill: SkillDef,
        skillEntry: SkillEntry
    ) {
        if (world !is ServerWorld) return

        val now = world.time
        // Throttle: Producer has minute-long cooldowns, no need to check every tick
        if (now % 20L != 0L) return

        val speciesName = BaseManager.resolveSpeciesName(pokemonEntity.pokemon)
        val entry = ProducerOverrides.getOverride(speciesName) ?: produceMap[speciesName] ?: return

        val pokemonId = pokemonEntity.pokemon.uuid
        val baseCooldown = entry.cooldownSeconds ?: skill.cooldownSeconds
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(baseCooldown, skillEntry.proficiency, skill.id)

        // Cooldown check — default to now so first assignment waits full cooldown
        val lastTime = lastProduceTime.getOrPut(pokemonId) { now }
        if (now - lastTime < cooldownTicks) {
            if (world.time % 20 == 0L) SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
            return
        }

        // Create the item stack
        // Support both minecraft: and cobblemon: items
        val id = if (entry.itemId.contains(":")) Identifier.of(entry.itemId) else Identifier.of("minecraft", entry.itemId)
        val item = Registries.ITEM.get(id)
        val stack = ItemStack(item, entry.count)

        if (stack.isEmpty) return

        // Update cooldown
        lastProduceTime[pokemonId] = now

        // Drop items with pasture origin tagging
        InventoryHelper.dropItems(world, pokemonEntity.blockPos, listOf(stack), origin)

        // Play success effect
        SkillEffects.playSuccess(world, pokemonEntity, skill.effectType, origin)

        // Log production
        LogManager.log(
            origin, world.time,
            pokemonEntity.pokemon.species.name,
            skill.name,
            "${entry.displayName} x${entry.count}",
            LogManager.Rarity.COMMON
        )
    }

    /** Returns the effective produce entry (override > hardcoded) for admin GUI display.
     *  Normalizes item IDs to include namespace (e.g. "lead" -> "minecraft:lead"). */
    fun getProduceEntry(species: String): ProduceEntry? {
        val entry = ProducerOverrides.getOverride(species) ?: produceMap[species] ?: return null
        val normalizedId = if (entry.itemId.contains(":")) entry.itemId else "minecraft:${entry.itemId}"
        return if (normalizedId != entry.itemId) ProduceEntry(normalizedId, entry.count, entry.displayName) else entry
    }

    /** Returns true if this species has a hardcoded entry (for "Reset" logic). */
    fun hasHardcodedEntry(species: String): Boolean = produceMap.containsKey(species)
}

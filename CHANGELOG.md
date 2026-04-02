# Changelog - Cobblebase

## [0.3.0] - 2026-04-01

### 5 Specialized Finder Subtypes
- **FinderExecutor** refactored from `object` to `class` with `finderType` parameter -- each variant uses its own loot table prefix
- **Finder Evo** (`finder_evo`) -- Evolution items only: stones (Common), Dusk/Dawn/Shiny/Ice Stone + Linking Cord (Uncommon), trade evo items like Metal Coat, King's Rock, Dragon Scale (Rare), Ability Patch/Capsule + Rare Candy x3 (Ultra Rare)
- **Finder Hea** (`finder_hea`) -- Healing items only: Potion, Oran/Sitrus Berry, Remedy (Common), Super Potion, Revive, Full Heal (Uncommon), Hyper Potion, Max Revive, Full Restore (Rare), Max Elixir, Sacred Ash (Ultra Rare)
- **Finder Bui** (`finder_bui`) -- Building materials (no ores): Planks, Cobblestone, Sand (Common), Stone Bricks, Terracotta, Quartz (Uncommon), Prismarine, Sea Lantern, Deepslate Bricks (Rare), Crying Obsidian, Gilded Blackstone, Sculk (Ultra Rare)
- **Finder Ore** (`finder_ore`) -- Ores only: Raw Copper/Iron, Coal (Common), Raw Gold, Lapis, Redstone (Uncommon), Diamond, Emerald, Amethyst Shard (Rare), Diamond x2-4, Ancient Debris, Netherite Scrap (Ultra Rare)
- **Finder See** (`finder_see`) -- Seeds and plantable items: Wheat/Beetroot Seeds, Apricorn Seeds (Common), Pumpkin/Melon Seeds, Mint Seeds (Uncommon), Berries, Sweet/Glow Berries, Nether Wart (Rare), Torchflower Seeds, Pitcher Pod (Ultra Rare)
- **20 new loot table JSONs** -- 4 tiers (common/uncommon/rare/ultra_rare) x 5 types
- **5 new skill definition JSONs** in `data/cobblebase/skills/`
- **Species assignments:**
  - Finder Hea: Added to all 20 Healer Pokemon (proficiency matches their healer level)
  - Finder See: Added to all 50 Harvester Pokemon (proficiency matches their harvester level)
  - Finder Evo: Eevee (3), Kadabra (2), Haunter (2), Machoke (2), Graveler (2), Boldore (2), Clamperl (3), Snorunt (2), Kirlia (2), Scyther (2)
  - Finder Ore: Excadrill (5), Dugtrio (4), Onix (3), Steelix (4), Aggron (4), Rhyperior (4), Drilbur (3), Diglett (2), Nosepass (2), Probopass (3)
  - Finder Bui: Machamp (4), Conkeldurr (4), Gurdurr (3), Timburr (2), Hariyama (3), Pangoro (3)
- **21 new species files** for Pokemon not previously in the registry
- ExecutorRegistry updated with 5 new executor registrations
- All specialized finders share the same proficiency-based rarity distribution as the generic Finder

## [0.2.0] - 2026-04-01

### Mentor Skill
- **MentorExecutor** -- passive XP boost for all Pokemon in the same Pasture Block
- Proficiency scaling: Prof 1 = +20%, Prof 2 = +40%, Prof 3 = +60%, Prof 4 = +80%, Prof 5 = +100% (double XP)
- Multiple mentors do NOT stack -- highest proficiency wins
- Visual: enchant + happy villager particles every 3 seconds around the mentor
- **11 Pokemon** with Mentor skill: Alakazam (5), Metagross (4), Gardevoir (4), Slowking (4), Oranguru (4), Espeon (3), Lucario (3), Mr. Mime (3), Orbeetle (3), Blissey (2), Chansey (1)
- **Config options**: mentor_enabled (default true), mentor_max_boost (default 1.0 = 100%)
- PassiveXp now accepts pasture origin to apply mentor multiplier
- 5 new species added to SpeciesSkillRegistry: Metagross, Slowking, Mr. Mime, Oranguru, Orbeetle

## [0.1.1] - 2026-04-01

### Gatherer Skill (Item Cleanup)
- **GathererExecutor** -- Pokemon picks up dropped ItemEntities from the ground and deposits them in nearby chests/barrels
- **Smart sorting** -- Uses InventoryHelper to prioritize chests already containing the same item type
- **Proficiency scaling** -- Search radius (Prof 1: 5 blocks, Prof 5: 12 blocks) and movement speed scale with proficiency
- **10-second base cooldown** between pickups, affected by proficiency
- **Visual feedback** -- Enchant sparkle particles on pickup, happy villager particles on deposit
- **Navigation timeout** -- Auto-pickup after 5 seconds if pathfinding fails (same pattern as Harvester)
- **Species assignments:** Munchlax (4), Snorlax (5), Zigzagoon (2), Linoone (3), Pachirisu (3), Aipom (2), Ambipom (3), Sentret (2), Furret (4)
- Replaces GenericLootExecutor placeholder for `gather_items`

## [0.1.0] - 2026-03-21

### Foundation
- Project setup with Fabric mod template
- **SkillRegistry** -- 22 built-in skills across 6 categories (gathering, generation, combat, support, utility, legendary/fairy)
- **SpeciesSkillRegistry** -- 90+ Pokemon species with unique skill assignments and proficiency levels (1-5)
- **SkillDef data model** -- JSON-configurable skills with executor, cooldown, radius, loot table, effect type
- **SpeciesSkills data model** -- JSON-configurable per-species skill lists with proficiency
- **BaseManager** -- Dispatches skill execution with manual job assignment support
- **ExecutorRegistry** -- Maps skill executor names to behavior implementations
- **SkillExecutor interface** -- Generic tick-based execution for all skills
- **Persistent skill assignments** -- Saved as JSON in world folder, survives restarts

### Executors
- **HarvesterExecutor** -- Unified harvester: apricorns, crops, berries, mints, netherwart, tumblestones
- **FishingExecutor** -- Water navigation, fishing loot, item depositing
- **GuardExecutor** -- Wild Pokemon repelling, XP/loot rewards, level cap awareness
- **HealerExecutor** -- Regeneration effect for nearby players
- **GenericLootExecutor** -- Pickup, archeology, diving, honey loot generation
- **CauldronFillExecutor** -- Lava, water, powder snow cauldron filling
- **FurnaceFuelExecutor** -- Adds burn time to furnaces
- **RecruiterExecutor** -- Spawns wild Pokemon of same type as recruiter
- **InventoryHelper** -- Smart chest sorting (prioritizes chests with matching items)
- **NavigationHelper** -- Ported from Cobbleworkers (bounding box intersection + throttled pathfinding)

### Friend Recruiter System
- **Type-based recruiting** -- Spawns Pokemon that share a type with the recruiter
- **Official spawn data** -- Embedded Cobblemon 1.7.3 spawn CSV with rarity buckets
- **Rarity scaling by proficiency:**
  - Proficiency 1: Common 93.8%, Uncommon 5%, Rare 1%, Ultra-Rare 0.2%
  - Proficiency 5: Uncommon/Rare/Ultra-Rare rates doubled
- **Spawn effects** -- Enchant + end rod + sparkle particles on recruited Pokemon
- **Persistent sparkles** -- Recruited Pokemon have ongoing enchant particles until caught/despawned
- **Cry on spawn** -- Recruited Pokemon play cry animation when appearing
- **Spawns next to recruiter** -- 3-block radius around the recruiter Pokemon
- **12 Fairy Pokemon** with Friend Recruiter skill (Jigglypuff, Togekiss, Gardevoir, etc.)
- **Legendary Recruiter** kept separate for Mew, Jirachi, etc. (longer cooldown)
- **All rates configurable** in settings menu

### Healer System
- **Heals players AND Pokemon** -- prioritizes lowest HP percentage
- **Revives fainted Pokemon** -- sets HP to heal% then continues healing
- **Direct % healing** instead of slow Regeneration:
  - Prof 1: 5%, Prof 2: 8%, Prof 3: 12%, Prof 4: 18%, Prof 5: 25% max HP per tick
- **Healer Pokemon:** Igglybuff/Jigglypuff/Wigglytuff (1/2/3), Cleffa/Clefairy/Clefable (1/2/3), Happiny/Chansey/Blissey (3/4/5)

### Finder Skill
- **Renamed from Pick-up to Finder** -- Pokemon searches for rare items and treasures
- **10-minute default cooldown** (vs 2 min in Cobbleworkers) to keep items valuable
- **Finder loot table:** Pokeballs (30%), Heal items (25%), Type Gems (15%), Apricorn Seeds (15%), Evo Stones (8%), Exp Candy (5%), Ancient Held Items (2%)
- **Cooldown configurable** in settings
- **Finder Pokemon:** Meowth/Persian (4/5), Stoutland (5), Zigzagoon/Linoone (3/4), Aipom/Ambipom (3/4), Ditto (3), Eevee (2), and more

### Effects
- **SkillEffects** -- Visual effects system with per-skill-type particles and animations
- **Cry + attack animation** in single packet to prevent override
- **Working particles** -- Periodic particles while on cooldown
- **Server-side animations** -- Uses CobblemonNetwork + PlayPosableAnimationPacket

### Passive XP
- **Percentage-based** -- 5% of XP needed for next level per tick (scales equally for all levels)
- **Configurable** interval and percentage
- **Level cap aware** -- Pokemon at max level permanently skipped (no spam alerts)

### Skill Assignment GUI
- **"Skills" button** in Pasture Block UI (PastureWidget Mixin)
- **Dark panel** with color-coded skill buttons by category
- **Proficiency stars** displayed below each skill
- **Scrollable** (vertical + horizontal)
- **Per-species skills** -- Only shows skills each Pokemon can actually perform

### Settings (Cloth Config)
- **Dev Mode** -- 5-second cooldowns for testing
- **Passive XP** -- Enable/disable, XP percentage, interval
- **Skill Toggles** -- Enable/disable each skill category
- **Recruiter Cooldowns** -- Friend (300s) and Legendary (600s), adjustable
- **Recruiter Spawn Rates** -- Common/Uncommon/Rare/Ultra-Rare individually adjustable
- **Search Radius** -- Default block search radius

### Smart Systems
- **Auto-harvest timeout** -- If pathfinding fails after 5 seconds, harvest anyway
- **Chest sorting** -- Prioritizes chests already containing the same item type
- **Distance-based chest selection** -- Each Pokemon goes to its nearest chest

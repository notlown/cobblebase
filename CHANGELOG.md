# Changelog - Cobblebase

## [0.6.0] - 2026-04-01

### Support Buff System (8 new skills)
- **BuffExecutor** -- Flexible class-based executor that applies Minecraft status effects to all players within 16 blocks of the pasture origin
- Duration scales with proficiency: Prof 1 = 15s, Prof 2 = 25s, Prof 3 = 35s, Prof 4 = 50s, Prof 5 = 70s (effectively permanent)
- Cooldown scales with proficiency: Prof 1 = 60s, Prof 2 = 45s, Prof 3 = 30s, Prof 4 = 15s, Prof 5 = 0s (reapplies every tick)
- Subtle themed particles at the pasture origin when buffs are applied (different per buff type)
- Activity log entries when a player first receives a buff

### 8 Buff Skills
- **Speed Boost** (Speed II): Jolteon (5), Ninjask (4), Rapidash (4), Arcanine (3), Dodrio (3), Ponyta (2), Voltorb (2)
- **Strength Boost** (Strength I): Machamp (5), Hariyama (4), Conkeldurr (4), Lucario (3), Pangoro (3), Machoke (2)
- **Resistance Boost** (Resistance I): Steelix (5), Aggron (4), Bastiodon (4), Shuckle (4), Onix (3), Geodude (2)
- **Night Vision**: Umbreon (5), Noctowl (4), Hoothoot (3), Espeon (3), Zubat (2)
- **Water Breathing**: Lapras (5), Vaporeon (4), Milotic (4), Tentacruel (3), Golduck (3), Psyduck (2)
- **Jump Boost** (Jump Boost I): Lopunny (4), Hitmonlee (4), Blaziken (3), Spoink (2), Buneary (2)
- **Haste Boost** (Haste I): Alakazam (4), Metagross (3), Kadabra (3), Abra (2)
- **Saturation Boost**: Snorlax (5), Munchlax (4), Slurpuff (3), Chansey (3)

### GUI Updates
- BuffsPanel now shows descriptions for all 8 buff skills

## [0.5.0] - 2026-04-01

### Scout Executor
- **ScoutExecutor** -- Replaces the generic loot placeholder for the Scout skill
- Pokemon explores the surrounding area and discovers wild Pokemon, structures, and biomes
- **Wild Pokemon scouting**: Finds real wild Pokemon entities nearby, reports species, level, distance, and direction
- **Structure discovery** (Prof 3+): Locates Villages, Mineshafts, Ruined Portals, Shipwrecks, and Ocean Ruins
- **Biome discovery** (Prof 4+): Locates rare biomes like Mushroom Fields, Ice Spikes, Cherry Grove, Deep Dark, and Lush Caves
- **Proficiency scaling**: Prof 1 = 50 block range (Pokemon only), Prof 5 = 200 blocks (all types, faster cooldown)
- **Chat notifications**: Colored chat messages sent to nearby players on discovery
- **Visual feedback**: Enchant particles during scouting, cry + special animation on discovery

### Discovery Registry (Server-side Persistence)
- **DiscoveryRegistry** -- Tracks all discoveries made by Scout Pokemon
- Permanent discoveries (structures + biomes) persist forever in `cobblebase_discoveries.json`
- Wild Pokemon sightings auto-expire after 30 minutes
- Chunk-based dedup prevents re-reporting the same discovery
- Loaded/saved with world lifecycle (same pattern as BaseManager and LogManager)

### Discovery Tab (4th GUI Tab)
- **DiscoveryPanel** -- New tab in the Cobblebase GUI: [Skills] [Buffs] [Logs] [Discovery]
- Shows all permanent discoveries (structures + biomes) in a scrollable table
- Columns: Type, Name, Coordinates, Discovered By, When
- Color-coded by type: Structure=blue, Biome=green
- Filter buttons: All, Structures, Biomes
- Discovery count in footer
- Client-server sync via DiscoverySyncS2CPacket / DiscoveryRequestC2SPacket

### Species Skill Updates
- **Ninjask**: Scout proficiency 3 -> 5 (fastest scout)
- **Espeon**: Scout proficiency 3 -> 4
- **Pidgeot**: Scout proficiency 5 -> 4
- **Staraptor**: Scout proficiency 5 -> 4
- **Talonflame**: Added Scout proficiency 4
- **Eevee**: Added Scout proficiency 2
- **Growlithe**: Added Scout proficiency 2
- **Stoutland**: Added Scout proficiency 3
- **Starly**: Added Scout proficiency 1
- **Lillipup**: Added Scout proficiency 1
- Existing scout assignments unchanged: Jolteon (4), Absol (3), Arcanine (3), Lucario (3), Pidgey (1)

## [0.4.1] - 2026-04-01

### MiningExecutor (replaces broken HarvesterExecutor for mining)
- **MiningExecutor** -- New cooldown-based loot executor for the Mining skill (replaces HarvesterExecutor which incorrectly searched for apricorns)
- Pokemon walks to a random nearby position, plays digging animation with stone/gravel particles, then generates loot
- **30-second base cooldown** (proficiency reduces: Prof 1 = 100%, Prof 5 = 60%)
- **4 loot tiers** with same proficiency-based distribution as FinderExecutor:
  - **Common**: Tumblestone x1-3, Raw Copper x2-4, Raw Iron x1-2, Coal x2-4, Cobblestone x4-8, Gravel x2-4, Flint x1-2
  - **Uncommon**: Black/Sky Tumblestone x1-2, Raw Gold x1-2, Lapis x2-4, Redstone x3-6, Fire/Water/Grass/Electric Gem
  - **Rare**: Diamond, Emerald x1-2, Amethyst Shard x2-4, 10 Fossils (Helix, Dome, Old Amber, Root, Claw, Skull, Armor, Cover, Plume, Jaw, Sail), Dragon/Dark/Psychic Gem
  - **Ultra Rare**: Diamond x2-3, Ancient Debris, Netherite Scrap, 11 Type Gems (Fairy, Ghost, Ice, Fighting, Steel, Poison, Flying, Bug, Ground, Rock, Normal)
- **4 new loot table JSONs**: mining_common, mining_uncommon, mining_rare, mining_ultra_rare
- **mining.json** skill definition updated: cooldownSeconds 0 -> 30, description updated
- **ExecutorRegistry** updated: `mining` now maps to MiningExecutor instead of HarvesterExecutor
- **LogManager integration**: All mined items logged with appropriate rarity

## [0.4.0] - 2026-04-01

### Tabbed Cobblebase GUI
- **CobblebaseScreen** -- New tabbed interface replaces direct SkillAssignmentScreen
- Button renamed from "Skills" to "Cobblebase" in the Pasture Block UI
- **3 tabs**: Skills, Buffs, Logs -- each with its own colored accent bar

### Skills Tab
- All existing skill assignment functionality preserved
- Same dark-themed UI with category-colored buttons and proficiency stars
- Refactored into SkillsPanel for clean tab embedding

### Buffs Tab
- Shows all currently active jobs/effects for the Pasture
- Displays Pokemon Name, Job Name, Effect Description for each active skill
- Color-coded category bars (gathering=green, generation=orange, combat=red, support=pink, utility=blue, legendary=gold)
- Proficiency stars and human-readable effect descriptions (e.g., "Mentor (Prof 5): +166% Bonus XP every 60s")
- Scrollable for pastures with many active Pokemon

### Logs Tab
- Activity log showing recent events for the Pasture
- Scrollable table: Time, Pokemon, Action, Item, Rarity
- Filter buttons: All, Uncommon+, Rare+, Ultra Rare
- Color-coded by rarity: Common=gray, Uncommon=green, Rare=blue, Ultra Rare=gold
- Synced from server via LogSyncS2CPacket

### LogManager (Backend)
- **LogManager** object in core/ -- stores activity log entries per pasture BlockPos
- `LogEntry` data class: timestamp, pokemonName, action, itemName, rarity, worldTime
- `Rarity` enum: COMMON, UNCOMMON, RARE, ULTRA_RARE with display colors
- Max 100 entries per pasture, auto-cleanup of entries older than 24 hours
- Persistent: saved to `cobblebase_logs.json` alongside skill assignments
- Client-side cache populated via S2C packet

### Log Integration with Executors
- **FinderExecutor** (all 8 types) -- logs finds with rarity from loot table tier
- **HarvesterExecutor** -- logs harvested items as COMMON
- **FishingExecutor** -- logs fished items as COMMON
- **GuardExecutor** -- logs repelled Pokemon + guard loot drops
- **GathererExecutor** -- logs sorted/deposited items
- **GenericLootExecutor** -- logs all loot table drops

### Networking
- **LogRequestC2SPacket** -- Client requests logs when opening Cobblebase screen
- **LogSyncS2CPacket** -- Server sends log entries to client
- Server finds nearest pasture block entity to player for log lookup

## [0.3.1] - 2026-04-01

### 2 New Specialized Finder Subtypes
- **Finder Bal** (`finder_bal`) -- Pokeballs only: Poke/Great Ball + color balls (Common), Ultra/Net/Dive/Nest/Repeat/Timer/Quick Ball (Uncommon), Dusk/Luxury/Premier/Heal + Apricorn balls (Rare), Master/Beast/Dream/Park/Cherish Ball (Ultra Rare)
- **Finder Exp** (`finder_exp`) -- XP Candies only: Exp Candy XS/S (Common), Exp Candy S/M (Uncommon), Exp Candy M/L (Rare), Exp Candy L/XL + Rare Candy (Ultra Rare)
- **8 new loot table JSONs** -- 4 tiers x 2 types
- **2 new skill definition JSONs** in `data/cobblebase/skills/`
- **Species assignments:**
  - Finder Bal: Aipom (3), Ambipom (4), Pachirisu (3), Sentret (2), Furret (3), Zigzagoon (2), Linoone (3) -- collector Pokemon
  - Finder Exp: Added to all 11 Mentor Pokemon (proficiency = mentor prof - 1, min 1): Alakazam (4), Metagross (3), Gardevoir (3), Slowking (3), Oranguru (3), Espeon (2), Lucario (2), Mr. Mime (2), Orbeetle (2), Blissey (1), Chansey (1)
- ExecutorRegistry updated with 2 new executor registrations
- FinderExecutor companion object: added `Bal` and `Exp` instances

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
- **"Cobblebase" button** in Pasture Block UI (PastureWidget Mixin)
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

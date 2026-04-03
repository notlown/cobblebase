# Changelog

All notable changes to Cobblebase are documented here.

---

## [1.3.0] - Unreleased

### Admin GUI — In-Game Species Skill Editor
- **`/cobblebase admin`** command opens a two-pane admin screen (OP level 2 required)
- **Left pane:** Searchable species list showing ALL loaded Pokemon including fakemons
- **Right pane:** Skill editor with checkbox toggles and proficiency stars (1-5)
- **Live updates** — changes take effect immediately without restart
- **Persistence** — custom assignments saved to `cobblebase_species_overrides.json` per world
- **Reset to Default** — reverts a species to built-in assignments
- **Fakemon support** — assign skills to any species, even those without built-in data
- **Add new species** — type a Fakemon name in the search bar and click "+ Add" to create it
- **Pokemon sprites** in the species list with fallback for unknown species
- **3-column skill grid** — skills displayed in a compact 3-column layout per category
- **0.75x font scaling** — consistent compact text across all panels
- **Click+drag scrollbars** — both panels have functional draggable scrollbars
- **Buff particles disabled** — player particles from aura buffs (speed swirls, haste glitter etc.) removed

### Fakemon Pack Support — 7,430 New Species
Built-in skill assignments for 5 popular Fakemon mods:

| Pack | Species |
|------|---------|
| Lively Mons | 59 |
| Alatias Fakemon Pack | 87 |
| Laser's Fakemon Pack | 53 |
| Wilbayan's Fakemons | 37 |
| Gravelmon | 7,194 |

Skills auto-assigned by typing, base stats, and BST tiers:
- **BST 600+** (Legendary): Recruiter, Lucky Charm, Aura Boost, Prof 5
- **BST 570+** (Pseudo): Aura Boost, high Mentor
- **BST 530+**: +2 proficiency bonus
- **BST 480+**: +1 proficiency bonus

### Also New
- **Diving loot table** — Prismarine, Nautilus Shells, Heart of the Sea, Trident
- **Chest deposit for all loot jobs** — Diving, Archeologist, Honey Collect now deposit into nearby chests instead of dropping on ground
- **Farfetch'd crash fix** — Species names with special characters no longer crash the GUI
- **Skill selection GUI fix** — Selected skills no longer reset to "Auto" on tab switch or scroll
- **Water tethering mixin** — Prevents Cobblemon from teleporting swimming Pokemon out of water
- **Diving mons stay in water** — `canWalkOnWater` set via reflection for diving Pokemon

### Balance & Immersion
- **Standardized cooldowns** — All loot-producing jobs now use 300s base cooldown (Prof 1: ~8 min, Prof 3: 5 min, Prof 5: ~1.7 min)
- **Swim speed capped** — Water mons (Sharpedo, Basculegion etc.) limited to 0.15 swimSpeed to prevent zooming
- **Movement speed halved** — NavigationHelper default speed reduced from 1.0 to 0.5 for calmer base gameplay
- **Gatherer speed reduced** — Prof 1: 0.4, Prof 5: 0.6 (was 0.8 - 2.0)
- **Idle wandering** — All mons now wander randomly (15 block radius) when idle instead of standing still on the pasture box
- **Natural species speed** — Mons use their Cobblemon walkSpeed (capped at 0.4) instead of hardcoded values
- **Item dupe glitch fixed** — Gatherer now claims items immediately on targeting (sets pickupDelay to prevent simultaneous player pickup)

**Total species: 8,426**

---

## [1.2.0] - 2026-04-03

### Bug Fixes
- **Fixed GUI blur on Minecraft 1.21+** — Vanilla `Screen.render()` applies a Gaussian blur shader by default in 1.21+. Replaced `super.render()` with manual widget rendering to prevent blur without breaking button functionality
- **Fixed NeoForge crash** — Removed `@JvmStatic` from event subscribers causing `IllegalArgumentException` with Kotlin for Forge
- **NeoForge SkillsPanel** — Added 0.75x text scaling matching Fabric version for consistent compact text

---

## [1.1.0] - 2026-04-03

### Skills Tab Redesign
- **Dynamic row height** — Mons with few skills use compact rows (24px), mons with many skills get tall rows (42px) for a tighter layout
- **Auto button separated** — Auto has its own narrow column (36px), skills start in a dedicated column to the right so buttons never mix
- **Aura icon inline** — Removed the separate Aura column; buff icon now appears between Pokemon name and Skills buttons (reserved space for all rows, icon only shows for buff mons)
- **Sprite alignment fix** — Pokemon sprite icon is now top-aligned to match name/level text
- **Column headers fix** — Headers no longer overlap with the first row entries
- **Narrower Pokemon column** — Reduced empty space between name and buttons

### GUI Polish
- **Consistent 0.75x font scaling** across all tabs (Skills, Buffs, Logs, Scout)
- **Compact footer** — Footer area reduced from 28px to 18px, smaller Done button
- **Tab titles removed** — "Active Buffs & Jobs", "Activity Log", "Discovery Map" removed from all tabs for more content space
- **Hint text removed** — "Click to assign | Scroll" removed

### Aura Buff Rarity Overhaul
All aura buffs are now significantly rarer — only the most thematic Pokemon retain them.

| Buff | Before | After | Example Keepers |
|------|--------|-------|-----------------|
| Water Breathing | 133 | 18 | Kyogre, Milotic, Lapras, Vaporeon, Wailord |
| Night Vision | 132 | 15 | Giratina, Umbreon, Darkrai, Yveltal, Spectrier |
| Speed Boost | 45 | 12 | Ninjask, Regieleki, Rapidash, Jolteon, Zeraora |
| Strength Boost | 55 | 12 | Kartana, Rayquaza, Groudon, Machamp, Regigigas |
| Resistance Boost | 58 | 12 | Regirock, Steelix, Melmetal, Aggron, Shuckle |
| Haste Boost | 53 | 12 | Palkia, Dialga, Rayquaza, Mewtwo, Alakazam |
| Saturation Boost | 27 | 10 | Slurpuff, Snorlax, Munchlax, Ting-Lu, Blissey |
| Jump Boost | 5 | 5 | *(unchanged)* |

### 4 New Executors — All Placeholders Replaced
Every job in Cobblebase is now fully functional.

| Executor | Effect | Key Pokemon |
|----------|--------|-------------|
| 🌟 **Lucky Charm** | Boosts shiny rate for wild Pokemon near the **owner** (10-20 block radius, Prof 1: 1.4x, Prof 5: 3.0x) | Arceus (5), Mew (5), Jirachi (4), Victini (4) |
| 🍀 **Aura Boost** | Applies Luck effect to nearby players (Prof 1-2: Luck I, Prof 3-4: Luck II, Prof 5: Luck III global) | Victini (5), Rayquaza (5), Arceus (5) |
| 🌱 **Growth Aura** | Passively accelerates crop growth near the pasture | Arceus (5), Celebi (5), Shaymin (4) |
| 🧯 **Extinguisher** | Scans for and removes fire/soul fire/lit campfires near the base | Wartortle (3), Muk (3), Squirtle (2), Blastoise (?) |

### Honey Collect
- Now fully functional — drops Honeycomb, Honey Bottles, Honey Blocks without needing beehives
- Pokemon produce honey on cooldown-based drops

### Quality of Life
- **Buff logging removed** — Passive aura buffs no longer clutter the Activity Log
- **Lucky Charm**, **Aura Boost**, and **Growth Aura** show as passive icons (🌟 ✨ 🌱) in the Skills tab

---

## [0.7.0] - 2026-04-01

### Finder Jobs Renamed to Themed Names
All Finder jobs have been renamed to thematic profession names for better immersion. Internal IDs remain unchanged for backward compatibility.

| Old Name | New Name |
|----------|----------|
| Finder Evo | Alchemist |
| Finder Hea | Pharmacist |
| Finder Bui | Architect |
| Finder Ore | Excavator |
| Finder See | Botanist |
| Finder Bal | Collector |
| Finder Exp | Scholar |

### 5 New Finder Jobs
| Job | Focus | Key Pokemon |
|-----|-------|-------------|
| Chef | Food & cooking items | Snorlax (5), Munchlax (4), Alcremie (4) |
| Trainer | Vitamins & training items | Machamp (5), Lucario (4), Blaziken (4) |
| Armorer | Battle held items | Aegislash (5), Kingambit (5), Scizor (4) |
| Prospector | Relics & treasure | Gholdengo (5), Persian (4), Honchkrow (4) |
| Smith | Smithing templates & pottery | Tinkaton (5), Aegislash (4), Excadrill (3) |

### Botanist Mulch
- Added mulch variants (Growth, Rich, Surprise, Loamy) to Botanist common loot table

---

## [0.6.0] - 2026-04-01

### Support Buff System
- Added **BuffExecutor** — applies Minecraft status effects to all players within 16 blocks of the pasture
- Duration scales with proficiency: 15s (Prof 1) to 70s (Prof 5, effectively permanent)
- Cooldown scales with proficiency: 60s (Prof 1) to 0s (Prof 5)
- Themed particles at pasture origin when buffs are applied
- Activity log entries when players receive buffs

### 8 Buff Skills
| Buff | Effect | Key Pokemon |
|------|--------|-------------|
| Speed Boost | Speed II | Jolteon (5), Ninjask (4), Rapidash (4) |
| Strength Boost | Strength I | Machamp (5), Hariyama (4), Conkeldurr (4) |
| Resistance Boost | Resistance I | Steelix (5), Aggron (4), Bastiodon (4) |
| Night Vision | Night Vision | Umbreon (5), Noctowl (4), Espeon (3) |
| Water Breathing | Water Breathing | Lapras (5), Vaporeon (4), Milotic (4) |
| Jump Boost | Jump Boost I | Lopunny (4), Hitmonlee (4), Blaziken (3) |
| Haste Boost | Haste I | Alakazam (4), Metagross (3), Kadabra (3) |
| Saturation Boost | Saturation | Snorlax (5), Munchlax (4), Slurpuff (3) |

---

## [0.5.0] - 2026-04-01

### Scout Executor
- Added **ScoutExecutor** — discovers wild Pokemon, structures, and biomes
- Prof 1 = 50-block range (Pokemon only), Prof 5 = 200 blocks (all discovery types)
- Structures (Prof 3+): Villages, Mineshafts, Ruined Portals, Shipwrecks, Ocean Ruins
- Biomes (Prof 4+): Mushroom Fields, Ice Spikes, Cherry Grove, Deep Dark, Lush Caves
- Chat notifications and visual feedback on discovery

### Discovery System
- Added **DiscoveryRegistry** — persistent server-side storage for discoveries
- Permanent discoveries saved to `cobblebase_discoveries.json`
- Wild Pokemon sightings auto-expire after 30 minutes
- Chunk-based deduplication prevents re-reporting

### Discovery Tab (4th GUI Tab)
- Scrollable table with Type, Name, Coordinates, Discovered By, When
- Filter buttons: All, Structures, Biomes
- Client-server sync via network packets

### Species Updates
- Ninjask promoted to Scout 5 (fastest scout)
- Added Scout to: Talonflame (4), Stoutland (3), Eevee (2), Growlithe (2), Starly (1), Lillipup (1)

---

## [0.4.1] - 2026-04-01

### Mining Executor
- Added **MiningExecutor** — replaces broken HarvesterExecutor for mining skill
- 30-second base cooldown, reduced by proficiency
- 4 loot tiers: Common (tumblestone, raw ores), Uncommon (gold, lapis, type gems), Rare (diamond, fossils), Ultra Rare (ancient debris, netherite scrap)
- 4 new loot table JSONs and LogManager integration

---

## [0.4.0] - 2026-04-01

### Tabbed GUI
- Added **CobblebaseScreen** — new tabbed interface with Skills, Buffs, and Logs tabs
- Button renamed from "Skills" to "Cobblebase" in Pasture Block UI

### Buffs Tab
- Shows all active jobs/effects with category color-coding and proficiency stars

### Logs Tab
- Activity log with Time, Pokemon, Action, Item, Rarity columns
- Rarity filters: All, Uncommon+, Rare+, Ultra Rare
- Color-coded entries (gray/green/blue/gold)

### LogManager
- Stores up to 100 entries per pasture, auto-cleanup after 24 hours
- Persistent via `cobblebase_logs.json`
- Integrated with all executors (Finder, Harvester, Fishing, Guard, Gatherer)

---

## [0.3.1] - 2026-04-01

### New Finder Subtypes
- **Finder Bal** — Pokeballs: Poke/Great Ball to Master/Beast/Dream Ball
- **Finder Exp** — XP Candies: Exp Candy XS through XL + Rare Candy
- Auto-assigned: Finder Bal to collector Pokemon (Aipom, Zigzagoon), Finder Exp to all Mentor Pokemon

---

## [0.3.0] - 2026-04-01

### 5 Specialized Finder Subtypes
- Refactored FinderExecutor into class-based system with `finderType` parameter
- **Finder Evo** — Evolution items (stones, trade items, Ability Patch)
- **Finder Hea** — Healing items (potions, berries, revives, Sacred Ash)
- **Finder Bui** — Building materials (planks, bricks, prismarine, crying obsidian)
- **Finder Ore** — Ores and minerals (raw ores, diamonds, ancient debris)
- **Finder See** — Seeds and plants (wheat/apricorn/mint seeds, berries)
- 20 new loot table JSONs, 5 new skill definitions, 21 new species files

---

## [0.2.0] - 2026-04-01

### Mentor Skill
- Added **MentorExecutor** — passive XP boost for all Pokemon in the same pasture
- Prof 1 = +20% XP, Prof 5 = +100% (double XP). Highest proficiency wins (no stacking)
- 11 Pokemon: Alakazam (5), Metagross (4), Gardevoir (4), Slowking (4), Oranguru (4), and more
- Configurable max boost via settings

---

## [0.1.1] - 2026-04-01

### Gatherer Skill
- Added **GathererExecutor** — picks up dropped items and sorts into nearby chests/barrels
- Smart sorting prioritizes chests with matching items
- Radius 5–12 blocks based on proficiency
- 9 Pokemon: Snorlax (5), Furret (4), Munchlax (4), Linoone (3), and more

---

## [0.1.0] - 2026-03-21

### Foundation
- Project setup with Fabric mod template
- **SkillRegistry** — 22 built-in skills across 6 categories
- **SpeciesSkillRegistry** — 90+ Pokemon species with unique skill assignments
- JSON-configurable skill definitions and species assignments
- **BaseManager** — skill dispatch with manual/auto job assignment
- Persistent skill assignments saved to world folder

### Executors
- Harvester, Fishing, Guard, Healer, Generic Loot, Cauldron Fill, Furnace Fuel, Recruiter
- Smart inventory system with chest sorting and distance-based selection
- Navigation helper with pathfinding and timeout fallback

### Friend Recruiter
- Type-based recruiting with official Cobblemon 1.7.3 spawn rarity data
- Proficiency scaling, visual effects (particles + cry), all rates configurable

### Healer
- Direct % healing (5–25% max HP per tick) + fainted Pokemon revive
- Prioritizes lowest HP percentage

### Finder
- 10-minute cooldown, tiered loot (Pokeballs, Type Gems, Evo Stones, Exp Candy)

### Visual Effects
- Cry + attack animation on success, themed particles, working particles during cooldown

### Passive XP
- 5% of XP to next level every 60s, respects level cap

### GUI
- Cobblebase button in Pasture Block UI
- Dark-themed panel with color-coded skill buttons and proficiency stars

### Settings (Cloth Config)
- Dev Mode, Passive XP, Skill Toggles, Recruiter Cooldowns/Rates, Search Radius

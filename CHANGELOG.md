# Changelog

All notable changes to Cobblebase are documented here.

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

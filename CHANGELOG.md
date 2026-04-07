# Changelog

All notable changes to Cobblebase are documented here.

---

## [1.5.0] - 2026-04-08

The biggest release since launch — full Admin GUI overhaul, in-game Loot Editor, two new fakemon packs, and a long batch of performance + stability fixes.

### Performance
- **Particle stripping** — every job except Healer no longer emits its working particles (TPS optimization)
- **Finder / Producer / Recruiter throttling** — long-cooldown jobs skip the bulk of their tick logic 19 ticks out of 20
- **Cached loot table keys** — Finder no longer allocates `Identifier`/`RegistryKey` per roll
- **Targeted leaves pass-through** — restricted to the pasture area instead of the entire world
- **Admin GUI lazy loading** — species skill data fetched on demand
- **Smooth-scroll fix** for touchpad / smooth-scroll mice in the species list

### Admin GUI Overhaul
- New **Wiki** tab — curated outbound links to Documentation, Species Database, Datapack Generator, Job Reference, Loot Tables, GitHub, Modrinth, Discord
- New **Loot Editor** tab — edit bundled loot tables live in-game without datapacks. Per-job sidebar with rarity tabs, item id / weight / min / max / on-off toggle per entry, live item icon preview, **autocomplete suggestions** for item ids and item tags, **bulk add by tag** (`#minecraft:logs`, `#c:ores`), tooltips on every column header, save/reset buttons. Overrides persist in `<world>/cobblebase_loot_overrides.json` and apply immediately.
- **Jobs tab redesign** — compact sidebar with category list, custom-drawn rows at scale 0.7, hover tooltips with description/cooldown/radius, **per-job radius now actually applies in-game** (the previous version stored `radiusMin/radiusMax` which were dead fields)
- **Species tab improvements** — **sort toggle** (Pokedex# / A-Z / Z-A), species list **filtered to installed mons** so addon species you don't have are hidden, species sprite in the editor header, auto-refresh after lazy load
- Two orphaned loot tables removed: `honey_collect` and `dive_treasure`

### New Fakemon Pack Support
- **Baby Legends** (22 species) — baby forms of legendaries, each inheriting its evolution target's skill set with prof -1
- **Extra Paradox Mons** (27 species) — paradox alternates with ultra-rare-tier curated skills, prof capped at 4
- **Gravelmon removed** — Cobblemon 1.6 only and incompatible with 1.7+. 7,194 species removed.
- 39 missing species added: paradox pokemon, tapus, treasures of ruin, naming aliases
- Applied user submissions #JLZI (5 species) and #QPKX (2 species)
- **Total species: 8,426 → 1,367** (996 hand-crafted + 371 fakemon)

### Stability & Bug Fixes
- **Stuck detection** — escape from solid blocks via virtual bounding-box collision test, improved clip detection with `Box.intersects()`, 1-block oscillation tolerance, escalating unstick with Y-axis variation for flying mons, velocity impulse instead of immediate teleport at intensity 3+
- **Gatherer item dupe glitch fixed** — visual item entity no longer detected by pickup search, pickup cooldown after deposit timeout, drop-on-timeout instead of teleporting to chest, breadcrumb retrace for stair-stuck recovery
- **Mining items spawn at ground level** (was 1 block too high so gatherers couldn't reach them)
- **Sleep behavior** — working mons now sleep at night and wake up at sunrise to resume working, `forceSleep` instead of waiting for random chance, periodic sleep animation packets every 4s, animation chain fallback, wake-up animation sent before clearing rest state, velocity freeze only for sleeping mons (not sitting/socializing), passive buffs continue while sleeping
- **Hearty grain harvesting** — 2-block tall crops harvest both blocks, only adjacent block harvested if also mature
- **Berry harvest** uses Berry API for drops and resets to MATURE_AGE
- **22 broken `cobblebase:cauldron_fill` skill references** in species_skills replaced with `cobblebase:water_fill` (those species had no-op skills before this fix)
- **Cobblemon dependency** declared in fabric.mod.json so users on Cobblemon 1.6 get a clean unmet-dependency error instead of a mixin crash
- **Recruiter consolidation** into `friend_recruiter` + spawn buckets loaded from Cobblemon
- **Archeologist skill removed** (was unused)

### GUI Polish
- **Bottom bar redesign** — Discord icon | Mute toggle | Admin button (OP only) | Done
- **Mute button updates instantly** on click
- **Admin button** uses pending pattern to wait for data before opening
- New behavior variants: faint after chase, recoil in socialize, faint-to-sleep

---

## [1.3.9] - 2026-04-06

### Bug Fixes
- **Singleplayer handshake fix** — version check skipped in singleplayer, no more kicks
- **Version check default OFF** — players without the client mod can join servers normally. Enable `enforceVersionCheck` in settings if needed.
- **Furnace fuel fix** — Pokemon only fuel furnaces that are NOT already burning, no more wasting fuel on active furnaces
- **Gatherer filter fix** — simplified pickup logic back to v1.3.7 style to fix potential item pickup issues
- **Buffs tab descriptions** — Harvester no longer shows "20s" timer, Producer shows "Producing species-specific items"
- **Moomoo Milk** — Miltank/Gogoat/Skiddo now produce stackable Cobblemon Moomoo Milk instead of unstackable Milk Buckets
- **C2ME compatibility** — fixed threading crash with `world.random`
- **Modded container support** — Fabric Transfer API + NeoForge Capabilities for Sophisticated Storage, Iron Chests etc.
- **Passive buffs without entity** — buffs continue working when Pokemon owner is offline
- **Repo cleanup** — removed development files from repository

### Config
- **Gatherer pickup player drops** — new setting to control whether Gatherer picks up player-dropped items (default: on)
- **Keep Entities Alive** — [EXPERIMENTAL] force Cobblemon to keep Pokemon spawned when owner offline
- **Enforce Version Check** — optional setting to kick players without the mod (default: off)

### Community Skill Submissions
- Applied 11 species updates from community submission #XPYB (Zeraora)
- Includes: Absol, Accelgor, Aegislash, Aipom, Arbok, Audino, Bellibolt, Blitzle, Boltund, Bouffalant, Bunnelby

---

## [1.3.8] - 2026-04-05

### Producer Job
- New **"Producer"** job — Pokemon produce species-specific items passively based on their species
- **~180 species** with unique products across 35+ item types
- Products include: wool, string, milk, eggs, honeycomb, gold nuggets, diamonds, slime balls, charcoal, gunpowder, iron nuggets, blaze powder, ghast tears, rabbit's foot, redstone, cobblestone, feathers, bones, ink sacs, leather, clay, sand, mushrooms, pumpkins, logs, ores, and more
- Includes wood producers (Komala, Timburr-line, Trevenant), ore producers (Roggenrola-line, Ferroseed, Copperajah), object Pokemon (Honedge-line, Klink-line, Klefki), sweets (Alcremie, Milcery)
- Works alongside proficiency system — higher prof = faster production
- Removed Honey Collector (replaced by Producer for Combee/Vespiquen)

### Admin GUI — Jobs Tab
- **New "Jobs" tab** in `/cobblebase admin` — configure per-job settings server-wide
- **Per-job Cooldown** — set custom cooldown (seconds) for each job individually
- **Per-job Radius Min/Max** — set allowed search radius range for each job
- **Per-job Enable/Disable** — toggle jobs on/off server-wide
- **Save/Reset buttons** — changes are collected and only applied on Save
- **Disabled jobs auto-hide** — deactivated jobs no longer appear in the Skills panel at Pasture Blocks
- **Auto-reset assignments** — Pokemon assigned to a disabled job are automatically set to Relax

### Scout Improvements
- **Xaero's Minimap waypoint integration** — structure and biome discoveries automatically create Xaero waypoints with [Add] button
- **Increased search radius** — Prof 1: 100 blocks, Prof 2: 300, Prof 3: 500, Prof 4: 750, Prof 5: 1000 blocks
- **Spawn rarity filter** — Scout now filters wild Pokemon by Cobblemon spawn rarity instead of level. Only Uncommon+ Pokemon are reported.
- **Surface height for waypoints/TP** — discoveries use correct surface Y position instead of hardcoded values

### Bug Fixes
- **SkillDef.copy() NPE fix** — Gson parsed missing JSON fields as null, crashing Kotlin's copy() for non-null fields. Root cause of Admin Job Config not working.
- **Admin Job Config now works** — `executeSkill()` uses `getEffective()` to apply cooldown/radius overrides from the Admin GUI
- **C2ME compatibility** — replaced `world.random` with `ThreadLocalRandom` to avoid threading crash with C2ME mod
- **Gatherer respects `jobSearchRadius` config** — was hardcoded to 24 blocks, now uses the config value (capped at 24)
- **Item stacking fix** — items produced by jobs now stack normally with vanilla items (origin tags stripped on chest deposit)
- **Pokemon spawn offset** — safety/unstick teleports now place Pokemon 1.5-2.5 blocks from the Pasture Block instead of on top of it
- **Unstick without teleport** — stuck Pokemon now get redirected in a random direction instead of being teleported. Safe for enclosed builds (pens, aquariums, globes)
- **Unstick threshold reduced** — 15s → 7s for faster recovery
- **Buffs tab fix** — Relax mode now only shows passive buffs, not all available skills
- **Assignment pre-fetch** — skill assignments are fetched on server join so GUI shows correct state on first open
- **enableUnstickTeleport** config toggle removed (no longer needed since unstick doesn't teleport)

### Config
- **Console Logging toggle** — `enableConsoleLogging` (default: off) to reduce server console spam
- All new Admin GUI settings include descriptive tooltips

---

## [1.3.7] - 2026-04-05

### Config
- **Added `enableSafetyTeleport` toggle** — allows disabling the safety teleport entirely
- **Added `safetyTeleportDistance` setting** (10-100 blocks, default 30) — configure how far Pokemon can wander before being teleported back
- **Renamed `defaultSearchRadius` → `jobSearchRadius`** — clearer name with tooltip explaining it controls how far Pokemon search for resources (ores, crops, water, etc.)

### Gatherer Pasture Ownership
- **Gatherer now respects Pasture Block ownership** — items dropped by Cobblebase jobs are tagged with their Pasture Block origin, Gatherer only picks up items from its own Pasture Block (fixes item stealing in multiplayer bases)
- Untagged items (player drops, mob drops) can still be picked up by any Gatherer

### Multiplayer GUI Sync
- **Skill assignments now synchronize across all connected players in real-time** — when one player assigns or unassigns a job, every other player's GUI updates automatically
- **Client-side assignment cache** — GUI reads from a dedicated `AssignmentCache` instead of the server-only `BaseManager`, preventing stale state on clients
- **Assignment request on GUI open** — clients fetch the latest assignments from the server when opening the Cobblebase screen
- **Broadcast on change** — every skill assignment change is broadcast to all online players immediately

---

## [1.3.6] - 2026-04-05

### Version Handshake System
- **Server-enforced version check** — when a player joins, the client sends its Cobblebase version to the server
- **Minimum version enforcement** — server kicks players running versions older than 1.3.0 with a clear error message
- **Missing mod detection** — players without Cobblebase installed are kicked after a 5-second grace period
- **Kick messages** — color-formatted disconnect messages with version info and Modrinth download link
- Prevents exploits and desync from outdated clients

### Bug Fixes
- **Gatherer no longer deposits items into Pasture Blocks** — mods like CobBreeding add an Inventory to PokemonPastureBlockEntity (for egg slots), which caused the Gatherer to treat it as a storage container. Pasture Blocks are now explicitly excluded.

---

## [1.3.0] - 2026-04-04

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

### Balance & Immersion
- **Standardized cooldowns** — All jobs use 300s base cooldown (Prof 1: ~8 min, Prof 3: 5 min, Prof 5: ~1.7 min)
- **Harvester cooldown** — 60s base (Prof 3: 60s, Prof 5: 20s)
- **Legendary Recruiter cooldown** — 540s base (Prof 1: 15 min, Prof 5: 3 min)
- **All loot table item counts halved** — reduced item spam across 27 loot tables
- **Mining: 1 drop per cooldown** — no more burst looting after cooldown
- **Swim speed capped** — Water mons limited to 0.15 swimSpeed
- **Idle wandering** — All mons wander randomly (15 block radius) when idle
- **Cry sound cooldown** — Max 1 cry per 60 seconds per Pokemon
- **Default cry volume** — 80 → 30
- **In-GUI mute button** — 🔊/🔇 toggle in top-right corner
- **"Auto" renamed to "Idle"** — does nothing, only passive buffs remain active
- **Growth Aura nerfed** — pulse 3s → 30s, crops per pulse 2-16 → 1-3
- **Honey loot reduced** — 40% 1 comb, 15% 2 combs
- **Diving job removed** — will return improved in future update

### Bug Fixes
- **Item dupe glitch** — Gatherer claims items immediately on targeting
- **Skill selection reset** — no longer resets to "Idle" on tab switch or scroll
- **Idle actually idles** — null assignment no longer runs all skills
- **Farfetch'd crash** — special characters in species names no longer crash GUI
- **Mining burst loot** — was re-rolling every 8s after cooldown, now 1 roll per cycle
- **All cooldown formulas standardized** — Mining, Finder, Scout, Harvester had custom broken formulas
- **Gatherer deposit timeout** — 10s max to reach chest, then force deposit
- **Leaves escape** — mons auto-drop to ground when stuck in tree canopies
- **Stuck detection** — auto-teleport after 15s of not moving
- **Navigation fallback** — tries midpoint + random offset when pathfinding fails
- **Console log spam reduced** — removed 25 per-tick debug log lines
- **Dev mode removed** from settings

### Multiplayer
- **Pasture lock** — only the owner can open the Cobblebase GUI
- **Owner-only messages** — Recruiter and Scout notifications sent only to the owner
- **Fishing deposits to chest** — smart sorting with failed chest tracking (other jobs drop on floor for Gatherer)
- **Fishing water block cache** — scanned once, refreshed every 5 min

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

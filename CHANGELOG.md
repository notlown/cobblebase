# Changelog - Cobblebase

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

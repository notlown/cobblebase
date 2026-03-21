# Changelog - Cobblebase

## [0.1.0] - 2026-03-21

### Foundation
- Project setup with Fabric mod template
- **SkillRegistry** -- 21 built-in skills across 6 categories (gathering, generation, combat, support, utility, legendary)
- **SpeciesSkillRegistry** -- 90+ Pokemon species with unique skill assignments and proficiency levels (1-5)
- **SkillDef data model** -- JSON-configurable skills with executor, cooldown, radius, loot table, effect type
- **SpeciesSkills data model** -- JSON-configurable per-species skill lists with proficiency
- **BaseManager** -- Dispatches skill execution with manual job assignment support
- **ExecutorRegistry** -- Maps skill executor names to behavior implementations
- **SkillExecutor interface** -- Generic tick-based execution for all skills

### Executors (Phase 2)
- **HarvesterExecutor** -- Unified harvester: apricorns, crops, berries, mints, netherwart, tumblestones
- **FishingExecutor** -- Water navigation, fishing loot, item depositing
- **GuardExecutor** -- Wild Pokemon repelling, XP/loot rewards, level cap awareness
- **HealerExecutor** -- Regeneration effect for nearby players
- **GenericLootExecutor** -- Pickup, archeology, diving, honey loot generation
- **CauldronFillExecutor** -- Lava, water, powder snow cauldron filling
- **FurnaceFuelExecutor** -- Adds burn time to furnaces
- **InventoryHelper** -- Shared chest/barrel finding and item insertion

### Effects and Passive XP (Phase 3)
- **SkillEffects** -- Visual effects system with per-skill-type particles and animations:
  - Harvest: tackle animation + green sparkles + composter particles
  - Water/Fishing: watergun animation + splash + fishing wake + bubbles
  - Fire: ember animation + flames + lava + smoke
  - Combat: tackle animation + angry villager + crit stars + smoke
  - Heal: wish animation + hearts + green sparkles
  - Special/Legendary: enchant glitter + end rod glow
- **Working particles** -- Periodic particles while on cooldown (fishing bubbles, patrol anger, etc.)
- **Server-side animations** -- Uses CobblemonNetwork + PlayPosableAnimationPacket with fallback chains
- **Passive XP** -- 125 XP every 60 seconds for all pastured Pokemon (~1 level per in-game day)
- **CobblebaseExperienceSource** -- Custom ExperienceSource marked as sidemod

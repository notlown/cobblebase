# Changelog - Cobblebase

## [0.1.0] - 2026-03-21

### Foundation
- Project setup with Fabric mod template
- **SkillRegistry** — 25+ built-in skills across 6 categories (gathering, generation, combat, support, utility, legendary)
- **SpeciesSkillRegistry** — 60+ Pokemon species with unique skill assignments and proficiency levels (1-5)
- **Skill data model** — JSON-configurable skills with executor, cooldown, radius, loot table, effect type
- **SpeciesSkills data model** — JSON-configurable per-species skill lists with proficiency
- **BaseManager** — Dispatches skill execution with manual assignment support
- **ExecutorRegistry** — Maps skill executor names to behavior implementations
- **SkillExecutor interface** — Generic tick-based execution for all skills
- README with full documentation

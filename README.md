# Cobblebase

[![License: MPL-2.0](https://img.shields.io/badge/License-MPL_2.0-brightgreen.svg?style=flat-square)](https://opensource.org/licenses/MPL-2.0)

> Palworld-style Pokemon base management for [Cobblemon](https://cobblemon.com/)

**Cobblebase** turns the Pasture Block into a living, breathing Pokemon base. Every Pokemon has unique job skills -- not based on type, but on species. Assign your Pokemon to jobs, watch them work, and build the ultimate base.

## Features

### Per-Species Skill System
Unlike type-based job systems, Cobblebase gives **every Pokemon species its own unique skill set**, just like Palworld's work suitabilities:

| Pokemon | Skills | Proficiency |
|---------|--------|-------------|
| Charizard | Lava Fill, Fuel, Guard, Scout | 4, 4, 3, 4 |
| Nidoking | Archeology, Guard, Mining | 4, 3, 2 |
| Ninjask | Harvester, Scout, Item Gather | 4, 3, 2 |
| Chansey | Healer | 5 (Master) |
| Mew | Recruiter, Lucky Charm, Aura Boost | 5, 5, 3 |
| Arceus | Recruiter, Aura, Lucky Charm, Growth | 5, 5, 5, 5 |

**150+ Pokemon** have hand-crafted skill assignments, including all starters from Gen 1-9. Fairy Pokemon and starters get **Friend Recruiter**, legendaries get exclusive skills like **Recruiter** and **Aura Boost**.

### Friend Recruiter System
Fairy Pokemon and all starters can recruit wild Pokemon of their own type:
- **Type-based**: A Water starter recruits Water-types, a Fire starter recruits Fire-types
- **Official rarity data**: Uses Cobblemon 1.7.3 spawn bucket data (Common/Uncommon/Rare/Ultra-Rare)
- **Proficiency scaling**: Prof 1 = normal rates, Prof 5 = double chance for rare/ultra-rare
- **Visual feedback**: Recruited Pokemon spawn with enchant sparkles and play their cry
- **All rates configurable** in the settings menu

| Proficiency | Common | Uncommon | Rare | Ultra-Rare |
|-------------|--------|----------|------|------------|
| 1 (base) | 93.8% | 5.0% | 1.0% | 0.2% |
| 3 (skilled) | 90.3% | 7.5% | 1.5% | 0.3% |
| 5 (master) | 86.8% | 10.0% | 2.0% | 0.4% |

### Proficiency System
Each Pokemon has a proficiency level (1-5) for each of its skills:
- **1** -- Novice (1.67x cooldown)
- **2** -- Apprentice (1.33x cooldown)
- **3** -- Skilled (normal speed)
- **4** -- Expert (0.67x cooldown)
- **5** -- Master (0.33x cooldown)

A Magikarp with Fishing proficiency 1 is slow. A Gyarados with Fishing proficiency 5 is a machine.

### Skill Assignment GUI
Open the Pasture Block and click the **"Skills"** button to assign jobs:
- Each Pokemon shows **only the skills it can perform**
- Click a skill to assign it (green = active)
- **Auto mode** (default) -- Pokemon performs all its skills
- **Manual mode** -- Pokemon focuses on one specific skill
- Scrollable list for many Pokemon and skills

### 21 Skills Across 6 Categories

| Category | Skills | Description |
|----------|--------|-------------|
| **Gathering** | Harvester, Fishing, Diving, Mining, Honey | Collect resources from the world |
| **Generation** | Lava Fill, Water Fill, Snow Fill, Furnace Fuel, Brew Fuel | Produce resources in containers |
| **Combat** | Guard | Patrol and repel wild Pokemon for XP and loot |
| **Support** | Healer | Direct % healing for players and Pokemon, revives fainted mons |
| **Utility** | Irrigator, Extinguisher, Item Gatherer, Scout, Archeologist, Finder | Various helper jobs |
| **Fairy/Starters** | Friend Recruiter | Spawns wild Pokemon of same type nearby |
| **Legendary** | Recruiter, Aura Boost, Lucky Charm, Growth Aura | Exclusive to rare/legendary Pokemon |

### Visual Effects
Every skill has themed visual feedback:
- **Cry + attack animation** on success (uses Cobblemon's animation fallback system)
- **Themed particles**: splash for water, flames for fire, hearts for healing, enchant sparkle for legendaries
- **Working particles** during cooldowns so you can see who's busy

### Smart Inventory System
- Pokemon find and deposit items in the **nearest chest or barrel**
- Distance calculated from **Pokemon position** (not Pasture Block), so items spread across multiple containers
- Retries all containers before dropping items as last resort

### Passive XP
All pastured Pokemon slowly gain experience over time:
- Default: **125 XP every 60 seconds** (~1 level per in-game day at mid-levels)
- Works even while the Pokemon is idle or sleeping
- Respects the global level cap

## Data-Driven Design

Everything is JSON-configurable and can be customized via **datapacks**:

### Skill Definitions
```json
{
  "id": "cobblebase:fishing",
  "name": "Fishing",
  "category": "gathering",
  "cooldownSeconds": 60,
  "searchRadius": 10,
  "executor": "fishing",
  "effectType": "water",
  "lootTable": "minecraft:gameplay/fishing"
}
```

### Species Skill Assignments
```json
{
  "species": "nidoking",
  "skills": [
    { "skillId": "cobblebase:archeologist", "proficiency": 4 },
    { "skillId": "cobblebase:guard", "proficiency": 3 },
    { "skillId": "cobblebase:mining", "proficiency": 2 }
  ]
}
```

Server admins can add new skills, modify existing ones, or change which Pokemon can do what -- all without touching code.

## Requirements

- **Minecraft** 1.21.1
- **Fabric Loader** 0.16.14+
- **Fabric Language Kotlin**
- **Cobblemon** 1.7.0+

## Installation

1. Download the latest JAR from [Releases](https://github.com/notlown/cobblebase/releases)
2. Place it in your `mods/` folder
3. Make sure Cobblemon and Fabric Language Kotlin are installed
4. Launch the game

## Building from Source

```bash
git clone https://github.com/notlown/cobblebase.git
cd cobblebase
./gradlew fabric:build
```

Output JAR will be in `fabric/build/libs/`. Requires **Java 21**.

## Architecture

```
cobblebase/
├── core/
│   ├── Cobblebase.kt          # Entry point
│   ├── SkillDef.kt            # Skill data model
│   ├── SpeciesSkills.kt       # Per-species skill assignments
│   ├── SkillRegistry.kt       # Loads and manages skill definitions
│   ├── SpeciesSkillRegistry.kt # Maps species to skills
│   ├── BaseManager.kt         # Skill dispatch + job assignment
│   ├── ExecutorRegistry.kt    # Maps executor names to implementations
│   ├── SkillExecutor.kt       # Executor interface
│   ├── PassiveXp.kt           # Passive XP system
│   ├── executors/              # All skill executor implementations
│   ├── effects/                # Visual effects (particles, animations)
│   └── net/                    # Network packets
├── mixin/                      # Pasture Block integration
└── fabric/
    ├── CobblebaseFabric.kt     # Fabric entrypoint + packet registration
    ├── client/gui/             # Skill Assignment Screen
    └── mixin/                  # Client-side PastureWidget mixin
```

## Roadmap

- [ ] Datapack loading for custom skills and species assignments
- [ ] Config screen (Cloth Config integration)
- [ ] Stamina/rest system with sleep animations
- [ ] More legendary-exclusive skills
- [ ] Pokemon visual item holding
- [ ] Skill leveling through use

## Credits

- Inspired by [Cobbleworkers](https://github.com/Accieo/cobbleworkers) by [Accieo](https://github.com/Accieo)
- Built by [notlown](https://github.com/notlown)

## License

Licensed under [MPL-2.0](https://mozilla.org/MPL/2.0/)

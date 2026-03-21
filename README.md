# Cobblebase

> Palworld-style Pokemon base management for [Cobblemon](https://cobblemon.com/)

**Cobblebase** turns the Pasture Block into a living, breathing Pokemon base. Every Pokemon has unique job skills — not based on type, but on species. Assign your Pokemon to jobs, watch them work, and build the ultimate base.

## How It Works

Unlike type-based job systems, Cobblebase gives **every Pokemon species its own skill set**:

| Pokemon | Skills | Proficiency |
|---------|--------|-------------|
| Charizard | Lava, Fuel, Guard, Scout | 4, 4, 3, 4 |
| Nidoking | Archeology, Guard, Amethyst | 4, 3, 2 |
| Ninjask | Apricorn, Scout, Item Gather | 4, 3, 2 |
| Chansey | Healer | 5 (Master) |
| Mew | Recruiter, Lucky Charm, Aura Boost | 5, 5, 3 |

**Proficiency** (1-5) affects speed and output quality:
- 1 = Novice (slow)
- 3 = Skilled (normal)
- 5 = Master (fast, better loot)

## Skill Categories

| Category | Skills |
|----------|--------|
| Gathering | Fishing, Diving, Apricorn, Berry, Crops, Amethyst, Tumblestone, Mint, Netherwart, Honey |
| Generation | Lava, Water, Snow, Fuel, Brewing Fuel |
| Combat | Guard |
| Support | Healer |
| Utility | Irrigator, Fire Extinguisher, Item Gatherer, Scout, Archeologist, Pick-up |
| Legendary | Recruiter, Aura Boost, Lucky Charm, Growth Aura |

## Data-Driven

Everything is defined in JSON and can be customized via datapacks:

**Skills** (`data/cobblebase/skills/*.json`):
```json
{
  "id": "cobblebase:fishing",
  "name": "Fishing",
  "category": "gathering",
  "cooldownSeconds": 60,
  "executor": "fishing",
  "lootTable": "minecraft:gameplay/fishing"
}
```

**Species Skills** (`data/cobblebase/species_skills/*.json`):
```json
{
  "species": "nidoking",
  "skills": [
    { "skill": "cobblebase:archeologist", "proficiency": 4 },
    { "skill": "cobblebase:guard", "proficiency": 3 }
  ]
}
```

## Building

```bash
git clone https://github.com/notlown/cobblebase.git
cd cobblebase
./gradlew fabric:build
```

Requires Java 21.

## Architecture

```
cobblebase/
├── core/           # Entry point, registries, base manager
├── skill/          # Skill and SpeciesSkills data models
├── executor/       # Skill executors (behavior implementations)
├── config/         # Cloth Config integration
├── gui/            # Job assignment screen
├── net/            # Network packets
└── mixin/          # Pasture Block integration
```

## Credits

- Inspired by [Cobbleworkers](https://github.com/Accieo/cobbleworkers) by Accieo
- Built by [notlown](https://github.com/notlown)

## License

Licensed under [MPL-2.0](https://mozilla.org/MPL/2.0/)

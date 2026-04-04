<p align="center">
  <img src="cobblebase_header.png" alt="Cobblebase" width="100%">
</p>

<h1 align="center">Cobblebase</h1>

<p align="center"><b>Palworld-style base management for Cobblemon — assign jobs, watch Pokemon work, build the ultimate base.</b></p>

<p align="center">

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?style=flat-square&logo=minecraft)](https://minecraft.net)
[![Cobblemon](https://img.shields.io/badge/Cobblemon-1.7.0+-E8532E?style=flat-square)](https://cobblemon.com)
[![Fabric](https://img.shields.io/badge/Fabric-0.16.14+-DBD0B4?style=flat-square)](https://fabricmc.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1+-F16436?style=flat-square)](https://neoforged.net)
[![License: MPL-2.0](https://img.shields.io/badge/License-MPL_2.0-brightgreen.svg?style=flat-square)](https://opensource.org/licenses/MPL-2.0)
[![Discord](https://img.shields.io/badge/Discord-Join%20Us-5865F2?style=flat-square&logo=discord&logoColor=white)](https://discord.gg/6As3sVZgVT)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-Support%20Us-FF5E5B?style=flat-square&logo=ko-fi&logoColor=white)](https://ko-fi.com/notlown)
[![Modrinth](https://img.shields.io/badge/Modrinth-Download-00AF5C?style=flat-square&logo=modrinth&logoColor=white)](https://modrinth.com/project/cobblebase)

</p>

---

## Overview

**Cobblebase** transforms the Pasture Block into a living, breathing Pokemon base. Every Pokemon species has hand-crafted job skills and proficiency levels — not based on type, but on identity. A Charizard fills lava cauldrons and guards your base. An Alakazam mentors your entire team. A Mew recruits legendaries.

- 💎 **8,426 Pokemon** with unique skill assignments (996 hand-crafted + 7,430 Fakemon)
- ⚡ **43 jobs** across 10 categories — gathering, finding, combat, support, logistics, exploration, environmental, recruiting, passive buffs, and legendary
- ⭐ **Proficiency 1-5** per skill per species — novice to master
- 🎯 **Tabbed GUI** with Skills, Buffs, Logs, and Discovery panels
- 🔧 **Admin GUI** for in-game species skill editing (`/cobblebase admin`)
- 🔒 **Multiplayer ready** — pasture lock, owner-only messages
- 🛡️ **Fully configurable** via Cloth Config and JSON datapacks

### 🌐 Online Tools

| Tool | Description |
|------|-------------|
| 📚 [Documentation](https://notlown.github.io/cobblebase-web/docs/) | Full guides for jobs, proficiency, GUI, admin, datapacks, and more |
| 📊 [Species Database](https://notlown.github.io/cobblebase-web/database/) | Browse all 8,426 Pokemon with skills, proficiency, and an inline editor |
| 🔧 [Datapack Generator](https://notlown.github.io/cobblebase-web/generator/) | Create custom species skill datapacks without editing JSON |

### 💬 Community & Support

<p align="center">
<a href="https://discord.gg/6As3sVZgVT"><img src="discord_banner_small.png" alt="Join us on Discord"></a>
&nbsp;&nbsp;
<a href="https://ko-fi.com/notlown"><img src="kofi_banner_small.png" alt="Support me on Ko-fi"></a>
</p>

---

## 🌾 Gathering

Pokemon harvest, fish, mine, and collect resources from the world around your base.

| Job | Description | Example Pokemon |
|-----|-------------|-----------------|
| 🌾 **Harvester** | Picks apricorns, crops, berries, mints, tumblestones | Venusaur, Scizor, Celebi |
| 🎣 **Fishing** | Navigates to water and catches fish/loot | Gyarados, Blastoise, Kyogre |
| ⛏️ **Mining** | Digs for ores, fossils, gems, and tumblestones (1 drop per cycle) | Steelix, Excadrill, Ting-Lu |
| 🍯 **Honey Collect** | Produces honey and honeycomb (no beehive needed) | Vespiquen, Beedrill, Combee |
| 🏺 **Archeologist** | Excavates ancient relics and artifacts | Nidoking, Steelix, Nidoqueen |

---

## 🔍 Finding

Twelve specialized Finder subtypes with dedicated loot tables for targeted item discovery.

| Job | Focus | Example Loot |
|-----|-------|--------------|
| 🧪 **Alchemist** | Evolution items | Fire/Thunder Stone, Linking Cord, Ability Patch |
| 💊 **Pharmacist** | Healing items | Potions, Revives, Sacred Ash |
| 🏗️ **Architect** | Building materials | Prismarine, Sea Lantern, Crying Obsidian |
| ⛏️ **Excavator** | Ores and minerals | Raw Iron/Gold, Diamond, Ancient Debris |
| 🌱 **Botanist** | Seeds, plants, mulch | Apricorn Seeds, Mint Seeds, Mulch |
| 📦 **Collector** | Pokeballs | Great Ball, Apricorn Balls, Master Ball |
| 📚 **Scholar** | XP Candies | Exp Candy XS-XL, Rare Candy |
| 🍳 **Chef** | Food and cooking | Ponigiri, Lava Cookie, Enchanted Golden Apple |
| 💪 **Trainer** | Vitamins and training | HP Up, Protein, Ability Patch |
| ⚔️ **Armorer** | Battle held items | Choice Band, Life Orb, Focus Sash |
| 💰 **Prospector** | Relics and treasure | Relic Coins, Gold, Netherite Ingot |
| 🔨 **Smith** | Smithing templates | Armor Trims, Pottery Sherds, Netherite Upgrade |

---

## ⚔️ Combat

| Job | Description | Example Pokemon |
|-----|-------------|-----------------|
| 🛡️ **Guard** | Patrols the area, repels wild Pokemon for XP and loot | Gallade, Scizor, Incineroar |

---

## 💚 Support

| Job | Effect | Example Pokemon |
|-----|--------|-----------------|
| 💚 **Healer** | Heals injured Pokemon in your team, revives fainted Pokemon | Blissey, Xerneas, Zacian |
| 🎓 **Mentor** | Passive XP boost for all Pokemon in the pasture | Alakazam, Latios, Mesprit |

---

## ✨ Passive Buffs

Status effects applied to all players within 40 blocks of the pasture. Beyond 40 blocks, buffs apply to the pasture owner only. At Prof 5, buffs are global (owner only) and effectively permanent.

| Buff | Effect | Example Pokemon |
|------|--------|-----------------|
| ⚡ **Speed Boost** | Speed II | Ninjask, Regieleki, Jolteon |
| 💪 **Strength Boost** | Strength I | Kartana, Rayquaza, Machamp |
| 🛡️ **Resistance Boost** | Resistance I | Regirock, Steelix, Melmetal |
| 👁️ **Night Vision** | Night Vision | Giratina, Umbreon, Darkrai |
| 🫧 **Water Breathing** | Water Breathing | Kyogre, Milotic, Lapras |
| 🦘 **Jump Boost** | Jump Boost I | Lopunny, Hitmonlee, Blaziken |
| ⚒️ **Haste Boost** | Haste I | Palkia, Dialga, Alakazam |
| 🍖 **Saturation** | Saturation | Slurpuff, Snorlax, Munchlax |
| 🍀 **Aura Boost** | Luck I-III | Victini, Rayquaza, Arceus |

---

## 🌟 Legendary Abilities

Special passive abilities only found on legendary and mythical Pokemon.

| Ability | Effect | Pokemon |
|---------|--------|---------|
| 🌟 **Lucky Charm** | Boosts shiny rate for wild Pokemon near the owner (1.4x-3.0x) | Arceus, Mew, Jirachi, Victini |
| 🌱 **Growth Aura** | Accelerates crop growth near the pasture (pulse every 30s, 1-3 crops) | Arceus, Celebi, Shaymin |
| 🧯 **Extinguisher** | Auto-removes fire and extinguishes campfires near the base | Wartortle, Muk, Squirtle, Blastoise |

---

## 📦 Logistics

| Job | Description | Example Pokemon |
|-----|-------------|-----------------|
| 📥 **Gatherer** | Picks up dropped items and sorts them into nearby chests | Ambipom, Furret, Munchlax |

Smart sorting prioritizes chests already containing the same item type. Deposit timeout of 10 seconds.

---

## 🔭 Exploration

| Job | Description | Example Pokemon |
|-----|-------------|-----------------|
| 🔭 **Scout** | Discovers wild Pokemon, structures, and biomes | Ninjask, Spectrier, Rayquaza |

| Proficiency | Range | Discovers |
|-------------|-------|-----------|
| 1-2 | 50-100 blocks | Wild Pokemon only |
| 3 | 120 blocks | + Structures (Villages, Mineshafts, Shipwrecks) |
| 4-5 | 160-200 blocks | + Rare Biomes (Mushroom Fields, Deep Dark, Cherry Grove) |

---

## 🌿 Environmental

| Job | Description | Example Pokemon |
|-----|-------------|-----------------|
| 💧 **Irrigator** | Hydrates nearby farmland | Venusaur, Virizion, Shaymin |
| 🌋 **Lava Fill** | Fills cauldrons with lava | Magmortar, Flareon, Magmar |
| 💦 **Water Fill** | Fills cauldrons with water | Vaporeon, Blastoise, Palkia |
| ❄️ **Snow Fill** | Fills cauldrons with powder snow | Regice, Glastrier, Articuno |
| 🔥 **Furnace Fuel** | Adds burn time to furnaces | Moltres, Volcanion, Ho-Oh |
| 🧪 **Brew Fuel** | Fuels brewing stands | Weezing, Dragonite, Koffing |

---

## 🤝 Recruiting

| Job | Description | Example Pokemon |
|-----|-------------|-----------------|
| 🤝 **Friend Recruiter** | Attracts wild Pokemon of the same type | Togekiss, Sylveon, Gardevoir |
| ⭐ **Legendary Recruiter** | Spawns rare/legendary Pokemon (540s cooldown) | Arceus, Mew, Jirachi |

---

## ⭐ Proficiency System

Every Pokemon has a proficiency level (1-5) for each of its skills. Higher proficiency means faster cooldowns, larger range, and better loot quality. Standard base cooldown is **300 seconds** (Harvester: 60s, Legendary Recruiter: 540s).

| Level | Cooldown Multiplier | Effect |
|-------|---------------------|--------|
| 1 | 1.67x (slower) | Base rates, small range |
| 2 | 1.33x | Slightly improved |
| 3 | 1.00x (normal) | Standard performance |
| 4 | 0.67x (faster) | Better loot tiers, wider range |
| 5 | 0.33x (fastest) | Best rates, maximum range |

---

## 🎮 GUI

Open any Pasture Block and click **"Cobblebase"** to access the management interface.

| Tab | What It Shows |
|-----|---------------|
| **Skills** | Per-Pokemon skill list with Idle/manual assignment. Aura icons inline. Dynamic row heights. Mute button in top-right. |
| **Buffs** | All active jobs/effects for this pasture. Color-coded by category with proficiency stars. |
| **Logs** | Recent activity log. Filterable by rarity (Uncommon+, Rare+, Ultra Rare). Last 100 events. |
| **Discovery** | Permanent scout discoveries — structures and biomes with coordinates. |

**Pasture Lock:** Only the pasture owner can open the Cobblebase GUI on multiplayer servers.

---

## 🔧 Admin GUI

Server admins can manage skill assignments for any Pokemon species in-game — no JSON editing or restarts needed.

**Open with:** `/cobblebase admin` (requires OP level 2)

| Feature | Description |
|---------|-------------|
| **Species Browser** | Searchable list of ALL loaded Pokemon, including fakemons |
| **Skill Editor** | Toggle skills on/off, set proficiency (1-5) with clickable stars |
| **Live Updates** | Changes take effect immediately — no restart required |
| **Persistence** | Saved to `cobblebase_species_overrides.json` per world |
| **Fakemon Support** | Assign skills to ANY species, even those without built-in data |

---

## 🎨 Supported Fakemon Packs

| Pack | Species | Method |
|------|---------|--------|
| **Cobblemon** (official) | 996 | Hand-crafted, individually tuned |
| **Lively Mons** | 59 | Auto-assigned by typing + stats |
| **Alatias Fakemon Pack** | 87 | Auto-assigned by typing + stats |
| **Laser's Fakemon Pack** | 53 | Auto-assigned by typing + stats |
| **Wilbayan's Fakemons** | 37 | Auto-assigned by typing + stats |
| **Gravelmon** | 7,194 | Auto-assigned by typing + stats |

**Total: 8,426 species with skill assignments**

---

## 🎵 Immersion

- **Idle wandering** — Pokemon wander randomly (15 block radius) when not on a job
- **Cry sound cooldown** — Max 1 cry per 60 seconds per Pokemon (default volume: 30)
- **Mute button** — Toggle cry sounds in the GUI
- **Stuck detection** — Auto-teleport after 15 seconds of not moving
- **Leaves escape** — Pokemon auto-drop to ground when stuck in tree canopies
- **Swim speed cap** — Water Pokemon limited to 0.15 swim speed

---

## ⚙️ Configuration

Press **K** to open the Cloth Config settings screen. Key options:

- **Passive XP** — Toggle, percentage (default 5%), interval (default 60s)
- **Skill Toggles** — Enable/disable entire skill categories
- **Recruiter Settings** — Cooldowns and spawn rates
- **Search Radius** — Default block search radius for all skills
- **Mentor Max Boost** — Cap the mentor XP multiplier (default 100% at Prof 5)

Everything is also JSON-configurable and customizable via **[datapacks](https://notlown.github.io/cobblebase-web/docs/datapacks.html)**.

---

## 📥 Installation

### Requirements
- Minecraft **1.21.1**
- Cobblemon **1.7.0+**
- **Cloth Config** (required — mod crashes without it)
- **Fabric**: Fabric Loader 0.16.14+ and Fabric Language Kotlin
- **NeoForge**: NeoForge 21.1+ and Kotlin for Forge 5.x

### Steps
1. Download the latest JAR from [Modrinth](https://modrinth.com/project/cobblebase) or [Releases](https://github.com/notlown/cobblebase/releases)
2. Place it in your `mods/` folder alongside Cobblemon and Cloth Config
3. Launch the game — right-click a Pasture Block to get started

### Building from Source
```bash
git clone https://github.com/notlown/cobblebase.git
cd cobblebase
./gradlew build          # builds both Fabric and NeoForge
./gradlew fabric:build   # Fabric only
./gradlew neoforge:build # NeoForge only
```
Output JARs in `fabric/build/libs/` and `neoforge/build/libs/`. Requires **Java 21**.

---

## 💬 Community and Support

- [Discord](https://discord.gg/6As3sVZgVT) — Get help, report bugs, suggest features
- [Ko-fi](https://ko-fi.com/notlown) — Support the project
- [Documentation](https://notlown.github.io/cobblebase-web/docs/) — Full guides and reference
- [Species Database](https://notlown.github.io/cobblebase-web/database/) — Browse and edit all 8,426 Pokemon
- [Datapack Generator](https://notlown.github.io/cobblebase-web/generator/) — Create custom skill datapacks

---

## Credits

Built by [notlown](https://github.com/notlown)

## License

Licensed under [MPL-2.0](https://mozilla.org/MPL/2.0/)

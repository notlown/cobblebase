<p align="center">
  <img src="https://raw.githubusercontent.com/notlown/cobblebase/main/cobblebase_header.png" alt="Cobblebase" width="100%">
</p>

# Cobblebase

**Palworld-style base management for Cobblemon — assign jobs, watch Pokemon work, build the ultimate base.**

## ✨ Overview

Cobblebase transforms the Pasture Block into a living, breathing Pokemon base. Every Pokemon species has hand-crafted job skills and proficiency levels — not based on type, but on identity. A Charizard fills lava cauldrons and guards your base. An Alakazam mentors your entire team. A Mew recruits legendaries.

- 💎 **1,367 Pokemon** with unique skill assignments (996 hand-crafted + 371 Fakemon)
- ⚡ **43 jobs** across 10 categories
- ⭐ **Proficiency 1-5** per skill — faster cooldowns, better loot, wider range
- 🎮 **Tabbed GUI** — Skills, Buffs, Logs, and Discovery panels
- 🔧 **Admin GUI** — Edit species skills, jobs config, and **loot tables** live in-game (`/cobblebase admin`)
- 🍱 **Loot Editor** — change item drops for any job without writing a datapack, with autocomplete and `#tag` bulk-add
- 🔒 **Multiplayer ready** — Pasture lock, owner-only notifications
- 🛡️ **Fully customizable** via Cloth Config and JSON datapacks
##
<p align="center">
<a href="https://discord.gg/6As3sVZgVT"><img src="https://raw.githubusercontent.com/notlown/cobblebase/main/discord_banner_small.png" alt="Join us on Discord"></a>
&nbsp;&nbsp;
<a href="https://ko-fi.com/notlown"><img src="https://raw.githubusercontent.com/notlown/cobblebase/main/kofi_banner_small.png" alt="Support me on Ko-fi"></a>
</p>

## 🌐 Online Tools

- 📚 [Full Documentation](https://notlown.github.io/cobblebase-web/docs/) — Guides for every feature
- 📊 [Species Database](https://notlown.github.io/cobblebase-web/database/) — Browse & edit all 1,367 Pokemon
- 🔧 [Datapack Generator](https://notlown.github.io/cobblebase-web/generator/) — Create custom skill datapacks

## 🌾 Jobs

| Category | Jobs |
|----------|------|
| 🌾 Gathering | Harvester, Fishing, Mining, Producer |
| 🔍 Finding | Alchemist, Pharmacist, Architect, Excavator, Botanist, Collector, Scholar, Chef, Trainer, Armorer, Prospector, Smith |
| ⚔️ Combat | Guard |
| 💚 Support | Healer, Mentor |
| ✨ Buffs | Speed, Strength, Resistance, Night Vision, Water Breathing, Jump, Haste, Saturation, Aura Boost |
| 🌟 Legendary | Lucky Charm (shiny boost), Growth Aura, Extinguisher |
| 📦 Logistics | Item Gatherer |
| 🔭 Exploration | Scout |
| 🌿 Environmental | Irrigator, Lava/Water/Snow Fill, Furnace Fuel, Brew Fuel |
| 🤝 Recruiting | Friend Recruiter, Legendary Recruiter |

## 🛠️ In-Game Admin GUI

Open with `/cobblebase admin`. OP-only.

- **Species tab** — edit any species' skill set, search the full registry, sort by Pokedex number / A-Z / Z-A, filtered to mons your server actually has loaded
- **Jobs tab** — per-job cooldown, search radius, and enable/disable. Compact category sidebar with hover tooltips for every job
- **Loot tab** — edit any bundled loot table live. Per-job sidebar with rarity tabs (Common / Uncommon / Rare / Ultra Rare), item icon preview, autocomplete suggestions for item ids and item tags, bulk add via `#namespace:tag` (e.g. `#c:ores`, `#minecraft:logs`), on/off toggle for default items, save / reset
- **Wiki tab** — quick links to docs, the species database, the datapack generator, and the Discord

All edits persist in the world save and broadcast live to other players — no restart needed.

## 🎵 Immersion

- 🚶 Idle wandering (15 block radius)
- 🌙 Working mons sleep at night and wake up at sunrise to resume their job
- 🔇 Mute button + cry cooldown (60s per Pokemon)
- 🔄 Stuck detection + escape from solid blocks (clipping recovery)
- 🍃 Leaves escape — Pokemon drop to ground from tree canopies

## 🎨 Supported Fakemon Packs

Built-in skill assignments for **6 Fakemon packs** (371 species):

- 🐾 Lively Mons (59)
- 🔥 Alatias AFP (87)
- ⚡ Laser's Pack (53)
- 🎭 Wilbayan's Fakemons (37)
- 👶 Baby Legends (22) — baby forms of legendaries, inherit their evolution target's skills with prof -1
- 👽 Extra Paradox Mons (27) — paradox alternates with ultra-rare-tier curated skills

Not using one of these? Use `/cobblebase admin` to assign skills manually — or build a datapack with the [generator](https://notlown.github.io/cobblebase-web/generator/).

## 📥 Requirements

- Minecraft **1.21.1** · Cobblemon **1.7.0+** · Cloth Config (required)
- Fabric Language Kotlin (Fabric) or Kotlin for Forge 5.x (NeoForge)

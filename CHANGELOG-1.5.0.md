# Cobblebase v1.5.0

The biggest release since launch — a full Admin GUI overhaul, an in-game Loot Editor, two new fakemon packs, and a long batch of performance + stability fixes that have been brewing since 1.4.0.

---

## ⚡ Performance

A round of TPS-focused work that's been in progress for a while and ships in this release.

- **Particle stripping** — every job except Healer no longer emits its working particles. The visual noise was costing real TPS on busy bases. Healer keeps its particles because the heal effect is information the player needs.
- **Finder / Producer / Recruiter throttling** — long-cooldown jobs now skip the bulk of their tick logic 19 ticks out of 20 (their cooldowns are minutes, no need to evaluate them every tick).
- **Cached loot table keys** — Finder no longer allocates `Identifier` and `RegistryKey` objects every roll. The four rarity-tier keys are built once at construction.
- **Targeted leaves pass-through** — restricted to the pasture area instead of the entire world. Pokemon outside a base are unaffected.
- **Admin GUI lazy loading** — the species list now sends names + override flags up front and fetches per-species skill data on demand instead of front-loading everything.
- **Smooth-scroll fix** — touchpad / smooth-scroll mice now scroll the species list at a sane rate instead of jumping pages.

---

## 🛠️ Admin GUI Overhaul

The `/cobblebase admin` screen got four new tabs and a redesign of the existing ones.

### New: Wiki Tab
- Curated outbound links — Documentation, Species Database, Datapack Generator, Job Reference, Loot Tables, GitHub, Modrinth, Discord
- Each entry has an "Open" button that hands the URL to your OS browser
- Compact scrollable layout

### New: Loot Editor Tab
Edit bundled loot tables live in-game without writing a datapack.

- **Per-job sidebar** with pretty names matching the Skills tab (Mining, Collector, Alchemist, Scholar, Chef, Pharmacist, Armorer, Excavator, Botanist, Smith, Trainer, Prospector, Architect)
- **Rarity tabs per job** (Common / Uncommon / Rare / Ultra Rare)
- Edit item id, weight, min count, max count per entry
- **Live item icon preview** as you type the item id
- **Autocomplete suggestions** for item ids and item tags — start typing `diamond` and pick `minecraft:diamond` from a popup, or type `#` for tag matches; arrow keys navigate, Tab autocompletes, Enter commits
- **Bulk add by item tag** — type `#namespace:tag` (e.g. `#c:ores`, `#minecraft:logs`) and the row expands into one entry per item in that tag, inheriting the row's weight/min/max
- **On/Off toggle** for default items — disable a default drop without losing it (you can re-enable later)
- **Delete (X)** button for custom items you added
- **Tooltips** on every column header explain what each value does with concrete examples
- **Save** writes the override and broadcasts to all OPs viewing the GUI; **Reset** removes the override and restores the bundled defaults
- Overrides persist in `<world>/cobblebase_loot_overrides.json` and apply immediately to the next loot generation
- Two orphaned loot tables removed: `honey_collect` (now produced by the Producer job) and `dive_treasure` (replaced by Fishing)

### Jobs Tab Redesign
- **Compact sidebar layout** with category list (All Jobs / Gathering / Generation / Combat / Support / Utility / Legendary / Social), right pane with the actual job rows
- Custom-drawn rows at scale 0.7 — fits the whole job catalogue without scrolling for most categories
- **Job tooltips** on hover — show description, category, default cooldown, default radius
- **Per-job radius now actually applies in-game** — the previous version stored `radiusMin/radiusMax` which were dead fields. Now writes `searchRadius` directly, which the executors consume via `SkillRegistry.getEffective()`
- Override marker (orange dot) when a job's cooldown / radius / enabled differs from the default

### Species Tab Improvements
- **Sort toggle** next to the Species header — cycles between Pokedex#, A-Z, Z-A (default Pokedex#, with national pokedex numbers resolved from Cobblemon's runtime registry)
- **Species list filtered to installed mons** — the bundled `species_skills/*.json` files cover thousands of fakemon names from optional addons. The list now intersects with `PokemonSpecies.species` (Cobblemon's runtime registry) so you only see mons your server actually has loaded. Override entries are always kept regardless.
- **Species sprite** in the editor header (top-right, zoomed) so you always know which mon you're editing
- **Auto-refresh** after lazy-load completes (no more manual refresh)
- **Use SpeciesSkillRegistry keys** for species names instead of pulling them from Cobblemon — fixes naming-mismatch entries

---

## 🆕 New Fakemon Pack Support

### Baby Legends (22 species)
- New supported pack — baby forms of legendaries that evolve into their adult counterparts
- Each baby inherits its evolution target's skill set with every proficiency reduced by 1 (min 1)
- Example: BabyMewtwo gets Mewtwo's skills minus 1 prof tier across the board

### Extra Paradox Mons (27 species)
- New supported pack — paradox-themed legendary alternates
- All entries get an ultra-rare-tier curated skill set based on type
- Proficiencies capped at 4 (no prof 5 — that stays reserved for true legendaries / mythicals / starter finals)

### Gravelmon Removed
- Gravelmon support dropped — the pack is Cobblemon 1.6 only and incompatible with Cobblemon 1.7+ which Cobblebase requires
- 7,194 Gravelmon species removed from the bundled database
- **Total species: 8,426 → 1,367** (996 hand-crafted Cobblemon + 371 fakemon)

### Other Species Additions
- 39 missing species filled in earlier in the cycle: paradox pokemon, tapus, treasures of ruin, naming aliases
- Applied user submissions #JLZI (5 species) and #QPKX (2 species)

---

## 🐛 Stability & Bug Fixes

### Stuck Detection & Escape
- **Escape from solid blocks** — Pokemon clipped inside a wall (glass ceiling, oak plank, etc.) are now teleported out using a virtual bounding-box collision test against actual VoxelShapes
- **Improved clip detection** uses `Box.intersects()` against real VoxelShape bounding boxes instead of contracted boxes, so glass ceilings don't get missed due to floor() rounding
- **Stuck timer tolerance** — 1-block oscillation no longer resets the stuck timer (Pokemon bobbing between two Y values are still considered stuck)
- **Escalating unstick** for flying Pokemon now uses Y-axis variation, not just horizontal teleport
- **Velocity impulse** at unstick intensity 3+ instead of an immediate teleport (gentler recovery for normal cases)
- **Job-based stuck recovery** removed — was causing Pokemon to escape enclosures
- **Stuck detection only checks working mons** and uses velocity in addition to blockPos

### Gatherer
- **Item dupe glitch fixed** — Gatherer's visual item entity (the floating itemstack above its head) is no longer detected by the pickup search, so it can't pick itself up in a dupe loop. Pickup cooldown after a deposit timeout was added too.
- **Drop on timeout** instead of teleporting to the chest — keeps the gatherer at its location and lets another mon (or a player) pick the items up
- **Breadcrumb retrace** — Gatherers walking onto stairs and getting stuck on the way back can now retrace the path they took to get there
- **Y-distance filter removed** in favor of the breadcrumb system

### Mining
- **Items spawn at ground level** — Mining drops were being spawned 1 block too high (`pos.y + 1.0`); now spawned at `pos.y + 0.25` with zero velocity so they land in the right spot

### Sleep / Night Behavior
- **Working mons sleep at night** — they now treat the night phase as idle, keep their job assignment, and wake up at sunrise to resume working
- **forceSleep at night** instead of waiting for the random sleep chance — fixes mons that would never lie down
- **Periodic sleep animation packets** every 4s for working mons — fixes the missing sleep animation
- **Sleep animation chain fallback** — uses Cobblemon's full animation chain so the sleep pose plays even on species with limited animation sets
- **wake-up animation** sent before clearing rest state — no more mons frozen in the sleep pose
- **Velocity freeze only for sleeping mons**, not for sitting / socializing — those animations need free velocity to play correctly
- **Passive buffs continue while sleeping** via `tickPassiveBuffsWithoutEntity`

### Other Fixes
- **Hearty grain harvesting** — 2-block tall crops harvest both top and bottom blocks, only adjacent block harvested if it's also mature (no breaking seedlings)
- **Berry harvest** uses the Berry API for drops and resets to MATURE_AGE instead of 0
- **`_index.txt` `.json` extension restored** — the loader filters by `endsWith ".json"` so the bare names didn't match
- **Cobblemon dependency declaration** added to fabric.mod.json so users on Cobblemon 1.6 get a clean unmet-dependency error at startup instead of a `TICKER$lambda$0` mixin crash (NeoForge already declared this)
- **22 broken `cobblebase:cauldron_fill` references** in species_skills JSONs replaced with the actual `cobblebase:water_fill` id — those species had effectively no-op skills before this fix
- **Recruiter consolidation** into `friend_recruiter` + spawn buckets loaded from Cobblemon
- **Archeologist skill removed** (was unused, replaced by other Finder variants)

### Behavior
- New behavior variants: faint after chase, recoil in socialize, faint-to-sleep
- SITTING behavior state removed (no animation available for it)

---

## 🎨 GUI Polish

- **Bottom bar redesign** — Discord icon | Mute toggle | Admin button (OP only) | Done
- **Mute button updates instantly** on click without reopening the GUI
- **Admin button uses pending pattern** to wait for data before opening so the species list is populated when the screen appears
- **General admin tab is hidden** for this release (held back for a later release; the Discord button on the Skills panel always shows now for max reach)

---

**Total Species in Database**: 1,367 (996 Cobblemon hand-crafted + 371 fakemon across Lively Mons / Alatias / Laser / Wilbayan / Baby Legends / Extra Paradox Mons)

**Cobblemon required**: 1.7.0 or newer
**Minecraft**: 1.21.1
**Loaders**: Fabric + NeoForge

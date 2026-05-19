# Cobblebase v2.0

A full UI overhaul, the long-awaited per-pasture settings, and an end-to-end
NeoForge port. Everything since 1.5 has been consolidated into one release
to mark the shift from "growing helper mod" to "feature-complete platform."

---

## Per-Pasture Settings & Base Settings Modal

The biggest single addition. Each pasture owner now controls their own
working radius and downward-scan reach inside admin-defined caps, plus a
new access mode that gates who else can interact with their pasture's UI.

- **Base Settings button** — bottom-right of every Cobblebase screen. Opens
  a modal popup with two sliders (Pasture Range, Below-Pasture Reach) plus
  an access pill that cycles through 🔒 Private / 👁 View-Only / 🔓 Public.
- **Per-pasture state** persisted in `<world>/cobblebase_pasture_settings.json`.
  Re-clamped automatically when the admin tightens the caps.
- **Owner gate** — only the pasture's recorded Cobblemon `ownerId` (or an
  OP) can mutate settings. Server-side `BaseManager.canEditPasture` revalidates
  every C2S packet, so a tampered client can't write.
- **Admin caps split** — the single "Pasture Range" / "Below-Pasture Reach"
  sliders are now MIN + MAX pairs. Owners pick any value in `[min, max]`;
  legacy single-value behaviour preserved by defaulting overrides to MAX.
- Every active block-scanning job (Harvester, Gatherer, Builder, Supplier,
  Cauldron-Fill, Extinguisher, FurnaceFuel, Fishing, Irrigator, Healer,
  Guard, Mining, EggHatcher, GenericLoot, Craftsman) reads the per-pasture
  value, not just the admin max. Auras / passive buffs stay on their own
  per-skill JSON radius (balance-tuned).

## Show-Radius Wireframe — Now Server-Broadcast

`RadiusVisible` is the new server-side set of pastures whose wireframe is
currently shown. Toggling the Show-Radius button sends a C2S packet; the
server validates ownership and broadcasts the new set to every connected
player. Result: when a pasture owner enables their radius box, every
nearby player sees it simultaneously instead of just themselves. In-memory
only — cleared on server restart, since wireframes are diagnostic, not
world state.

## Harvester Downward Reach

- Previously the harvest scan only checked Y at the pasture level and
  above (`for (y in 0..radius)`), which hid apricorns and berries on
  cliff-edge or stair-garden trees that grow a few blocks below the
  pasture block.
- The Y loop is now asymmetric: full `radius` upward (tall berry bushes,
  hanging apricorns), but only `belowPastureReach` downward — admin- and
  owner-configurable, default 6. Enough for stairs and small cliffs;
  nowhere near deep enough to drag mons into caves under the base.
- Same buffer applies to every block-scanning executor's Y loop.

## Pasture Range = Real Cap For Every Active Job

The admin's Pasture Range slider is now the hard cap for everything an
active job does — scan range AND idle wander AND safety-teleport floor.
Auras / passive buffs are intentionally exempt so balance-tuned effects
keep their JSON-defined reach. The Show-Radius wireframe is now the
literal truth of where work happens, not an approximation.

## Pasture-Tab Redesign

The Pokemon → Pasture sub-tab was rebuilt around three goals: see more
rows without scrolling, kill the "ragged silhouette" from
variable-width chips, and bring the active-skill stand-out front and centre.

- **Hero active chip** — the assigned skill renders larger and tinted by
  its job category; available chips render muted so the eye knows where
  to look.
- **Uniform chip height** between active and available (no more
  bulging-row silhouette). Differentiation by colour, not size.
- **Row 18 px** so 10+ rows fit on screen without scrolling.
- **Sprites slightly bigger, names slightly smaller**; row layout brings
  every column on the same vertical centre line.
- **New Relax column** between Pokemon and Skills (icon-only zZz button).
- **Auto-Assign Best button** — Pasture sub-tab only, single click
  assigns the highest-prof skill per Pokemon.

## Skill Chips Polish

Every chip in the Skills view got rebuilt:

- **Alphabetical sort** (sortedBy name.lowercase) — the previous prof-DESC
  order was confusing for users with mixed teams.
- **Watermark-large job icon** at the chip's left edge, vertically centred.
- **2-second hover tooltip** with the skill's description.
- **Long-name auto-shrink** — chips with > 11-char names scale the text
  down so it never marquees.
- **Drop-shadow on prof stars** so they remain readable against any chip
  background colour.
- **Uniform width** (60 px) — no more dynamic 42–64 px range that made
  the right edge of the row look ragged.
- **Job icons revamped** for semantic match — Healer → Totem of Undying,
  Pharmacist → Potion, Friend Recruiter → Name Tag, Speed Boost → Feather,
  Haste Boost → Golden Pickaxe, Jump Boost → Slime Block, Aura Boost → Beacon.

## Bottom Bar + Close Button + Footer

- **Top-right X close button** on every Cobblebase + Admin screen.
- **Persistent bottom bar** (Discord, Mute, Radius, Admin, Base Settings)
  across every tab — no more re-clicking through tabs to reach the radius
  toggle.
- **`BOTTOM_BAR_H = 14`** (was 20). Sub-panels' 18-px dead zone above the
  bottom bar is gone.
- **Bottom bar uniform height** across all tabs.
- **Radius toggle no longer closes the GUI** on click — flip ON/OFF
  without the PC GUI snapping shut every time.

## NeoForge Parity Port

Cobblebase 2.0 ships in full feature parity on NeoForge. Every Fabric
panel, packet codec, mixin equivalent, and renderer has a NeoForge
counterpart wired through the same common code path. NeoForge users get
the same Pasture redesign, Base Settings modal, per-pasture overrides,
admin GUI, and Workshop / Hatchery panels.

## Admin GUI

- **Server Settings tab** — real slider with default-value tick + ↻ reset
  for every numeric setting. Min/max sliders for Pasture Range and
  Below-Pasture Reach. Working-cap slider (max simultaneously working
  Pokemon per pasture; 0 = unlimited).
- **Job-disabled hides its tab** — admin disables a job, the corresponding
  player tab disappears from the user GUI entirely.
- **Admin → Jobs → Craftsman → Recipes** tab — enable/disable individual
  vanilla + mod recipes per Craftsman.

## Hatchery + Workshop

- **HatcheryPanel** now uses UiTokens for every padding and font scale (3
  scales instead of 6).
- **Idle-state message** — when no eggs are in the box, the Active Hatchers
  row says "X is ready, drop egg" instead of the misleading
  "assign the egg hatcher" hint.
- **Egg-orphan periodic sweep** every cleanup interval — floating egg
  `ItemDisplay` entities surviving across restarts are now purged
  whenever the world re-loads, matching by display custom-name **or** by
  held ItemStack type. No more pile-up of un-pickupable eggs in the
  overworld.
- **Egg ItemDisplay hold position** — eggs render in front of the
  hatcher's body, not floating in its head.
- **WorkshopPanel** also tokenised; Workshop sub-tabs renamed and laid
  out to match the rest of the GUI.

## Logs Tab

- **Match by pastureId** instead of geometric-nearest pasture — fixes the
  cross-pasture log-leak where two pastures showed identical entries.
- **Per-job colour palette** (`JobColors` is the single source of truth).
  Each job's chip BG, Buffs name colour, and Logs row tint share the
  same hue.
- **Full-size Pokemon sprite** (`renderIconByName` 16 px) in 14-px rows —
  Pokemon are recognisable at a glance instead of a tiny 11-px badge.
- **Drop zebra striping** — uniform base + per-action tint stacked on
  top so two consecutive same-action rows render identical (no zebra
  + tint combinatorial confusion).

## Buffs Tab

- **Passive dedupe** — only the highest-prof mon per buff actually
  applies its effect (no stack-on-stack waste).
- **Per-buff name colour** matches the effect it applies — Speed reads
  as sky-blue, Strength as fire-red, Aura as gold.
- **Meaningful effect descriptions** for every job. Diving, Builder, and
  Craftsman previously fell through to "Working" — now show
  "Diving for underwater treasure", "Building structures from chest
  materials", "Crafting items from chest materials" etc.

## Pasture Mob Movement

- **Idle wander = admin Pasture Range** — mons no longer cluster near
  the centre when the pasture is large.
- **Safety-teleport** has an admin-radius floor so mons can be pulled
  back from anywhere the wander could reach them.
- **Tether resize REMOVED** — the previous attempt to shrink Cobblemon's
  64-block tether to the admin radius caused mons to despawn into the
  PC the moment they were outside the new (smaller) box. Cobblemon's
  default 64-block tether is honoured.

## UiTokens Design System

New `UiTokens.kt` centralises sizes that used to be scattered across
every panel. Three text scales replace the previous six; row heights,
button heights, cell paddings, and bottom-bar geometry all flow from
one file.

## Bug Fixes Worth Calling Out

- **Loot jobs broken since 1.5.5-beta.1** — 60s stale-sweep wiped active
  cooldowns of 12 executors because the sweep used the cooldown timer
  itself as the "last seen" heartbeat. Executors now use a dedicated
  `lastScanTime` heartbeat updated every tick.
- **Flying mons stuck in trees / fences** — `escapeLeaves` now clears
  brain memory every tick (not only on the 1s sweep), has a wider
  scan radius, and re-registers leaf blocks dynamically when modded
  packs add new ones.
- **Cross-pasture log mix-up** — Logs tab now keys by pasture ID, not
  by geometric distance.
- **Cobblebase main button collides with other mods' buttons** — fixed
  by making it position-configurable (four corners of the PC GUI) and
  by enforcing click priority via a new mixin.
- **Sleeping mons still moving** — added a state lock from
  `PokemonEntity.tick` head so we don't fight Cobblemon's sleep state.
- **Admin sliders snapped to wrong default on reset** — every slider's
  ↻ now snaps to the documented default value with a yellow tick on
  the track marking it.
- **Egg Hatcher effect description showed "Working"** — now displays the
  prof-keyed speed multiplier ("Incubating Cobbreeding eggs · 3× speed").

## Quieter Logs By Default

`Cobblebase.LOGGER.info` calls have been removed or downgraded to debug.
Server consoles at default log level no longer get the per-scan
Harvester radius readouts or the orphan-egg-purge tally. Errors and
warnings stay informational.

---

[1.5.5]: ./CHANGELOG-1.5.5.md

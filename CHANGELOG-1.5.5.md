# Cobblebase v1.5.5

UI/UX polish for the Pasture-PC integration: the Cobblebase main button is now position-configurable and reliably clickable even when other mods add overlapping widgets to the same screen.

---

## Configurable Cobblebase Button Position

The Cobblebase main button (the one that opens the Cobblebase screen from the PC GUI) can now be anchored to any of the **four corners of the Cobblemon PC window**.

### Why
The previous fixed position above the Pasture widget collided with other mods that add buttons in the same region — notably Cobbreeding's egg button. Letting users move the button avoids those collisions instead of fighting them.

### The four corners
- **Top Left** — above the PCGUI, left-aligned with its left edge
- **Top Right** — above the PCGUI, right-aligned with its right edge
- **Bottom Left** — below the PCGUI, left-aligned
- **Bottom Right** — below the PCGUI, right-aligned _(default)_

The button always sits **outside** the grey PCGUI window, flush against the chosen edge, so it never overlaps with internal widgets (Pokemon detail panel, Box storage, Pasture, Recall All, etc.).

### How to change it
Mod Menu → Cobblebase → **General → "Cobblebase Button Position"** → click through the four values.

The change applies instantly while the PC is open — no restart needed.

---

## Click Priority Over Overlapping Widgets

Even with the new anchor positions, other mods can register widgets that visually or interactively overlap the Cobblebase button. To guarantee the button stays usable in modpack contexts, click priority is now enforced at the screen level.

### What changed
A new mixin (`PCGUIClickPriorityMixin`) intercepts mouse clicks on the Cobblemon PC screen at the parent level — **before** Minecraft's normal widget-dispatch loop hands the click to other mods' widgets. If the click falls on the Cobblebase button, Cobblebase handles it and cancels further dispatch.

### Why this is safe
- Only fires while the Cobblebase button has rendered within the last 200 ms (freshness gate) — prevents stale references from intercepting clicks on screens where the button isn't visible.
- Only intercepts clicks **on the button's exact bounds**. All other clicks dispatch normally to other widgets.
- The render-side Z translation (+200) introduced earlier remains, so the button is also visually on top.

---

## Polish

- Config UI now shows readable names instead of raw translation keys: **"Cobblebase Button Position"** with values **"Top Left / Top Right / Bottom Left / Bottom Right"** (translation entries added to `en_us.json`).

---

## Bug Fixes

### Furnace Fuel: Pokemon distribute across furnaces instead of all rushing the same one

**Reported by a user:** *"Just curious if it's possible to somehow assign 'Furnace Fuel' pokemon to specific furnaces? Seems if I have more than one, they all rush to heat up the same furnace and then ignore all the others."*

**Cause:** Each Furnace-Fuel Pokemon independently scanned for "the closest furnace needing fuel" starting from the same pasture origin. With no coordination between Pokemon, they all picked the same nearest furnace, ignoring others that also needed fuel.

**Fix:** The executor now tracks per-Pokemon **claims** on furnace positions. When a Pokemon picks a target, that furnace is reserved for it. Other Pokemon scanning for work skip claimed furnaces and pick the next-closest unclaimed one.

**Edge cases handled:**
- Claims have a **30-second TTL** — if a Pokemon is recalled, despawns, or otherwise stops ticking, its claim is dropped automatically so the furnace becomes available to others.
- When a Pokemon completes fueling, its claim is released immediately so it can pick a new furnace on its next tick.
- If a Pokemon's claimed furnace stops needing fuel (e.g. ran out of smeltable input), the claim drops and the Pokemon searches for a new target.

This is a soft reservation, not an explicit per-pasture assignment — Pokemon still pick targets dynamically based on which furnace needs fuel and how far it is. They just don't pile up on the same one anymore.

---

## Server Admin: Toggle Aura/Buff Skills

Server admins can now individually enable or disable each Aura/Buff skill in the Cobblebase settings menu (**Mod Menu → Cobblebase → Buffs & Auras**). Disabled buffs short-circuit at the start of the executor's tick — no effect applied, no particles played.

### Why
Some server admins want to limit which player-affecting buffs Pokemon can grant — e.g. disabling Saturation Boost to keep hunger management meaningful, or disabling the Luck Aura on a survival server where loot rates matter.

### Available toggles
All nine buff skills get an individual on/off toggle:
- **Speed Boost** — Movement-speed status effect
- **Strength Boost** — Melee damage status effect
- **Resistance Boost** — Incoming-damage reduction status effect
- **Night Vision** — Vision in dark areas
- **Water Breathing** — Underwater breathing
- **Jump Boost** — Jump height
- **Haste Boost** — Mining speed
- **Saturation Boost** — Hunger refill
- **Aura (Luck)** — Loot quality buff with proficiency-scaling amplifier

### Notes
- All toggles default to **enabled** — existing pasture setups behave identically after the update.
- Changes apply on the next executor tick — no restart needed.
- The toggle is server-side: if Cobblebase is installed on both server and client, the server's config wins.

---

### Gatherer: items vanishing instead of reaching the chest

**Reported by a user (Discord):** *"my wooloo has this and is not putting items in chest where are they going and how do i fix this? — logs would say they picked it up but it would be nowhere to be found."*

**Root causes (multiple, all contributing):**

1. **Chest search radius was hardcoded to 10 blocks.** The user-configurable `jobSearchRadius` (5–20) was ignored on this path, so any chest sitting 11+ blocks from the pasture was invisible to the Gatherer. When `findBestContainer` returned `null`, items were then dropped on the ground **untagged** — meaning no other Gatherer could re-pickup them, and they despawned after 5 minutes if no player was nearby.
2. **Deposit timeout was a flat 10 seconds** regardless of distance to the chest or how fast the Pokemon walks. Slow species like **Wooloo** (low species `walkSpeed`) routinely hit the deadline before reaching a chest only 6–8 blocks away. The timeout branch then dropped the items at the Pokemon's wander position — scattered along the gathering trail instead of at the base.
3. **Held-items map wasn't cleaned up when a Pokemon was recalled** to its ball, chunk-unloaded, or its owner went offline. The state lived in memory only, so on server restart any in-flight items were silently lost. Wooloo + producer wool was particularly bad because the produce-pickup-deposit loop was constantly mid-flight.

**Fixes:**

- **`findBestContainer` now uses `CobblebaseConfig.jobSearchRadius`** for the chest lookup, so users with bigger bases can dial the search radius up.
- **"No chest found" now drops items at the pasture origin**, tagged with the pasture, instead of at the Pokemon's wander position untagged. A later Gatherer (or this one after cooldown) will re-pickup them once a chest exists.
- **Deposit timeout is now dynamic**: `10s base + 1s per block of pasture→chest distance, capped at 30s`. Slow Pokemon finally have enough time to actually reach the chest.
- **Timeout-drop also goes to the pasture origin** (not the wander position), so failed-deposit items end up next to the chest and the next attempt succeeds without scattering loot across the field.
- **3-fail recovery now inserts items directly into the chest** after the recovery-teleport (the Pokemon is now adjacent to the pasture, so insertion is safe), instead of dropping nearby. Overflow that doesn't fit drops at the pasture, tagged.
- **Orphan-sweep**: every 10 seconds, the executor checks for Pokemon whose tick has been silent for 30+ seconds while still holding items. Their held items are inserted into a nearby chest (preferred) or dropped tagged at the remembered pasture origin. Fixes the "items disappear on recall/restart" class of reports.

**Wooloo-specifically:** Wooloo's slow `walkSpeed` is what made this so visible — every other case (Pidgey, Eevee, etc.) usually reached the chest in 10s. The fix benefits all Pokemon, but Wooloo, Slowpoke, Munchlax and the other low-walkSpeed species are the ones whose drops should now consistently make it home.

---

### Gatherer: per-job search radius from Admin GUI now applies

Every other executor was reading `skill.searchRadius` (which already passes through `JobConfigOverrides` via `SkillRegistry.getEffective`, so admin overrides apply). The Gatherer was the lone exception — it ignored the per-job radius and hardcoded the global cloth-config `jobSearchRadius` for both the item scan and the chest search. Setting the Gatherer's radius in **Mod Menu → Admin → Jobs** therefore did nothing.

**Fix:** The Gatherer now respects `skill.searchRadius` for both the item scan and the chest lookup. Per-job admin overrides take effect immediately, and the JSON default (24) is used when no override is set. Item-scan range additionally scales with Proficiency (50% at Prof 1 → 100% at Prof 5) so newly trained Gatherers cover less ground.

---

### Performance: tick load slashed, memory leaks plugged

A profiling pass on a fully-loaded base revealed that the dominant tick cost was the buff/aura executor family — they were re-applying their status effects (and scanning all nearby players) on **every single server tick**, even though the effects they grant last 15–70 seconds. Several executors also leaked per-Pokemon state indefinitely on long-running servers, since there was no cleanup hook when a Pokemon was recalled to its ball or its chunk unloaded.

**Throttling fixes (no behavior change):**

| Executor | Before | After | Why it's safe |
|---|---|---|---|
| `BuffExecutor` (Speed/Strength/Resistance/Jump/Haste/Saturation/Night Vision/Water Breathing) | applied every tick (20/s) | applied every 20 ticks (1/s) | Effect duration is 15s+, so a 1s refresh interval leaves a 14s safety window before the effect would expire. Players walking into range wait ≤1 second to receive the buff (imperceptible). |
| `AuraBoostExecutor` | applied every tick | applied every 20 ticks | Same logic — Luck effect duration far exceeds the refresh interval. |
| `HealerExecutor` | when the heal cooldown was up, scanned all nearby players **every tick** until someone needed healing | scans at most once per second when idle | Heal latency increases from ≤50 ms to ≤1 s when a player suddenly takes damage — within the heal animation's own 3-pulse timing. |

On a base running all 8 buff Pokemon + Aura + Healer, this cuts player-AABB scans and status-effect-applications by ~20× per second.

**Memory-leak fixes:**

- **`MentorExecutor.cleanupStale`** existed but was never called. Pasture positions accumulated in `activeMentors` over time. Now invoked from a periodic sweep in `BaseManager`.
- **`BuffExecutor` and `AuraBoostExecutor`** maintained `lastBuffTime` and `activeBuffPlayers` keyed by Pokemon UUID with no cleanup path. Both now expose a `cleanupStale(now)` that drops entries untouched for 60s, invoked by the same periodic sweep.
- **`GathererExecutor` orphan-sweep was incomplete** — it cleaned `heldItems`, `heldOrigin`, `heldLastTick`, and `visualItems`, but left 10 other per-Pokemon maps (`originalHeldItem`, `pickupCooldown`, `depositFails`, `breadcrumbs`, `lastBreadcrumbTick`, `breadcrumbIndex`, `targetItem`, `targetSetTime`, `lastPickupTime`, `lastSearchTime`, `depositStartTime`) to leak indefinitely. The sweep now clears all of them for any Pokemon whose tick hasn't run within 30s.

**Cleanup cadence:** `BaseManager.tickPokemon` runs `MentorExecutor.cleanupStale`, `BuffExecutor.cleanupStale`, and `AuraBoostExecutor.cleanupStale` once per minute (1200-tick interval). Same call site for all of them, single dispatch path.

**Additional throttles (Phase 2):**

| Operation | Before | After |
|---|---|---|
| Water-Pokemon-on-land water-scan in `PokemonPastureBlockEntityMixin` | scanned ~3000 block positions every tick per land-bound water mon | `NavigationHelper.findNearbyWaterCached` memoizes the result per Pokemon for 2 seconds — first scan still runs, subsequent calls within 2s return the cached position |
| `GuardExecutor` wild-Pokemon AABB scan | scanned every tick once the guard cooldown had elapsed but no targets were nearby | scans at most once per second when idle (separate `lastScanTime`, independent of the per-action guard cooldown) |
| `BaseManager.tickPokemon` safety-teleport + drown checks | `sqrt` + distance compare + water-submerged check every tick per Pokemon | runs every 10 ticks (500 ms). Also switched the safety-teleport distance comparison to squared-distance space — the `sqrt` is gone entirely. The 30-block safety threshold can't be crossed in 500 ms, so the throttle is safe. |

The squared-distance change is a minor but real win: vanilla `Entity.squaredDistanceTo` is already optimized, but the previous code took the square root just to compare against `safetyTeleportDistance`. Comparing `distSq > maxDist * maxDist` instead skips the `sqrt` on every check.

**Phase 3 throttles + caches:**

| Operation | Before | After |
|---|---|---|
| `BaseManager.isBuffSkill` | did up to 2 list scans + a HashMap lookup on every call; called once per skill per Pokemon per tick inside the passive-buff loop | result memoized per skillId in `buffSkillCache` — first call costs the same as before, every subsequent call is a single HashMap lookup |
| `NavigationHelper.escapeLeaves` | 2-5 block-state reads every tick per Pokemon, even though "trapped in leaves" is a rare event | runs at most once per second per Pokemon (1s leaf-detection latency is imperceptible) |

**Phase 3 memory-leak cleanups** (added to the existing 60-second periodic sweep in `BaseManager`):

| Source | Maps cleaned |
|---|---|
| `HealerExecutor.cleanupStale` | `lastHealTime`, `lastScanTime`, `activeSessions`, `navStartTime` |
| `GuardExecutor.cleanupStale` | `lastGuardTime`, `lastScanTime` |
| `NavigationHelper.cleanupStale` | `lastEscapeLeavesTick`, `lastPathfindTick`, `waterCache` |
| `BaseManager` inline | `lastSafetyCheck` |

After Phase 3 there is no per-Pokemon state in the mod that grows without bound on a long-running server.

**Phase 4 — final fixes:**

- **Per-species buff-skill list cached.** The passive-buff loop in `BaseManager.tickPokemon` used to iterate every skill on every Pokemon every tick and HashMap-lookup each one to check "is this a buff?". Now `SpeciesSkillRegistry.getBuffSkills(species)` returns a pre-filtered list, computed once per species and invalidated on `register()`. For a species with 30 skills where 2 are buffs, we now iterate 2 entries per tick instead of 30.
- **`sqrt` removed from `ScoutExecutor`** safety-teleport check (same squared-distance trick used in `BaseManager` safety check; sqrt only computed for the debug-log message if a teleport actually fires).
- **`sqrt` removed from `NavigationHelper`** swim-back-to-pasture distance check (20-block threshold → 400-square comparison).
- **Scout teleport log** gated by `LOGGER.isDebugEnabled` — avoids the `sqrt` and string interpolation when debug logging is off (the default).

**Phase 5 — additional throttles + cached attribute lookups:**

- **Water-type lookup cached** in `NavigationHelper.isWaterType`. The pasture mixin and `BaseManager.tickPokemon` previously iterated `pokemon.getTypes()` and string-compared "water" on every tick for every pastured Pokemon. Now memoized per Pokemon UUID, invalidated by the periodic stale-sweep.
- **`FurnaceFuelExecutor`** got the same scan-throttle treatment as `HealerExecutor`/`GuardExecutor`: once the fuel-cooldown elapses but no furnace needs work, the radius³ block scan no longer runs every tick — throttled to once per second when no target is claimed.
- **`CauldronFillExecutor`** — same fix: when there's no claimed target and no empty cauldron in range, scan at most once per second.
- **Cleanup hooks for the new state**: `FurnaceFuelExecutor.cleanupStale` and `CauldronFillExecutor.cleanupStale` wired into the 60-second sweep, so the `lastFuelTime`/`lastFillTime`/`lastScanTime`/`*Target` maps don't grow unbounded.

**Phase 6 — every remaining per-Pokemon map now cleans up:**

The previous phases plugged the major memory leaks (Mentor/Buff/Aura/Healer/Guard/Gatherer/FurnaceFuel/CauldronFill). Phase 6 added `cleanupStale` to the **15 other executors** that still had UUID-keyed maps growing forever:

`BuilderExecutor`, `CraftsmanExecutor`, `ExtinguisherExecutor`, `FinderExecutor` (all 13 finder-type instances), `FishingExecutor`, `GenericLootExecutor`, `GrowthAuraExecutor`, `HarvesterExecutor`, `IrrigatorExecutor`, `LuckyCharmExecutor`, `MiningExecutor`, `ProducerExecutor`, `RecruiterExecutor`, `ScoutExecutor`, `SupplierExecutor`.

All hook into the same 60-second periodic sweep in `BaseManager`. Per-Pokemon state across the entire mod now has a 60-second TTL after a Pokemon stops ticking.

**Two pre-existing cleanup functions that were defined but never called:**

- **`LogManager.cleanup()`** — strips in-game log entries older than 24 real-world hours. Was previously only invoked by `LogManager.save()` (which itself is throttled). Now also runs in the periodic sweep, so memory stays bounded between saves.
- **`PastureLeavesTracker.cleanupOrphanedPastures(world)`** (new) — walks the tracked-pasture cache and removes entries whose pasture block has been broken. Without this, breaking a pasture block left its leaf-position cache in the global `pastureLeaves` set for the rest of the server's runtime, slowing every leaf-collision check via `LeavesBlockMixin`.

After Phase 6, every cleanup function the mod defines is reachable from the periodic sweep, and no per-entity / per-block / per-pasture state grows indefinitely.

---

### Admin GUI: tunable per-skill parameters for support jobs

**Reported by a user (Discord):** *"im jobs tab unter support sollte man einstellen können auch wie viel die bringen, nicht nur cooldown radius sondern zb bei mentor auch wv exp man kriegt pro prof oder so"*

Until now, the Admin → Jobs tab only exposed three values per job: cooldown, search radius, and enable. For support jobs (auras, buffs, mentor), the "amount" of each effect was hardcoded inside the executor — admins couldn't, for example, dial Mentor's XP rate up for a fast-progress server or down for a slow one.

**What changed:** The skill JSON now supports a `tuning` object that declares per-skill configurable parameters. Each parameter renders as its own sub-row in the Jobs tab beneath the parent job. The schema:

```json
"tuning": {
  "xpMultiplier": {
    "label": "XP Multiplier",
    "defaultValue": 1.0,
    "min": 0.0, "max": 5.0, "step": 0.1,
    "unit": "x",
    "tooltip": "Multiplier on top of the proficiency-scaled XP boost."
  }
}
```

The value flows: admin GUI input → `JobConfigOverrides.tuning` map → `SkillRegistry.getEffectiveTuning(skillId, key, fallback)` → executor consumes it. Overrides persist in `cobblebase_job_overrides.json` alongside the existing cooldown/radius/enabled fields and sync to clients via `AdminJobsSyncS2CPacket`.

**Support jobs that ship with tuning fields:**

| Job | Tuning key | What it does |
|---|---|---|
| Mentor | `xpMultiplier` | Scales the proficiency-derived XP boost (1.0 = vanilla, 2.0 = doubled) |
| Speed / Strength / Resistance / Jump / Haste / Saturation / Night Vision / Water Breathing | `effectLevel` | Sets the status effect level applied to players (Speed I, II, III, ...) |
| Aura Boost | `luckBonus` | Adds extra Luck levels on top of the proficiency-scaled amplifier |
| Lucky Charm | `shinyMultiplier` | Multiplier on top of the proficiency-scaled shiny rate |
| Growth Aura | `growthMultiplier` | Multiplier on crops ticked per pulse |

Each support executor falls back to its built-in default if the JSON omits the tuning declaration, so existing third-party skill JSONs keep working unchanged.

**GUI:** Jobs with tuning declarations now render an indented sub-row per parameter under the job. The label shows the field name and unit; the value is editable like cooldown/radius. Hovering the label surfaces the tooltip plus the parameter's default and min/max range.

---

### Admin GUI: disabling support/aura jobs now actually turns them off

**Reported by a user (Discord):** *"support jobs deaktivieren scheint nicht zu klappen im admin? vlt weil man diese jobs nicht manuell aktivieren deaktivieren kann sondern man die automatisch hat wenn ein pokemon das besitzt sobald es in pasturebox is"*

**Cause:** Support-style skills (Speed Boost, Strength Boost, Aura, Night Vision, etc.) are passive — they activate the moment the Pokemon enters a pasture, without the player having to assign them like a normal job. `BaseManager.tickPokemon` ran the passive-buff loop unconditionally, so the **Enabled** toggle in Mod Menu → Admin → Jobs was only honored for actively-assigned jobs and silently ignored for everything in the `BUFF_EXECUTORS` set.

**Fix:** Both passive-buff dispatch loops (`tickPokemon` and `tickPassiveBuffsWithoutEntity`) now consult `JobConfigOverrides.isEnabled(skillDef.id)` before ticking the executor. Disabling a support job in the Admin GUI now actually stops it, even though it was never "assigned" in the normal sense. This is the per-job admin counterpart to the global cloth-config Buffs toggles introduced earlier in 1.5.5 — the cloth-config blocks all instances of one buff type server-wide, while the Admin GUI per-job switch blocks a specific cobblebase skill from being ticked.

---

### Furnace Fuel: modded furnace blocks via datapack tag

**Requested by a user (Discord):** *"was wondering if there was a way to maybe add to the configs some ability to add other 'furnace' objects to what Cobblebase allows pokemon to target? Like upgraded ones from Iron Furnaces mod and such."*

**What changed:**
- New datapack tag `#cobblebase:furnace_compatible` (block tag, defaults to vanilla furnace/blast_furnace/smoker). Modpack authors or end users add their modded furnace blocks via vanilla datapack mechanics — no code change required per mod.
- `FurnaceFuelExecutor.furnaceNeedsFuel` and `addFuel` accept any block in this tag, not just `AbstractFurnaceBlock` subclasses.
- **Two fueling paths:**
  - Furnaces extending `AbstractFurnaceBlockEntity` (e.g. Iron Furnaces, most "upgrade" mods) → existing fast NBT BurnTime path, no behavior change.
  - Furnaces that don't extend the vanilla class but implement `Inventory` → fallback path drops a piece of coal into slot 1 (vanilla fuel-slot convention). The mod's own ticking logic burns it down.
- Block tag is honored by both `furnaceNeedsFuel` (detection) and `findFurnaceNeedingFuel` (radius scan).

**How users extend it:**

Create `data/yourpack/tags/block/cobblebase/furnace_compatible.json`:
```json
{
  "replace": false,
  "values": [
    "ironfurnaces:iron_furnace",
    "ironfurnaces:gold_furnace",
    "ironfurnaces:diamond_furnace"
  ]
}
```

Or via global tag override at `data/cobblebase/tags/block/furnace_compatible.json` with `replace: false`.

**What's not yet supported:** Genuinely different heating systems like Create's Blaze Burner (kinetic + heat network, no inventory fuel slot). That would need a separate skill executor with Create's API — tracked for a future release.

---

### Builder: auto-target Helper Pokemon (Phase 2)

**Reported by a user (Discord):** *"können wir machen wie bei workshop das man bestimmte mons als helfer zuweisen kann so das mehrere mons zusammen daran arbeiten ein gebäude zu bauen? zb einige suchen die materialien bis fertig und dann baut der builder am ende oder so."*

A coordinated Builder-Helper system layered on top of the existing per-Pokemon assignment model. Multiple helpers can work the same pasture simultaneously, picking different needed blocks via a shared claim ledger.

**How it works:**

1. Player opens the Builder tab while a build job is active and sees a **Helpers row** under the active-job panel — one button per tethered Pokemon. Click toggles `builder_helper` assignment.
2. Each helper's tick consults `BuilderHelperCoordinator.getOrAssignClaim(...)` to pick a needed block. The coordinator:
   - Looks at `BuilderExecutor.getNeededBlocks(world, pasture)` (the list of distinct block IDs still missing from the build plan).
   - Filters to blocks the helper's **species can supply** (`BlockSupplyMap.rolesFor(blockId)` cross-referenced with the helper's species skills).
   - Skips blocks already claimed by other helpers (claim TTL = 30 seconds).
   - Returns the first match.
3. `BuilderHelperExecutor` dispatches to the right sub-executor based on the claim's role:
   - **PRODUCER(species)** → `ProducerExecutor.tick(...)` (uses the species' produce map — Wooloo makes wool, Miltank milk, Combee honeycomb, etc.).
   - **HARVESTER** → `HarvesterExecutor.tick(...)` (gathers crops/berries within radius).
   - **MINING** → `MiningExecutor.tick(...)` (mines random nearby blocks, looses raw materials).
4. When the build completes or the needed-block disappears from the plan, the helper short-circuits — no claim, no ticks, no leaked items.

**`BlockSupplyMap` content:**
- MINING blocks: vanilla stone family (cobblestone, deepslate, andesite, etc.), all ores' raw drops, sand/gravel/dirt family.
- HARVESTER blocks: wheat, carrots, potatoes, beetroot, melon, pumpkin, sugar cane, kelp, bamboo, sweet/glow berries, cocoa beans, nether wart, cactus, mushrooms.
- PRODUCER blocks: derived from `ProducerExecutor`'s produce-map (wool, milk, egg, honey, glowstone dust, slime balls, ink sacs, leather, raw iron/copper/gold, gunpowder, etc., per species).

**What helpers don't supply (yet):**
- Crafted blocks (planks, doors, glass, smooth_stone, slabs, stairs, etc.). The user has to provide them manually, or chain a Workshop-Craftsman to craft them from raw materials a helper gathers. Phase 3 may add inline crafting via Workshop.
- Block-specific target filtering in HARVESTER/MINING — helpers run their normal skill and rely on Gatherer to deposit drops, so they're "skill-targeted" but not "block-targeted" on those two roles. Producer-role helpers are inherently target-correct since their output is fixed by species.

**Implementation files:**
- `BlockSupplyMap.kt` — block → role lookup with lazy producer-map ingest via reflection on `ProducerExecutor.produceMap`.
- `BuilderHelperCoordinator.kt` — per-Pokemon-UUID claim ledger with 30s TTL + per-block dedup across helpers.
- `BuilderHelperExecutor.kt` — claim → sub-executor dispatch.
- `BaseManager.tickPokemon` — recognises the `builder_helper` assignment prefix and routes to the helper executor.
- `BuilderPanel.kt` — Helpers row above the template list with one toggle button per tethered Pokemon.
- `BuilderExecutor.getNeededBlocks(world, pasture)` — returns deduplicated list of still-needed block IDs from the build plan.

**Cleanup:** `BuilderHelperCoordinator.cleanupStale` is wired into the 60-second periodic sweep alongside the other executor cleanups, so claims don't leak.

---

### Builder: ghost-block preview + active-job status panel

**Reported by a user (Discord):** *"ich wollte wirklich eine vorschau wie das gebäude aussehen würde. und wenn ich bei builder ein ding starte, was passiert dann? ich seh nirgends eine jobs queue oder so, oder ob die jz iwas dfür tun, ob irgendwer jz anfängt die blöcke zu sammeln."*

**Ghost-block preview:** When the player hits "Place Preview", the wireframe outline is now accompanied by a full **block-by-block ghost render** of the structure. The client requests the template's block list from the server once on preview start, applies the current rotation/mirror, and draws each block at its world position via `BlockRenderManager.renderBlockAsEntity` on the cutout layer. Rotation/mirror updates re-use the cached block list — no extra server round-trip.

**Implementation:**
- New `BuildPreviewBlocksRequestC2SPacket` / `BuildPreviewBlocksSyncS2CPacket` pair carries the (local-x, local-y, local-z, block-state-id) tuples. Block-state IDs are the raw `Block.STATE_IDS.getRawId` values; the client resolves them via the same registry.
- 20 000-block render cap so monstrously large templates don't tank framerate.
- Air variants and structure markers are filtered server-side to keep payload small.

**Active-job status panel:** A strip at the top of the Builder tab now shows what's happening when a build is in progress:

- Template name + completion state (orange while building, green when done).
- Progress text + bar: `47 / 120 blocks (39%)`.
- The next block the Builder Pokemon is waiting for, rendered as an item icon; hover the icon to see the full block ID.

Powered by:
- `BuilderExecutor.getJobStatus(world, pasture)` walks the build plan and counts matching world blocks. Cost is O(plan size) but only called from the GUI poll path (no per-tick overhead).
- `BuildJobStatusRequestC2SPacket` / `BuildJobStatusSyncS2CPacket` carry the snapshot. The Builder tab auto-polls every 2 seconds while open; the rest of the time the server isn't touched.
- `BuildJobStatusCache` on the client stores the latest reply and the renderer reads from it.

---

### Builder: visual material preview in the tab

**Reported by a user (Discord):** *"als user will ich auch previews von den sachen sehen die mir zum bauen angeboten werden, wenn ich auf place preview klicke wird mir nur angezeigt wie groß das sein wird nicht was für blöcke etc, ich will ein visuelle preview auch im tab am besten direkt schon."*

Previously the Builder tab only showed each template's name and dimensions — to find out what blocks the structure contained, you had to "Place Preview" in the world and look at it manually.

**What changed:**

- **Server scans the block content of every template** at startup (and on `/reload`) and computes a top-8 block-type histogram. Air variants are excluded. The histogram + total block count are stored in `StructureTemplateRef`.
- **Sync packet carries the histogram to clients** alongside the existing name/size fields. Roughly 8 extra strings + an int per template, sent once per Builder-tab open (cached client-side).
- **Builder tab renders a material-preview strip** above the action buttons whenever a template is selected: the template name, size, total non-air block count, and an icon row of the top 8 block types with their counts. Hovering an icon shows the full block ID.

Templates loaded from older builds without histogram data fall back to a "no block preview available" line so the panel stays usable.

---

### Gatherer: smart-chest sorter no longer drops items when the matching chest is almost full

**Reported by a user (Discord):** *"ich hab hier 3 chests, in einem davon is moomoo milk, aber die chest is schon voll. daneben sind 2 leere chests. wooloo versuchts wegen dem smart chest system in die volle zu tun weil da bereits moomoo milk is in den anderen nicht, aber droppt die wieder weil voll."*

**Cause:** `InventoryHelper.findBestContainer` Phase 1 picked the closest chest that *contained* the matching item with at least one partially-filled slot. It didn't verify the chest had enough room for the **whole** incoming stack — so if the matching chest had a single matching slot with 1 free slot left, the Gatherer would head there, deposit 1 item, and either retry or drop the rest. When the matching chest was genuinely full of the item at max stack size, the Gatherer wouldn't pick it (good) — but a chest with mixed partial stacks of the matching item was still preferred over a nearby empty chest that could fit everything.

The retry path in `GathererExecutor.depositItems` *did* exist to find an alternate chest with the leftovers, but it used a hardcoded `radius = 10` instead of the Gatherer's job radius (24 by default), so chests 11-24 blocks from the pasture weren't seen at all on the retry pass.

**Fix:**

1. **`findBestContainer` now computes the actual capacity** of each matching chest for the incoming items — sums free room in matching slots plus empty slots × maxStackSize — and only picks a matching chest if it has room for **the entire incoming stack**. If no matching chest can fit everything, falls back to the closest chest with any free space.
2. **Gatherer retry radius** uses `jobRadius` (the same value the first pass uses) so the leftover-deposit pass sees the same set of containers.
3. **No-alt-chest fallback drops items at the pasture origin** (tagged) instead of at the Pokemon's wander position, so they can be re-picked up by a future Gatherer run instead of scattering.

In the reporter's setup: Wooloo now sees that the matching milk chest is full (capacity = 0 for new moomoo milk), checks the next-nearest chests, finds an empty one with capacity = 27 × maxStack, and deposits there.

---

### Loot tables: corrected Cobblemon item IDs

**Reported by a user:** *"Some items in the Cobblebase loot tables use the wrong Cobblemon item IDs — they don't exist, so those entries silently produce nothing when rolled."*

**Cause:** A handful of loot-table entries used display-name guesses (`old_amber`, `linking_cord`, `x_defense`) or items that don't actually exist in Cobblemon (`sacred_ash`, `cobblemon:nugget`, `hyper_training_candy`), so when those rolls hit, the slot dropped nothing.

**Fix:** Verified every ID against `Cobblemon-fabric-1.7.3+1.21.1.jar`'s `en_us.json` and corrected:

| Table | Before | After |
|---|---|---|
| `mining_rare` | `cobblemon:old_amber` | `cobblemon:old_amber_fossil` |
| `finder_evo_uncommon` | `cobblemon:linking_cord` | `cobblemon:link_cable` |
| `finder_food_ultra_rare` | `cobblemon:candied_berries` | `cobblemon:candied_berry` |
| `finder_stat_uncommon` | `cobblemon:x_defense` | `cobblemon:x_defence` (British spelling, matches Cobblemon's ID) |
| `finder_hea_ultra_rare` | `cobblemon:sacred_ash` | entry removed (item does not exist in Cobblemon yet) |
| `finder_stat_ultra_rare` | `cobblemon:hyper_training_candy` (1 entry) | replaced with the 12 real Cobblemon candies: `health_candy`, `mighty_candy`, `tough_candy`, `smart_candy`, `courage_candy`, `quick_candy`, `sickly_candy`, `weak_candy`, `brittle_candy`, `numb_candy`, `coward_candy`, `slow_candy` |
| `finder_treasure_uncommon` | `cobblemon:nugget` | tag entry `c:nuggets` (uniform pick across all nugget items in the common tag) |

**Why the candy split:** `hyper_training_candy` was a single placeholder; Cobblemon actually has 12 separate nature-stat candies. The new entries are weighted **2** for the 6 stat-boosting candies (Health/Mighty/Tough/Smart/Courage/Quick) and **1** for the 6 stat-lowering candies (Sickly/Weak/Brittle/Numb/Coward/Slow), keeping the beneficial outcomes more likely on an ultra-rare drop while still letting the negative candies roll for trade value or hindering nature swaps.

---

### Hatchery: dedicated tab with progress bar, hatch log, and item-display eggs

A complete egg-hatching feature, surfacing what was previously a hidden Cobbreeding side-effect into its own first-class panel.

**What's new:**
- **New "Hatchery" top-level tab** in the Cobblebase pasture screen with sub-tabs Home / Logs.
- **EggHatcherExecutor** — a Pokemon assigned this job ticks down each held egg's internal `TIMER` data component (read via reflection so we stay agnostic to Cobbreeding internal version). Multiple hatchers can work in parallel on different eggs.
- **Egg-progress bar** in the Hatchery tab shows time-to-hatch for every egg currently in inventory, computed from the Cobbreeding TIMER component (DataComponentType lookup uses cobbreeding:pokemon_egg's component schema).
- **Hidden species** — the panel deliberately doesn't reveal what Pokemon will hatch (kept as a surprise) but does show the player's progress.
- **Visual egg-holding entity** — when a hatcher is actively progressing an egg, an `ItemDisplay` entity is spawned at the Pokemon's head height (rotated, scaled) so others can see at a glance which mon is incubating.
- **Hatch log** stored server-side in `HatchLogManager`, persisted to disk per world, capped at 100 entries with TTL cleanup. Each entry: timestamp, species, parent species, hatcher Pokemon, world time.
- **HatchLogSyncS2CPacket / HatchLogRequestC2SPacket** keep the client view in sync; the Logs sub-tab polls every 2s when open.
- **Compact log rows** match the LogsPanel styling — sprite + name + time-ago + rarity dot.

**Why it matters:** Cobbreeding ships with a working egg system but doesn't surface progress or history. Hatchery turns that into a feedback-rich activity players can plan around.

---

### Pasture screen: My Pokemon and WorkerWiki sub-tabs

The Skills (now **Pokemon**) tab is split into three sub-tabs so it's easier to browse what you have and what each species can do:

- **Pasture** — the existing active-worker assignment view (previously "Active Workers").
- **My Pokemon** — every Pokemon in your Party + every PC box, rendered in a Cobblemon-PC-style grid that mirrors the actual storage 1:1 (box names preserved, slot positions preserved, empty slots shown as faded frames).
- **WorkerWiki** — full species reference (previously "PokeWiki") with sortable grid of every Pokemon that has at least one Cobblebase skill assignment.

**My Pokemon highlights:**
- Server-side `MyPokemonRequestC2SPacket` + `MyPokemonSyncS2CPacket` enumerate `Cobblemon.storage.getParty(player)` and `getPC(player)` slot-by-slot. Original box names + indices preserved.
- **Greedy masonry layout** — boxes flow 2 per row (or more on wider panels). A short box (e.g. a half-filled Party) doesn't reserve full row height — the next box drops into the shorter column. No more wasted vertical space.
- **Empty boxes hidden** — only boxes containing at least one matching mon are rendered.
- **Per-Pokemon aspects + species identifier** are sent in the packet so the client can render the correct sprite for every entry. Fixes fakemons like RLX/IronVirus/IronWarp/LunarVeil whose display name didn't strip cleanly into a Cobblemon ID, and fixes Pyroar (female-only base sprite) which needed the gender aspect to resolve.

**WorkerWiki highlights:**
- Full right-pane grid with dynamic column count based on panel width.
- **Two sort controls** in the top-right corner:
  - **Field cycler**: Dex# → Name → Prof → Skills → Rarity → Dex# …
  - **Direction toggle**: ↑ (ascending) / ↓ (descending), independent of the field choice.
- "Prof" mode sums each mon's proficiency across the selected skills (or falls back to the mon's overall best proficiency when no skill is filtered).
- "Skills" mode sorts by job count — most versatile workers float to the top.
- "Rarity" mode sorts by Cobblemon spawn-bucket: Common → Uncommon → Rare → Ultra Rare.

**Both sub-tabs share:**
- An **expandable filter sidebar** along the left edge: All Pokemon (top), then each skill-category row with a chevron. Click a category to expand and reveal the individual skills inside it (matches the Admin → Jobs sidebar pattern). Each row carries a `(count)` showing how many mons match.
- **Multi-select AND-filter** — click checkboxes on multiple skills to find mons that have ALL selected skills. The selected skills bubble to the top of the cell's tooltip in yellow + bold (▶ marker) so it's obvious which jobs matched.
- **Independent sidebar scroll** — when the mouse is over the sidebar the wheel scrolls the category/skill list; when it's over the content area it scrolls the boxes/grid.
- **Rarity-tinted cell backgrounds** — Common stays neutral, Uncommon/Rare/Ultra Rare get a subtle hue-shifted dark-color background so the rare workers visually pop without flooding the panel.
- **Rarity prefix in tooltips** — `(C)/(UC)/(R)/(UR)` in matching color is prepended to the Pokemon name so players learn the rarity palette without a legend.

---

### Skills overhaul: tab/sub-tab rename + per-skill icons

- Top-level tab **Skills** → **Pokemon**.
- Sub-tab **Active Workers** → **Pasture**.
- Sub-tab **PokeWiki** → **WorkerWiki**.

The renames clarify the mental model: "Pokemon" is the family, "Pasture" is the active workforce, "My Pokemon" is your storage, "WorkerWiki" is the reference manual.

**New `JobIcons` central mapping** (`fabric/.../JobIcons.kt`): every one of the ~44 Cobblebase skills now has a single vanilla `ItemStack` icon — used everywhere skills are displayed (Admin job grid, skill-assignment buttons, Buffs tab, Pasture sub-tab tab bar, Admin sub-tab bar). Lazy reflective lookups for non-vanilla icons (Cobbreeding's `pokemon_egg`, Cobblemon's `poke_ball`) fall back to a vanilla equivalent when the optional mod isn't installed.

---

### Admin GUI: Server Settings tab + WorkerWiki visibility toggle

A new dedicated **Server** tab in the Admin screen separates server-wide feature flags from the curated outbound-link Wiki tab.

- **`AdminServerSettingsPanel`** with one toggle for now (WorkerWiki visibility) and room to grow as more server flags get added.
- `GeneralSettings.pokeWikiEnabled` (default `true`) — when off, the WorkerWiki sub-tab renders a "WorkerWiki disabled" message instead of the species grid. Admins can disable it to make players discover skills by experimenting instead of consulting the in-game reference.
- Setting persists in `cobblebase_general.json` and broadcasts via `GeneralSettingsSyncS2CPacket` to all clients on change.

Previously this toggle lived inside the Wiki tab's footer alongside the Ko-fi support box — it didn't belong there. Wiki is now purely outbound links + support box; server-config flags live in their own tab.

---

### Admin GUI: Jobs tab redesign with expandable sidebar + per-job detail view

- **Expandable category sidebar** at the left (mirrors the new Pasture-screen sidebar). Each category expands to reveal its individual jobs. Click a job to enter the detail view.
- **Grid landing page** when "All Jobs" or a single category is selected — 4-column tile grid with thematic vanilla item icons (from `JobIcons.kt`), name, category accent, enable badge.
- **Per-job detail view** with three sub-tabs:
  - **Settings** — cooldown, search radius, enable, declared tuning fields (xpMultiplier, effectLevel, etc.), AND **five synthetic per-proficiency cooldown rows** (`_prof1Cd` … `_prof5Cd`) auto-injected for every job. Defaults follow the existing prof-cooldown formula, so admins only need to edit the ones they want to override. Stored in the same `JobConfigOverrides.tuning` map and consumed via `CobblebaseConfig.getEffectiveCooldownTicks(base, prof, skillId)`.
  - **Loot** — embedded `AdminLootPanel` in `lockedToBaseName` mode so admins edit a job's loot table without leaving the Jobs tab. The global "Loot" top-level tab is gone — loot lives per-job now.
  - **Stats** — placeholder for future analytics (drops/hour, blocks broken, etc).
- **Sort-toggle compact layout**, hover tooltips with full job description, "*unsaved" indicator next to the Save button when edits are pending.

---

### Admin GUI: Species DB smooth scrolling

**Reported by a user (Discord):** *"das scrollen in der speciesdb in cobblebase admin echt kacke, das skippt da ganze mons is kein smooth scrolling wie wirs an andren stellen haben."*

**Cause:** `AdminSpeciesListPanel.scrollOffset` was a row index. One mouse-wheel notch = ±1 row = ±18 pixels of jump. With the rest of the mod (Workshop, Pasture grid, Hatchery) on per-pixel scrolling, the species DB felt out of place.

**Fix:** Converted `scrollOffset` to pixel units (still bounded by total content height). Each wheel notch now moves 9 pixels (half a row), and the render path renders the first partially-visible row at a fractional offset via scissor clipping. Mouse-down on a row uses pixel-aware math: `idx = ((mouseY - rowsAreaTop) + scrollOffset) / ROW_HEIGHT`. The scrollbar position is also computed from the pixel offset, so dragging it feels continuous instead of snapping to rows.

---

### Cooking & crafting: CobbleCuisine + Cook specialization

- New **Cook** Craftsman specialization with its own Workshop sub-tab.
- **CobbleCuisine recipes** are loaded from the mod's custom recipe type (the recipes were previously hidden from the vanilla recipe book and unreachable through Workshop). 45 species mapped to the Cook role.
- Recipe browser gains **sub-category tabs** (Furniture / Tools / Weapons / Armor / Cobblefurnies / CobbleCuisine etc.) so the previously flat ~1000-recipe list is navigable.

---

### Workshop & Craftsman: 6 new specializations + sidebar

- **6 new Craftsman specializations** beyond the original generic Craftsman: Architect, Cook (above), Weaponsmith, Armorer, Toolsmith, plus the Furniture / Cobblefurnies tab consolidation.
- **45 species assignments** updated to map to the new specializations (e.g. Conkeldurr → Toolsmith, Lucario → Weaponsmith, Audino → Cook).
- **Workshop sidebar** for switching between supplier / craftsman / project views without leaving the panel.
- **Supplier executor** (separate from Gatherer): real plant harvesting (apricorn-color check, BerryBlock `AGE < FRUIT_AGE`, `CropBlock.isMature`, sweet-berry stage, nether-wart age). Visual gold-particle indicators on harvestable target blocks so players can see what the supplier will pick next.

---

### Job rename: Chef → Forager

The "Chef" job was confusing — it didn't cook, it gathered raw ingredients. Renamed to **Forager** across `species_skills/*.json`, `skills/_index.txt`, and every GUI reference. The new "Cook" Craftsman specialization is what actually crafts food.

---

### Pokemon sprites missing for hyphenated/punctuated species

**Reported by a user:** *"Chi-yu, Chien-pao, Ting-lu, and Wo-chien appear without icons, and Ho-Oh and Type:Null don't appear at all. Their Cobblemon IDs don't use the special characters."*

**Cause:** The `resolveSpeciesFromName` helper normalized hyphens and spaces by mapping them to underscores ("Wo-Chien" → "wo_chien"). Cobblemon, however, strips punctuation entirely in its species IDs ("Wo-Chien" → "wochien"). The underscore form never matched any registered species, so the helper fell through to the colored letter-badge fallback.

**Fix:** The name resolver now tries the **strict alphanumeric form first** (no underscores, no hyphens), which matches Cobblemon's actual ID convention. The underscore form is kept as a secondary fallback for species that legitimately use snake_case in their ID, and the registry-walk fallback now also matches strict-stripped variants of registered species names.

**Coverage:** Fixes Chi-Yu, Chien-Pao, Ting-Lu, Wo-Chien, Ho-Oh, Type: Null, Mr. Mime, Mime Jr., Mr. Rime, Porygon-Z, Porygon2, Nidoran-♀/♂, and any other species whose display name contains hyphens, periods, colons, or other non-alphanumeric characters.

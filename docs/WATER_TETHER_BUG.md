# Water Pokemon Tether Bug — Investigation Log

## Problem
Water-type Pokemon (specifically Basculegion) get pulled out of water and tethered back to the pasture block on land. This ONLY happens with Cobblebase installed — not with Cobbleworkers or vanilla Cobblemon.

## Goal
Water-type Pokemon should stay in water, swim freely, perform water jobs (fishing, diving), and only return to land to deposit items in chests.

## Observed Behavior

### With Cobblebase (BROKEN):
- **Basculegion**: Gets constantly pulled out of water back to pasture block
- **Lapras**: Does NOT get pulled out — stays in water and swims fine
- The difference between Lapras and Basculegion needs investigation

### With Cobbleworkers (WORKS CORRECTLY):
- Basculegion swims in water freely
- Dives underwater, collects items
- Returns to land voluntarily to deposit items in chest
- Goes back to water after depositing
- No forced tethering

### Without any job mod (vanilla Cobblemon):
- No tethering issues reported

## Root Cause Analysis

### Confirmed: NOT Cobblemon's fault
- Cobblemon's `checkPokemon()` only validates tethering, does NOT teleport
- Cobblemon's `TICKER$lambda$0` does NOT contain teleport code
- The tethering only occurs with Cobblebase installed

### Confirmed: It's OUR code
THREE systems in Cobblebase cause the problem:

#### 1. `NavigationHelper.checkAndUnstick()` (NavigationHelper.kt:165-241)
- Tracks Pokemon position every tick
- If a Pokemon stays in the same BlockPos for >7 seconds, considers it "stuck"
- Water Pokemon swimming in place ARE in the same BlockPos (water has no collision)
- After 7s: navigates the Mon toward random land positions
- After 21s+: applies velocity impulse to physically push it out
- **This is the PRIMARY cause** — treats swimming as being stuck

#### 2. `BaseManager.tickPokemon()` DROWNING PREVENTION (BaseManager.kt:88-94)
- **THE PRIMARY CAUSE** found on 2026-04-14
- Code: `if (pokemonEntity.isSubmergedInWater || pokemonEntity.air < 100)` → teleport to pasture
- NO type check — teleports ALL Pokemon including Water-type
- This runs EVERY TICK for EVERY tethered Pokemon
- Immediately teleports any submerged Pokemon to pasture block origin
- Also resets air and clears navigation targets
- This is why Basculegion gets teleported ON TOP of the pasture box

#### 3. `BaseManager.tickPokemon()` SAFETY TELEPORT (BaseManager.kt:75-86)
- Teleports Pokemon back to pasture if they wander beyond safetyTeleportDistance (30 blocks)
- This is why Lapras gets teleported back when it swims too far
- Should exempt water-type Pokemon or use a larger distance for them

#### 4. `NavigationHelper.wanderNearOrigin()` (NavigationHelper.kt:368-391)
- Called for idle working mons when navigation is idle
- Picks random positions within radius of pasture block
- Pasture block is on LAND → all wander targets are on land
- Water mons get navigated away from water toward the pasture

### Why Lapras works but Basculegion doesn't
**NEEDS INVESTIGATION** — possible reasons:
- Lapras is larger and may move between BlockPos more while swimming (not triggering stuck detection)
- Lapras may have different Cobblemon swimming behavior (moves more actively)
- Basculegion may be smaller and hover in place more precisely
- Different entity sizes could affect BlockPos tracking

## Failed Fix Attempts

### 1. Mixin on `checkPokemon()` to save/restore positions
- **What**: beforeCheckPokemon saves water-mon positions, afterCheckPokemon restores them
- **Why it failed**: checkPokemon doesn't do the teleport — wrong target
- **Files**: PokemonPastureBlockEntityMixin.java beforeCheckPokemon/afterCheckPokemon

### 2. `setTicksUntilCheck(0)` cap to 100
- **What**: Changed keepEntitiesAlive from resetting every tick to every 5 seconds
- **Why it failed**: ticksUntilCheck isn't related to the teleport at all
- **Files**: PokemonPastureBlockEntityMixin.java tick handler

### 3. UUID-based position saving with dual keys
- **What**: Saved positions with both entity UUID and pokemon UUID
- **Why it failed**: Same problem — hooked the wrong method (checkPokemon)

### 4. isTouchingWater() check for tether protection
- **What**: Only protect mons that are touching water
- **Why it failed**: The teleport source isn't in checkPokemon

### Additional problems reported 2026-04-14:
- Water mons AVOID water entirely — never go in voluntarily
- Mons need to be manually pushed into water by the player
- With Cobbleworkers, water mons go into water by themselves
- Mons should only be teleported 1-2 blocks BEHIND pasture box, not ON TOP
- Cobblemon's Cobbleworkers navigates water mons TO water — Cobblebase does not

## Correct Fix (TODO)

### Fix 1: Skip checkAndUnstick for water-type Pokemon in water
- In `NavigationHelper.checkAndUnstick()`, check if the Pokemon is water-type AND touching/near water
- If yes, skip the stuck detection entirely — they're swimming, not stuck
- File: `NavigationHelper.kt:165`

### Fix 2: Water-aware wanderNearOrigin
- In `NavigationHelper.wanderNearOrigin()`, for water-type Pokemon:
  - Pick wander targets IN water instead of on land
  - Or skip wandering entirely if the mon is already in water and has a water job
- File: `NavigationHelper.kt:368`

### Fix 3: Remove unnecessary checkPokemon mixin code
- The beforeCheckPokemon/afterCheckPokemon code is useless — remove it
- Keep only the tick handler code that's actually needed

## Reference: How Cobbleworkers handles water mons
- Cobbleworkers does NOT use a stuck detection system
- Cobbleworkers navigates water mons TO water, not away from it
- Water mons voluntarily return to land for chest deposits
- Navigation is water-aware (finds water blocks as valid targets)

## Key Files
- `NavigationHelper.kt` — checkAndUnstick (line 165), wanderNearOrigin (line 368)
- `PokemonPastureBlockEntityMixin.java` — tick handler, checkPokemon hooks
- `FishingExecutor.kt` — water navigation for fishing job
- `CobblebaseConfig.kt` — enableSafetyTeleport, safetyTeleportDistance

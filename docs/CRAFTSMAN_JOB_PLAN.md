# Craftsman Job — Feature Plan

## Overview

A new job type where a Craftsman Pokemon acts as a project leader. The player selects what to build, and other Mons in the pasture automatically supply the required materials. Once all materials are gathered, the Craftsman crafts the final item.

## How It Works

### 1. Project Selection
- Assign a Mon the Craftsman job
- A new **"Workshop" tab** appears in the Pasture GUI
- Browse craftable items by category (Doors, Stairs, Slabs, Fences, Redstone, Decoration, Cobblemon items)
- Select a project (e.g. "Iron Door")
- System shows required materials: 6x Iron Ingot — Status: 0/6

### 2. Supplier System
- Other Mons in the same pasture can become suppliers
- When a project is active, compatible Mons (Miners, Producers, Architects) get a "Supply Workshop" option
- Supplier Mons work their normal jobs but route drops to the Workshop inventory instead of the player's chest
- Example: Steelix (Miner) → Raw Iron, Magcargo (Smelter/Producer) → Iron Ingot

### 3. Automatic Crafting
- Craftsman checks Workshop inventory periodically
- When all materials are collected → cooldown → craft animation → finished item goes to output chest
- Then starts next cycle (same project, or player can queue/change)

## Workshop Tab GUI Design

```
┌──────────────────────────────────────┐
│ Active Project: Iron Door            │
│ Status: Crafting... (3/6 Iron Ingot) │
│ ━━━━━━━━━━▒▒▒▒▒▒▒▒▒▒ 50%          │
│                                      │
│ Suppliers:                           │
│ ⚒ Steelix (Miner) → Raw Iron       │
│ 🔥 Magcargo (Producer) → Iron Ingot │
│                                      │
│ [Change Project]  [Pause]            │
└──────────────────────────────────────┘
```

## Project Categories

- **Doors:** Iron Door, Oak Door, Spruce Door, etc.
- **Stairs:** Stone Stairs, Brick Stairs, Oak Stairs, etc.
- **Slabs:** Stone Slab, Oak Slab, etc.
- **Fences:** Oak Fence, Iron Bars, etc.
- **Redstone:** Piston, Hopper, Dropper, Dispenser, etc.
- **Decoration:** Lantern, Chain, Flower Pot, etc.
- **Cobblemon:** Healing Machine, PC, Pasture Block, etc.

## Supply Chain Example

```
Miner (Steelix) → Raw Iron ──┐
                              ├──→ Workshop Inventory → Craftsman (Tinkaton) → Iron Door
Architect → Planks ───────────┘
```

## Which Mons Fit

- **Metal Craftsman:** Tinkaton, Conkeldurr, Gurdurr, Timburr, Klinklang, Melmetal
- **Wood Craftsman:** Leavanny, Sudowoodo, Trevenant, Phantump
- **Stone Craftsman:** Conkeldurr, Gurdurr, Timburr, Golem, Rhyperior

## What Makes This Special

1. Not a simple item dropper — requires planning and setup
2. Multi-Mon cooperation — Mons work together
3. Proficiency scaling — higher Prof = faster crafting, maybe less material needed
4. Pasture becomes a real factory — players optimize Mon compositions

## Simplified V1 Alternative

If the full supplier system is too complex for initial release:
- Craftsman takes items from a nearby **Input Chest** (player or other Mons fill it)
- No special supplier routing needed
- Player selects project in Workshop tab, puts materials in chest, Craftsman crafts
- Supplier system can be added as V2 upgrade

## Implementation Priority

This feature is planned for the update AFTER the current release. Current release focuses on bugfixes + Producer admin improvements.

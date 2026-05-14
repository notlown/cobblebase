# Digger Job - Design Plan

## Concept

Ein neuer Job fuer Pokemon, die Bloecke im Boden abbauen und dabei eine Treppe
ins Erdreich graben (endlos bis Hoehle oder Bedrock). Perfekt fuer Mons wie
Drilbur, Diglett, Sandshrew, Onix.

## UX Flow - Dig Position (Digging Compass)

1. Im Workshop GUI -> neuer Tab **"Digger"** (nur wenn ein Buddelmon im Pasture ist)
2. Button **"Position markieren"** -> gibt dem Spieler einen `Digging Compass` (temp item)
3. Spieler geht zum Zielblock, **Rechtsklick drauf**
4. Compass verschwindet aus Inventar, Position wird gespeichert
5. GUI zeigt: `Dig target: (x, y, z) - 12 blocks from base`
6. Button **"Start Digging"** -> Mon wandert dorthin und beginnt

## Technische Umsetzung

### Neues Item: `cobblebase:digging_compass`

- Custom Item mit `useOnBlock()` Override
- NBT: `dig_target_for = <pokemon_uuid>`
- Bei Rechtsklick: sendet Packet `DigTargetC2S(pos)` -> speichert in `DiggerManager`
- Item wird direkt konsumiert nach Nutzung

### Neuer Executor: `DiggerExecutor`

```kotlin
object DiggerExecutor {
    private val targets = mutableMapOf<UUID, BlockPos>()  // mon -> dig start
    private val progress = mutableMapOf<UUID, Int>()      // blocks dug so far

    fun tick(world, origin, pokemon, skill) {
        val target = targets[pokemon.uuid] ?: return
        val depth = progress[pokemon.uuid] ?: 0

        // Safety checks
        if (depth >= 64) return  // max depth
        val current = target.down(depth)
        if (world.getBlockState(current).isIn(BlockTags.WITHER_IMMUNE)) return  // bedrock

        // Navigate, break, staircase pattern
        // Prof 1: stone+dirt only, Prof 5: bis obsidian
    }
}
```

### Safety Features

- Stop bei Bedrock
- Max 64 Bloecke tief
- Auto-stop wenn Hoehle erreicht (air unter ihm fuer >3 Bloecke)
- Kein Abbau von Container-Bloecken (Chest, Furnace, Shulker)
- Kein Abbau von `#minecraft:wither_immune`
- Nicht durch fluessige Bloecke (Lava, Wasser)

### Species Mapping

| Species | Proficiency | Kann abbauen |
|---------|-------------|--------------|
| Drilbur / Excadrill | Prof 5 | Alles bis Obsidian |
| Diglett / Dugtrio | Prof 3 | Stein / Ores |
| Sandshrew / Sandslash | Prof 2 | Sand / Dirt / Stein |
| Onix / Steelix | Prof 4 | Stein / Ores, langsam |
| Rhyhorn / Rhydon / Rhyperior | Prof 4 | Stein / Ores |

### Dig Patterns (optional, spaeter)

- `STAIRCASE` (default) - Treppe diagonal runter
- `STRAIGHT_DOWN` - gerade nach unten (mit Stufen fuer Ausstieg)
- `TUNNEL_NORTH/EAST/...` - horizontaler 2x1 Tunnel

## Implementation Schritte

1. `DiggingCompassItem` registrieren (Fabric + NeoForge)
2. `DigTargetC2SPayload` fuer Position-Sync
3. `DiggerManager` fuer State
4. `DiggerExecutor` mit Staircase-Logic
5. Workshop GUI Tab "Digger"
6. `digger.json` Skill Definition
7. Species assignments in den JSON Files

## Balance Ideen

- Cooldown pro Block: 5 Sekunden bei Prof 1, 1 Sekunde bei Prof 5
- Abgebaute Bloecke landen im Container beim Pasture
- XP fuer den Mon pro abgebautem Block
- Fackeln automatisch platzieren (bei hoher Prof)?

## Status

GEPLANT - Implementation noch nicht gestartet.
Idee festgehalten am 2026-04-16.

# Cobblebase v1.5.2

Bugfix release addressing three community-reported issues: item stacking when picked up by players, Gatherer depositing into non-storage blocks, and console log spam.

---

## 🐛 Bug Fixes

### Produced items don't stack with regular items when picked up by players
Items dropped by Producer, Miner, Harvester and other jobs were tagged with a `cobblebase_origin` marker inside the ItemStack's `CUSTOM_DATA` component. This tag is used by the Gatherer to identify which Pasture Block produced the item. While the tag was correctly stripped when items went through the Gatherer → chest path, **players who picked items up directly from the ground** kept the tag on the ItemStack — making them unable to stack with vanilla-obtained items of the same type (e.g. produced diamonds wouldn't stack with mined diamonds).

**Fix:** Origin tracking now lives on the **ItemEntity** (via command tags) instead of the **ItemStack**. The actual item data stays completely clean regardless of how it's picked up. The Gatherer checks the entity tag before pickup, and items that enter a player's inventory are always tag-free and stack normally.

### Gatherer deposits items into Waystones, modded Crafting Tables and other non-storage blocks
The Gatherer's container detection used a broad `blockEntity is Inventory` check, which matched any block entity that implements the Inventory interface — including Waystones, modded crafting tables, and other blocks that have an inventory for functional reasons but aren't actual storage containers.

**Fix:** Container detection now checks for `LockableContainerBlockEntity` (vanilla storage: chests, barrels, hoppers, dispensers, shulker boxes, furnaces) plus platform-specific storage APIs (Fabric Transfer API / NeoForge IItemHandler) for modded containers. Blocks that incidentally implement Inventory but aren't real storage are no longer targeted. No blacklist configuration needed.

### Console log spam in production
Runtime debug logs (`[Ambient]`, `[Gatherer]`, `[Harvester]`, etc.) were gated behind a config toggle (`enableConsoleLogging`) but logged at INFO level when enabled — flooding server consoles and Discord log channels. Some server owners had the toggle enabled from debugging sessions and couldn't easily find it.

**Fix:** All runtime debug logs now use `LOGGER.debug()` instead of `LOGGER.info()`. They are suppressed by Minecraft's default log4j configuration and only visible when explicitly enabling DEBUG level logging. The `enableConsoleLogging` config option is now obsolete.

---

## 📋 Summary

| Change | Type | Impact |
|---|---|---|
| Item origin tracking moved to ItemEntity | Fix | Produced items always stack with vanilla items |
| Smart container detection | Fix | Gatherer only deposits into real storage blocks |
| Debug logs demoted to DEBUG level | Fix | Zero console spam in production |

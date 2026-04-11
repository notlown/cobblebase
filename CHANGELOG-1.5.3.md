# Cobblebase v1.5.3

Bug fixes, Legendary Recruiter rework, and species skill rebalancing.

---

## Legendary Recruiter Rework

### Prof 5 Recruiter now summons Legendaries
Pokemon with Proficiency 5 Friend Recruiter will now summon random Legendary, Mythical, or Ultra Beast Pokemon instead of regular type-matched species. The legendary summon ignores the recruiter's type — any legendary can appear.

**Cooldown:** 30 minutes (fixed, not reduced by proficiency). This is intentionally high to prevent legendary spam.

**Notification:** Players see a special "summoned a Legendary" message with a level-up sound effect.

### Species rebalancing
- **Prof 5 Recruiter restricted to 6 species only:** Mew, Celebi, Jirachi, Azelf, Mesprit, Uxie
- **18 pseudo-legendaries downgraded** from Prof 5 to Prof 4: Dragonite, Garchomp, Tyranitar, Metagross, Salamence, Hydreigon, Goodra, Kommo-o, Dragapult, Baxcalibur, and others
- **Arceus:** Friend Recruiter removed entirely
- **Celebi, Azelf, Mesprit, Uxie:** Friend Recruiter added at Prof 5 (new)

---

## Bug Fixes

### LeafMixin console spam
The `[LeafMixin] Pokemon vs leaf at ... isPastureLeaf=...` message was still logging at INFO level despite the 1.5.2 debug log cleanup. This was in a Java mixin file that was missed during the Kotlin-only sweep. Now correctly uses `LOGGER.debug()`.

### Gatherer deposits into modded Crafting Tables (Visual Workbench)
The container detection filter introduced in 1.5.2 (`LockableContainerBlockEntity`) correctly blocked vanilla non-storage blocks, but modded crafting tables that expose Fabric Transfer API or NeoForge IItemHandler capabilities still passed the secondary check. Added a minimum slot count requirement (>= 9 slots) for modded containers detected via platform APIs. Crafting tables typically have fewer slots and are now excluded.

### Traveler's Backpack infinite item duplication
The Gatherer had no item type filtering — it would pick up any dropped item, including container items (backpacks, shulker boxes) from mods like Traveler's Backpack. When the Gatherer removed the container item entity and the source mod respawned it, an infinite pickup-deposit loop created millions of duplicated items.

**Fix:** The Gatherer now skips container-type items during pickup. Items are classified as containers if they are Shulker Boxes or contain inventory NBT data (`BlockEntityTag`, `Inventory`, or `Items` components). This prevents the dupe loop while still allowing all normal item pickups.

### Remaining runtime LOGGER.info calls
Five additional `LOGGER.info()` calls that fired during gameplay (NavigationHelper unstick, GathererExecutor recovery, RecruiterExecutor type map, BaseManager load, PastureLeavesTracker) were demoted to `LOGGER.debug()`. The production console is now completely clean.

---

## Summary

| Change | Type | Impact |
|---|---|---|
| Legendary Recruiter (Prof 5) | Feature | 6 cute legendaries can summon any legendary with 30min cooldown |
| Species rebalancing | Balance | Prof 5 recruiter restricted, Arceus recruiter removed |
| LeafMixin spam | Fix | Zero console spam from leaf collision checks |
| Visual Workbench deposits | Fix | Gatherer ignores small modded inventories (< 9 slots) |
| Traveler's Backpack dupe | Fix | Gatherer skips container-type items |
| Debug log cleanup | Fix | All runtime logs demoted to DEBUG level |

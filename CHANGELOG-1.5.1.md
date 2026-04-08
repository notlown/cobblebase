# Cobblebase v1.5.1

Fast-follow patch for four critical 1.5.0 bugs. If you're on 1.5.0, **update to this one** — especially for multiplayer.

---

## 🐛 Critical Fixes

### Items don't stack in chests anymore (regression since 1.5)
`InventoryHelper.insertItems` was stripping the `cobblebase_origin` NBT tag before inserting — but only in the vanilla Inventory fallback path. The platform-specific path (Fabric Transfer API / NeoForge Capabilities), which is the one most modpacks hit because of modded storage, handed the items through with the tag still attached. The tagged ItemStack then failed to merge with vanilla stacks of the same item in the chest because `ItemStack.areItemsAndComponentsEqual` sees the extra `CUSTOM_DATA` and treats the stacks as different.

The tag is now stripped up front, then the cleaned list is handed to either insertion path.

### Items from destroyed pastures would never go away
If you broke a pasture, the items its Pokemon had dropped on the ground stayed there forever — no Gatherer from any other pasture could claim them because the origin tag pointed at a pasture that no longer existed. The Gatherer's ownership check now detects orphan origins and lets any Gatherer clean them up.

### Species skill overrides didn't show up in the Pasture Skills tab on multiplayer
The Pasture Skills tab reads `SpeciesSkillRegistry.getSkills()` on the client, but admin-set overrides only ever registered into the *server's* copy of the registry. In singleplayer that was invisible because SP shares the registry between server and client, but on a dedicated server the client never saw the overrides and kept showing the bundled default skill list.

New `SpeciesOverrideSyncS2CPacket` is sent to every player on join and re-broadcast after every admin update. Client receiver applies the map to its local registry.

### Disabled jobs still appeared in the Pasture Skills tab on multiplayer
Same class of bug as above: `SkillsPanel` reads `JobConfigOverrides.isEnabled(skillId)` on the client, but `JobConfigOverrides` was only populated on the server from the world file. In multiplayer the client's copy stayed empty so every job defaulted to enabled and disabled jobs still showed up in the Pasture Skills tab — and players could assign Pokemon to them, where the server would then silently no-op.

New `JobOverrideSyncS2CPacket` with the same join-sync + post-update broadcast pattern.

### Fakemons showed the wrong preview icon in the Admin GUI
`PokemonSpriteHelper.resolveSpeciesFromName` hardcoded the `cobblemon:` namespace, so any species registered under a fakemon mod's own namespace (`babylegends:beta`, `extraparadoxmons:saberjaw`, `livelymons:*`, etc.) never resolved and always fell back to the colored badge placeholder. `drawProfilePokemon` already supports any species id regardless of namespace — the bug was purely in name → id resolution.

The resolver now walks `PokemonSpecies.getByName` and the full `PokemonSpecies.species` collection as a fallback, and caches the result per normalized name so the sidebar doesn't retraverse the registry on every frame.

---

**Cobblemon required**: 1.7.0 or newer
**Minecraft**: 1.21.1
**Loaders**: Fabric + NeoForge

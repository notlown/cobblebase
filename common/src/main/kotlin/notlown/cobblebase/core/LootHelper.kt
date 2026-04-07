package notlown.cobblebase.core

import net.minecraft.item.ItemStack
import net.minecraft.loot.context.LootContextParameterSet
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.loot.context.LootContextTypes
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

/**
 * Single entry point for loot generation in Cobblebase. Resolves the loot
 * table id (e.g. `cobblebase:finder_bal_common`) through the following
 * priority chain:
 *
 *   1. [LootOverrides] — admin-edited override stored in the world save.
 *   2. [CobblebaseLootRegistry] — bundled default parsed from JSON.
 *   3. Vanilla `world.server.reloadableRegistries.getLootTable(...)` —
 *      fallback for tables we did not import (e.g. `minecraft:gameplay/fishing`).
 *
 * The first two paths use our own simple weighted-pick implementation. The
 * vanilla fallback uses the standard loot context machinery.
 */
object LootHelper {

    fun generateLoot(
        lootTableId: String,
        world: ServerWorld,
        origin: BlockPos,
        thisEntity: net.minecraft.entity.Entity? = null
    ): List<ItemStack> {
        val normalized = normalize(lootTableId)

        // 1. Admin override
        LootOverrides.getOverride(normalized)?.let { def ->
            return rollDef(def, world)
        }

        // 2. Bundled default (in-memory)
        CobblebaseLootRegistry.get(normalized)?.let { def ->
            return rollDef(def, world)
        }

        // 3. Vanilla fallback
        return try {
            val (ns, path) = if (normalized.contains(":")) {
                val parts = normalized.split(":", limit = 2)
                parts[0] to parts[1]
            } else {
                "cobblebase" to normalized
            }
            val identifier = Identifier.of(ns, path)
            val key = RegistryKey.of(RegistryKeys.LOOT_TABLE, identifier)
            val lootTable = world.server.reloadableRegistries.getLootTable(key)
            val params = LootContextParameterSet.Builder(world)
                .add(LootContextParameters.ORIGIN, origin.toCenterPos())
                .apply { if (thisEntity != null) addOptional(LootContextParameters.THIS_ENTITY, thisEntity) }
                .build(LootContextTypes.CHEST)
            lootTable.generateLoot(params)
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Loot fallback failed for $normalized: ${e.message}")
            emptyList()
        }
    }

    /**
     * Tells the caller whether [lootTableId] is editable in the admin GUI.
     * Right now this is true for any table we either parsed at startup or
     * have an override for.
     */
    fun isEditable(lootTableId: String): Boolean {
        val normalized = normalize(lootTableId)
        return CobblebaseLootRegistry.get(normalized) != null || LootOverrides.hasOverride(normalized)
    }

    /**
     * Returns the effective definition (override > default > null) for use
     * by the admin GUI. Never falls back to vanilla — vanilla loot tables
     * are too rich to round-trip through our flat schema.
     */
    fun getEffective(lootTableId: String): LootTableDef? {
        val normalized = normalize(lootTableId)
        return LootOverrides.getOverride(normalized) ?: CobblebaseLootRegistry.get(normalized)
    }

    private fun normalize(id: String): String =
        if (id.contains(":")) id else "cobblebase:$id"

    private fun rollDef(def: LootTableDef, world: ServerWorld): List<ItemStack> {
        if (def.entries.isEmpty()) return emptyList()
        val totalWeight = def.entries.sumOf { it.weight }
        if (totalWeight <= 0) return emptyList()

        val drops = mutableListOf<ItemStack>()
        val random = world.random
        repeat(def.rolls.coerceAtLeast(1)) {
            var roll = random.nextInt(totalWeight)
            for (entry in def.entries) {
                roll -= entry.weight
                if (roll < 0) {
                    val stack = createStack(entry, random)
                    if (!stack.isEmpty) drops.add(stack)
                    break
                }
            }
        }
        return drops
    }

    private fun createStack(entry: LootEntry, random: net.minecraft.util.math.random.Random): ItemStack {
        val parts = entry.itemId.split(":", limit = 2)
        val identifier = if (parts.size == 2) Identifier.of(parts[0], parts[1]) else Identifier.of("minecraft", entry.itemId)
        val item = Registries.ITEM.get(identifier)
        if (item == net.minecraft.item.Items.AIR) return ItemStack.EMPTY
        val count = if (entry.maxCount > entry.minCount) {
            entry.minCount + random.nextInt(entry.maxCount - entry.minCount + 1)
        } else {
            entry.minCount
        }
        return ItemStack(item, count.coerceAtLeast(1))
    }
}

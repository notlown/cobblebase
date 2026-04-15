package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.*
import notlown.cobblebase.core.effects.SkillEffects
import java.util.UUID

/**
 * Dedicated executor for Craftsman supplier Mons.
 * Instead of running the normal job (which produces random loot),
 * this produces the EXACT item the Craftsman needs on a cooldown.
 *
 * The supplier Mon "specializes" in finding/producing that one item.
 */
object SupplierExecutor {

    private val lastProduceTime = mutableMapOf<UUID, Long>()

    fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity, skillEntry: SkillEntry) {
        if (world !is ServerWorld) return
        val pokemonId = pokemonEntity.pokemon.uuid
        val now = world.time

        if (now % 20L != 0L) return

        // Find what the Craftsman needs
        val neededItem = findNeededItem(pokemonId) ?: return

        // Use the skill's cooldown scaled by proficiency
        val skillDef = SkillRegistry.getEffective(skillEntry.skillId) ?: return
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(skillDef.cooldownSeconds, skillEntry.proficiency)

        val lastTime = lastProduceTime.getOrPut(pokemonId) { now }
        if (now - lastTime < cooldownTicks) {
            if (now % 60 == 0L) SkillEffects.playWorking(world, pokemonEntity, skillDef.effectType)
            return
        }

        // Produce the item
        val itemId = Identifier.of(
            if (neededItem.contains(":")) neededItem.substringBefore(":") else "minecraft",
            if (neededItem.contains(":")) neededItem.substringAfter(":") else neededItem
        )
        val item = Registries.ITEM.get(itemId)
        if (item == net.minecraft.item.Items.AIR) return

        val stack = ItemStack(item, 1)
        lastProduceTime[pokemonId] = now

        // Deposit directly into nearest chest (like other jobs)
        val containerPos = InventoryHelper.findBestContainer(world, origin, skillDef.searchRadius, listOf(stack))
        if (containerPos != null) {
            InventoryHelper.insertItems(world, containerPos, listOf(stack))
        } else {
            InventoryHelper.dropItems(world, pokemonEntity.blockPos, listOf(stack), origin)
        }

        SkillEffects.playSuccess(world, pokemonEntity, skillDef.effectType)

        LogManager.log(
            origin, now,
            pokemonEntity.pokemon.species.name,
            "Supplied",
            "${stack.name.string} x1",
            LogManager.Rarity.COMMON
        )

        Cobblebase.log("[Supplier] ${pokemonEntity.pokemon.species.name} produced 1x $neededItem for Craftsman")
    }

    /** Public accessor for the GUI to show what a supplier is producing. */
    fun findNeededItemPublic(supplierId: UUID): String? = findNeededItem(supplierId)

    /**
     * Find which item the Craftsman currently needs that this supplier's skill can provide.
     */
    private fun findNeededItem(supplierId: UUID): String? {
        val assignment = BaseManager.getAssignment(supplierId) ?: return null
        if (!assignment.startsWith(BaseManager.SUPPLIER_PREFIX)) return null
        val afterPrefix = assignment.removePrefix(BaseManager.SUPPLIER_PREFIX)
        // Parse "skillId:targetItemId" — e.g. "cobblebase:finder_bui:minecraft:acacia_planks"
        val parts = afterPrefix.split(":")
        val skillId = if (parts.size >= 2) "${parts[0]}:${parts[1]}" else parts[0]
        // If a specific target item is encoded, return it directly
        if (parts.size >= 4) {
            val targetItem = "${parts[2]}:${parts[3]}"
            return targetItem
        }

        // Check all active Craftsman projects — prioritize unfulfilled items, but keep producing for stockpile
        var stockpileItem: String? = null
        for ((_, project) in WorkshopManager.getAllProjects()) {
            if (project.phase != WorkshopManager.Phase.GATHERING && project.phase != WorkshopManager.Phase.CRAFTING) continue
            for ((itemId, required) in project.requiredItems) {
                val suppliers = SupplierHelper.getSupplierJobs(itemId)
                if (!suppliers.any { it.skillId == skillId }) continue

                val gathered = project.gatheredItems[itemId] ?: 0
                if (gathered < required) {
                    return itemId // Priority: unfulfilled items
                }
                if (stockpileItem == null) {
                    stockpileItem = itemId // Keep producing for next cycle
                }
            }
        }
        return stockpileItem
    }
}

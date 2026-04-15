package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.block.ApricornBlock
import com.cobblemon.mod.common.block.BerryBlock
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.CropBlock
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.*
import notlown.cobblebase.core.effects.SkillEffects
import java.util.UUID

/**
 * Dedicated executor for Craftsman supplier Mons.
 *
 * For harvestable items (apricorns, crops, berries): requires real plants nearby.
 * The supplier walks to the plant, harvests it, and deposits the item.
 *
 * For non-harvestable items (ores, metals, etc.): generates the item on cooldown
 * (same as before — represents the Mon "finding" the material).
 */
object SupplierExecutor {

    private val lastProduceTime = mutableMapOf<UUID, Long>()
    private val APRICORNS_TAG = TagKey.of(RegistryKeys.BLOCK, Identifier.of("cobblemon", "apricorns"))

    // Items that must come from real plants — can't be generated from nothing
    private val HARVESTABLE_ITEMS = setOf(
        // Apricorns
        "cobblemon:red_apricorn", "cobblemon:blue_apricorn", "cobblemon:yellow_apricorn",
        "cobblemon:green_apricorn", "cobblemon:black_apricorn", "cobblemon:white_apricorn",
        "cobblemon:pink_apricorn",
        // Vanilla crops
        "minecraft:wheat", "minecraft:carrot", "minecraft:potato", "minecraft:beetroot",
        "minecraft:sugar_cane", "minecraft:melon_slice", "minecraft:pumpkin",
        "minecraft:cocoa_beans", "minecraft:nether_wart",
        // Berries (Cobblemon)
        "minecraft:sweet_berries"
    )

    fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity, skillEntry: SkillEntry) {
        if (world !is ServerWorld) return
        val pokemonId = pokemonEntity.pokemon.uuid
        val now = world.time

        if (now % 20L != 0L) return

        val neededItem = findNeededItem(pokemonId) ?: return
        val skillDef = SkillRegistry.getEffective(skillEntry.skillId) ?: return
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(skillDef.cooldownSeconds, skillEntry.proficiency)

        val lastTime = lastProduceTime.getOrPut(pokemonId) { now }
        if (now - lastTime < cooldownTicks) {
            if (now % 60 == 0L) SkillEffects.playWorking(world, pokemonEntity, skillDef.effectType)
            return
        }

        val isHarvestable = HARVESTABLE_ITEMS.contains(neededItem.lowercase()) ||
            neededItem.lowercase().contains("apricorn") ||
            neededItem.lowercase().contains("berry")

        if (isHarvestable) {
            // Must find and harvest a real plant
            tickHarvest(world, origin, pokemonEntity, pokemonId, neededItem, skillDef, now)
        } else {
            // Generate from nothing (ores, metals, building materials)
            tickGenerate(world, origin, pokemonEntity, pokemonId, neededItem, skillDef, now)
        }
    }

    /**
     * Harvest mode: find a real plant nearby that produces the needed item,
     * walk to it, and harvest it.
     */
    private fun tickHarvest(
        world: ServerWorld, origin: BlockPos, pokemonEntity: PokemonEntity,
        pokemonId: UUID, neededItem: String, skillDef: SkillDef, now: Long
    ) {
        val radius = skillDef.searchRadius

        // Search for a harvestable block that drops the needed item
        val harvestPos = findHarvestableFor(world, origin, radius, neededItem)
        if (harvestPos == null) {
            // No plant ready — show idle particles, don't reset cooldown
            if (now % 100 == 0L) {
                SkillEffects.playWorking(world, pokemonEntity, skillDef.effectType)
                Cobblebase.log("[Supplier] ${pokemonEntity.pokemon.species.name} looking for ${neededItem} plants — none ready nearby")
            }
            return
        }

        // Navigate to the plant
        NavigationHelper.navigateTo(pokemonEntity, harvestPos, 1.0)

        // Harvest if close enough
        if (NavigationHelper.isPokemonAtPosition(pokemonEntity, harvestPos, 3.0)) {
            // Break the block and get drops
            val state = world.getBlockState(harvestPos)
            val drops = net.minecraft.block.Block.getDroppedStacks(state, world, harvestPos, world.getBlockEntity(harvestPos))

            if (drops.isNotEmpty()) {
                // Reset the block (set age to 0 for re-growth)
                resetPlant(world, harvestPos)

                // Deposit drops into chest
                val containerPos = InventoryHelper.findBestContainer(world, origin, radius, drops)
                if (containerPos != null) {
                    InventoryHelper.insertItems(world, containerPos, drops)
                } else {
                    InventoryHelper.dropItems(world, pokemonEntity.blockPos, drops, origin)
                }

                lastProduceTime[pokemonId] = now
                SkillEffects.playSuccess(world, pokemonEntity, skillDef.effectType)

                val dropNames = drops.joinToString(", ") { "${it.name.string} x${it.count}" }
                LogManager.log(origin, now, pokemonEntity.pokemon.species.name, "Harvested", dropNames, LogManager.Rarity.COMMON)
                Cobblebase.log("[Supplier] ${pokemonEntity.pokemon.species.name} harvested $dropNames from ${harvestPos}")
            }
        }
    }

    /**
     * Generate mode: create the item from nothing (for ores, metals, etc.)
     */
    private fun tickGenerate(
        world: ServerWorld, origin: BlockPos, pokemonEntity: PokemonEntity,
        pokemonId: UUID, neededItem: String, skillDef: SkillDef, now: Long
    ) {
        val itemId = Identifier.of(
            if (neededItem.contains(":")) neededItem.substringBefore(":") else "minecraft",
            if (neededItem.contains(":")) neededItem.substringAfter(":") else neededItem
        )
        val item = Registries.ITEM.get(itemId)
        if (item == net.minecraft.item.Items.AIR) return

        val stack = ItemStack(item, 1)
        lastProduceTime[pokemonId] = now

        val containerPos = InventoryHelper.findBestContainer(world, origin, skillDef.searchRadius, listOf(stack))
        if (containerPos != null) {
            InventoryHelper.insertItems(world, containerPos, listOf(stack))
        } else {
            InventoryHelper.dropItems(world, pokemonEntity.blockPos, listOf(stack), origin)
        }

        SkillEffects.playSuccess(world, pokemonEntity, skillDef.effectType)
        LogManager.log(origin, now, pokemonEntity.pokemon.species.name, "Supplied", "${stack.name.string} x1", LogManager.Rarity.COMMON)
        Cobblebase.log("[Supplier] ${pokemonEntity.pokemon.species.name} produced 1x $neededItem")
    }

    /**
     * Find a harvestable plant nearby that produces the needed item.
     */
    private fun findHarvestableFor(world: ServerWorld, origin: BlockPos, radius: Int, neededItem: String): BlockPos? {
        val itemLower = neededItem.lowercase()
        val candidates = mutableListOf<BlockPos>()

        for (x in -radius..radius) {
            for (y in 0..radius) {
                for (z in -radius..radius) {
                    val pos = origin.add(x, y, z)
                    val state = world.getBlockState(pos)

                    // Apricorns
                    if (itemLower.contains("apricorn") && state.isIn(APRICORNS_TAG)) {
                        try {
                            if (state.get(ApricornBlock.AGE) == ApricornBlock.MAX_AGE) {
                                // Check if this apricorn color matches the needed one
                                val blockName = Registries.BLOCK.getId(state.block).path.lowercase()
                                val neededColor = itemLower.substringAfterLast(":").replace("_apricorn", "")
                                if (blockName.contains(neededColor)) {
                                    candidates.add(pos.toImmutable())
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    // Crops (wheat, carrot, potato, beetroot)
                    if (state.block is CropBlock && (state.block as CropBlock).isMature(state)) {
                        if (itemLower.contains("wheat") || itemLower.contains("carrot") ||
                            itemLower.contains("potato") || itemLower.contains("beetroot")) {
                            candidates.add(pos.toImmutable())
                        }
                    }

                    // Berries
                    if (state.block is BerryBlock) {
                        try {
                            if (state.get(BerryBlock.AGE) >= BerryBlock.FRUIT_AGE) {
                                candidates.add(pos.toImmutable())
                            }
                        } catch (_: Exception) {}
                    }

                    // Sweet berry bushes
                    if (state.block is net.minecraft.block.SweetBerryBushBlock) {
                        try {
                            if (state.get(net.minecraft.block.SweetBerryBushBlock.AGE) >= 2) {
                                candidates.add(pos.toImmutable())
                            }
                        } catch (_: Exception) {}
                    }

                    // Nether wart
                    if (state.block is net.minecraft.block.NetherWartBlock && itemLower.contains("nether_wart")) {
                        try {
                            if (state.get(net.minecraft.block.NetherWartBlock.AGE) == 3) {
                                candidates.add(pos.toImmutable())
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        return candidates.randomOrNull()
    }

    /**
     * Reset a harvested plant so it can regrow (set age to 0 instead of breaking the block).
     */
    private fun resetPlant(world: ServerWorld, pos: BlockPos) {
        val state = world.getBlockState(pos)
        try {
            when {
                state.isIn(APRICORNS_TAG) -> {
                    world.setBlockState(pos, state.with(ApricornBlock.AGE, 0))
                }
                state.block is CropBlock -> {
                    world.setBlockState(pos, (state.block as CropBlock).defaultState)
                }
                state.block is BerryBlock -> {
                    // Use BerryBlock's harvest method if available, otherwise reset age
                    world.setBlockState(pos, state.with(BerryBlock.AGE, 0))
                }
                state.block is net.minecraft.block.SweetBerryBushBlock -> {
                    world.setBlockState(pos, state.with(net.minecraft.block.SweetBerryBushBlock.AGE, 1))
                }
                state.block is net.minecraft.block.NetherWartBlock -> {
                    world.setBlockState(pos, state.with(net.minecraft.block.NetherWartBlock.AGE, 0))
                }
            }
        } catch (_: Exception) {}
    }

    /** Public accessor for the GUI to show what a supplier is producing. */
    fun findNeededItemPublic(supplierId: UUID): String? = findNeededItem(supplierId)

    private fun findNeededItem(supplierId: UUID): String? {
        val assignment = BaseManager.getAssignment(supplierId) ?: return null
        if (!assignment.startsWith(BaseManager.SUPPLIER_PREFIX)) return null
        val afterPrefix = assignment.removePrefix(BaseManager.SUPPLIER_PREFIX)
        val parts = afterPrefix.split(":")
        val skillId = if (parts.size >= 2) "${parts[0]}:${parts[1]}" else parts[0]
        if (parts.size >= 4) {
            val targetItem = "${parts[2]}:${parts[3]}"
            return targetItem
        }

        var stockpileItem: String? = null
        for ((_, project) in WorkshopManager.getAllProjects()) {
            if (project.phase != WorkshopManager.Phase.GATHERING && project.phase != WorkshopManager.Phase.CRAFTING) continue
            for ((itemId, required) in project.requiredItems) {
                val suppliers = SupplierHelper.getSupplierJobs(itemId)
                if (!suppliers.any { it.skillId == skillId }) continue
                val gathered = project.gatheredItems[itemId] ?: 0
                if (gathered < required) return itemId
                if (stockpileItem == null) stockpileItem = itemId
            }
        }
        return stockpileItem
    }
}

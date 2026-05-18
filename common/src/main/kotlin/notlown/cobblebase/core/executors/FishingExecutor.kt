package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.loot.LootTables
import net.minecraft.loot.context.LootContextParameterSet
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.loot.context.LootContextTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.LogManager
import notlown.cobblebase.core.effects.SkillEffects
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import notlown.cobblebase.core.NavigationHelper
import java.util.UUID

/**
 * Fishing executor: Pokemon navigates to water, generates fishing loot, deposits in chest.
 * Uses water block cache and failed deposit tracking for performance.
 */
object FishingExecutor : SkillExecutor {

    private val lastGenerationTime = mutableMapOf<UUID, Long>()
    private val heldItems = mutableMapOf<UUID, List<ItemStack>>()
    private val successTime = mutableMapOf<UUID, Long>()
    private val waterTarget = mutableMapOf<UUID, BlockPos>()
    private const val SUCCESS_PAUSE_TICKS = 40L
    private val waterModeApplied = mutableSetOf<UUID>()
    /** Per-Pokemon heartbeat for [cleanupStale]. Updated every tick so on-cooldown
     *  Pokemon don't have their cooldown timer wiped after 60s. */
    private val lastScanTime = mutableMapOf<UUID, Long>()
    private const val STALE_TTL_TICKS = 1200L

    fun cleanupStale(now: Long) {
        val stale = lastScanTime.entries.filter { now - it.value > STALE_TTL_TICKS }.map { it.key }
        for (id in stale) {
            lastScanTime.remove(id)
            lastGenerationTime.remove(id)
            heldItems.remove(id)
            successTime.remove(id)
            waterTarget.remove(id)
            waterModeApplied.remove(id)
            failedDepositLocations.remove(id)
        }
    }

    // Water block cache per pasture origin — scanned once, refreshed every 5 minutes
    private val waterCache = mutableMapOf<BlockPos, MutableSet<BlockPos>>()
    private val waterCacheTime = mutableMapOf<BlockPos, Long>()
    private const val CACHE_TTL_TICKS = 6000L // 5 minutes

    // Failed deposit locations per Pokemon — skip full chests
    private val failedDepositLocations = mutableMapOf<UUID, MutableSet<BlockPos>>()

    override fun tick(
        world: World,
        origin: BlockPos,
        pokemonEntity: PokemonEntity,
        skill: SkillDef,
        skillEntry: SkillEntry
    ) {
        if (world !is ServerWorld) return
        val pokemonId = pokemonEntity.pokemon.uuid
        val now = world.time
        lastScanTime[pokemonId] = now
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(skill.cooldownSeconds, skillEntry.proficiency, skill.id)
        val items = heldItems[pokemonId]

        // Adjust swim behaviour for water mons: cap swimSpeed
        if (pokemonId !in waterModeApplied) {
            try {
                val swimBehaviour = pokemonEntity.behaviour.moving.swim
                val speedField = swimBehaviour.javaClass.getDeclaredField("swimSpeed")
                speedField.isAccessible = true
                val exprClass = Class.forName("com.bedrockk.molang.Expression")
                val companionField = exprClass.getDeclaredField("Companion")
                companionField.isAccessible = true
                val companion = companionField.get(null)
                val ofMethod = companion.javaClass.getMethod("of", Double::class.java)
                val newSpeed = ofMethod.invoke(companion, 0.15)
                speedField.set(swimBehaviour, newSpeed)
                waterModeApplied.add(pokemonId)
            } catch (e: Exception) {
                waterModeApplied.add(pokemonId)
            }
        }

        if (!items.isNullOrEmpty()) {
            // Has items - wait for success pause, then deposit
            val catchTime = successTime[pokemonId]
            if (catchTime != null && now - catchTime < SUCCESS_PAUSE_TICKS) return
            successTime.remove(pokemonId)
            depositItems(world, origin, pokemonEntity, pokemonId)
            return
        }

        if (isNearWater(world, pokemonEntity)) {
            waterTarget.remove(pokemonId)
            failedDepositLocations.remove(pokemonId) // Reset failed deposits when back at water
            // At water - check cooldown
            val lastTime = lastGenerationTime[pokemonId] ?: 0L
            if (now - lastTime < cooldownTicks) {
                if (world.time % 20 == 0L) SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
                return
            }

            // Generate fishing loot
            generateLoot(world, pokemonEntity, pokemonId, now)
            val fished = heldItems[pokemonId]
            if (!fished.isNullOrEmpty()) {
                SkillEffects.playSuccess(world, pokemonEntity, skill.effectType, origin)
                for (item in fished) {
                    LogManager.log(
                        origin, world.time,
                        pokemonEntity.pokemon.species.name,
                        "Fished",
                        "${item.name.string} x${item.count}",
                        LogManager.Rarity.COMMON
                    )
                }
            }
        } else {
            // Navigate to water using cached water blocks
            val target = waterTarget[pokemonId] ?: findWaterCached(world, origin, skill.searchRadius, now)
            if (target != null) {
                waterTarget[pokemonId] = target
                NavigationHelper.navigateTo(pokemonEntity, target.up())
            }
        }
    }

    private fun isNearWater(world: World, pokemonEntity: PokemonEntity): Boolean {
        if (pokemonEntity.isTouchingWater) return true
        val pos = pokemonEntity.blockPos
        return BlockPos.iterate(pos.add(-1, -1, -1), pos.add(1, 1, 1)).any { checkPos ->
            world.getBlockState(checkPos).block == Blocks.WATER
        }
    }

    /**
     * Find nearest water using cached water block positions.
     * Cache is rebuilt every 5 minutes per pasture origin.
     */
    private fun findWaterCached(world: World, origin: BlockPos, radius: Int, now: Long): BlockPos? {
        val lastScan = waterCacheTime[origin] ?: 0L
        if (now - lastScan > CACHE_TTL_TICKS || waterCache[origin] == null) {
            // Rebuild cache
            val blocks = mutableSetOf<BlockPos>()
            for (x in -radius..radius) {
                for (y in -radius..radius) {
                    for (z in -radius..radius) {
                        val pos = origin.add(x, y, z)
                        if (world.getBlockState(pos).block == Blocks.WATER) {
                            blocks.add(pos.toImmutable())
                        }
                    }
                }
            }
            waterCache[origin] = blocks
            waterCacheTime[origin] = now
        }

        val cached = waterCache[origin] ?: return null
        if (cached.isEmpty()) return null
        return cached.minByOrNull { it.getSquaredDistance(origin) }
    }

    private fun generateLoot(world: ServerWorld, pokemonEntity: PokemonEntity, pokemonId: UUID, now: Long) {
        val treasureChance = 0.05
        val useTreasure = world.random.nextFloat() < treasureChance

        val lootParams = LootContextParameterSet.Builder(world)
            .add(LootContextParameters.ORIGIN, pokemonEntity.blockPos.toCenterPos())
            .add(LootContextParameters.TOOL, ItemStack(Items.FISHING_ROD))
            .addOptional(LootContextParameters.THIS_ENTITY, pokemonEntity)
            .build(LootContextTypes.FISHING)

        val lootTable = if (useTreasure) {
            world.server.reloadableRegistries.getLootTable(LootTables.FISHING_TREASURE_GAMEPLAY)
        } else {
            world.server.reloadableRegistries.getLootTable(LootTables.FISHING_GAMEPLAY)
        }

        val drops = lootTable.generateLoot(lootParams)
        if (drops.isNotEmpty()) {
            lastGenerationTime[pokemonId] = now
            heldItems[pokemonId] = drops
            successTime[pokemonId] = now
        }
    }

    private fun depositItems(world: World, origin: BlockPos, pokemonEntity: PokemonEntity, pokemonId: UUID) {
        val items = heldItems[pokemonId] ?: return
        val failed = failedDepositLocations.getOrPut(pokemonId) { mutableSetOf() }

        // Try to deposit into a nearby chest, skipping known-full ones
        val containerPos = InventoryHelper.findBestContainer(world, origin, 10, items)
        if (containerPos != null && containerPos !in failed) {
            // Navigate to chest
            NavigationHelper.navigateTo(pokemonEntity, containerPos)
            if (NavigationHelper.isPokemonAtPosition(pokemonEntity, containerPos, 2.5)) {
                val remaining = InventoryHelper.insertItems(world, containerPos, items)
                if (remaining.isEmpty() || remaining.all { it.isEmpty }) {
                    heldItems.remove(pokemonId)
                    return
                }
                // Chest was full or partially full — mark as failed
                failed.add(containerPos)
                heldItems[pokemonId] = remaining
                return // Try another chest next tick
            }
            return // Still walking to chest
        }

        // No valid chest found — drop items on ground
        InventoryHelper.dropItems(world, pokemonEntity.blockPos, items, origin)
        heldItems.remove(pokemonId)
    }
}

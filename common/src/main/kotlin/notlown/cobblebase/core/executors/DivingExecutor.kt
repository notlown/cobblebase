package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.loot.context.LootContextParameterSet
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.loot.context.LootContextTypes
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.LogManager
import notlown.cobblebase.core.NavigationHelper
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import notlown.cobblebase.core.effects.SkillEffects
import java.util.UUID

/**
 * Diving executor: Pokemon navigates to water, dives in, and retrieves underwater treasure.
 * Uses NavigationHelper to physically move the Pokemon to water, similar to FishingExecutor.
 */
object DivingExecutor : SkillExecutor {

    private val lastDiveTime = mutableMapOf<UUID, Long>()
    private val heldItems = mutableMapOf<UUID, List<ItemStack>>()
    private val successTime = mutableMapOf<UUID, Long>()
    private val waterTarget = mutableMapOf<UUID, BlockPos>()
    private val hasReachedWater = mutableMapOf<UUID, Boolean>() // Track if mon ever reached water
    private const val SUCCESS_PAUSE_TICKS = 60L

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

        if (now % 100 == 0L) {
            notlown.cobblebase.core.Cobblebase.LOGGER.info("[DivingExecutor] ${pokemonEntity.pokemon.species.name} ticking, inWater=${isInWater(world, pokemonEntity)}, pos=${pokemonEntity.blockPos}")
        }
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(skill.cooldownSeconds, skillEntry.proficiency)
        val items = heldItems[pokemonId]

        if (!items.isNullOrEmpty()) {
            val diveTime = successTime[pokemonId]
            if (diveTime != null && now - diveTime < SUCCESS_PAUSE_TICKS) return
            successTime.remove(pokemonId)
            depositItems(world, origin, pokemonEntity, pokemonId)
            return
        }

        // Passive approach (like CobbleWorkersPlus):
        // Don't navigate the mon to water — let Cobblemon's wander AI do it naturally.
        // Only generate loot when the mon happens to be touching water.
        if (!pokemonEntity.isTouchingWater) return

        // Mon is in water — check cooldown
        val lastTime = lastDiveTime[pokemonId] ?: 0L
        if (now - lastTime < cooldownTicks) {
            // Bubble particles while diving
            if (now % 20 == 0L) {
                world.spawnParticles(
                    ParticleTypes.BUBBLE,
                    pokemonEntity.x, pokemonEntity.y + 0.5, pokemonEntity.z,
                    5, 0.3, 0.2, 0.3, 0.01
                )
            }
            return
        }

        // Cooldown done — generate dive loot
        generateDiveLoot(world, pokemonEntity, pokemonId, now, skill)
        val treasure = heldItems[pokemonId]
        if (!treasure.isNullOrEmpty()) {
            world.spawnParticles(
                ParticleTypes.BUBBLE_COLUMN_UP,
                pokemonEntity.x, pokemonEntity.y, pokemonEntity.z,
                20, 0.5, 0.5, 0.5, 0.1
            )
            SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
            for (item in treasure) {
                LogManager.log(
                    origin, world.time,
                    pokemonEntity.pokemon.species.name,
                    "Dived",
                    "${item.name.string} x${item.count}",
                    LogManager.Rarity.UNCOMMON
                )
            }
        }
    }

    private fun isInWater(world: World, pokemonEntity: PokemonEntity): Boolean {
        if (pokemonEntity.isTouchingWater) return true
        val pos = pokemonEntity.blockPos
        return BlockPos.iterate(pos.add(-1, -1, -1), pos.add(1, 1, 1)).any { checkPos ->
            world.getBlockState(checkPos).block == Blocks.WATER
        }
    }

    private fun findWater(world: World, origin: BlockPos, radius: Int): BlockPos? {
        var best: BlockPos? = null
        var bestDist = Double.MAX_VALUE

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = origin.add(x, y, z)
                    if (world.getBlockState(pos).block == Blocks.WATER) {
                        val dist = pos.getSquaredDistance(origin)
                        if (dist < bestDist) {
                            bestDist = dist
                            best = pos.toImmutable()
                        }
                    }
                }
            }
        }
        return best
    }

    private fun generateDiveLoot(world: ServerWorld, pokemonEntity: PokemonEntity, pokemonId: UUID, now: Long, skill: SkillDef) {
        val lootTableId = skill.lootTable ?: "cobblebase:dive_treasure"
        notlown.cobblebase.core.Cobblebase.LOGGER.info("[DivingExecutor] ${pokemonEntity.pokemon.species.name} generating loot from $lootTableId")

        try {
            val lootParams = LootContextParameterSet.Builder(world)
                .add(LootContextParameters.ORIGIN, pokemonEntity.blockPos.toCenterPos())
                .addOptional(LootContextParameters.THIS_ENTITY, pokemonEntity)
                .build(LootContextTypes.CHEST)

            val identifier = Identifier.of(lootTableId.substringBefore(":"), lootTableId.substringAfter(":"))
            val lootTableKey = RegistryKey.of(RegistryKeys.LOOT_TABLE, identifier)
            val lootTable = world.server.reloadableRegistries.getLootTable(lootTableKey)
            val drops = lootTable.generateLoot(lootParams)

            notlown.cobblebase.core.Cobblebase.LOGGER.info("[DivingExecutor] Generated ${drops.size} items")

            // Always set cooldown timer even if no drops
            lastDiveTime[pokemonId] = now

            if (drops.isNotEmpty()) {
                heldItems[pokemonId] = drops
                successTime[pokemonId] = now
            }
        } catch (e: Exception) {
            notlown.cobblebase.core.Cobblebase.LOGGER.error("[DivingExecutor] Loot generation failed: ${e.message}")
            lastDiveTime[pokemonId] = now
        }
    }

    private fun depositItems(world: World, origin: BlockPos, pokemonEntity: PokemonEntity, pokemonId: UUID) {
        val items = heldItems[pokemonId] ?: return
        // Try to deposit into a nearby chest
        val containerPos = InventoryHelper.findBestContainer(world, origin, 10, items)
        if (containerPos != null) {
            val remaining = InventoryHelper.insertItems(world, containerPos, items)
            if (remaining.isEmpty() || remaining.all { it.isEmpty }) {
                heldItems.remove(pokemonId)
                return
            }
            InventoryHelper.dropItems(world, pokemonEntity.blockPos, remaining)
        } else {
            InventoryHelper.dropItems(world, pokemonEntity.blockPos, items)
        }
        heldItems.remove(pokemonId)
    }
}

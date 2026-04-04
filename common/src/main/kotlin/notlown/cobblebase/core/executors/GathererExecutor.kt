package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.LogManager
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import notlown.cobblebase.core.NavigationHelper
import notlown.cobblebase.core.effects.SkillEffects
import java.util.UUID

/**
 * Gatherer: Pokemon picks up dropped ItemEntities from the ground and deposits them
 * into nearby chests/barrels using InventoryHelper's smart sorting.
 *
 * Proficiency affects search radius and cooldown between pickups:
 *   Prof 1 = 5 blocks, Prof 2 = 7 blocks, Prof 3 = 8 blocks, Prof 4 = 10 blocks, Prof 5 = 12 blocks
 *   Base cooldown: 10 seconds (affected by proficiency via CobblebaseConfig)
 */
object GathererExecutor : SkillExecutor {

    private val heldItems = mutableMapOf<UUID, List<ItemStack>>()
    private val originalHeldItem = mutableMapOf<UUID, ItemStack>()  // backup of Pokemon's real held item
    private val visualItems = mutableMapOf<UUID, ItemEntity>()  // floating visual item above Pokemon's head
    private val targetItem = mutableMapOf<UUID, Int>()          // entity ID of target ItemEntity
    private val targetSetTime = mutableMapOf<UUID, Long>()
    private val lastPickupTime = mutableMapOf<UUID, Long>()
    private val lastSearchTime = mutableMapOf<UUID, Long>()

    private const val NAV_TIMEOUT_TICKS = 60L         // 3 seconds - auto-pickup if can't reach
    private const val SEARCH_INTERVAL_TICKS = 20L    // 1 second between scans
    private const val BASE_COOLDOWN_SECONDS = 2L     // 2 seconds between pickups

    /**
     * Returns search radius based on proficiency (1-5).
     * Prof 1 = 5, Prof 2 = 7, Prof 3 = 8, Prof 4 = 10, Prof 5 = 12
     */
    private fun getRadiusForProficiency(proficiency: Int): Double {
        // All proficiency levels get max radius (24 blocks) — Prof only affects speed + cooldown
        // Must be >= Harvester radius (20) so Gatherer can reach all dropped items
        return 24.0
    }

    /**
     * Returns movement speed based on proficiency (1-5).
     * Prof 1 = 0.4 (slow), Prof 5 = 1.2 (fast)
     */
    private fun getSpeedForProficiency(proficiency: Int): Double {
        return -1.0 // Use species' natural walkSpeed (via NavigationHelper)
    }

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
        val speed = getSpeedForProficiency(skillEntry.proficiency)
        val items = heldItems[pokemonId]

        // Update visual floating item position to follow Pokemon
        val visual = visualItems[pokemonId]
        if (visual != null && visual.isAlive) {
            visual.setPosition(pokemonEntity.x, pokemonEntity.y + pokemonEntity.height + 0.5, pokemonEntity.z)
            visual.setVelocity(0.0, 0.0, 0.0)
        }

        // Phase 1: deposit items if holding any
        if (!items.isNullOrEmpty()) {
            depositItems(world, origin, pokemonEntity, pokemonId, speed)
            return
        }

        // Phase 2: navigate to target item entity and pick it up
        val targetEntityId = targetItem[pokemonId]
        if (targetEntityId != null) {
            val itemEntity = world.getEntityById(targetEntityId)
            if (itemEntity == null || !itemEntity.isAlive || itemEntity !is ItemEntity) {
                // Target gone (picked up by player, despawned, etc.)
                targetItem.remove(pokemonId)
                targetSetTime.remove(pokemonId)
                return
            }

            NavigationHelper.navigateTo(pokemonEntity, itemEntity.blockPos, speed)
            val navStarted = targetSetTime[pokemonId] ?: now
            val timedOut = now - navStarted >= NAV_TIMEOUT_TICKS

            if (NavigationHelper.isPokemonAtPosition(pokemonEntity, itemEntity.blockPos) || timedOut) {
                pickupItem(world, itemEntity, pokemonEntity, pokemonId)
                targetItem.remove(pokemonId)
                targetSetTime.remove(pokemonId)
                lastPickupTime[pokemonId] = now
            }
            return
        }

        // Phase 3: cooldown between pickups
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(BASE_COOLDOWN_SECONDS, skillEntry.proficiency)
        val lastPickup = lastPickupTime[pokemonId] ?: 0L
        if (now - lastPickup < cooldownTicks) {
            if (now % 60 == 0L) {
                SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
            }
            return
        }

        // Phase 4: search for dropped items (throttled to avoid lag)
        val lastSearch = lastSearchTime[pokemonId] ?: 0L
        if (now - lastSearch < SEARCH_INTERVAL_TICKS) return
        lastSearchTime[pokemonId] = now

        val radius = getRadiusForProficiency(skillEntry.proficiency)
        val found = findNearestDroppedItem(world, pokemonEntity, radius)
        if (found != null) {
            // Claim the item immediately — prevent player pickup to avoid dupe glitch
            found.setPickupDelay(Short.MAX_VALUE.toInt())
            targetItem[pokemonId] = found.id
            targetSetTime[pokemonId] = now
            Cobblebase.LOGGER.info("[Gatherer] ${pokemonEntity.pokemon.species.name} targeting ${found.stack.name.string} at ${found.blockPos}")
        } else if (now % 100 == 0L) {
            // Debug: log when no items found
            val searchBox = Box.of(pokemonEntity.pos, radius * 2, radius * 2, radius * 2)
            val allItems = world.getEntitiesByClass(ItemEntity::class.java, searchBox) { true }
            Cobblebase.LOGGER.info("[Gatherer] ${pokemonEntity.pokemon.species.name} searching radius=$radius — found ${allItems.size} items on ground")
        }
    }

    /**
     * Finds the OLDEST dropped ItemEntity within the search radius.
     * Prioritizes by age (oldest first) so far-away items don't get starved
     * by constantly spawning nearby items.
     */
    private fun findNearestDroppedItem(world: ServerWorld, pokemonEntity: PokemonEntity, radius: Double): ItemEntity? {
        val searchBox = Box.of(pokemonEntity.pos, radius * 2, radius * 2, radius * 2)
        val items = world.getEntitiesByClass(ItemEntity::class.java, searchBox) { entity ->
            entity.isAlive && !entity.stack.isEmpty
        }
        // Pick oldest item (highest age = been on ground longest)
        return items.maxByOrNull { it.age }
    }

    /**
     * Picks up a dropped item: removes the entity, stores the stack in state.
     * Spawns pickup particles.
     */
    private fun pickupItem(world: ServerWorld, itemEntity: ItemEntity, pokemonEntity: PokemonEntity, pokemonId: UUID) {
        val stack = itemEntity.stack.copy()
        if (stack.isEmpty) return

        // Remove the target item
        itemEntity.discard()

        // Also grab ALL other items within 3 blocks (batch pickup)
        val nearbyItems = world.getEntitiesByClass(ItemEntity::class.java,
            net.minecraft.util.math.Box.of(pokemonEntity.pos, 6.0, 4.0, 6.0)
        ) { it.isAlive && !it.stack.isEmpty && it.id != itemEntity.id }

        val allStacks = mutableListOf(stack)
        for (nearby in nearbyItems.take(15)) { // max 15 items per batch
            nearby.setPickupDelay(Short.MAX_VALUE.toInt()) // Prevent player pickup
            allStacks.add(nearby.stack.copy())
            nearby.discard()
        }

        heldItems[pokemonId] = allStacks

        // Show gathered item visually — spawn a no-gravity, no-pickup item floating above the Pokemon
        // Remove any existing visual item first
        val oldVisual = visualItems.remove(pokemonId)
        if (oldVisual != null && oldVisual.isAlive) oldVisual.discard()
        val visualItem = ItemEntity(world, pokemonEntity.x, pokemonEntity.y + pokemonEntity.height + 0.5, pokemonEntity.z, stack.copy())
        visualItem.setPickupDelay(Short.MAX_VALUE.toInt()) // never pickable
        visualItem.setNoGravity(true)
        visualItem.isInvulnerable = true
        world.spawnEntity(visualItem)
        visualItems[pokemonId] = visualItem
        Cobblebase.LOGGER.info("[Gatherer] ${pokemonEntity.pokemon.species.name} showing visual item above head")

        // Pickup particles (item sparkle effect)
        val x = pokemonEntity.x
        val y = pokemonEntity.y
        val h = pokemonEntity.height.toDouble()
        val z = pokemonEntity.z
        world.spawnParticles(ParticleTypes.ENCHANT, x, y + h, z, 15, 0.3, 0.3, 0.3, 0.5)
        world.spawnParticles(ParticleTypes.END_ROD, x, y + h * 0.5, z, 5, 0.2, 0.2, 0.2, 0.02)

        Cobblebase.LOGGER.info("[Gatherer] ${pokemonEntity.pokemon.species.name} picked up ${stack.name.string}x${stack.count}")
        // Play cry + success effect on pickup
        SkillEffects.playSuccess(world, pokemonEntity, "default")
    }

    /**
     * Navigates to the best container and deposits held items.
     * Plays happy villager particles on successful deposit.
     */
    private val depositStartTime = mutableMapOf<UUID, Long>()
    private const val DEPOSIT_TIMEOUT_TICKS = 200L // 10 seconds max to reach chest

    private fun depositItems(world: ServerWorld, origin: BlockPos, pokemonEntity: PokemonEntity, pokemonId: UUID, speed: Double) {
        val items = heldItems[pokemonId] ?: return
        val chestPos = InventoryHelper.findBestContainer(world, origin, 10, items)

        if (chestPos == null) {
            // No chest found - drop items so Pokemon doesn't get stuck
            InventoryHelper.dropItems(world, pokemonEntity.blockPos, items)
            heldItems.remove(pokemonId)
            restoreOriginalHeldItem(pokemonEntity, pokemonId)
            depositStartTime.remove(pokemonId)
            return
        }

        // Track how long we've been trying to reach the chest
        val startTime = depositStartTime.getOrPut(pokemonId) { world.time }
        val elapsed = world.time - startTime

        NavigationHelper.navigateTo(pokemonEntity, chestPos, speed)
        if (NavigationHelper.isPokemonAtPosition(pokemonEntity, chestPos) || elapsed >= DEPOSIT_TIMEOUT_TICKS) {
            InventoryHelper.insertItems(world, chestPos, items)
            heldItems.remove(pokemonId)
            restoreOriginalHeldItem(pokemonEntity, pokemonId)

            // Happy villager particles on deposit
            val x = pokemonEntity.x
            val y = pokemonEntity.y
            val h = pokemonEntity.height.toDouble()
            val z = pokemonEntity.z
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y + h, z, 12, 0.4, 0.3, 0.4, 0.03)

            // Log the deposit
            for (item in items) {
                LogManager.log(
                    origin, world.time,
                    pokemonEntity.pokemon.species.name,
                    "Sorted",
                    "${item.name.string} x${item.count}",
                    LogManager.Rarity.COMMON
                )
            }

            Cobblebase.LOGGER.info("[Gatherer] ${pokemonEntity.pokemon.species.name} deposited items at $chestPos")
            depositStartTime.remove(pokemonId)
        }
    }

    /**
     * Restores the Pokemon's original held item (or clears it) after depositing gathered items.
     */
    private fun restoreOriginalHeldItem(pokemonEntity: PokemonEntity, pokemonId: UUID) {
        originalHeldItem.remove(pokemonId)
        // Remove the floating visual item
        val visual = visualItems.remove(pokemonId)
        if (visual != null && visual.isAlive) visual.discard()
    }
}

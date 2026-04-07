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
import notlown.cobblebase.core.ItemOriginHelper
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
    private val pickupCooldown = mutableMapOf<UUID, Long>()      // prevents dupe loop after deposit timeout
    private val targetItem = mutableMapOf<UUID, Int>()          // entity ID of target ItemEntity
    private val targetSetTime = mutableMapOf<UUID, Long>()
    private val lastPickupTime = mutableMapOf<UUID, Long>()
    private val lastSearchTime = mutableMapOf<UUID, Long>()

    private const val NAV_TIMEOUT_TICKS = 200L        // 10 seconds - auto-pickup if can't reach
    private const val SEARCH_INTERVAL_TICKS = 20L    // 1 second between scans
    // Gatherer cooldown comes from skill.cooldownSeconds (JSON/admin overrides)

    /**
     * Returns search radius based on proficiency (1-5).
     * Prof 1 = 5, Prof 2 = 7, Prof 3 = 8, Prof 4 = 10, Prof 5 = 12
     */
    private fun getRadiusForProficiency(proficiency: Int): Double {
        // Uses the config jobSearchRadius as max, capped at 24
        // Gatherer radius should be >= other job radii so it can reach all dropped items
        return CobblebaseConfig.jobSearchRadius.toDouble().coerceAtMost(24.0)
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

            if (NavigationHelper.isPokemonAtPosition(pokemonEntity, itemEntity.blockPos, 1.5) || timedOut) {
                pickupItem(world, itemEntity, pokemonEntity, pokemonId, origin)
                targetItem.remove(pokemonId)
                targetSetTime.remove(pokemonId)
                lastPickupTime[pokemonId] = now
            }
            return
        }

        // Phase 3: cooldown between pickups
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(skill.cooldownSeconds, skillEntry.proficiency)
        val lastPickup = lastPickupTime[pokemonId] ?: 0L
        if (now - lastPickup < cooldownTicks) {
            if (now % 60 == 0L) {
                SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
            }
            return
        }

        // Pickup cooldown after deposit timeout (prevents dupe loop when stuck)
        val pickupCd = pickupCooldown[pokemonId] ?: 0L
        if (now < pickupCd) return

        // Phase 4: search for dropped items (throttled to avoid lag)
        val lastSearch = lastSearchTime[pokemonId] ?: 0L
        if (now - lastSearch < SEARCH_INTERVAL_TICKS) return
        lastSearchTime[pokemonId] = now

        val radius = getRadiusForProficiency(skillEntry.proficiency)
        val found = findNearestDroppedItem(world, pokemonEntity, radius, origin)
        if (found != null) {
            // Claim the item immediately — prevent player pickup to avoid dupe glitch
            found.setPickupDelay(Short.MAX_VALUE.toInt())
            targetItem[pokemonId] = found.id
            targetSetTime[pokemonId] = now
        } else if (now % 100 == 0L) {
            // Debug: log when no items found
            val searchBox = Box.of(pokemonEntity.pos, radius * 2, radius * 2, radius * 2)
            val allItems = world.getEntitiesByClass(ItemEntity::class.java, searchBox) { true }
        }
    }

    /**
     * Finds the OLDEST dropped ItemEntity within the search radius.
     * Prioritizes by age (oldest first) so far-away items don't get starved
     * by constantly spawning nearby items.
     *
     * Only picks up items that belong to this Gatherer's Pasture Block (or untagged items).
     */
    private fun findNearestDroppedItem(world: ServerWorld, pokemonEntity: PokemonEntity, radius: Double, pastureOrigin: BlockPos): ItemEntity? {
        val searchBox = Box.of(pokemonEntity.pos, radius * 2, radius * 2, radius * 2)
        // Collect all visual item entity IDs across all gatherers (avoid picking up own/other visuals)
        val visualIds = visualItems.values.map { it.id }.toSet()
        val items = world.getEntitiesByClass(ItemEntity::class.java, searchBox) { entity ->
            entity.isAlive && !entity.stack.isEmpty
                && entity.id !in visualIds // skip floating visual items above gatherer heads
                && !entity.hasNoGravity() // visual items have no gravity — extra safety filter
                && belongsToOrAllowed(entity.stack, pastureOrigin)
        }
        // Pick oldest item (highest age = been on ground longest)
        return items.maxByOrNull { it.age }
    }

    /**
     * Checks if an item should be picked up by this Gatherer.
     * Uses the original v1.3.7 belongsTo logic + optional player drop setting.
     */
    private fun belongsToOrAllowed(stack: ItemStack, pastureOrigin: BlockPos): Boolean {
        val origin = ItemOriginHelper.getOrigin(stack)
        if (origin == null) {
            // Untagged = player/mob drop — respect setting
            return CobblebaseConfig.gathererPickupPlayerDrops
        }
        // Tagged = from a Cobblebase job — check if same pasture
        return origin == pastureOrigin
    }

    /**
     * Picks up a dropped item: removes the entity, stores the stack in state.
     * Spawns pickup particles.
     * Only picks up nearby items that belong to this Pasture Block (or are untagged).
     */
    private fun pickupItem(world: ServerWorld, itemEntity: ItemEntity, pokemonEntity: PokemonEntity, pokemonId: UUID, pastureOrigin: BlockPos) {
        val stack = itemEntity.stack.copy()
        if (stack.isEmpty) return

        // Remove the target item
        itemEntity.discard()

        // Also grab ALL other items within 3 blocks (batch pickup) — only from own pasture
        // Skip visual items (no gravity, max pickup delay) to prevent dupe loop
        val visualIds = visualItems.values.map { it.id }.toSet()
        val nearbyItems = world.getEntitiesByClass(ItemEntity::class.java,
            net.minecraft.util.math.Box.of(pokemonEntity.pos, 6.0, 4.0, 6.0)
        ) {
            it.isAlive && !it.stack.isEmpty && it.id != itemEntity.id
                && it.id !in visualIds && !it.hasNoGravity()
                && ItemOriginHelper.belongsTo(it.stack, pastureOrigin)
        }

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

        // Play cry on pickup (no markJobSuccess — that fires only on real deposit)
        SkillEffects.playCryOnly(world, pokemonEntity)
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
        val atChest = NavigationHelper.isPokemonAtPosition(pokemonEntity, chestPos)
        val timedOut = elapsed >= DEPOSIT_TIMEOUT_TICKS

        if (atChest) {
            // Actually reached the chest — insert items
            InventoryHelper.insertItems(world, chestPos, items)
            heldItems.remove(pokemonId)
            restoreOriginalHeldItem(pokemonEntity, pokemonId)

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
            // Mark as successful job action (only on real chest reach)
            notlown.cobblebase.core.BaseManager.markJobSuccess(pokemonId, world.time)
            depositStartTime.remove(pokemonId)
        } else if (timedOut) {
            // Couldn't reach chest in 10s — drop items on ground (no fake success)
            // Tag with pasture origin so OTHER gatherers can pick them up, but apply cooldown to THIS gatherer
            InventoryHelper.dropItems(world, pokemonEntity.blockPos, items, origin)
            heldItems.remove(pokemonId)
            restoreOriginalHeldItem(pokemonEntity, pokemonId)
            depositStartTime.remove(pokemonId)
            // Cooldown: don't pickup new items for 30 seconds (prevents dupe loop)
            pickupCooldown[pokemonId] = world.time + 600L
            Cobblebase.log("[Gatherer] ${pokemonEntity.pokemon.species.name} deposit timeout — dropped items (couldn't reach chest), 30s cooldown")
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

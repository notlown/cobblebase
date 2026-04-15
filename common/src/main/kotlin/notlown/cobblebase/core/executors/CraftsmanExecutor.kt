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
 * Craftsman executor: takes materials from nearby chests, crafts items using vanilla recipes.
 *
 * State machine:
 *   IDLE -> GATHERING -> CRAFTING -> DEPOSITING -> GATHERING (repeat)
 *
 * The Craftsman:
 * 1. Has an active project (recipe) selected by the player in the Workshop tab
 * 2. Scans nearby chests for required materials, takes one item per gather cycle
 * 3. When all materials are collected, waits for crafting cooldown
 * 4. Produces the output item and deposits it into a nearby chest
 */
object CraftsmanExecutor : SkillExecutor {

    // Per-pokemon state (not persisted — reconstructed from WorkshopManager)
    private val targetChest = mutableMapOf<UUID, BlockPos>()
    private val lastGatherTick = mutableMapOf<UUID, Long>()

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

        // Only check every second (20 ticks)
        if (now % 20L != 0L) return

        val project = WorkshopManager.getProject(pokemonId)
        if (project == null) {
            // No project selected — idle
            if (now % 60 == 0L) SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
            return
        }

        when (project.phase) {
            WorkshopManager.Phase.IDLE -> {
                WorkshopManager.setPhase(pokemonId, WorkshopManager.Phase.GATHERING, now)
            }

            WorkshopManager.Phase.GATHERING -> tickGathering(world, origin, pokemonEntity, skill, skillEntry, project, pokemonId, now)
            WorkshopManager.Phase.CRAFTING -> tickCrafting(world, origin, pokemonEntity, skill, skillEntry, project, pokemonId, now)
            WorkshopManager.Phase.DEPOSITING -> tickDepositing(world, origin, pokemonEntity, skill, skillEntry, project, pokemonId, now)
        }
    }

    private fun tickGathering(
        world: ServerWorld, origin: BlockPos, pokemonEntity: PokemonEntity,
        skill: SkillDef, skillEntry: SkillEntry,
        project: WorkshopManager.WorkshopProject, pokemonId: UUID, now: Long
    ) {
        val recipe = RecipeHelper.getRecipeById(world, project.recipeId)
        if (recipe == null) {
            // Recipe no longer exists — clear project
            WorkshopManager.clearProject(pokemonId)
            return
        }

        val required = RecipeHelper.getRequiredMaterials(recipe)
        val needed = mutableMapOf<String, Int>()
        for ((item, count) in required) {
            val itemId = Registries.ITEM.getId(item).toString()
            val gathered = project.gatheredItems[itemId] ?: 0
            val remaining = count - gathered
            if (remaining > 0) needed[itemId] = remaining
        }

        // All materials gathered — move to crafting
        if (needed.isEmpty()) {
            WorkshopManager.setPhase(pokemonId, WorkshopManager.Phase.CRAFTING, now)
            SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
            return
        }

        // Gather cooldown: higher prof = faster gathering
        val gatherCooldown = getGatherCooldownTicks(skillEntry.proficiency)
        val lastGather = lastGatherTick[pokemonId] ?: 0L
        if (now - lastGather < gatherCooldown) {
            SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
            return
        }

        // Try to take the next needed item from any nearby chest
        for ((itemId, _) in needed) {
            val chestPos = InventoryHelper.findContainerWithItem(world, origin, skill.searchRadius, itemId)
            if (chestPos != null) {
                // Navigate toward the chest
                NavigationHelper.navigateTo(pokemonEntity, chestPos, getSpeedForProficiency(skillEntry.proficiency))

                // Extract one item
                val extracted = InventoryHelper.extractItem(world, chestPos, itemId, 1)
                if (!extracted.isEmpty) {
                    WorkshopManager.addGatheredItem(pokemonId, itemId, extracted.count)
                    lastGatherTick[pokemonId] = now
                    SkillEffects.playWorking(world, pokemonEntity, "harvest")
                    return
                }
            }
        }

        // No chests have what we need — keep waiting, re-scan next cycle
        if (now % 100 == 0L) SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
    }

    private fun tickCrafting(
        world: ServerWorld, origin: BlockPos, pokemonEntity: PokemonEntity,
        skill: SkillDef, skillEntry: SkillEntry,
        project: WorkshopManager.WorkshopProject, pokemonId: UUID, now: Long
    ) {
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(skill.cooldownSeconds, skillEntry.proficiency)
        val elapsed = now - project.phaseStartTick

        if (elapsed < cooldownTicks) {
            // Still crafting — show working effect
            if (now % 40 == 0L) SkillEffects.playWorking(world, pokemonEntity, "harvest")
            return
        }

        // Crafting complete — create output
        val recipe = RecipeHelper.getRecipeById(world, project.recipeId) ?: run {
            WorkshopManager.clearProject(pokemonId)
            return
        }

        val output = recipe.getResult(world.registryManager)
        if (output.isEmpty) {
            WorkshopManager.clearProject(pokemonId)
            return
        }

        // Store output for depositing
        craftedOutput[pokemonId] = output.copy()
        WorkshopManager.resetGathered(pokemonId)
        WorkshopManager.setPhase(pokemonId, WorkshopManager.Phase.DEPOSITING, now)
        SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)

        // Log
        LogManager.log(
            origin, now,
            pokemonEntity.pokemon.species.name,
            skill.name,
            "${output.name.string} x${output.count}",
            LogManager.Rarity.COMMON
        )
    }

    private val craftedOutput = mutableMapOf<UUID, ItemStack>()

    private fun tickDepositing(
        world: ServerWorld, origin: BlockPos, pokemonEntity: PokemonEntity,
        skill: SkillDef, skillEntry: SkillEntry,
        project: WorkshopManager.WorkshopProject, pokemonId: UUID, now: Long
    ) {
        val output = craftedOutput[pokemonId]
        if (output == null || output.isEmpty) {
            // Nothing to deposit — back to gathering
            WorkshopManager.setPhase(pokemonId, WorkshopManager.Phase.GATHERING, now)
            return
        }

        val containerPos = InventoryHelper.findBestContainer(world, origin, skill.searchRadius, listOf(output))
        if (containerPos != null) {
            InventoryHelper.insertItems(world, containerPos, listOf(output))
        } else {
            // No chest — drop near pasture
            InventoryHelper.dropItems(world, origin, listOf(output), origin)
        }

        craftedOutput.remove(pokemonId)
        // Back to gathering for the next cycle
        WorkshopManager.setPhase(pokemonId, WorkshopManager.Phase.GATHERING, now)
    }

    /**
     * Gather cooldown per item extraction (in ticks).
     * Higher proficiency = faster gathering.
     */
    private fun getGatherCooldownTicks(proficiency: Int): Long {
        return when (proficiency) {
            1 -> 60L    // 3 seconds
            2 -> 50L    // 2.5 seconds
            3 -> 40L    // 2 seconds
            4 -> 30L    // 1.5 seconds
            5 -> 20L    // 1 second
            else -> 40L
        }
    }

    private fun getSpeedForProficiency(proficiency: Int): Double {
        return when (proficiency) {
            1 -> 0.8
            2 -> 0.9
            3 -> 1.0
            4 -> 1.1
            5 -> 1.2
            else -> 1.0
        }
    }
}

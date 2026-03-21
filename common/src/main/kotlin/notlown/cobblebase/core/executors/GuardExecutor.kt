package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.item.ItemStack
import net.minecraft.loot.context.LootContextParameterSet
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.loot.context.LootContextTypes
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.CobblebaseConfig
import notlown.cobblebase.core.effects.SkillEffects
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import java.util.UUID

/**
 * Guard executor: finds wild Pokemon, navigates to them, repels them.
 * Earns XP when under level cap, generates loot when at level cap.
 */
object GuardExecutor : SkillExecutor {

    private val lastGuardTime = mutableMapOf<UUID, Long>()
    private val heldItems = mutableMapOf<UUID, List<ItemStack>>()
    private const val CANDY_DROP_CHANCE = 30 // percent
    private const val XP_PER_REPEL = 50

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
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(skill.cooldownSeconds, skillEntry.proficiency)
        val items = heldItems[pokemonId]

        // If holding loot items, deposit first
        if (!items.isNullOrEmpty()) {
            depositItems(world, origin, pokemonEntity, pokemonId)
            return
        }

        // Cooldown check
        val lastTime = lastGuardTime[pokemonId] ?: 0L
        if (now - lastTime < cooldownTicks) {
            if (world.time % 20 == 0L) SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
            return
        }

        // Find nearest wild Pokemon within search radius
        val guardRadius = skill.searchRadius.toDouble()
        val searchBox = Box.of(origin.toCenterPos(), guardRadius * 2, guardRadius * 2, guardRadius * 2)
        val wildMons = world.getEntitiesByClass(PokemonEntity::class.java, searchBox) { entity ->
            entity != pokemonEntity && entity.pokemon.isWild() && entity.isAlive
        }

        if (wildMons.isEmpty()) return

        val targetMon = wildMons.minByOrNull { it.squaredDistanceTo(pokemonEntity) } ?: return

        if (isNearPosition(pokemonEntity, targetMon.blockPos, 3.0)) {
            lastGuardTime[pokemonId] = now

            val pokemon = pokemonEntity.pokemon
            if (pokemon.canLevelUpFurther()) {
                // Under level cap - give XP directly
                val xp = if (skill.xpReward > 0) skill.xpReward else XP_PER_REPEL
                pokemon.addExperience(CobblebaseExperienceSource, xp)
            } else {
                // At level cap - chance to generate loot
                if (world.random.nextInt(100) < CANDY_DROP_CHANCE) {
                    val lootTableId = skill.lootTable
                    if (lootTableId != null) {
                        val lootParams = LootContextParameterSet.Builder(world)
                            .add(LootContextParameters.ORIGIN, pokemonEntity.pos)
                            .addOptional(LootContextParameters.THIS_ENTITY, pokemonEntity)
                            .build(LootContextTypes.CHEST)

                        val lootTableKey = RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(Cobblebase.MODID, lootTableId.removePrefix("cobblebase:")))
                        val lootTable = world.server.reloadableRegistries.getLootTable(lootTableKey)
                        val drops = lootTable.generateLoot(lootParams)
                        if (drops.isNotEmpty()) {
                            heldItems[pokemonId] = drops
                        }
                    }
                }
            }

            // Repel the wild Pokemon
            targetMon.discard()
            SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)
        } else {
            navigateTo(pokemonEntity, targetMon.blockPos)
        }
    }

    private fun depositItems(world: World, origin: BlockPos, pokemonEntity: PokemonEntity, pokemonId: UUID) {
        val items = heldItems[pokemonId] ?: return
        val chestPos = InventoryHelper.findBestContainer(world, pokemonEntity.blockPos, 10, items) ?: return

        navigateTo(pokemonEntity, chestPos)
        if (isNearPosition(pokemonEntity, chestPos, 2.0)) {
            InventoryHelper.insertItems(world, chestPos, items)
            heldItems.remove(pokemonId)
        }
    }


    private fun navigateTo(pokemonEntity: PokemonEntity, target: BlockPos) {
        pokemonEntity.navigation.startMovingTo(target.x + 0.5, target.y.toDouble(), target.z + 0.5, 1.0)
    }

    private fun isNearPosition(pokemonEntity: PokemonEntity, pos: BlockPos, range: Double = 2.0): Boolean {
        return pokemonEntity.blockPos.getSquaredDistance(pos) <= range * range
    }
}

/**
 * Custom ExperienceSource for Cobblebase guard duty XP.
 */
object CobblebaseExperienceSource : com.cobblemon.mod.common.api.pokemon.experience.ExperienceSource {
    override fun isSidemod() = true
}

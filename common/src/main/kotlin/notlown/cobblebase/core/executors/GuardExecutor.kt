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
import notlown.cobblebase.core.LogManager
import notlown.cobblebase.core.effects.SkillEffects
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import notlown.cobblebase.core.NavigationHelper
import java.util.UUID

/**
 * Guard executor: finds wild Pokemon, navigates to them, repels them.
 * Earns XP when under level cap, generates loot when at level cap.
 */
object GuardExecutor : SkillExecutor {

    private val lastGuardTime = mutableMapOf<UUID, Long>()
    private val lastScanTime = mutableMapOf<UUID, Long>()
    private val heldItems = mutableMapOf<UUID, List<ItemStack>>()
    private const val CANDY_DROP_CHANCE = 30 // percent
    private const val XP_PER_REPEL = 50
    private const val SCAN_INTERVAL_TICKS = 20L  // throttle wild-Pokemon AABB scan when no target is found
    private const val STALE_TTL_TICKS = 1200L

    /** Drops per-Pokemon state for guards that have stopped ticking. Held items stay because
     *  the deposit logic preserves them across ticks; if the guard is gone, the items will be
     *  reclaimed by a Gatherer on the same pasture. */
    fun cleanupStale(now: Long) {
        val stale = lastScanTime.entries.filter { now - it.value > STALE_TTL_TICKS }.map { it.key }
        for (id in stale) {
            lastScanTime.remove(id)
            lastGuardTime.remove(id)
        }
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
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(skill.cooldownSeconds, skillEntry.proficiency, skill.id)
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

        // Even with cooldown elapsed, don't AABB-scan wild Pokemon every tick when none are
        // in range — throttle to once per second.
        val lastScan = lastScanTime[pokemonId] ?: 0L
        if (now - lastScan < SCAN_INTERVAL_TICKS) return
        lastScanTime[pokemonId] = now

        // Find nearest wild Pokemon within search radius
        val guardRadius = skill.searchRadius.toDouble()
        val searchBox = Box.of(origin.toCenterPos(), guardRadius * 2, guardRadius * 2, guardRadius * 2)
        val wildMons = world.getEntitiesByClass(PokemonEntity::class.java, searchBox) { entity ->
            entity != pokemonEntity && entity.pokemon.isWild() && entity.isAlive
        }

        if (wildMons.isEmpty()) return

        val targetMon = wildMons.minByOrNull { it.squaredDistanceTo(pokemonEntity) } ?: return

        if (NavigationHelper.isPokemonAtPosition(pokemonEntity, targetMon.blockPos, 3.0)) {
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
                        val drops = notlown.cobblebase.core.LootHelper.generateLoot(
                            lootTableId, world, pokemonEntity.blockPos, pokemonEntity
                        )
                        if (drops.isNotEmpty()) {
                            heldItems[pokemonId] = drops
                        }
                    }
                }
            }

            // Repel the wild Pokemon
            val targetName = targetMon.pokemon.species.name
            targetMon.discard()
            SkillEffects.playSuccess(world, pokemonEntity, skill.effectType, origin)

            // Log the guard action
            LogManager.log(
                origin, world.time,
                pokemonEntity.pokemon.species.name,
                "Repelled",
                targetName,
                LogManager.Rarity.COMMON
            )

            // Log loot drops if any
            val guardDrops = heldItems[pokemonId]
            if (!guardDrops.isNullOrEmpty()) {
                for (item in guardDrops) {
                    LogManager.log(
                        origin, world.time,
                        pokemonEntity.pokemon.species.name,
                        "Guard Loot",
                        "${item.name.string} x${item.count}",
                        LogManager.Rarity.UNCOMMON
                    )
                }
            }
        } else {
            NavigationHelper.navigateTo(pokemonEntity, targetMon.blockPos)
        }
    }

    private fun depositItems(world: World, origin: BlockPos, pokemonEntity: PokemonEntity, pokemonId: UUID) {
        val items = heldItems[pokemonId] ?: return
        InventoryHelper.dropItems(world, pokemonEntity.blockPos, items, origin)
        heldItems.remove(pokemonId)
    }



}

/**
 * Custom ExperienceSource for Cobblebase guard duty XP.
 */
object CobblebaseExperienceSource : com.cobblemon.mod.common.api.pokemon.experience.ExperienceSource {
    override fun isSidemod() = true
}

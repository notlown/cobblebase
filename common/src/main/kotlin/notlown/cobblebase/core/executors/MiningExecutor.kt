package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
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
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import notlown.cobblebase.core.NavigationHelper
import notlown.cobblebase.core.LogManager
import notlown.cobblebase.core.effects.SkillEffects
import java.util.UUID

/**
 * Mining: Pokemon walks to a nearby position, digs, and finds underground items.
 * Cooldown-based loot generation (like FinderExecutor), not block harvesting.
 * Produces ores, tumblestones, fossils, and gems based on proficiency tier.
 *
 * Base cooldown: 30 seconds (from skill JSON), reduced by proficiency.
 * Pokemon navigates to a random spot, plays digging animation with
 * stone/gravel particles, then generates loot from mining loot tables.
 */
object MiningExecutor : SkillExecutor {

    private val lastMineTime = mutableMapOf<UUID, Long>()
    private val heldItems = mutableMapOf<UUID, List<ItemStack>>()
    private val digTarget = mutableMapOf<UUID, BlockPos>()
    private val digStartTime = mutableMapOf<UUID, Long>()
    private val celebrationUntil = mutableMapOf<UUID, Long>() // short break after finding loot

    private const val DIG_DURATION_TICKS = 60L // 3 seconds of digging animation before loot roll
    private const val NAV_TIMEOUT_TICKS = 100L // 5 seconds max navigation
    private const val CELEBRATION_TICKS = 100L // 5-second break after finding loot

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
        val items = heldItems[pokemonId]

        // Phase 1: Drop held items on ground (Gatherer will sort into chests)
        if (!items.isNullOrEmpty()) {
            InventoryHelper.dropItems(world, pokemonEntity.blockPos, items, origin)
            heldItems.remove(pokemonId)
            return
        }

        // Phase 2: Currently digging at a target position
        val target = digTarget[pokemonId]
        if (target != null) {
            val digStart = digStartTime[pokemonId] ?: now

            // Navigate towards dig spot
            NavigationHelper.navigateTo(pokemonEntity, target, getSpeedForProficiency(skillEntry.proficiency))

            val atPosition = NavigationHelper.isPokemonAtPosition(pokemonEntity, target, 3.0)
            val navTimedOut = now - digStart >= NAV_TIMEOUT_TICKS

            if (atPosition || navTimedOut) {
                // Play digging particles while digging
                val ticksDigging = now - digStart - (if (navTimedOut) NAV_TIMEOUT_TICKS else 0L)
                if (ticksDigging < DIG_DURATION_TICKS) {
                    // Digging in progress — spawn stone/gravel particles
                    if (now % 10 == 0L) {
                        playDiggingEffects(world, pokemonEntity)
                    }
                    return
                }

                // Digging complete — generate 1 loot drop, then wait full cooldown
                generateMiningLoot(world, origin, pokemonEntity, pokemonId, skillEntry, skill)
                digTarget.remove(pokemonId)
                digStartTime.remove(pokemonId)
                lastMineTime[pokemonId] = now // Always reset cooldown — no rapid re-rolls
            }
            return
        }

        // Phase 3: Cooldown check — use standard formula
        val cooldownTicks = CobblebaseConfig.getEffectiveCooldownTicks(skill.cooldownSeconds, skillEntry.proficiency)
        val lastTime = lastMineTime[pokemonId] ?: now.also { lastMineTime[pokemonId] = now }
        if (now - lastTime < cooldownTicks) {
            // Play continuous digging animation + particles while waiting for cooldown
            if (now % 40 == 0L) {
                playDiggingEffects(world, pokemonEntity)
            }
            return
        }

        // Phase 4: Pick a random nearby position to "mine" at
        val radius = skill.searchRadius.coerceAtLeast(5)
        val offsetX = world.random.nextInt(radius * 2 + 1) - radius
        val offsetZ = world.random.nextInt(radius * 2 + 1) - radius
        val minePos = origin.add(offsetX, 0, offsetZ)

        digTarget[pokemonId] = minePos
        digStartTime[pokemonId] = now
    }

    /**
     * Returns movement speed based on proficiency (1-5).
     * Prof 1 = 0.4 (slow), Prof 5 = 1.2 (fast)
     */
    private fun getSpeedForProficiency(proficiency: Int): Double {
        return 1.0 // Use standard navigation speed
    }

    /**
     * Plays digging animation and spawns stone/gravel particles.
     */
    private fun playDiggingEffects(world: ServerWorld, pokemonEntity: PokemonEntity) {
        val x = pokemonEntity.x
        val y = pokemonEntity.y
        val z = pokemonEntity.z

        // Only particles during digging — NO animation/cry (would spam)
        // The cry plays only on successful find via playSuccess
        world.spawnParticles(ParticleTypes.ASH, x, y + 0.3, z, 12, 0.4, 0.1, 0.4, 0.02)
        world.spawnParticles(ParticleTypes.SMOKE, x, y + 0.2, z, 6, 0.3, 0.1, 0.3, 0.01)
        world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.5, z, 3, 0.2, 0.2, 0.2, 0.005)
    }

    /**
     * Generates mining loot based on proficiency tier.
     * Uses the same rarity distribution as FinderExecutor.
     */
    private fun generateMiningLoot(
        world: ServerWorld,
        origin: BlockPos,
        pokemonEntity: PokemonEntity,
        pokemonId: UUID,
        skillEntry: SkillEntry,
        skill: SkillDef
    ): Boolean {
        try {
            val lootTableName = pickLootTable(world, skillEntry.proficiency)
            val lootTableKey = RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(lootTableName))
            val lootTable = world.server.reloadableRegistries.getLootTable(lootTableKey)

            val lootParams = LootContextParameterSet.Builder(world)
                .add(LootContextParameters.ORIGIN, pokemonEntity.pos)
                .addOptional(LootContextParameters.THIS_ENTITY, pokemonEntity)
                .build(LootContextTypes.CHEST)

            val drops = lootTable.generateLoot(lootParams)

            if (drops.isNotEmpty()) {
                heldItems[pokemonId] = drops
                SkillEffects.playSuccess(world, pokemonEntity, skill.effectType)

                // Log to activity log
                val rarity = when {
                    lootTableName.endsWith("_ultra_rare") -> LogManager.Rarity.ULTRA_RARE
                    lootTableName.endsWith("_rare") -> LogManager.Rarity.RARE
                    lootTableName.endsWith("_uncommon") -> LogManager.Rarity.UNCOMMON
                    else -> LogManager.Rarity.COMMON
                }
                for (drop in drops) {
                    LogManager.log(
                        origin, world.time,
                        pokemonEntity.pokemon.species.name,
                        "Mined",
                        "${drop.name.string} x${drop.count}",
                        rarity
                    )
                }
                return true
            }
            return false
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Mining] Error generating loot: ${e.message}")
            return false
        }
    }

    /**
     * 4 tiers: Common, Uncommon, Rare, Ultra Rare.
     * Mining uses more common-heavy distribution with very low ultra-rare chance.
     * 5-minute cooldown means each roll matters more.
     *
     * Prof 1: Common 90%, Uncommon 8%, Rare 1.5%, Ultra Rare 0.5%
     * Prof 2: Common 82%, Uncommon 13%, Rare 4%, Ultra Rare 1%
     * Prof 3: Common 70%, Uncommon 20%, Rare 8%, Ultra Rare 2%
     * Prof 4: Common 55%, Uncommon 28%, Rare 13%, Ultra Rare 4%
     * Prof 5: Common 40%, Uncommon 33%, Rare 20%, Ultra Rare 7%
     */
    private fun pickLootTable(world: World, proficiency: Int): String {
        val roll = world.random.nextInt(1000) // use 1000 for more precision

        val ultraRare = when (proficiency) { 1->5; 2->10; 3->20; 4->40; else->70 }
        val rare = when (proficiency) { 1->15; 2->40; 3->80; 4->130; else->200 }
        val uncommon = when (proficiency) { 1->80; 2->130; 3->200; 4->280; else->330 }

        return when {
            roll < ultraRare -> "cobblebase:mining_ultra_rare"
            roll < ultraRare + rare -> "cobblebase:mining_rare"
            roll < ultraRare + rare + uncommon -> "cobblebase:mining_uncommon"
            else -> "cobblebase:mining_common"
        }
    }
}

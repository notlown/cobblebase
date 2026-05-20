package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.Natures
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.IVs
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.Cobblebase
import notlown.cobblebase.core.CobbreedingBridge
import notlown.cobblebase.core.LogManager
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import notlown.cobblebase.core.SkillRegistry
import notlown.cobblebase.core.effects.SkillEffects
import java.util.UUID
import kotlin.random.Random

/**
 * Breeder executor — Ditto-only passive egg producer.
 *
 * One Ditto in a pasture autonomously pairs with a random female partner in that same
 * pasture and produces a Cobbreeding-format egg on a per-Ditto cooldown. Higher proficiency
 * = faster cycle. A shiny Ditto multiplies the offspring's shiny roll (default ×2, tunable).
 *
 * Why this exists: Cobbreeding's native breeding requires the player to interact with the
 * pasture in specific ways (or activate breeding mode). The Breeder skill makes egg
 * production a passive Cobblebase mechanic so that the existing [EggHatcherExecutor] always
 * has something to work on.
 *
 * Soft-dep: requires Cobbreeding for the egg ItemStack format. Without it, the executor
 * no-ops (skill stays selectable in GUI, just does nothing).
 *
 * Compatibility rules (mirrors Cobbreeding's breeding eligibility, simplified):
 *   - Partner must be FEMALE (genderless partners excluded — Ditto-on-genderless is
 *     fringe and Cobbreeding's rules make it pasture-config-dependent; not worth
 *     replicating in MVP).
 *   - Partner's egg groups must NOT contain UNDISCOVERED (= no legendaries / mythicals).
 *   - Multiple Dittos in the same pasture each maintain their own cooldown and can
 *     produce eggs independently — limited only by [CobbreedingBridge.getPastureInventorySize].
 */
object BreederExecutor : SkillExecutor {

    /**
     * When this Ditto's *next* breeding attempt becomes eligible. Random within a
     * proficiency-scaled range of Cobbreeding's `[min, max]BreedingTimeInTicks`.
     */
    private val nextBreedTime = mutableMapOf<UUID, Long>()

    /** Heartbeat — last tick we saw this Ditto. Drives stale-cleanup. */
    private val lastScanTime = mutableMapOf<UUID, Long>()

    private const val STALE_TTL_TICKS = 1200L

    /**
     * Called by BaseManager every 60s. Drops state for Dittos that haven't ticked recently
     * (despawned / chunk-unloaded). Same pattern as [ProducerExecutor.cleanupStale].
     */
    fun cleanupStale(now: Long) {
        val stale = lastScanTime.entries.filter { now - it.value > STALE_TTL_TICKS }.map { it.key }
        for (id in stale) {
            lastScanTime.remove(id)
            nextBreedTime.remove(id)
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

        // Hard gate: species must be Ditto. (Form-aware name match isn't needed —
        // there's no "Ditto Alolan" etc., and we want this strictly limited to the
        // canonical species.)
        if (pokemonEntity.pokemon.species.name.lowercase() != "ditto") return

        // Soft gate: Cobbreeding must be installed to package the egg item.
        if (!CobbreedingBridge.isLoaded) return

        val pokemonId = pokemonEntity.pokemon.uuid
        val now = world.time
        lastScanTime[pokemonId] = now

        // Cooldown gate. First tick: initialize the timer so the Ditto doesn't
        // immediately produce an egg the moment it spawns into a pasture.
        val nextTime = nextBreedTime[pokemonId]
        if (nextTime == null) {
            nextBreedTime[pokemonId] = now + nextCooldown(skill, skillEntry.proficiency)
            return
        }
        if (now < nextTime) {
            // Periodic working particles (same effect type as EggHatcher → hearts).
            if (now % 40L == 0L) SkillEffects.playWorking(world, pokemonEntity, skill.effectType)
            return
        }

        // Capacity check — don't burn the cooldown if there's nowhere to put the egg.
        if (CobbreedingBridge.countEmptyEggSlots(world, origin) <= 0) {
            // Re-poll in 5s; the player might collect an egg.
            nextBreedTime[pokemonId] = now + 100L
            return
        }

        // Find a compatible female partner in the same pasture.
        val partner = findFemalePartner(world, origin, pokemonId) ?: run {
            // Re-check in 5s; the partner might get added.
            nextBreedTime[pokemonId] = now + 100L
            return
        }

        val dittoShiny = pokemonEntity.pokemon.shiny
        val props = rollChildProperties(partner, dittoShiny, skill)
        val egg = CobbreedingBridge.createEgg(props, timer = null)
        if (egg == null) {
            Cobblebase.LOGGER.warn("[Breeder] createEgg returned null for partner=${partner.species.name}; aborting cycle")
            nextBreedTime[pokemonId] = now + nextCooldown(skill, skillEntry.proficiency)
            return
        }

        val placedSlot = CobbreedingBridge.placeEggInPasture(world, origin, egg)
        if (placedSlot < 0) {
            // Race condition: slot was free when we checked but full now. Try again soon.
            nextBreedTime[pokemonId] = now + 100L
            return
        }

        // Success. Roll the next cooldown.
        nextBreedTime[pokemonId] = now + nextCooldown(skill, skillEntry.proficiency)

        val partnerName = partner.species.name
        val shinyMark = if (props.shiny == true) " ✨" else ""
        Cobblebase.LOGGER.debug(
            "[Breeder] Ditto (prof ${skillEntry.proficiency}, shiny=$dittoShiny) bred " +
                "$partnerName egg$shinyMark → pasture $origin slot $placedSlot"
        )
        LogManager.log(
            origin = origin,
            worldTime = now,
            pokemonName = "Ditto",
            action = "bred",
            itemName = "$partnerName egg$shinyMark",
            rarity = if (props.shiny == true) LogManager.Rarity.RARE else LogManager.Rarity.UNCOMMON
        )
        SkillEffects.playSuccess(world, pokemonEntity, skill.effectType, origin)
    }

    /**
     * Rolls the next per-Ditto cooldown in ticks. Falls within
     * `[Cobbreeding.minBreedingTicks, Cobbreeding.maxBreedingTicks]` scaled by the
     * proficiency multiplier. Defaults: P1 = 1.0×, P5 = 0.5× (= twice as fast).
     */
    private fun nextCooldown(skill: SkillDef, proficiency: Int): Long {
        val prof = proficiency.coerceIn(1, 5)
        val defaultByProf = mapOf(1 to 1.0, 2 to 0.875, 3 to 0.75, 4 to 0.625, 5 to 0.5)
        val mult = SkillRegistry.getEffectiveTuning(
            skill.id, "prof${prof}Multiplier", defaultByProf.getOrDefault(prof, 1.0)
        )
        val minTicks = (CobbreedingBridge.getMinBreedingTicks() * mult).toLong().coerceAtLeast(20L)
        val maxTicks = (CobbreedingBridge.getMaxBreedingTicks() * mult).toLong().coerceAtLeast(minTicks + 1)
        return Random.nextLong(minTicks, maxTicks + 1)
    }

    /**
     * Walks `pasture.tetheredPokemon` and picks one female partner at random:
     *   - skip the Ditto itself
     *   - skip non-female Pokemon (males/genderless)
     *   - skip species with UNDISCOVERED egg group (legendaries/mythicals)
     *
     * Returns the [Pokemon] object directly (read from the tether's snapshot, no entity
     * resolve required — works even if the partner's entity isn't currently loaded).
     */
    private fun findFemalePartner(world: ServerWorld, origin: BlockPos, dittoId: UUID): Pokemon? {
        val pasture = world.getBlockEntity(origin)
            as? com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity ?: return null
        val candidates = mutableListOf<Pokemon>()
        for (tether in pasture.tetheredPokemon) {
            if (tether.pokemonId == dittoId) continue
            val p = try { tether.getPokemon() } catch (_: Exception) { null } ?: continue
            if (p.gender != Gender.FEMALE) continue
            val eggGroups = p.species.eggGroups
            if (eggGroups.contains(EggGroup.UNDISCOVERED)) continue
            candidates += p
        }
        if (candidates.isEmpty()) return null
        return candidates[Random.nextInt(candidates.size)]
    }

    /**
     * Builds [PokemonProperties] for the offspring egg.
     *
     * Inheritance is intentionally simplified (no held-item mechanics, no Masuda):
     *   - species   = female parent's species (mainline Ditto-breeding rule)
     *   - level     = 1
     *   - gender    = unset → Cobbreeding/Cobblemon rolls at hatch per species ratio
     *   - shiny     = roll 1/8192, doubled if Ditto is shiny (override via shinyDittoBonus)
     *   - ivs       = 3 random stats inherited from the female parent, 3 random 0–31
     *   - nature    = random from [Natures.allNatures]
     *   - ability   = unset → Cobblemon picks species default at hatch
     */
    private fun rollChildProperties(female: Pokemon, dittoShiny: Boolean, skill: SkillDef): PokemonProperties {
        val props = PokemonProperties()
        props.species = female.species.name
        props.level = 1

        // Shiny roll. 1/8192 base, configurable bonus when Ditto is shiny.
        val bonus = SkillRegistry.getEffectiveTuning(skill.id, "shinyDittoBonus", 2.0)
        val multiplier = if (dittoShiny) bonus.coerceAtLeast(1.0) else 1.0
        val odds = (8192.0 / multiplier).toInt().coerceAtLeast(1)
        props.shiny = Random.nextInt(odds) == 0

        // IV inheritance — Destiny-Knot-LITE: 3 random stat slots take the female's value,
        // the other 3 are random 0–31. No power-item logic in MVP.
        val inheritedIvs = IVs.createRandomIVs(0)
        val allStats = listOf(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED)
        val inheritFrom = allStats.shuffled().take(3)
        for (stat in inheritFrom) {
            val parentValue = female.ivs.getOrDefault(stat).coerceIn(0, 31)
            inheritedIvs.set(stat, parentValue)
        }
        props.ivs = inheritedIvs

        // Random nature. `Natures.getRandomNature()` returns a Nature whose name is an
        // Identifier — PokemonProperties wants the path part (e.g. "adamant").
        val nature = Natures.getRandomNature()
        props.nature = nature.name.path

        return props
    }
}

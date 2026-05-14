package notlown.cobblebase.core.executors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import notlown.cobblebase.core.BaseManager
import notlown.cobblebase.core.BlockSupplyMap
import notlown.cobblebase.core.BuilderHelperCoordinator
import notlown.cobblebase.core.SkillDef
import notlown.cobblebase.core.SkillEntry
import notlown.cobblebase.core.SkillExecutor
import notlown.cobblebase.core.SkillRegistry
import notlown.cobblebase.core.SpeciesSkillRegistry

/**
 * Pokemon assigned as `builder_helper` get this executor dispatched. The helper queries
 * the [BuilderHelperCoordinator] each tick to find a needed block it can supply, then
 * routes the work to the appropriate sub-executor:
 *   - PRODUCER role  → [ProducerExecutor] (uses species-specific produce map)
 *   - HARVESTER role → [HarvesterExecutor] (gathers crops/berries; not target-filtered yet)
 *   - MINING role    → [MiningExecutor]   (mines random nearby blocks)
 *
 * Helpers short-circuit when the build is complete or no claim is available — no skill
 * tick happens, no items leak.
 *
 * Note on target-filtering: producer-role helpers naturally produce only their species
 * item, so they're effectively targeted. Mining/Harvester helpers run normally and trust
 * Gatherer to deposit drops in the Builder's chest area. Block-specific mining/harvesting
 * is a Phase 3 feature.
 */
object BuilderHelperExecutor : SkillExecutor {

    private val producerSkill: SkillDef? by lazy { SkillRegistry.get("cobblebase:producer") }
    private val harvesterSkill: SkillDef? by lazy { SkillRegistry.get("cobblebase:harvester") }
    private val miningSkill: SkillDef? by lazy { SkillRegistry.get("cobblebase:mining") }

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

        // Capabilities: which Builder-Helper roles can this Pokemon fulfil?
        val capabilities = computeCapabilities(pokemonEntity)
        if (capabilities.isEmpty()) return  // species can't supply anything → idle

        // Get or assign a claim from the coordinator.
        val claim = BuilderHelperCoordinator.getOrAssignClaim(
            world, origin, pokemonId, capabilities, now
        ) ?: return  // no needed block this helper can supply → idle

        // Dispatch to the executor that does the actual gathering, using its own skill def.
        when (claim.role) {
            is BlockSupplyMap.Role.PRODUCER -> {
                val def = producerSkill ?: return
                ProducerExecutor.tick(world, origin, pokemonEntity, def, skillEntry)
            }
            BlockSupplyMap.Role.HARVESTER -> {
                val def = harvesterSkill ?: return
                HarvesterExecutor.tick(world, origin, pokemonEntity, def, skillEntry)
            }
            BlockSupplyMap.Role.MINING -> {
                val def = miningSkill ?: return
                MiningExecutor.tick(world, origin, pokemonEntity, def, skillEntry)
            }
        }
    }

    /**
     * Determines which Builder-Helper roles this Pokemon's species can fulfil based on its
     * registered species skills + producer entry.
     */
    private fun computeCapabilities(pokemonEntity: PokemonEntity): Set<BlockSupplyMap.Role> {
        val speciesName = BaseManager.resolveSpeciesName(pokemonEntity.pokemon)
        val data = SpeciesSkillRegistry.getSkills(speciesName) ?: return emptySet()
        val roles = mutableSetOf<BlockSupplyMap.Role>()
        for (entry in data.skills) {
            val def = SkillRegistry.get(entry.skillId) ?: continue
            when (def.executor) {
                "producer" -> roles.add(BlockSupplyMap.Role.PRODUCER(speciesName))
                "harvester" -> roles.add(BlockSupplyMap.Role.HARVESTER)
                "mining" -> roles.add(BlockSupplyMap.Role.MINING)
            }
        }
        return roles
    }
}

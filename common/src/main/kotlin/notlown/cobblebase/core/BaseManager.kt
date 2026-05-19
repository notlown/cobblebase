package notlown.cobblebase.core

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.WorldSavePath
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.io.File
import java.util.UUID

object BaseManager {

    private val gson = Gson()
    private val assignments = mutableMapOf<UUID, String?>()
    private var dirty = false
    private var lastSaveTick = 0L
    private val SAVE_INTERVAL = 600L // save every 30 seconds
    private var lastCleanupTick = 0L
    private const val CLEANUP_INTERVAL = 1200L // sweep stale per-Pokemon executor state every 60 seconds

    // Unstuck detection: track last known position + time it changed
    private data class PosRecord(val pos: BlockPos, val changedAt: Long)
    private val lastKnownPos = mutableMapOf<UUID, PosRecord>()
    private const val STUCK_TIMEOUT_TICKS = 160L // 8 seconds without movement = clear nav

    // Throttle the safety-teleport check (sqrt + distance compare + water-submerged check)
    // to once every 10 ticks per Pokemon — running it 20x/sec was pure overhead since the
    // Pokemon can't drift through the 30-block safety threshold in 500 ms.
    private val lastSafetyCheck = mutableMapOf<UUID, Long>()
    private const val SAFETY_CHECK_INTERVAL = 10L

    // Job-based stuck detection: track last successful job action per Pokemon
    private val lastJobSuccess = mutableMapOf<UUID, Long>()

    /** Called by executors when they successfully complete a job action. */
    fun markJobSuccess(pokemonId: UUID, now: Long) {
        lastJobSuccess[pokemonId] = now
    }

    fun getLastJobSuccess(pokemonId: UUID): Long? = lastJobSuccess[pokemonId]

    /**
     * Resolves a form-aware species name for skill lookups.
     * If the Pokemon has a regional form aspect (e.g. "alolan"), returns "species_form" (e.g. "vulpix_alolan").
     * Falls back to just the species name if no form-specific skills are registered.
     */
    fun resolveSpeciesName(pokemon: com.cobblemon.mod.common.pokemon.Pokemon): String {
        // Use resourceIdentifier path (no spaces) for reliable lookups
        // species.name can have spaces ("Iron Valiant") but JSON files use "ironvaliant"
        val baseName = pokemon.species.resourceIdentifier.path
        return SpeciesSkillRegistry.resolveFormName(baseName, pokemon.aspects)
    }

    fun tickPokemon(world: World, pastureOrigin: BlockPos, pokemonEntity: PokemonEntity) {
        val pokemonId: UUID = pokemonEntity.pokemon.uuid
        val speciesName: String = resolveSpeciesName(pokemonEntity.pokemon)
        val now = world.time
        val tickStart = System.nanoTime()

        // Auto-save periodically
        if (dirty && world is ServerWorld && now - lastSaveTick > SAVE_INTERVAL) {
            save(world)
        }

        // Periodic cleanup of stale per-Pokemon state across executors (prevents memory growth
        // for long-running servers where Pokemon get recalled/despawned without an explicit hook).
        //
        // Initialize on first tick instead of running immediately — otherwise on world load the
        // sweep fires for every Pokemon's first tick (lastCleanupTick=0, so the diff is huge),
        // iterating LogManager + 20 executor caches at once and contributing to load-time lag.
        if (lastCleanupTick == 0L) {
            lastCleanupTick = now
        } else if (now - lastCleanupTick > CLEANUP_INTERVAL) {
            lastCleanupTick = now
            notlown.cobblebase.core.executors.MentorExecutor.cleanupStale(now)
            notlown.cobblebase.core.executors.BuffExecutor.cleanupStale(now)
            notlown.cobblebase.core.executors.AuraBoostExecutor.cleanupStale(now)
            notlown.cobblebase.core.executors.HealerExecutor.cleanupStale(now)
            notlown.cobblebase.core.executors.GuardExecutor.cleanupStale(now)
            notlown.cobblebase.core.executors.FurnaceFuelExecutor.cleanupStale(now)
            notlown.cobblebase.core.executors.CauldronFillExecutor.cleanupStale(now)
            // Phase 6: 15 more executors whose per-Pokemon state previously leaked indefinitely
            notlown.cobblebase.core.executors.CraftsmanExecutor.cleanupStale(now)
            notlown.cobblebase.core.executors.ExtinguisherExecutor.cleanupStale(now)
            notlown.cobblebase.core.executors.FinderExecutor.cleanupStale(now)
            notlown.cobblebase.core.executors.FishingExecutor.cleanupStale(now)
            notlown.cobblebase.core.executors.GenericLootExecutor.cleanupStale(now)
            notlown.cobblebase.core.executors.GrowthAuraExecutor.cleanupStale(now)
            notlown.cobblebase.core.executors.HarvesterExecutor.cleanupStale(now)
            notlown.cobblebase.core.executors.IrrigatorExecutor.cleanupStale(now)
            notlown.cobblebase.core.executors.LuckyCharmExecutor.cleanupStale(now)
            notlown.cobblebase.core.executors.MiningExecutor.cleanupStale(now)
            notlown.cobblebase.core.executors.ProducerExecutor.cleanupStale(now)
            notlown.cobblebase.core.executors.RecruiterExecutor.cleanupStale(now)
            notlown.cobblebase.core.executors.ScoutExecutor.cleanupStale(now)
            notlown.cobblebase.core.executors.SupplierExecutor.cleanupStale(now)
            NavigationHelper.cleanupStale(now)
            notlown.cobblebase.core.executors.InventoryHelper.cleanupStale(now)
            notlown.cobblebase.core.executors.EggHatcherExecutor.cleanupStale(now, world)
            // LogManager has a built-in 24h-age cleanup that was never wired to a periodic call
            // — now it runs every minute alongside the executor sweep.
            notlown.cobblebase.core.LogManager.cleanup()
            // Drop pasture-leaf tracking for pasture blocks that no longer exist in the world.
            if (world is net.minecraft.server.world.ServerWorld) {
                notlown.cobblebase.core.PastureLeavesTracker.cleanupOrphanedPastures(world)
            }
            // Drop the per-Pokemon safety-check throttle state for stale UUIDs too.
            val staleSafety = lastSafetyCheck.entries.filter { now - it.value > 1200L }.map { it.key }
            for (id in staleSafety) lastSafetyCheck.remove(id)
            // Sleep-lock mixin holds an anchor Vec3d per sleeping Pokemon. Normal wake-up
            // clears it; this sweep catches anchors orphaned by despawn / chunk unload.
            notlown.cobblebase.core.SleepLockState.cleanupStale()
        }

        // Resize the Pokemon's Cobblemon-managed tether roam box to match the
        // admin's "Pasture Range" slider. Cobblemon defaults to ~16 blocks which
        // would clamp every movement well inside our admin radius. Re-creating
        // the Tethering object is cheap and only happens when the existing min/max
        // don't already match (so steady state = no-op).
        run {
            val tethering = pokemonEntity.tethering ?: return@run
            val pasturePos = tethering.pasturePos
            val r = CobblebaseConfig.jobSearchRadius.coerceAtLeast(1)
            val expectedMin = BlockPos(pasturePos.x - r, pasturePos.y - r, pasturePos.z - r)
            val expectedMax = BlockPos(pasturePos.x + r, pasturePos.y + r, pasturePos.z + r)
            if (tethering.minRoamPos != expectedMin || tethering.maxRoamPos != expectedMax) {
                pokemonEntity.tethering = com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity.Tethering(
                    minRoamPos = expectedMin,
                    maxRoamPos = expectedMax,
                    playerId = tethering.playerId,
                    playerName = tethering.playerName,
                    tetheringId = tethering.tetheringId,
                    pokemonId = tethering.pokemonId,
                    pcId = tethering.pcId,
                    entityId = tethering.entityId,
                    pasturePos = pasturePos
                )
            }
        }

        // Brief stuck detection (8s no movement → clear nav) only for working mons
        val hasJob = assignments[pokemonId] != null
        if (hasJob) {
            val currentPos = pokemonEntity.blockPos
            val record = lastKnownPos[pokemonId]
            if (record == null || record.pos != currentPos) {
                lastKnownPos[pokemonId] = PosRecord(currentPos.toImmutable(), now)
            } else if (now - record.changedAt > STUCK_TIMEOUT_TICKS) {
                NavigationHelper.clearTargets(pokemonEntity)
                lastKnownPos[pokemonId] = PosRecord(currentPos.toImmutable(), now)
            }
        } else {
            lastKnownPos.remove(pokemonId)
            lastJobSuccess.remove(pokemonId)
        }

        // Safety teleport + drown check are throttled — see SAFETY_CHECK_INTERVAL.
        val lastSafety = lastSafetyCheck[pokemonId] ?: 0L
        val runSafety = now - lastSafety >= SAFETY_CHECK_INTERVAL
        if (runSafety) lastSafetyCheck[pokemonId] = now

        // Safety: prevent Pokemon from wandering too far from pasture (causes despawning)
        val isWaterType = NavigationHelper.isWaterType(pokemonEntity.pokemon)
        if (runSafety && CobblebaseConfig.enableSafetyTeleport) {
            val distSq = pokemonEntity.squaredDistanceTo(
                pastureOrigin.x + 0.5, pastureOrigin.y.toDouble(), pastureOrigin.z + 0.5
            )
            // Water-type Pokemon get double the safety radius (they swim farther).
            // Floor at the admin pasture range + 4 buffer blocks so the legit work
            // area is never inside the safety boundary (otherwise mons working at
            // the edge of a 30-block pasture get teleported back constantly).
            val baseSafety = CobblebaseConfig.safetyTeleportDistance.toDouble()
            val adminFloor = CobblebaseConfig.jobSearchRadius + 4.0
            val effectiveSafety = maxOf(baseSafety, adminFloor)
            val maxDist = if (isWaterType) effectiveSafety * 2.0 else effectiveSafety
            if (distSq > maxDist * maxDist) {
                val (sx, sz) = getSpawnOffset(world)
                pokemonEntity.setPosition(pastureOrigin.x + sx, pastureOrigin.y.toDouble(), pastureOrigin.z + sz)
                NavigationHelper.clearTargets(pokemonEntity)
            }
        }
        if (runSafety && !isWaterType && (pokemonEntity.isSubmergedInWater || pokemonEntity.air < 100)) {
            val (sx, sz) = getSpawnOffset(world)
            pokemonEntity.setPosition(pastureOrigin.x + sx, pastureOrigin.y.toDouble(), pastureOrigin.z + sz)
            pokemonEntity.air = pokemonEntity.maxAir
            NavigationHelper.clearTargets(pokemonEntity)
        }

        val speciesData: SpeciesSkills? = SpeciesSkillRegistry.getSkills(speciesName)
        if (speciesData == null) {
            if (now % 100 == 0L) Cobblebase.log("[BaseManager] $speciesName has NO species skills registered")
            return
        }

        val rawAssignment: String? = assignments[pokemonId]

        // Working-cap enforcement (lazy): if this worker is past the cap in its pasture's
        // tether order, drop the assignment to Relax now. This catches admins lowering the
        // cap mid-session — the trim happens on the next pasture tick after the change.
        // Aura-only mons (null assignment) don't count toward the cap and are skipped here.
        if (rawAssignment != null && relaxIfOverCap(world, pastureOrigin, pokemonId)) {
            return
        }

        // Craftsman supplier mode: run dedicated supplier executor
        // Format: "craftsman_supply:skillId" or "craftsman_supply:skillId:targetItemId"
        if (rawAssignment != null && rawAssignment.startsWith(SUPPLIER_PREFIX)) {
            // Auto-cleanup: a supplier with no Craftsman left in the same pasture is "stuck"
            // (player moved this Mon between pastures without resetting the assignment). Reset
            // it to relax so the user can re-assign normally.
            if (!hasCraftsmanInPasture(world, pastureOrigin)) {
                assignments.remove(pokemonId)
                dirty = true
                return
            }
            AmbientBehavior.clearState(pokemonId)
            val afterPrefix = rawAssignment.removePrefix(SUPPLIER_PREFIX)
            val supplierSkillId = afterPrefix.split(":").let {
                // Reconstruct namespace:path (e.g. "cobblebase:finder_bui")
                if (it.size >= 2) "${it[0]}:${it[1]}" else it[0]
            }
            val entry = speciesData.skills.find { it.skillId == supplierSkillId }
            if (entry != null) {
                notlown.cobblebase.core.executors.SupplierExecutor.tick(
                    world, pastureOrigin, pokemonEntity, entry
                )
            }
        } else if (rawAssignment != null && rawAssignment.startsWith(BUILDER_HELPER_PREFIX)) {
            // Builder helper mode: pasture has an active build job and this Pokemon is
            // automatically supplying needed materials. The coordinator picks which block
            // this helper chases; BuilderHelperExecutor routes to producer/harvester/mining.
            AmbientBehavior.clearState(pokemonId)
            val helperSkill = SkillRegistry.get("cobblebase:builder") ?: return
            // Re-use a synthesized SkillEntry so the executor has the right proficiency.
            val entry = speciesData.skills.firstOrNull() ?: SkillEntry("cobblebase:builder_helper", 3)
            notlown.cobblebase.core.executors.BuilderHelperExecutor.tick(
                world, pastureOrigin, pokemonEntity, helperSkill, entry
            )
        } else {
            val assignedSkillId = rawAssignment

            if (assignedSkillId != null) {
                if (!JobConfigOverrides.isEnabled(assignedSkillId)) {
                    assignments.remove(pokemonId)
                    dirty = true
                } else if (!isBuffSkill(assignedSkillId)) {
                    AmbientBehavior.clearState(pokemonId)
                    val entry: SkillEntry? = speciesData.skills.find { e -> e.skillId == assignedSkillId }
                    if (entry != null) {
                        executeSkill(world, pastureOrigin, pokemonEntity, entry)
                    }
                }
            }
        }
        // null assignment = Idle (do nothing). Only passive buffs run below.

        // Passive buffs: tick buff/aura skills regardless of job assignment, BUT respect
        // the admin disable flag (JobConfigOverrides.isEnabled). Without this check the
        // "Enabled" toggle in Mod Menu → Admin → Jobs did nothing for support-style jobs,
        // since players can't manually assign or unassign passive auras.
        //
        // The buff subset is cached per species via SpeciesSkillRegistry.getBuffSkills,
        // so we no longer iterate the full skill list every tick.
        val buffEntries = SpeciesSkillRegistry.getBuffSkills(speciesName) { isBuffSkill(it) }
        for (entry: SkillEntry in buffEntries) {
            val skillDef: SkillDef = SkillRegistry.get(entry.skillId) ?: continue
            if (!JobConfigOverrides.isEnabled(skillDef.id)) continue
            val exec: SkillExecutor = ExecutorRegistry.get(skillDef.executor) ?: continue
            exec.tick(world, pastureOrigin, pokemonEntity, skillDef, entry)
        }

        val tickMs = (System.nanoTime() - tickStart) / 1_000_000
        if (tickMs > 10) {
            Cobblebase.LOGGER.debug("[Cobblebase] PERF tickPokemon $speciesName / assign=$rawAssignment took ${tickMs}ms")
        }
    }

    /**
     * Tick passive buffs for Pokemon without an active entity (owner offline, entity despawned).
     * Uses pasture block position instead of entity position for range checks.
     * Only runs buff executors — no jobs, no navigation, no ambient behavior.
     */
    fun tickPassiveBuffsWithoutEntity(world: World, pastureOrigin: BlockPos, pokemon: com.cobblemon.mod.common.pokemon.Pokemon) {
        if (world !is net.minecraft.server.world.ServerWorld) return
        val speciesName = resolveSpeciesName(pokemon)
        val speciesData = SpeciesSkillRegistry.getSkills(speciesName) ?: return

        val buffEntries = SpeciesSkillRegistry.getBuffSkills(speciesName) { isBuffSkill(it) }
        for (entry in buffEntries) {
            val skillDef = SkillRegistry.get(entry.skillId) ?: continue
            if (!JobConfigOverrides.isEnabled(skillDef.id)) continue
            val exec = ExecutorRegistry.get(skillDef.executor) ?: continue
            if (exec !is notlown.cobblebase.core.executors.BuffExecutor) continue
            // Use the pasture-origin-based tick that doesn't need an entity
            exec.tickWithoutEntity(world, pastureOrigin, pokemon.uuid, pokemon.getOwnerUUID(), skillDef, entry)
        }
    }

    // Memoize isBuffSkill — this used to do 1-2 list scans + a HashMap lookup on every
    // call, and it's called for every skill in every pasture-tethered Pokemon's skill set
    // on every tick.
    private val buffSkillCache = mutableMapOf<String, Boolean>()

    private fun isBuffSkill(executorOrSkillId: String): Boolean {
        buffSkillCache[executorOrSkillId]?.let { return it }
        val result = run {
            if (executorOrSkillId in BUFF_EXECUTORS) return@run true
            val skillDef = SkillRegistry.get(executorOrSkillId)
            skillDef != null && skillDef.executor in BUFF_EXECUTORS
        }
        buffSkillCache[executorOrSkillId] = result
        return result
    }

    private val BUFF_EXECUTORS = listOf(
        "speed_boost", "strength_boost", "resistance_boost",
        "night_vision", "water_breathing", "jump_boost",
        "haste_boost", "saturation_boost",
        "lucky_charm", "aura", "growth"
    )

    private fun executeSkill(world: World, origin: BlockPos, pokemonEntity: PokemonEntity, entry: SkillEntry) {
        val skillDef: SkillDef? = SkillRegistry.getEffective(entry.skillId)
        if (skillDef == null) {
            return
        }
        val exec: SkillExecutor? = ExecutorRegistry.get(skillDef.executor)
        if (exec == null) {
            return
        }
        exec.tick(world, origin, pokemonEntity, skillDef, entry)
    }

    fun assignSkill(pokemonId: UUID, skillId: String?) {
        if (skillId == null) assignments.remove(pokemonId) else assignments[pokemonId] = skillId
        dirty = true
    }

    fun getAssignment(pokemonId: UUID): String? = assignments[pokemonId]

    /** Check if a Pokemon is assigned as a Craftsman supplier. */
    /**
     * Returns true if any Pokemon tethered to [pastureOrigin] has the Craftsman skill assigned.
     * Used by the supplier-cleanup guard so suppliers can't get stuck without a Craftsman.
     */
    private fun hasCraftsmanInPasture(world: net.minecraft.world.World, pastureOrigin: BlockPos): Boolean {
        val pasture = world.getBlockEntity(pastureOrigin)
                as? com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity ?: return false
        for (tether in pasture.tetheredPokemon) {
            if (assignments[tether.pokemonId] == "cobblebase:craftsman") return true
        }
        return false
    }

    fun isCraftsmanSupplier(pokemonId: UUID): Boolean {
        val assignment = assignments[pokemonId] ?: return false
        return assignment.startsWith("craftsman_supply:")
    }

    const val SUPPLIER_PREFIX = "craftsman_supply:"
    const val BUILDER_HELPER_PREFIX = "builder_helper"

    /**
     * Returns a snapshot of all current assignments for sync packets.
     * Values are skill IDs; null assignments are excluded.
     */
    fun getAllAssignments(): Map<UUID, String> {
        return assignments.filterValues { it != null }.mapValues { it.value!! }
    }

    /**
     * Working-cap helpers. Cap semantics: server admins set
     * `GeneralSettings.maxWorkingPokemonPerPasture`. The first N assigned mons in a
     * pasture's tether order are the active workers; if more mons get assigned past
     * the cap, they're hard-unassigned (set to Relax) instead of being silently
     * paused. The clear-to-Relax approach matches what the GUI already conveys for
     * idle mons — no separate "queued" state to confuse players.
     *
     * Enforcement happens in two places:
     *   - **Up-front rejection** via `wouldExceedWorkingCap`, called from the
     *     SkillAssignment C2S handler before applying a player click.
     *   - **Lazy on-tick trim** via `isWorkerOverCap`, called from `tickPokemon`.
     *     This handles cap-lowering by an admin: on the affected pasture's next tick,
     *     each over-cap worker gets Relaxed and the sync packet flows to the player.
     *
     * Aura-only mons (null assignment, species grants passive buff) are not counted
     * toward the cap and never touched — they're bonuses, not workers.
     */
    fun wouldExceedWorkingCap(
        world: World,
        pastureOrigin: BlockPos,
        pokemonId: UUID,
        incomingSkillId: String?
    ): Boolean {
        if (incomingSkillId == null) return false
        val cap = GeneralSettings.getSettings().maxWorkingPokemonPerPasture
        if (cap <= 0) return false

        val pasture = world.getBlockEntity(pastureOrigin)
                as? com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
            ?: return false

        var assignedCount = 0
        for (tether in pasture.tetheredPokemon) {
            if (tether.pokemonId == pokemonId) continue
            if (assignments[tether.pokemonId] != null) assignedCount++
        }
        return assignedCount >= cap
    }

    /**
     * Returns true if this currently-assigned worker is past the cap in its pasture's
     * tether order. Called from `tickPokemon`; if true, the caller drops the
     * assignment to Relax. Aura-only mons are skipped because the caller only invokes
     * this when `rawAssignment != null`.
     */
    private fun isWorkerOverCap(
        world: World,
        pastureOrigin: BlockPos,
        pokemonId: UUID
    ): Boolean {
        val cap = GeneralSettings.getSettings().maxWorkingPokemonPerPasture
        if (cap <= 0) return false

        val pasture = world.getBlockEntity(pastureOrigin)
                as? com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
            ?: return false

        var workingRank = 0
        for (tether in pasture.tetheredPokemon) {
            val isAssigned = assignments[tether.pokemonId] != null
            if (tether.pokemonId == pokemonId) {
                return isAssigned && workingRank >= cap
            }
            if (isAssigned) workingRank++
        }
        return false
    }

    /**
     * Called from `tickPokemon` to enforce the cap lazily: if this worker is past the
     * cap, drop its assignment to Relax. Returns true when an unassignment happened so
     * the caller can short-circuit (no executor, no buffs) on this tick.
     */
    private fun relaxIfOverCap(
        world: World,
        pastureOrigin: BlockPos,
        pokemonId: UUID
    ): Boolean {
        if (!isWorkerOverCap(world, pastureOrigin, pokemonId)) return false
        assignments.remove(pokemonId)
        lastJobSuccess.remove(pokemonId)
        dirty = true
        return true
    }

    /**
     * Walks the loaded pasture block entities within 16 blocks of [player] and returns
     * the position of the one whose tetheredPokemon contains [pokemonId]. Used by the
     * SkillAssignment C2S handler to find the pasture origin for the cap pre-check —
     * the packet itself only carries Pokemon + skill ID, but the player can only have
     * opened a pasture they're standing next to, so a small radius scan is sufficient.
     */
    fun findPokemonPasture(
        player: net.minecraft.server.network.ServerPlayerEntity,
        pokemonId: UUID
    ): BlockPos? {
        val world = player.serverWorld
        val playerPos = player.blockPos
        val searchRadius = 16
        for (x in -searchRadius..searchRadius) {
            for (y in -searchRadius..searchRadius) {
                for (z in -searchRadius..searchRadius) {
                    val pos = playerPos.add(x, y, z)
                    val be = world.getBlockEntity(pos)
                    if (be is com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity) {
                        if (be.tetheredPokemon.any { it.pokemonId == pokemonId }) {
                            return pos.toImmutable()
                        }
                    }
                }
            }
        }
        return null
    }

    fun getAvailableSkills(pokemonEntity: PokemonEntity): List<SkillEntry> {
        val speciesName: String = resolveSpeciesName(pokemonEntity.pokemon)
        return SpeciesSkillRegistry.getSkills(speciesName)?.skills ?: emptyList()
    }

    /**
     * Check if a skill executor name is a passive buff (not assignable as a job).
     */
    fun isBuffExecutor(executor: String): Boolean {
        return executor in BUFF_EXECUTORS
    }

    /**
     * Returns a random spawn offset (1-2 blocks from center) to avoid spawning directly on the pasture block.
     * Matches Cobblemon's default behavior of placing Pokemon near (not on) the pasture.
     */
    private fun getSpawnOffset(world: World): Pair<Double, Double> {
        val rand = java.util.concurrent.ThreadLocalRandom.current()
        val angle = rand.nextDouble() * Math.PI * 2
        val dist = 1.5 + rand.nextDouble() // 1.5-2.5 blocks from center
        return Pair(Math.cos(angle) * dist + 0.5, Math.sin(angle) * dist + 0.5)
    }

    // -- Persistence --

    private fun getSaveFile(world: ServerWorld): File {
        val saveDir = world.server.getSavePath(WorldSavePath.ROOT).toFile()
        return File(saveDir, "cobblebase_assignments.json")
    }

    fun save(world: World) {
        if (world !is ServerWorld) return
        try {
            val file = getSaveFile(world)
            val data = assignments.mapKeys { it.key.toString() }
            file.writeText(gson.toJson(data))
            dirty = false
            lastSaveTick = world.time
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to save assignments: ${e.message}")
        }
    }

    fun load(world: World) {
        if (world !is ServerWorld) return
        try {
            val file = getSaveFile(world)
            if (!file.exists()) return
            val type = object : TypeToken<Map<String, String?>>() {}.type
            val data: Map<String, String?> = gson.fromJson(file.readText(), type)
            assignments.clear()
            for ((key, value) in data) {
                assignments[UUID.fromString(key)] = value
            }
            Cobblebase.log("[Cobblebase] Loaded ${assignments.size} skill assignments")
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[Cobblebase] Failed to load assignments: ${e.message}")
        }
    }
}

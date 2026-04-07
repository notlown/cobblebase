package notlown.cobblebase.core

/**
 * Cobblemon 1.7.3 spawn bucket data.
 * Each Pokemon is categorized by its rarity bucket from the official spawn tables.
 * Used by the Recruiter to pick Pokemon with appropriate rarity.
 */
object SpawnData {

    enum class Bucket { COMMON, UNCOMMON, RARE, ULTRA_RARE }

    // Pokemon name -> highest rarity bucket it appears in
    private val speciesBucket = mutableMapOf<String, Bucket>()

    fun init() {
        Cobblebase.LOGGER.info("[SpawnData] Initialized with bucket lookup")
    }

    /**
     * Loads bucket data directly from Cobblemon's WORLD_SPAWN_POOL.
     * Called once after Cobblemon's spawn pool has finished loading.
     * For each Pokemon, finds the RAREST bucket it appears in.
     */
    fun loadFromCobblemonSpawnPool() {
        try {
            val pool = com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools.WORLD_SPAWN_POOL ?: run {
                Cobblebase.LOGGER.warn("[SpawnData] Cobblemon spawn pool not loaded yet")
                return
            }
            speciesBucket.clear()
            var entries = 0
            for (detail in pool) {
                if (detail !is com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail) continue
                val species = try {
                    detail.pokemon.species?.lowercase() ?: continue
                } catch (_: Exception) { continue }
                val bucketName = detail.bucket?.name?.lowercase() ?: continue
                val bucket = parseBucket(bucketName)
                register(species.substringAfterLast(":"), bucket)
                entries++
            }
            Cobblebase.LOGGER.info("[SpawnData] Loaded $entries spawn entries from Cobblemon, ${speciesBucket.size} unique species")
        } catch (e: Exception) {
            Cobblebase.LOGGER.error("[SpawnData] Failed to load from Cobblemon spawn pool: ${e.message}")
        }
    }

    /**
     * Get all known species and their buckets (for export/debugging).
     */
    fun getAllBuckets(): Map<String, Bucket> = speciesBucket.toMap()

    /**
     * Get the rarity bucket for a species. Defaults to COMMON if unknown.
     */
    fun getBucket(speciesName: String): Bucket {
        return speciesBucket[speciesName.lowercase()] ?: Bucket.COMMON
    }

    /**
     * Check if a species exists in our spawn data (confirmed to exist in Cobblemon).
     */
    fun exists(speciesName: String): Boolean {
        return speciesBucket.containsKey(speciesName.lowercase())
    }

    /**
     * Register a species with its bucket (called during data loading).
     * Keeps the RAREST bucket if a species appears in multiple.
     */
    fun register(speciesName: String, bucket: Bucket) {
        val name = speciesName.lowercase()
        val existing = speciesBucket[name]
        if (existing == null || bucket.ordinal > existing.ordinal) {
            speciesBucket[name] = bucket
        }
    }

    fun parseBucket(bucketName: String): Bucket {
        return when (bucketName.lowercase().trim()) {
            "common" -> Bucket.COMMON
            "uncommon" -> Bucket.UNCOMMON
            "rare" -> Bucket.RARE
            "ultra-rare", "ultra_rare" -> Bucket.ULTRA_RARE
            else -> Bucket.COMMON
        }
    }

    /**
     * Load bucket data from Cobblemon's spawn pool files embedded in the CSV.
     * Called at init time.
     */
    fun loadFromCsv(csvContent: String) {
        var count = 0
        for (line in csvContent.lines().drop(1)) { // skip header
            if (line.isBlank()) continue
            try {
                // CSV format: No.,Pokemon,Entry,Bucket,...
                val parts = parseCsvLine(line)
                if (parts.size < 4) continue
                val name = parts[1].split("[")[0].trim().lowercase()
                val bucket = parseBucket(parts[3])
                register(name, bucket)
                count++
            } catch (_: Exception) {}
        }
        Cobblebase.LOGGER.info("[SpawnData] Loaded $count entries, ${speciesBucket.size} unique species")
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { result.add(current.toString()); current = StringBuilder() }
                else -> current.append(c)
            }
        }
        result.add(current.toString())
        return result
    }
}

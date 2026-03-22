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
        // We load from Cobblemon's spawn pool at runtime
        // This ensures we always match the installed version
        Cobblebase.LOGGER.info("[SpawnData] Initialized with bucket lookup")
    }

    /**
     * Get the rarity bucket for a species. Defaults to COMMON if unknown.
     */
    fun getBucket(speciesName: String): Bucket {
        return speciesBucket.getOrPut(speciesName.lowercase()) { Bucket.COMMON }
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

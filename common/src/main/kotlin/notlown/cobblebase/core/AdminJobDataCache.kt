package notlown.cobblebase.core

/**
 * Client-side cache for admin job configuration data received from the server.
 * Updated when AdminJobsSyncS2CPacket is received.
 */
object AdminJobDataCache {

    /** All job definitions (skill ID -> SkillDef) */
    var allJobs: Map<String, SkillDef> = emptyMap()
        private set

    /** Per-job overrides (skill ID -> JobOverride) */
    var jobOverrides: Map<String, JobConfigOverrides.JobOverride> = emptyMap()
        private set

    /** Whether data has been loaded from the server */
    var loaded: Boolean = false
        private set

    fun update(
        jobs: Map<String, SkillDef>,
        overrides: Map<String, JobConfigOverrides.JobOverride>
    ) {
        allJobs = jobs
        jobOverrides = overrides
        loaded = true
    }

    fun clear() {
        allJobs = emptyMap()
        jobOverrides = emptyMap()
        loaded = false
    }
}

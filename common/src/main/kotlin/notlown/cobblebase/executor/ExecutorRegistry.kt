package notlown.cobblebase.executor

import notlown.cobblebase.core.Cobblebase

/**
 * Maps executor names (from Skill JSON) to executor implementations.
 */
object ExecutorRegistry {
    private val executors = mutableMapOf<String, SkillExecutor>()

    fun init() {
        // Register all built-in executors
        // These will be implemented in Phase 2
        Cobblebase.LOGGER.info("ExecutorRegistry: ${executors.size} executors registered")
    }

    fun register(name: String, executor: SkillExecutor) {
        executors[name] = executor
    }

    fun get(name: String): SkillExecutor? = executors[name]
}

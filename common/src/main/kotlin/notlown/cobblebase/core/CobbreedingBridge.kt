package notlown.cobblebase.core

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * All reflection against Cobbreeding (`ludichat.cobbreeding.*`) lives here.
 *
 * Soft-dep: every method returns a safe fallback (null / default / no-op) if Cobbreeding
 * isn't on the classpath. Callers check [isLoaded] for early gating where useful, but no
 * call into this object will throw because of a missing Cobbreeding install.
 *
 * Used by [executors.EggHatcherExecutor] (reads existing eggs) and [executors.BreederExecutor]
 * (constructs new eggs).
 */
object CobbreedingBridge {

    // ---------------------------------------------------------------------------
    // Lazy class handles
    // ---------------------------------------------------------------------------

    private val pokemonEggClass: Class<*>? by lazy {
        runCatching { Class.forName("ludichat.cobbreeding.PokemonEgg") }.getOrNull()
    }
    private val eggUtilitiesClass: Class<*>? by lazy {
        runCatching { Class.forName("ludichat.cobbreeding.EggUtilities") }.getOrNull()
    }
    private val cobbreedingClass: Class<*>? by lazy {
        runCatching { Class.forName("ludichat.cobbreeding.Cobbreeding") }.getOrNull()
    }
    private val configClass: Class<*>? by lazy {
        runCatching { Class.forName("ludichat.cobbreeding.Config") }.getOrNull()
    }

    /** Single source-of-truth flag — true iff every required class loaded. */
    val isLoaded: Boolean by lazy {
        pokemonEggClass != null && eggUtilitiesClass != null
    }

    // ---------------------------------------------------------------------------
    // Egg ItemStack: component access (read existing eggs)
    // ---------------------------------------------------------------------------

    /**
     * `PokemonEgg.Companion.getTIMER()` → `ComponentType<Integer>`. We pull the ComponentType
     * via reflection once at first use and cache it; `stack.get(componentType)` then reads the
     * live value off any egg ItemStack.
     */
    private val timerComponentType: Any? by lazy {
        try {
            val cls = pokemonEggClass ?: return@lazy null
            val companionField = cls.getField("Companion")
            val companion = companionField.get(null)
            val getTimerMethod = companion.javaClass.getMethod("getTIMER")
            getTimerMethod.invoke(companion)
        } catch (_: Exception) { null }
    }

    /** Static `int DEFAULT_TIMER` on PokemonEgg — fallback when no per-egg initial seen. */
    val defaultTimer: Int by lazy {
        try {
            pokemonEggClass?.getField("DEFAULT_TIMER")?.getInt(null) ?: 12000
        } catch (_: Exception) { 12000 }
    }

    private val extractPropertiesMethod by lazy {
        eggUtilitiesClass?.declaredMethods
            ?.firstOrNull { it.name == "extractProperties" && it.parameterCount == 1 }
    }

    /** Returns true iff [stack] is a Cobbreeding `PokemonEgg`. Safe when Cobbreeding absent. */
    fun isPokemonEgg(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val cls = pokemonEggClass ?: return false
        return cls.isInstance(stack.item)
    }

    /** Reads the egg's current TIMER component, or [defaultTimer] if absent / Cobbreeding missing. */
    fun readEggTimer(stack: ItemStack): Int {
        val componentType = timerComponentType ?: return defaultTimer
        return try {
            val getMethod = stack.javaClass.methods.firstOrNull {
                it.name == "method_57824" || it.name == "get"
            } ?: return defaultTimer
            (getMethod.invoke(stack, componentType) as? Int) ?: defaultTimer
        } catch (_: Exception) { defaultTimer }
    }

    /** Human-readable species name read off an egg stack ("Pikachu egg"), or "Unknown egg". */
    fun describeEgg(stack: ItemStack): String {
        val method = extractPropertiesMethod ?: return "Unknown egg"
        return try {
            val props = method.invoke(null, stack) ?: return "Unknown egg"
            val speciesField = props.javaClass.methods.firstOrNull {
                it.name == "getSpecies" && it.parameterCount == 0
            }
            val species = speciesField?.invoke(props)?.toString()
            species ?: "Unknown egg"
        } catch (_: Exception) { "Unknown egg" }
    }

    // ---------------------------------------------------------------------------
    // Egg factory (Breeder use): build a fresh ItemStack
    // ---------------------------------------------------------------------------

    /**
     * The factory we want. Cobbreeding's `EggUtilities.getEggFromPokemonProperties` packages
     * everything: applies the right component values (NAME, TIMER, POKEMON_PROPERTIES,
     * VERSION, EGG_INFO), respects the encryption toggle, and returns a ready-to-place
     * ItemStack. Passing `timer = null` lets Cobbreeding compute the per-species default.
     *
     * Returns null if Cobbreeding isn't loaded or reflection fails.
     */
    fun createEgg(properties: PokemonProperties, timer: Int? = null): ItemStack? {
        val cls = eggUtilitiesClass ?: return null
        val method = cls.declaredMethods.firstOrNull {
            it.name == "getEggFromPokemonProperties" && it.parameterCount == 2
        } ?: return null
        return try {
            method.isAccessible = true
            method.invoke(null, properties, timer) as? ItemStack
        } catch (_: Exception) { null }
    }

    /**
     * Wraps `EggUtilities.calculateTimer(Species)`. Returns null on failure so the caller
     * can fall back to [defaultTimer].
     */
    fun calculateTimer(species: com.cobblemon.mod.common.pokemon.Species): Int? {
        val cls = eggUtilitiesClass ?: return null
        val method = cls.declaredMethods.firstOrNull {
            it.name == "calculateTimer" && it.parameterCount == 1
        } ?: return null
        return try {
            method.isAccessible = true
            (method.invoke(null, species) as? Int)
        } catch (_: Exception) { null }
    }

    // ---------------------------------------------------------------------------
    // Config access
    // ---------------------------------------------------------------------------

    /** Reads `Cobbreeding.INSTANCE.getConfig()`. Cached implicitly through the methods below. */
    private val configInstance: Any? by lazy {
        try {
            val cls = cobbreedingClass ?: return@lazy null
            val instanceField = cls.getField("INSTANCE")
            val instance = instanceField.get(null)
            val getConfig = cls.getMethod("getConfig")
            getConfig.invoke(instance)
        } catch (_: Exception) { null }
    }

    private fun readConfigInt(getter: String, fallback: Int): Int {
        val cfg = configInstance ?: return fallback
        return try {
            (configClass?.getMethod(getter)?.invoke(cfg) as? Int) ?: fallback
        } catch (_: Exception) { fallback }
    }

    private fun readConfigBool(getter: String, fallback: Boolean): Boolean {
        val cfg = configInstance ?: return fallback
        return try {
            (configClass?.getMethod(getter)?.invoke(cfg) as? Boolean) ?: fallback
        } catch (_: Exception) { fallback }
    }

    /** Cobbreeding's lower bound for pasture-breeding cooldown (ticks). Fallback: 8000. */
    fun getMinBreedingTicks(): Int = readConfigInt("getMinBreedingTimeInTicks", 8000)

    /** Cobbreeding's upper bound for pasture-breeding cooldown (ticks). Fallback: 14000. */
    fun getMaxBreedingTicks(): Int = readConfigInt("getMaxBreedingTimeInTicks", 14000)

    /** Max number of egg slots in a pasture inventory. Fallback: 5. */
    fun getPastureInventorySize(): Int = readConfigInt("getPastureInventorySize", 5)

    /** Whether Cobbreeding encrypts egg properties in the EGG_INFO component. Fallback: true. */
    fun isEggEncryptionEnabled(): Boolean = readConfigBool("getEggEncryptionEnabled", true)

    // ---------------------------------------------------------------------------
    // Pasture interaction
    // ---------------------------------------------------------------------------

    /**
     * Places [eggStack] into the first empty slot of the pasture's inventory. Returns the
     * slot index used, or -1 if no empty slot was available (or the pasture isn't an
     * Inventory — i.e. Cobbreeding's PastureInventory mixin isn't active).
     *
     * Iteration order matches Cobbreeding's own egg-spawn loop, so hoppers/users see eggs
     * in the same positions they would from native breeding.
     */
    fun placeEggInPasture(world: World, pasturePos: BlockPos, eggStack: ItemStack): Int {
        val be = world.getBlockEntity(pasturePos) as? Inventory ?: return -1
        for (slot in 0 until be.size()) {
            if (be.getStack(slot).isEmpty) {
                be.setStack(slot, eggStack)
                be.markDirty()
                return slot
            }
        }
        return -1
    }

    /** Counts empty slots in the pasture inventory (= remaining egg capacity). */
    fun countEmptyEggSlots(world: World, pasturePos: BlockPos): Int {
        val be = world.getBlockEntity(pasturePos) as? Inventory ?: return 0
        var empty = 0
        for (slot in 0 until be.size()) {
            if (be.getStack(slot).isEmpty) empty++
        }
        return empty
    }
}

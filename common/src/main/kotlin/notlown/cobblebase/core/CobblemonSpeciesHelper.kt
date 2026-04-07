package notlown.cobblebase.core

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies

/**
 * Helper for querying Cobblemon's runtime species registry.
 *
 * The bundled species_skills JSON files cover ~8400
 * species, including a lot of fakemons that ship from third-party Cobblemon
 * addons. Most installations only have a small subset of those addons (or
 * none), so the admin GUI was showing thousands of species the player can
 * never actually catch.
 *
 * This helper returns the set of species names that are *actually loaded*
 * in this server's Cobblemon registry. The admin species list is filtered
 * against it before being sent to the client.
 */
object CobblemonSpeciesHelper {

    /**
     * Returns the lowercase names of every species currently loaded in
     * Cobblemon's runtime registry. Wrapped in try/catch because the
     * Cobblemon API throws if accessed before bootstrap.
     */
    fun getInstalledSpeciesNames(): Set<String> {
        return try {
            val out = HashSet<String>()
            for (species in PokemonSpecies.species) {
                out.add(species.name.lowercase())
            }
            out
        } catch (e: Exception) {
            Cobblebase.LOGGER.warn("[Cobblebase] Could not query Cobblemon species registry: ${e.message}")
            emptySet()
        }
    }
}

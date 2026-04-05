package notlown.cobblebase.core

/**
 * Client-side cache for per-pasture settings received from the server.
 * The Settings GUI reads from this cache.
 */
object PastureSettingsCache {

    var searchRadius: Int = 10
    var radiusMin: Int = 3
    var radiusMax: Int = 20
    var synced: Boolean = false

    fun update(searchRadius: Int, radiusMin: Int, radiusMax: Int) {
        this.searchRadius = searchRadius
        this.radiusMin = radiusMin
        this.radiusMax = radiusMax
        this.synced = true
    }

    fun clear() {
        searchRadius = 10
        radiusMin = 3
        radiusMax = 20
        synced = false
    }
}

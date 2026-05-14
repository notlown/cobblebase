package notlown.cobblebase.core

import notlown.cobblebase.core.net.StructureTemplateListSyncS2CPacket

/**
 * Client-side cache for builder data received from the server: the list of building
 * templates the server has loaded.
 *
 * Populated by [StructureTemplateListSyncS2CPacket]. The Builder tab in the Pasture
 * GUI reads from this to render its template list.
 */
object BuilderCache {

    var templates: List<StructureTemplateListSyncS2CPacket.TemplateDTO> = emptyList()
        private set

    var loaded: Boolean = false
        private set

    fun update(list: List<StructureTemplateListSyncS2CPacket.TemplateDTO>) {
        templates = list
        loaded = true
    }

    fun clear() {
        templates = emptyList()
        loaded = false
    }
}

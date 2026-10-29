package com.minimalpairs.app.ui.reference

/** A completed raster may bridge camera updates, never a different sound or cutaway mesh. */
data class OralRenderedFrame<T>(
    val selectionKey: String,
    val mesh: List<OralTriangle>,
    val view: OralView,
    val image: T,
    val sampleElapsedMs: Int? = null
) {
    fun forMesh(selectionKey: String, mesh: List<OralTriangle>): OralRenderedFrame<T>? =
        takeIf { this.selectionKey == selectionKey && this.mesh === mesh }
}

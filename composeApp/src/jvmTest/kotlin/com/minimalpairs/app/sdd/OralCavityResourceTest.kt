package com.minimalpairs.app.sdd

import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import com.minimalpairs.app.ui.reference.*
import kotlinx.coroutines.test.runTest
import minimalpairs.composeapp.generated.resources.Res
import kotlin.test.*

// Packaged-resource integration tests use the desktop resource reader. Android host tests
// have no Android runtime or asset manager; geometry-only checks remain in commonTest.
class OralCavityResourceTest {
    @Test
    fun supportedMeshesAreDistinctAndMissingVowelsDoNotGetGuessedPoses() = runTest {
        val unsupported = setOf("æ", "ɑː", "ʌ", "ɒ")
        val catalog = VowelContrastsCatalog.defaultContrasts.flatMap { listOf(it.phoneme1, it.phoneme2) }.toSet()
        assertEquals(catalog, VocalTractLabMeshes.sourceShapes.keys + unsupported)
        unsupported.forEach { assertNull(VocalTractLabMeshes.load(it)) }
        assertNull(VocalTractLabMeshes.load("unknown"))
        val loaded = VocalTractLabMeshes.sourceShapes.keys.map { VocalTractLabMeshes.load(it)!! }
        assertEquals(6, loaded.toSet().size, "Case-sensitive SAMPA names must not overwrite one another")
        for (mesh in loaded) {
            assertEquals(2804, mesh.size)
            assertEquals(OralPart.entries.toSet(), mesh.map { it.part }.toSet())
            for (left in listOf(false, true)) {
                val cut = OralCavityModel.cutaway(mesh, true, left)
                assertTrue(cut.isNotEmpty())
                for (face in cut) for (point in listOf(face.a, face.b, face.c)) {
                    assertTrue(if (left) point.x >= -.00001f else point.x <= .00001f)
                    for (yaw in listOf(-100f, 0f, 65f, 90f, 100f)) {
                        val view = OralView(yaw, 40f, 1.3f)
                        val projected = view.project(view.rotate(point))
                        assertTrue(projected.x.isFinite() && projected.y.isFinite() && projected.z.isFinite())
                    }
                }
            }
        }
    }

    @Test
    fun malformedMeshesAreRejectedBeforeRendering() = runTest {
        val valid = Res.readBytes("files/vtl/fleece.vtlm")
        fun changedInt(offset: Int, value: Int): ByteArray = valid.copyOf().apply {
            repeat(4) { this[offset + it] = (value ushr (8 * it)).toByte() }
        }
        assertFailsWith<IllegalArgumentException> { VocalTractLabMeshes.decode(valid.copyOf(20)) }
        assertFailsWith<IllegalArgumentException> { VocalTractLabMeshes.decode(changedInt(0, 0)) }
        assertFailsWith<IllegalArgumentException> { VocalTractLabMeshes.decode(changedInt(4, 2)) }
        assertFailsWith<IllegalArgumentException> { VocalTractLabMeshes.decode(changedInt(16, Int.MAX_VALUE)) }
        assertFailsWith<IllegalArgumentException> { VocalTractLabMeshes.decode(changedInt(24, Float.NaN.toBits())) }
        val vertexCount = (0..3).fold(0) { n, i -> n or ((valid[16 + i].toInt() and 255) shl (8 * i)) }
        assertFailsWith<IllegalArgumentException> { VocalTractLabMeshes.decode(changedInt(24 + vertexCount * 12, vertexCount)) }
        assertFailsWith<IllegalArgumentException> { VocalTractLabMeshes.decode(valid + byteArrayOf(0)) }
    }

}

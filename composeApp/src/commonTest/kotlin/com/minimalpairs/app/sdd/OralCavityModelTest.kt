package com.minimalpairs.app.sdd

import com.minimalpairs.app.ui.reference.*
import kotlin.math.abs
import kotlin.test.*

class OralCavityModelTest {
    @Test
    fun clippingPreservesTheExactPlaneIntersection() {
        val normal = OralPoint(0f, 0f, 1f)
        val original = OralTriangle(OralPoint(-1f, 0f, 0f), OralPoint(1f, 0f, 0f), OralPoint(0f, 1f, 0f),
            OralPart.PALATE, normal, normal, normal)
        val mesh = listOf(original)
        assertSame(mesh, OralCavityModel.cutaway(mesh, false))
        for (left in listOf(false, true)) {
            val cut = OralCavityModel.cutaway(mesh, true, left)
            val area = cut.sumOf { face ->
                abs((face.b.x - face.a.x) * (face.c.y - face.a.y) - (face.b.y - face.a.y) * (face.c.x - face.a.x)).toDouble() / 2
            }
            assertEquals(.5, area, .00001)
            assertTrue(cut.flatMap { listOf(it.a, it.b, it.c) }.contains(OralPoint(0f, 0f, 0f)))
        }
    }
    @Test
    fun cutawayRetainsWholeTongueInsteadOfInventingASection() {
        val n = OralPoint(0f, 0f, 1f)
        val tongue = OralTriangle(OralPoint(-1f, 0f, 0f), OralPoint(1f, 0f, 0f), OralPoint(0f, 1f, 0f),
            OralPart.TONGUE, n, n, n)
        for (left in listOf(false, true)) assertEquals(listOf(tongue), OralCavityModel.cutaway(listOf(tongue), true, left))
    }
}

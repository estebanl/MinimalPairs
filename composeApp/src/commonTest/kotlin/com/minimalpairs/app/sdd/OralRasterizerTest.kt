package com.minimalpairs.app.sdd

import com.minimalpairs.app.ui.reference.*
import kotlin.test.*

class OralRasterizerTest {
    private val n = OralPoint(0f, 0f, 1f)
    private fun triangle(z: Float, part: OralPart) = OralTriangle(
        OralPoint(-.7f, -.6f, z), OralPoint(.7f, -.6f, z), OralPoint(0f, .8f, z), part, n, n, n)

    @Test fun closerSurfaceWinsRegardlessOfTriangleOrder() {
        val back = triangle(-.3f, OralPart.TONGUE)
        val front = triangle(.3f, OralPart.TEETH)
        val view = OralView(yaw = 0f)
        val a = OralRasterizer.render(listOf(back, front), view, 64, 64)
        val b = OralRasterizer.render(listOf(front, back), view, 64, 64)
        assertContentEquals(a.argb, b.argb)
        val expected = OralRasterizer.render(listOf(front), view, 64, 64)
        assertEquals(expected.argb[32 * 64 + 32], a.argb[32 * 64 + 32])
        assertNotEquals(0, a.argb[32 * 64 + 32])
    }

    @Test fun intersectingSurfacesAreResolvedPerPixelNotByTheirCentroids() {
        val slope = OralTriangle(OralPoint(-1f, -1f, -.8f), OralPoint(1f, -1f, .8f),
            OralPoint(0f, 1f, 0f), OralPart.TONGUE, n, n, n)
        val plane = OralTriangle(OralPoint(-1f, -1f, 0f), OralPoint(1f, -1f, 0f),
            OralPoint(0f, 1f, 0f), OralPart.TEETH, n, n, n)
        val view = OralView(yaw = 0f)
        val a = OralRasterizer.render(listOf(slope, plane), view, 128, 128)
        val b = OralRasterizer.render(listOf(plane, slope), view, 128, 128)
        // Skip exact equal-depth line, where either coplanar surface is permitted.
        for (y in 0 until 128) for (x in 0 until 128) if (x !in 63..64) {
            assertEquals(a.argb[y * 128 + x], b.argb[y * 128 + x])
        }
        val justTongue = OralRasterizer.render(listOf(slope), view, 128, 128)
        val justTeeth = OralRasterizer.render(listOf(plane), view, 128, 128)
        assertEquals(justTeeth.argb[70 * 128 + 50], a.argb[70 * 128 + 50])
        assertEquals(justTongue.argb[70 * 128 + 78], a.argb[70 * 128 + 78])
        assertNotEquals(a.argb[70 * 128 + 50], a.argb[70 * 128 + 78])
    }

    @Test fun boundsDegenerateTrianglesAndCancellationAreSafe() {
        assertFailsWith<IllegalArgumentException> { OralRasterizer.render(emptyList(), OralView(), 0, 10) }
        assertFailsWith<IllegalArgumentException> { OralRasterizer.render(emptyList(), OralView(), 769, 10) }
        val point = OralPoint(0f, 0f, 0f)
        val degenerate = OralTriangle(point, point, point, OralPart.TONGUE, n, n, n)
        assertTrue(OralRasterizer.render(listOf(degenerate), OralView(), 8, 8).argb.all { it == 0 })
        assertFailsWith<IllegalStateException> {
            OralRasterizer.render(listOf(triangle(0f, OralPart.TONGUE)), OralView(), checkActive = { error("cancelled") })
        }
    }
}

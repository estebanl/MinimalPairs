package com.minimalpairs.app.sdd

import com.minimalpairs.app.ui.reference.OralPart
import com.minimalpairs.app.ui.reference.OralPoint
import com.minimalpairs.app.ui.reference.OralRasterizer
import com.minimalpairs.app.ui.reference.OralTriangle
import com.minimalpairs.app.ui.reference.OralView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OralRasterizerTest {
    private val facing = OralPoint(0f, 0f, 1f)
    private fun quad(z: Float, part: OralPart, size: Float = .6f): List<OralTriangle> {
        val a = OralPoint(-size, -size, z); val b = OralPoint(size, -size, z)
        val c = OralPoint(size, size, z); val d = OralPoint(-size, size, z)
        return listOf(OralTriangle(a, b, c, part, facing, facing, facing), OralTriangle(a, c, d, part, facing, facing, facing))
    }
    private fun hue(pixel: Int, part: OralPart): Boolean {
        val base = OralRasterizer.baseColor(part)
        val r = (pixel shr 16) and 255; val g = (pixel shr 8) and 255; val b = pixel and 255
        val br = (base shr 16) and 255; val bg = (base shr 8) and 255; val bb = base and 255
        // Same colour up to a uniform lighting factor.
        return kotlin.math.abs(r * bg - g * br) <= 2 * 255 && kotlin.math.abs(r * bb - b * br) <= 2 * 255
    }

    @Test
    fun closerSurfaceWinsRegardlessOfDrawOrder() {
        val view = OralView(yaw = 0f, pitch = 0f)
        val near = quad(.5f, OralPart.TEETH); val far = quad(-.5f, OralPart.LIPS, size = 1f)
        for (mesh in listOf(near + far, far + near)) {
            val frame = OralRasterizer.render(mesh, view, 64, 64)
            val center = frame.argb[32 * 64 + 32]
            assertTrue(hue(center, OralPart.TEETH), "Nearer teeth must cover the lips behind them")
        }
    }

    @Test
    fun intersectingSurfacesAreResolvedPerPixel() {
        // Two planes crossing at x = 0: each must be visible on the side where it is in front.
        val n = OralPoint(0f, 0f, 1f)
        val tilted = listOf(
            OralTriangle(OralPoint(-1f, -1f, -.5f), OralPoint(1f, -1f, .5f), OralPoint(1f, 1f, .5f), OralPart.TONGUE, n, n, n),
            OralTriangle(OralPoint(-1f, -1f, -.5f), OralPoint(1f, 1f, .5f), OralPoint(-1f, 1f, -.5f), OralPart.TONGUE, n, n, n))
        val frame = OralRasterizer.render(tilted + quad(0f, OralPart.PALATE, size = 1f), OralView(yaw = 0f), 80, 80)
        assertTrue(hue(frame.argb[40 * 80 + 60], OralPart.TONGUE))
        assertTrue(hue(frame.argb[40 * 80 + 20], OralPart.PALATE))
    }

    @Test
    fun uncoveredPixelsStayTransparent() {
        val frame = OralRasterizer.render(quad(0f, OralPart.TISSUE, size = .3f), OralView(yaw = 0f), 40, 40)
        assertEquals(0, frame.argb[0])
        assertEquals(255, frame.argb[20 * 40 + 20] ushr 24)
    }
}

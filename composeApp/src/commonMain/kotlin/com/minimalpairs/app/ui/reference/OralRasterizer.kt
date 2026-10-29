package com.minimalpairs.app.ui.reference

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Small, deterministic two-sided z-buffer. Larger camera-space z is closer to the viewer.
 *
 * Unlike triangle-centroid sorting, this resolves intersecting/overlapping tongue, teeth and
 * tract triangles per pixel. The buffer is bounded for interactive mobile use. Geometry is
 * never changed by lighting, microphone data or rendering. Output is straight ARGB.
 */
object OralRasterizer {
    data class Frame(val width: Int, val height: Int, val argb: IntArray)
    private data class Vertex(val x: Float, val y: Float, val q: Float, val n: OralPoint)

    fun render(mesh: List<OralTriangle>, view: OralView, width: Int = 384, height: Int = 334, checkActive: () -> Unit = {}): Frame {
        require(width in 1..768 && height in 1..768)
        val pixels = IntArray(width * height)
        val depth = FloatArray(pixels.size) { Float.NEGATIVE_INFINITY }
        val section = BooleanArray(pixels.size)
        val scale = min(width, height) / 3.4f
        fun vertex(p: OralPoint, n: OralPoint): Vertex {
            val rotated = view.rotate(p)
            // Same perspective as OralView; bounded near plane prevents singular projections.
            val q = 1f / max(1f, 5f - rotated.z)
            return Vertex(width / 2f + rotated.x * 5f * q * view.zoom * scale,
                height * .48f - rotated.y * 5f * q * view.zoom * scale, q, view.rotate(n))
        }
        fun edge(a: Vertex, b: Vertex, x: Float, y: Float) =
            (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x)
        for (face in mesh) {
            checkActive()
            val a = vertex(face.a, face.na); val b = vertex(face.b, face.nb); val c = vertex(face.c, face.nc)
            val area = edge(a, b, c.x, c.y)
            if (!area.isFinite() || kotlin.math.abs(area) < .00001f) continue
            val x0 = max(0, floor(min(a.x, min(b.x, c.x))).toInt())
            val x1 = min(width - 1, ceil(max(a.x, max(b.x, c.x))).toInt())
            val y0 = max(0, floor(min(a.y, min(b.y, c.y))).toInt())
            val y1 = min(height - 1, ceil(max(a.y, max(b.y, c.y))).toInt())
            val rgb = when (face.part) {
                OralPart.TONGUE -> 0xC97982
                OralPart.PALATE -> 0xD4ACA0
                OralPart.TEETH -> 0xF2E7D4
                OralPart.LIPS -> 0xA85C6A
                OralPart.TISSUE -> 0xCBA69B
                OralPart.CAVITY -> 0x4C1C23
                OralPart.MUCOSA -> 0xAE6863
                OralPart.BONE -> 0xD8C7A3
                // Covered epiglottis uses a warm illustrative tone, not exposed cartilage.
                OralPart.CARTILAGE -> 0xBC8078
            }
            for (y in y0..y1) for (x in x0..x1) {
                val wa = edge(b, c, x + .5f, y + .5f) / area
                val wb = edge(c, a, x + .5f, y + .5f) / area
                val wc = 1f - wa - wb
                if (wa < -.00001f || wb < -.00001f || wc < -.00001f) continue
                val q = wa * a.q + wb * b.q + wc * c.q
                val index = y * width + x
                val tied = kotlin.math.abs(q - depth[index]) < .0000001f
                if (q < depth[index] && !tied || tied && (!face.isSection || section[index])) continue
                depth[index] = q
                section[index] = face.isSection
                // Perspective-correct smooth normals. Orient toward the camera for two-sided surfaces.
                // Scalar interpolation avoids allocating one OralPoint per visible pixel.
                var nx = (wa * a.q * a.n.x + wb * b.q * b.n.x + wc * c.q * c.n.x) / q
                var ny = (wa * a.q * a.n.y + wb * b.q * b.n.y + wc * c.q * c.n.y) / q
                var nz = (wa * a.q * a.n.z + wb * b.q * b.n.z + wc * c.q * c.n.z) / q
                val length = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(.0001f)
                val orientation = if (nz < 0f) -1f / length else 1f / length
                nx *= orientation; ny *= orientation; nz *= orientation
                val diffuse = max(0f, nx * -.3f + ny * .6f + nz * .74f)
                val light = (.55f + .45f * diffuse).coerceIn(.55f, 1f)
                val red = (((rgb shr 16) and 255) * light).roundToInt()
                val green = (((rgb shr 8) and 255) * light).roundToInt()
                val blue = ((rgb and 255) * light).roundToInt()
                pixels[index] = (255 shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        return Frame(width, height, pixels)
    }
}

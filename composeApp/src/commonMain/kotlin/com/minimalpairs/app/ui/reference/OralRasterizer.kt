package com.minimalpairs.app.ui.reference

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Small two-sided z-buffer renderer for the reference meshes. Larger rotated z is closer
 * to the viewer, matching [OralView.project].
 *
 * Sorting whole triangles by centroid (the previous approach) cannot resolve intersecting
 * or long overlapping triangles, so teeth, lips and tongue drew through each other when
 * the model was rotated. Resolving depth per pixel fixes that. Geometry is never changed
 * by lighting, microphone data or rendering. Output is straight (unpremultiplied) ARGB;
 * uncovered pixels are fully transparent.
 */
object OralRasterizer {
    class Frame(val width: Int, val height: Int, val argb: IntArray)

    const val MAX_SIZE = 1024

    fun render(
        mesh: List<OralTriangle>,
        view: OralView,
        width: Int,
        height: Int,
        checkActive: () -> Unit = {}
    ): Frame {
        require(width in 1..MAX_SIZE && height in 1..MAX_SIZE) { "Unsupported frame size" }
        val pixels = IntArray(width * height)
        val depth = FloatArray(pixels.size) { Float.NEGATIVE_INFINITY }
        val scale = min(width, height) / 3.4f
        val cx = width / 2f
        val cy = height * .48f
        // Scratch arrays avoid one allocation per vertex.
        val sx = FloatArray(3); val sy = FloatArray(3); val sq = FloatArray(3)
        val nx = FloatArray(3); val ny = FloatArray(3); val nz = FloatArray(3)

        for ((faceIndex, face) in mesh.withIndex()) {
            if (faceIndex and 255 == 0) checkActive()
            val corners = arrayOf(face.a, face.b, face.c)
            val normals = arrayOf(face.na, face.nb, face.nc)
            for (i in 0..2) {
                val r = view.rotate(corners[i])
                // Same perspective as OralView.project; bounded near plane avoids singularities.
                val q = 1f / max(1f, 5f - r.z)
                sx[i] = cx + r.x * 5f * q * view.zoom * scale
                sy[i] = cy - r.y * 5f * q * view.zoom * scale
                sq[i] = q
                val n = view.rotate(normals[i])
                nx[i] = n.x; ny[i] = n.y; nz[i] = n.z
            }
            val area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sy[1] - sy[0]) * (sx[2] - sx[0])
            if (!area.isFinite() || kotlin.math.abs(area) < 1e-6f) continue
            val x0 = max(0, floor(min(sx[0], min(sx[1], sx[2]))).toInt())
            val x1 = min(width - 1, ceil(max(sx[0], max(sx[1], sx[2]))).toInt())
            val y0 = max(0, floor(min(sy[0], min(sy[1], sy[2]))).toInt())
            val y1 = min(height - 1, ceil(max(sy[0], max(sy[1], sy[2]))).toInt())
            if (x0 > x1 || y0 > y1) continue
            val rgb = baseColor(face.part)
            val r0 = (rgb shr 16) and 255; val g0 = (rgb shr 8) and 255; val b0 = rgb and 255
            val inv = 1f / area
            for (y in y0..y1) {
                val py = y + .5f
                for (x in x0..x1) {
                    val px = x + .5f
                    val wa = ((sx[2] - sx[1]) * (py - sy[1]) - (sy[2] - sy[1]) * (px - sx[1])) * inv
                    if (wa < -1e-5f) continue
                    val wb = ((sx[0] - sx[2]) * (py - sy[2]) - (sy[0] - sy[2]) * (px - sx[2])) * inv
                    if (wb < -1e-5f) continue
                    val wc = 1f - wa - wb
                    if (wc < -1e-5f) continue
                    val q = wa * sq[0] + wb * sq[1] + wc * sq[2]
                    val index = y * width + x
                    val current = depth[index]
                    // Section fills lie exactly on the cut plane; let them win ties so the
                    // filled tongue is not speckled by coplanar clipped surface edges.
                    if (q < current || q == current && !face.isSection) continue
                    depth[index] = q
                    val light = if (face.isSection) .95f else {
                        // Perspective-correct smooth normal, flipped toward the camera so both
                        // sides of the open source surfaces are lit.
                        var mx = (wa * sq[0] * nx[0] + wb * sq[1] * nx[1] + wc * sq[2] * nx[2]) / q
                        var my = (wa * sq[0] * ny[0] + wb * sq[1] * ny[1] + wc * sq[2] * ny[2]) / q
                        var mz = (wa * sq[0] * nz[0] + wb * sq[1] * nz[1] + wc * sq[2] * nz[2]) / q
                        val length = sqrt(mx * mx + my * my + mz * mz).coerceAtLeast(1e-4f)
                        val orientation = if (mz < 0f) -1f / length else 1f / length
                        mx *= orientation; my *= orientation; mz *= orientation
                        val diffuse = max(0f, mx * -.3f + my * .6f + mz * .74f)
                        (.55f + .45f * diffuse).coerceIn(.55f, 1f)
                    }
                    pixels[index] = (255 shl 24) or
                        ((r0 * light).toInt() shl 16) or ((g0 * light).toInt() shl 8) or (b0 * light).toInt()
                }
            }
        }
        return Frame(width, height, pixels)
    }

    fun baseColor(part: OralPart): Int = when (part) {
        OralPart.TONGUE -> 0xC97982
        OralPart.PALATE -> 0xD4ACA0
        OralPart.TEETH -> 0xF2E7D4
        OralPart.LIPS -> 0xA85C6A
        OralPart.TISSUE -> 0xCBA69B
    }
}

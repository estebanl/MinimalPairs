package com.minimalpairs.app.ui.reference

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Display-only sagittal tissue fill between the source tongue and lower-cover
 * contours. It is not a reconstructed muscle volume. Original surfaces stay intact.
 * The pinned exporter serializes LOWER_COVER_TWOSIDE as surface 1 of eleven.
 */
object OralSection {
    private const val epsilon = .00001f
    private data class Key(val y: Int, val z: Int)
    private fun key(p: OralPoint) = Key((p.y / epsilon).roundToInt(), (p.z / epsilon).roundToInt())
    private fun distance(a: OralPoint, b: OralPoint) = (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z)

    fun tongueFill(mesh: List<OralTriangle>, fromLeft: Boolean = true): List<OralTriangle> {
        fun contour(faces: List<OralTriangle>): List<OralPoint>? {
            val points = mutableMapOf<Key, OralPoint>()
            val edges = mutableMapOf<Key, MutableSet<Key>>()
            for (face in faces) {
                val corners = listOf(face.a, face.b, face.c)
                val hits = mutableMapOf<Key, OralPoint>()
                for (i in corners.indices) {
                    val a = corners[i]; val b = corners[(i + 1) % 3]
                    if (abs(a.x) < epsilon) hits[key(a)] = a.copy(x = 0f)
                    if (a.x * b.x < 0f) {
                        val t = a.x / (a.x - b.x)
                        val p = OralPoint(0f, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)
                        hits[key(p)] = p
                    }
                }
                if (hits.size != 2) continue
                points.putAll(hits)
                val (a, b) = hits.keys.toList()
                edges.getOrPut(a) { mutableSetOf() }.add(b)
                edges.getOrPut(b) { mutableSetOf() }.add(a)
            }
            val ends = edges.keys.filter { edges.getValue(it).size == 1 }
            if (ends.size != 2 || edges.values.any { it.size !in 1..2 }) return null
            var current = ends.minBy { points.getValue(it).y }
            var previous: Key? = null
            val result = mutableListOf<OralPoint>()
            val visited = mutableSetOf<Key>()
            while (visited.add(current)) {
                result += points.getValue(current)
                val next = edges.getValue(current).firstOrNull { it != previous } ?: break
                previous = current; current = next
            }
            return result.takeIf { visited.size == edges.size }
        }
        val tongue = contour(mesh.filter { it.part == OralPart.TONGUE && !it.isSection }) ?: return emptyList()
        val floor = contour(mesh.filter { it.sourceSurface == 1 && it.part == OralPart.TISSUE }) ?: return emptyList()
        data class OnFloor(val index: Int, val t: Float, val point: OralPoint)
        fun nearest(p: OralPoint): OnFloor = (0 until floor.lastIndex).map { i ->
            val a = floor[i]; val b = floor[i + 1]
            val t = (((p.y - a.y) * (b.y - a.y) + (p.z - a.z) * (b.z - a.z)) /
                distance(a, b).coerceAtLeast(epsilon * epsilon)).coerceIn(0f, 1f)
            OnFloor(i, t, OralPoint(0f, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t))
        }.minBy { distance(p, it.point) }
        val root = nearest(tongue.first()); val front = nearest(tongue.last())
        // Refuse unfamiliar/disconnected anatomy rather than inventing a long bridge.
        if (root.index + root.t >= front.index + front.t ||
            distance(root.point, tongue.first()) > .04f || distance(front.point, tongue.last()) > .04f) return emptyList()
        val raw = tongue + front.point + (front.index downTo root.index + 1).map { floor[it] } + root.point
        val polygon = mutableListOf<OralPoint>()
        for (p in raw) if (polygon.isEmpty() || distance(p, polygon.last()) > epsilon * epsilon) polygon += p
        if (polygon.size > 1 && distance(polygon.first(), polygon.last()) < epsilon * epsilon) polygon.removeAt(polygon.lastIndex)
        if (polygon.size < 3) return emptyList()
        fun cross(a: OralPoint, b: OralPoint, c: OralPoint) = (b.z - a.z) * (c.y - a.y) - (b.y - a.y) * (c.z - a.z)
        val area = polygon.indices.sumOf { i ->
            val a = polygon[i]; val b = polygon[(i + 1) % polygon.size]
            (a.z * b.y - b.z * a.y).toDouble()
        }
        val direction = if (area > 0) 1f else -1f
        val normal = OralPoint(if (fromLeft) -1f else 1f, 0f, 0f)
        val result = mutableListOf<OralTriangle>()
        // Ear clipping supports the concave root/body/blade outline. Fail closed
        // if the source contours do not form a triangulable simple section.
        while (polygon.size > 2) {
            val ear = polygon.indices.firstOrNull { i ->
                val a = polygon[(i + polygon.size - 1) % polygon.size]
                val b = polygon[i]; val c = polygon[(i + 1) % polygon.size]
                cross(a, b, c) * direction > epsilon * epsilon && polygon.none { p ->
                    p !== a && p !== b && p !== c && cross(a, b, p) * direction >= -epsilon * epsilon &&
                        cross(b, c, p) * direction >= -epsilon * epsilon && cross(c, a, p) * direction >= -epsilon * epsilon
                }
            } ?: return emptyList()
            result += OralTriangle(polygon[(ear + polygon.size - 1) % polygon.size], polygon[ear],
                polygon[(ear + 1) % polygon.size], OralPart.TONGUE, normal, normal, normal, isSection = true)
            polygon.removeAt(ear)
        }
        return result
    }
}

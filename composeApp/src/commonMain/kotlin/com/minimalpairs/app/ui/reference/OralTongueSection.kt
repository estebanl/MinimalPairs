package com.minimalpairs.app.ui.reference

import kotlin.math.abs
import kotlin.math.roundToInt

/** A section of an explicitly closed tongue mesh, never a bridge to the jaw or tract cover.
 * The cap uses only existing tongue/plane intersections. Open or branching contours fail closed:
 * no cap is returned rather than inventing anatomy. For authored derived volumes only.
 */
object OralTongueSection {
    private const val EPS = .00001f
    private data class Key(val y: Int, val z: Int)
    private fun key(p: OralPoint) = Key((p.y / EPS).roundToInt(), (p.z / EPS).roundToInt())
    private fun cross(a: OralPoint, b: OralPoint, c: OralPoint) =
        (b.y - a.y) * (c.z - a.z) - (b.z - a.z) * (c.y - a.y)

    fun cap(clipped: List<OralTriangle>, fromLeft: Boolean = true): List<OralTriangle> {
        val points = mutableMapOf<Key, OralPoint>()
        val edges = mutableSetOf<Set<Key>>()
        for (face in clipped) {
            if (face.part != OralPart.TONGUE || face.isSection) continue
            val v = listOf(face.a, face.b, face.c)
            val onPlane = v.filter { abs(it.x) < EPS }
            if (onPlane.size != 2) continue
            val normal = (face.b - face.a).cross(face.c - face.a)
            if (normal.x * normal.x + normal.y * normal.y + normal.z * normal.z < EPS * EPS * EPS) continue
            val a = key(onPlane[0]); val b = key(onPlane[1])
            if (a == b) continue
            points[a] = onPlane[0].copy(x = 0f); points[b] = onPlane[1].copy(x = 0f)
            val edge = setOf(a, b)
            // Internal duplicate edges are not part of a section boundary.
            if (!edges.add(edge)) edges.remove(edge)
        }
        if (edges.isEmpty()) return emptyList()
        val neighbors = mutableMapOf<Key, MutableSet<Key>>()
        edges.forEach { edge ->
            val (a, b) = edge.toList()
            neighbors.getOrPut(a) { mutableSetOf() }.add(b)
            neighbors.getOrPut(b) { mutableSetOf() }.add(a)
        }
        if (neighbors.values.any { it.size != 2 }) return emptyList()
        val remaining = neighbors.keys.toMutableSet()
        val loops = mutableListOf<List<OralPoint>>()
        while (remaining.isNotEmpty()) {
            val start = remaining.first(); var current = start; var previous: Key? = null
            val loop = mutableListOf<OralPoint>()
            do {
                if (!remaining.remove(current)) return emptyList()
                loop += points.getValue(current)
                val next = neighbors.getValue(current).first { it != previous }
                previous = current; current = next
            } while (current != start)
            if (loop.size < 3) return emptyList()
            loops += loop
        }
        // A tongue should have a simple cross-section; reject nested loops (holes) rather
        // than filling an airway/cavity. Separate nonnested islands can be capped independently.
        fun inside(p: OralPoint, poly: List<OralPoint>): Boolean {
            var result = false
            for (i in poly.indices) {
                val a = poly[i]; val b = poly[(i + 1) % poly.size]
                if ((a.z > p.z) != (b.z > p.z) && p.y < (b.y - a.y) * (p.z - a.z) / (b.z - a.z) + a.y) result = !result
            }
            return result
        }
        for (i in loops.indices) for (j in loops.indices) if (i != j && inside(loops[i][0], loops[j])) return emptyList()
        val result = mutableListOf<OralTriangle>()
        val n = OralPoint(if (fromLeft) -1f else 1f, 0f, 0f)
        for (original in loops) {
            val polygon = original.toMutableList()
            var changed = true
            while (changed && polygon.size > 3) {
                changed = false
                for (i in polygon.indices) if (abs(cross(polygon[(i + polygon.size - 1) % polygon.size], polygon[i], polygon[(i + 1) % polygon.size])) < EPS * EPS) {
                    polygon.removeAt(i); changed = true; break
                }
            }
            val area = polygon.indices.sumOf { i ->
                val a = polygon[i]; val b = polygon[(i + 1) % polygon.size]
                (a.y * b.z - b.y * a.z).toDouble()
            }
            if (abs(area) < EPS * EPS) return emptyList()
            if (area < 0) polygon.reverse() // ear clipping operates counterclockwise
            while (polygon.size > 3) {
                var found = false
                for (i in polygon.indices) {
                    val a = polygon[(i + polygon.size - 1) % polygon.size]; val b = polygon[i]; val c = polygon[(i + 1) % polygon.size]
                    if (cross(a, b, c) <= EPS * EPS) continue
                    if (polygon.any { p -> p != a && p != b && p != c &&
                        cross(a, b, p) >= -EPS * EPS && cross(b, c, p) >= -EPS * EPS && cross(c, a, p) >= -EPS * EPS }) continue
                    result += triangle(a, b, c, n, fromLeft)
                    polygon.removeAt(i); found = true; break
                }
                if (!found) return emptyList()
            }
            result += triangle(polygon[0], polygon[1], polygon[2], n, fromLeft)
        }
        return result
    }

    private fun triangle(a: OralPoint, b: OralPoint, c: OralPoint, n: OralPoint, fromLeft: Boolean) =
        if (fromLeft) OralTriangle(a, c, b, OralPart.TONGUE, n, n, n, isSection = true)
        else OralTriangle(a, b, c, OralPart.TONGUE, n, n, n, isSection = true)
}

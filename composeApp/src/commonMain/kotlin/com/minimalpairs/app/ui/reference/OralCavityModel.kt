package com.minimalpairs.app.ui.reference

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class OralPoint(val x: Float, val y: Float, val z: Float) {
    operator fun minus(other: OralPoint) = OralPoint(x - other.x, y - other.y, z - other.z)
    fun cross(other: OralPoint) = OralPoint(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x)
    fun normalized(): OralPoint {
        val length = sqrt(x * x + y * y + z * z).coerceAtLeast(.0001f)
        return OralPoint(x / length, y / length, z / length)
    }
}

enum class OralPart { PALATE, TONGUE, TEETH, LIPS, TISSUE }
data class OralTriangle(val a: OralPoint, val b: OralPoint, val c: OralPoint, val part: OralPart,
    val na: OralPoint, val nb: OralPoint, val nc: OralPoint,
    val sourceSurface: Int = -1, val isSection: Boolean = false)
data class OralView(val yaw: Float = 90f, val pitch: Float = 0f, val zoom: Float = 1f) {
    fun rotate(point: OralPoint): OralPoint {
        val yawRadians = yaw * PI.toFloat() / 180f
        val pitchRadians = pitch * PI.toFloat() / 180f
        val x = point.x * cos(yawRadians) + point.z * sin(yawRadians)
        val z = -point.x * sin(yawRadians) + point.z * cos(yawRadians)
        return OralPoint(x, point.y * cos(pitchRadians) - z * sin(pitchRadians),
            point.y * sin(pitchRadians) + z * cos(pitchRadians))
    }

    fun project(rotated: OralPoint): OralPoint {
        val perspective = 5f / (5f - rotated.z).coerceAtLeast(1f)
        return OralPoint(rotated.x * perspective * zoom, rotated.y * perspective * zoom, rotated.z)
    }
}

/** Clips against the midsagittal plane without moving any surviving anatomical vertex. */
object OralCavityModel {
    fun cutaway(mesh: List<OralTriangle>, enabled: Boolean, fromLeft: Boolean = true): List<OralTriangle> {
        if (!enabled) return mesh
        val sign = if (fromLeft) 1f else -1f
        data class Corner(val p: OralPoint, val n: OralPoint)
        fun mix(a: OralPoint, b: OralPoint, t: Float) = OralPoint(
            a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)
        return mesh.flatMap { face ->
            val source = listOf(Corner(face.a, face.na), Corner(face.b, face.nb), Corner(face.c, face.nc))
            val clipped = mutableListOf<Corner>()
            for (i in source.indices) {
                val a = source[i]
                val b = source[(i + 1) % source.size]
                val aInside = a.p.x * sign >= 0f
                val bInside = b.p.x * sign >= 0f
                if (aInside) clipped += a
                if (aInside != bInside) {
                    val t = a.p.x / (a.p.x - b.p.x)
                    clipped += Corner(mix(a.p, b.p, t).copy(x = 0f), mix(a.n, b.n, t).normalized())
                }
            }
            (1 until clipped.size - 1).map { i ->
                val a = clipped[0]; val b = clipped[i]; val c = clipped[i + 1]
                face.copy(a = a.p, b = b.p, c = c.p, na = a.n, nb = b.n, nc = c.n)
            }
        }
    }
}

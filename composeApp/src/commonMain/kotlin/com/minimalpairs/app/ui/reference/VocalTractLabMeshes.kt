package com.minimalpairs.app.ui.reference

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import minimalpairs.composeapp.generated.resources.Res

/** SAMPA presets from the upstream JD3 speaker; duration is not encoded in geometry. */
object VocalTractLabMeshes {
    val sourceShapes = mapOf("iː" to "i", "ɪ" to "I", "e" to "E", "ʊ" to "U", "uː" to "u", "ɔː" to "O")
    private val assetFiles = mapOf("i" to "fleece", "I" to "kit", "E" to "dress", "U" to "foot", "u" to "goose", "O" to "thought")
    private val cache = mutableMapOf<String, List<OralTriangle>>()
    private val mutex = Mutex()

    suspend fun load(phoneme: String): List<OralTriangle>? {
        val shape = sourceShapes[phoneme] ?: return null
        return mutex.withLock {
            cache[shape] ?: withContext(Dispatchers.Default) {
                decode(Res.readBytes("files/vtl/${assetFiles.getValue(shape)}.vtlm"))
            }.also { cache[shape] = it }
        }
    }

    /** Versioned, bounds-checked little-endian indexed meshes. Coordinates in source cm. */
    fun decode(bytes: ByteArray): List<OralTriangle> {
        require(bytes.size in 12..2_000_000) { "Invalid mesh size" }
        require(bytes.copyOfRange(0, 4).decodeToString() == "VTLM") { "Invalid mesh signature" }
        var offset = 4
        fun int(): Int {
            require(offset <= bytes.size - 4) { "Truncated mesh" }
            var value = 0
            repeat(4) { shift -> value = value or ((bytes[offset++].toInt() and 255) shl (shift * 8)) }
            return value
        }
        fun float(): Float = Float.fromBits(int()).also { require(it.isFinite()) { "Nonfinite mesh coordinate" } }
        fun point(): OralPoint {
            val anterior = float(); val superior = float(); val lateral = float()
            require(kotlin.math.abs(anterior) < 100 && kotlin.math.abs(superior) < 100 && kotlin.math.abs(lateral) < 100)
            // Fixed transform for every vowel: do not normalize each pose independently.
            return OralPoint(lateral * .23f, (superior + 3.7f) * .23f, (anterior - 2f) * .23f)
        }
        fun normal(): OralPoint {
            val anterior = float(); val superior = float(); val lateral = float()
            return OralPoint(lateral, superior, anterior).normalized()
        }
        require(int() == 1) { "Unsupported mesh version" }
        val count = int()
        require(count in 1..32)
        val faces = mutableListOf<OralTriangle>()
        repeat(count) {
            val part = OralPart.entries.getOrNull(int()) ?: error("Unknown mesh material")
            val vertexCount = int(); val triangleCount = int()
            require(vertexCount in 3..10000 && triangleCount in 1..20000)
            require(vertexCount * 12L + triangleCount * 48L <= bytes.size - offset)
            val points = List(vertexCount) { point() }
            repeat(triangleCount) {
                val indices = List(3) { int().also { require(it in points.indices) } }
                faces += OralTriangle(points[indices[0]], points[indices[1]], points[indices[2]], part, normal(), normal(), normal())
            }
        }
        require(offset == bytes.size) { "Unexpected trailing mesh data" }
        return faces
    }
}

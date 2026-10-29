package com.minimalpairs.app.ui.reference

import kotlin.math.sqrt

/** Artist-authored teaching motion. Durations do not describe or synchronize recorded speech. */
data class ArticulationPhase(val name: String, val assetPath: String, val transitionToNextMs: Int)
enum class ArticulationClipMode { LEGACY_FULL_TONGUE, AUTHORED_SECTIONS }
enum class ArticulationReviewStatus { EXPERIMENTAL, ACCEPTED }
data class ArticulationSpec(
    val modelFamily: String,
    val phoneme: String,
    val phases: List<ArticulationPhase>,
    val topologyHash: String,
    val reviewStatus: ArticulationReviewStatus = ArticulationReviewStatus.EXPERIMENTAL,
    val clipMode: ArticulationClipMode = ArticulationClipMode.LEGACY_FULL_TONGUE,
    val sectionTriangleIndices: List<Int> = emptyList()
) {
    init {
        require(modelFamily.isNotBlank() && phoneme.isNotBlank() && topologyHash.isNotBlank())
        require(sectionTriangleIndices.all { it >= 0 } && sectionTriangleIndices.distinct().size == sectionTriangleIndices.size)
        require(if (clipMode == ArticulationClipMode.AUTHORED_SECTIONS) sectionTriangleIndices.isNotEmpty() else sectionTriangleIndices.isEmpty())
        require(phases.size in 1..8 && phases.map { it.name }.distinct().size == phases.size)
        phases.forEachIndexed { index, phase ->
            require(phase.name.isNotBlank())
            require(phase.assetPath.startsWith("files/anatomy/") && phase.assetPath.endsWith(".vtlm"))
            require(".." !in phase.assetPath && '\\' !in phase.assetPath)
            require(if (index == phases.lastIndex) phase.transitionToNextMs == 0 else phase.transitionToNextMs in 50..5000)
        }
    }
}

/** No reviewed assets are registered yet. Existing static references remain the release fallback. */
object ReviewedArticulations {
    fun forPhoneme(phoneme: String): ArticulationSpec? = null
}

class ArticulationClip private constructor(val spec: ArticulationSpec, private val poses: List<List<OralTriangle>>) {
    val durationMs = spec.phases.sumOf { it.transitionToNextMs }

    fun sample(elapsedMs: Int): List<OralTriangle> {
        var remaining = elapsedMs.coerceIn(0, durationMs)
        for (index in 0 until poses.lastIndex) {
            val duration = spec.phases[index].transitionToNextMs
            if (remaining < duration) {
                if (remaining == 0) return poses[index]
                return interpolate(poses[index], poses[index + 1], remaining.toFloat() / duration)
            }
            remaining -= duration
        }
        return poses.last()
    }

    companion object {
        /** Decoder validates bounds/finite values first; compare actual topology, not an untrusted manifest hash. */
        fun decode(spec: ArticulationSpec, assets: List<ByteArray>): ArticulationClip {
            require(assets.size == spec.phases.size)
            val decoded = assets.map(VocalTractLabMeshes::decode)
            require(decoded.flatten().all { face ->
                listOf(face.na, face.nb, face.nc).all { it.x * it.x + it.y * it.y + it.z * it.z > .5f }
            }) { "Articulation normals must be nonzero" }
            require(decoded.flatten().all { face ->
                val cross = (face.b - face.a).cross(face.c - face.a)
                cross.x * cross.x + cross.y * cross.y + cross.z * cross.z > 1e-12f
            }) { "Articulation endpoints contain degenerate triangles" }
            val topology = assets.map(::topology)
            require(topology.all { it.contentEquals(topology.first()) }) { "Articulation topology differs between phases" }
            val sectionIds = spec.sectionTriangleIndices.toSet()
            require(sectionIds.all { it in decoded.first().indices }) { "Authored section triangle index is invalid" }
            val tagged = if (sectionIds.isEmpty()) decoded else decoded.map { pose ->
                pose.mapIndexed { index, face ->
                    if (index !in sectionIds) face else {
                        require(listOf(face.a, face.b, face.c).all { kotlin.math.abs(it.x) <= .00001f }) {
                            "Authored section must remain on the shared midline in every phase"
                        }
                        // Canonicalize the explicitly declared section only. The accepted
                        // exporter tolerance must not make a cap vanish from the other side.
                        face.copy(a = face.a.copy(x = 0f), b = face.b.copy(x = 0f), c = face.c.copy(x = 0f), isSection = true).also { section ->
                            val cross = (section.b - section.a).cross(section.c - section.a)
                            require(cross.x * cross.x + cross.y * cross.y + cross.z * cross.z > 1e-12f) {
                                "Authored section collapses on the midline"
                            }
                        }
                    }
                }
            }
            return ArticulationClip(spec, tagged)
        }

        private fun topology(bytes: ByteArray): IntArray {
            var offset = 8
            fun int(): Int {
                var value = 0
                repeat(4) { value = value or ((bytes[offset++].toInt() and 255) shl (it * 8)) }
                return value
            }
            val signature = mutableListOf<Int>()
            val surfaces = int()
            signature += surfaces
            repeat(surfaces) {
                val material = int(); val vertices = int(); val faces = int()
                signature += listOf(material, vertices, faces)
                offset += vertices * 12
                repeat(faces) {
                    repeat(3) { signature += int() }
                    offset += 36
                }
            }
            return signature.toIntArray()
        }

        private fun interpolate(a: List<OralTriangle>, b: List<OralTriangle>, t: Float): List<OralTriangle> {
            fun mix(a: OralPoint, b: OralPoint) = OralPoint(a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)
            fun normal(a: OralPoint, b: OralPoint): OralPoint {
                val blended = mix(a, b)
                val length = sqrt(blended.x * blended.x + blended.y * blended.y + blended.z * blended.z)
                // Antiparallel normals cannot normalize their zero midpoint. Use an endpoint deterministically.
                return if (length < .0001f) (if (t < .5f) a else b).normalized() else blended.normalized()
            }
            return a.indices.map { index ->
                val from = a[index]; val to = b[index]
                from.copy(a = mix(from.a, to.a), b = mix(from.b, to.b), c = mix(from.c, to.c),
                    na = normal(from.na, to.na), nb = normal(from.nb, to.nb), nc = normal(from.nc, to.nc))
            }
        }
    }
}

/** One selection generation owns loads and ticks. Old jobs cannot publish into a newer selection. */
data class ArticulationPlaybackState(
    val selection: String? = null,
    val generation: Long = 0,
    val clip: ArticulationClip? = null,
    val elapsedMs: Int = 0,
    val playing: Boolean = false,
    val failed: Boolean = false
) {
    fun select(key: String) = ArticulationPlaybackState(selection = key, generation = generation + 1)
    fun loaded(token: Long, value: ArticulationClip): ArticulationPlaybackState =
        if (token != generation || value.spec.phoneme != selection || value.spec.reviewStatus != ArticulationReviewStatus.ACCEPTED) this
        else copy(clip = value, elapsedMs = 0, playing = false, failed = false)
    fun loadFailed(token: Long) = if (token != generation) this else copy(clip = null, playing = false, failed = true)
    fun play(): ArticulationPlaybackState = if (clip == null || clip.durationMs == 0 || playing) this else
        copy(generation = generation + 1, playing = true, elapsedMs = if (elapsedMs >= clip.durationMs) 0 else elapsedMs)
    fun pause() = if (!playing) this else copy(generation = generation + 1, playing = false)
    fun reset() = copy(generation = generation + 1, elapsedMs = 0, playing = false)
    fun advance(token: Long, deltaMs: Int): ArticulationPlaybackState {
        if (token != generation || !playing || clip == null || deltaMs <= 0) return this
        val next = (elapsedMs.toLong() + deltaMs).coerceAtMost(clip.durationMs.toLong()).toInt()
        return copy(elapsedMs = next, playing = next < clip.durationMs)
    }
}

/** Includes clipping side: a yaw sign change must never retain the opposite half's pixels. */
fun articulationFrameKey(phoneme: String, modelFamily: String, generation: Long, cutaway: Boolean, fromLeft: Boolean): String =
    "$phoneme:$modelFamily:$generation:$cutaway:$fromLeft"

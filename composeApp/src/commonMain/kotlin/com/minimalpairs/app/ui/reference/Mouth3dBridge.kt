package com.minimalpairs.app.ui.reference

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class MouthPose { sheep, ship }
@Serializable
enum class MouthView { front, side }

/** This prototype is deliberately limited to this pair, not every use of these vowels. */
fun mouthPose(phoneme: String, word: String): MouthPose? = when {
    phoneme == "iː" && word.equals("sheep", true) -> MouthPose.sheep
    phoneme == "ɪ" && word.equals("ship", true) -> MouthPose.ship
    else -> null
}

@Serializable
data class Mouth3dState(
    val pose: MouthPose,
    val view: MouthView = MouthView.front,
    val opacity: Float = .5f,
    val active: Boolean = false,
    val reducedMotion: Boolean = true,
    val version: Int = 1,
    val type: String = "state"
) {
    init { require(opacity.isFinite() && opacity in 0f..1f) }
}

/** One session per web view. Keep only the latest selection until the ready handshake. */
class Mouth3dBridge(private val send: (String) -> Unit) {
    private val json = Json { encodeDefaults = true }
    var ready = false
        private set
    var closed = false
        private set
    private var latest: Mouth3dState? = null
    private var sent: Mouth3dState? = null
    fun update(state: Mouth3dState) {
        if (closed) return
        latest = state
        flush()
    }
    fun onReady() {
        if (closed || ready) return
        ready = true
        flush()
    }
    private fun flush() {
        val state = latest ?: return
        if (ready && state != sent) {
            send(json.encodeToString(state))
            sent = state
        }
    }
    fun dispose() {
        if (closed) return
        closed = true
        // The JS entry point can exist while texture decoding is still loading.
        // Native uses optional chaining, so cleanup is safe even before ready.
        send("""{"version":1,"type":"dispose"}""")
        latest = null
        sent = null
    }
}

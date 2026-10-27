package com.minimalpairs.app.ui.navigation

import com.minimalpairs.app.core.hal.AudioPlayer
import kotlinx.coroutines.*

/** Owns both pending audio loading and playback so capture can stop either one. */
internal class ModulePlaybackSession(
    private val scope: CoroutineScope,
    private val player: AudioPlayer,
    private val onPlaying: (Boolean) -> Unit
) {
    private var operation: Job? = null

    fun play(canPlay: () -> Boolean, load: suspend () -> ShortArray?) {
        val previous = operation
        previous?.cancel()
        operation = scope.launch {
            previous?.join()
            // An outcome requested at the end of evaluation starts after evaluation returns.
            yield()
            if (!canPlay()) return@launch
            val samples = load() ?: return@launch
            currentCoroutineContext().ensureActive()
            if (!canPlay()) return@launch
            onPlaying(true)
            player.play(samples) { onPlaying(false) }
        }
    }

    suspend fun stop() {
        val pending = operation
        operation = null
        pending?.cancelAndJoin()
        try { player.stop() } finally { onPlaying(false) }
    }
}

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
    private var generation = 0L

    enum class Failure { MISSING_AUDIO, LOAD_FAILED, PLAYBACK_FAILED }

    fun play(canPlay: () -> Boolean, load: suspend () -> ShortArray?) = play(canPlay, {}, load)

    fun play(
        canPlay: () -> Boolean,
        onFailure: (Failure) -> Unit,
        load: suspend () -> ShortArray?
    ) {
        val token = ++generation
        val previous = operation
        previous?.cancel()
        operation = scope.launch {
            previous?.join()
            // An outcome requested at the end of evaluation starts after evaluation returns.
            yield()
            if (!canPlay()) return@launch
            var finished = false
            fun fail(reason: Failure) {
                if (token != generation || finished) return
                finished = true
                onPlaying(false)
                onFailure(reason)
            }
            try {
                val samples = try {
                    load()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    fail(Failure.LOAD_FAILED)
                    return@launch
                }
                currentCoroutineContext().ensureActive()
                if (!canPlay()) return@launch
                if (samples == null || samples.isEmpty()) {
                    fail(Failure.MISSING_AUDIO)
                    return@launch
                }
                onPlaying(true)
                player.playWithFeedback(samples,
                    onComplete = {
                        scope.launch {
                            if (token == generation && !finished) {
                                finished = true
                                onPlaying(false)
                            }
                        }
                    },
                    onFailure = { scope.launch { fail(Failure.PLAYBACK_FAILED) } }
                )
            } catch (cancelled: CancellationException) {
                if (token == generation) onPlaying(false)
                throw cancelled
            } catch (_: Exception) {
                fail(Failure.PLAYBACK_FAILED)
            }
        }
    }

    suspend fun stop() {
        generation++
        val pending = operation
        operation = null
        pending?.cancel()
        // Close the output before joining: a native write/drain can block until
        // the player is stopped, even after its coroutine has been cancelled.
        try {
            player.stop()
        } finally {
            try { pending?.join() } finally { onPlaying(false) }
        }
    }
}

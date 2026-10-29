package com.minimalpairs.app.ui.navigation

import com.minimalpairs.app.core.hal.AudioRecorder
import com.minimalpairs.app.core.hal.AudioRecorderState
import kotlinx.coroutines.*

/** Confines recording buffers to the component dispatcher and cancels unfinished evaluations. */
internal class ModuleRecordingSession(
    private val scope: CoroutineScope,
    private val recorder: AudioRecorder,
    private val onRecording: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
    private val stopPlayback: suspend () -> Unit
) {
    private var operation: Job? = null
    private var cleanup: Job? = null
    private val samples = mutableListOf<Short>()
    private var accepting = false
    private var generation = 0L
    val busy: Boolean get() = operation?.isActive == true || cleanup?.isActive == true

    /** Let a following interaction wait for capture/evaluation teardown instead of dropping its action. */
    suspend fun awaitIdle() {
        cleanup?.join()
        operation?.join()
    }

    fun toggle(evaluate: suspend (ShortArray) -> Unit) {
        if (busy) return
        operation = scope.launch {
            try {
                if (accepting) {
                    accepting = false
                    generation++
                    onRecording(false)
                    check(recorder.stop())
                    evaluate(samples.toShortArray())
                } else {
                    samples.clear()
                    stopPlayback()
                    if (recorder.state.value != AudioRecorderState.READY) {
                        recorder.release()
                        check(recorder.prepare())
                    }
                    val token = ++generation
                    accepting = true
                    val started = recorder.start { buffer ->
                        val copy = buffer.copyOf()
                        scope.launch {
                            if (!accepting || token != generation) return@launch
                            val remaining = recorder.sampleRate * 30 - samples.size
                            samples.addAll(copy.take(remaining.coerceAtLeast(0)))
                            if (remaining <= copy.size) toggle(evaluate)
                        }
                    }
                    check(started)
                    onRecording(true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                accepting = false
                generation++
                onRecording(false)
                recorder.release()
                onError("Couldn't finish this recording. Check microphone access and try again.")
            }
        }
    }

    fun cancel() {
        accepting = false
        generation++
        samples.clear()
        onRecording(false)
        val pending = operation
        pending?.cancel()
        val previousCleanup = cleanup
        cleanup = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                previousCleanup?.join()
                pending?.join()
                try { stopPlayback() } finally { recorder.release() }
            }
        }
    }
}

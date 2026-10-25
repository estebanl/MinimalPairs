package com.minimalpairs.app.core.hal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Deterministic AudioRecorder implementation matching the exact 5-state machine
 * and 512-frame buffer streaming contract for tests and simulated microphone injection.
 */
class SimulatedAudioRecorder(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val frameProvider: ((frameIndex: Int) -> ShortArray)? = null
) : AudioRecorder {

    private val _state = MutableStateFlow(AudioRecorderState.UNINITIALIZED)
    override val state: StateFlow<AudioRecorderState> = _state.asStateFlow()

    private var streamingJob: Job? = null
    private var activeCallback: ((ShortArray) -> Unit)? = null

    override suspend fun prepare(): Boolean {
        if (_state.value == AudioRecorderState.ERROR) return false
        _state.value = AudioRecorderState.READY
        return true
    }

    override suspend fun start(onBuffer: (ShortArray) -> Unit): Boolean {
        if (_state.value != AudioRecorderState.READY && _state.value != AudioRecorderState.PAUSED) {
            _state.value = AudioRecorderState.ERROR
            return false
        }
        activeCallback = onBuffer
        _state.value = AudioRecorderState.RECORDING

        streamingJob?.cancel()
        streamingJob = scope.launch {
            var frameIdx = 0
            while (isActive && _state.value == AudioRecorderState.RECORDING) {
                // 512 frames @ 16kHz = 32ms per packet
                val buffer = frameProvider?.invoke(frameIdx) ?: ShortArray(512)
                activeCallback?.invoke(buffer)
                frameIdx++
                delay(32)
            }
        }
        return true
    }

    override suspend fun pause(): Boolean {
        if (_state.value != AudioRecorderState.RECORDING) return false
        _state.value = AudioRecorderState.PAUSED
        streamingJob?.cancel()
        return true
    }

    override suspend fun resume(): Boolean {
        if (_state.value != AudioRecorderState.PAUSED) return false
        val cb = activeCallback ?: return false
        return start(cb)
    }

    override suspend fun stop(): Boolean {
        if (_state.value == AudioRecorderState.UNINITIALIZED) return false
        streamingJob?.cancel()
        _state.value = AudioRecorderState.READY
        return true
    }

    override suspend fun release() {
        streamingJob?.cancel()
        activeCallback = null
        _state.value = AudioRecorderState.UNINITIALIZED
    }

    fun triggerError() {
        streamingJob?.cancel()
        _state.value = AudioRecorderState.ERROR
    }
}

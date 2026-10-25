package com.minimalpairs.app.core.hal

import kotlinx.coroutines.flow.StateFlow

enum class AudioRecorderState {
    UNINITIALIZED,
    READY,
    RECORDING,
    PAUSED,
    ERROR
}

interface AudioRecorder {
    val state: StateFlow<AudioRecorderState>
    val sampleRate: Int get() = 16000
    val bufferFrames: Int get() = 512

    suspend fun prepare(): Boolean
    suspend fun start(onBuffer: (ShortArray) -> Unit): Boolean
    suspend fun pause(): Boolean
    suspend fun resume(): Boolean
    suspend fun stop(): Boolean
    suspend fun release()
}

interface AudioPlayer {
    suspend fun play(samples: ShortArray, onComplete: () -> Unit = {})
    suspend fun stop()
    fun isPlaying(): Boolean
}

expect fun createAudioRecorder(): AudioRecorder

expect fun createAudioPlayer(): AudioPlayer

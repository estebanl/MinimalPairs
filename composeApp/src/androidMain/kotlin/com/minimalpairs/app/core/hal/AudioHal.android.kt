package com.minimalpairs.app.core.hal

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AndroidAudioRecorder : AudioRecorder {
    private val _state = MutableStateFlow(AudioRecorderState.UNINITIALIZED)
    override val state: StateFlow<AudioRecorderState> = _state.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var activeCallback: ((ShortArray) -> Unit)? = null

    override val sampleRate: Int = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    override suspend fun prepare(): Boolean = withContext(Dispatchers.IO) {
        try {
            val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = minBufSize.coerceAtLeast(1024)
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                _state.value = AudioRecorderState.ERROR
                return@withContext false
            }
            audioRecord = record
            _state.value = AudioRecorderState.READY
            true
        } catch (e: Exception) {
            _state.value = AudioRecorderState.ERROR
            false
        }
    }

    override suspend fun start(onBuffer: (ShortArray) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (_state.value != AudioRecorderState.READY && _state.value != AudioRecorderState.PAUSED) {
            return@withContext false
        }
        val record = audioRecord ?: return@withContext false
        activeCallback = onBuffer
        record.startRecording()
        _state.value = AudioRecorderState.RECORDING

        recordJob?.cancel()
        recordJob = scope.launch {
            val buffer = ShortArray(512)
            while (isActive && _state.value == AudioRecorderState.RECORDING) {
                val readCount = record.read(buffer, 0, buffer.size)
                if (readCount > 0) {
                    val packet = if (readCount == 512) buffer.copyOf() else buffer.copyOf(readCount)
                    activeCallback?.invoke(packet)
                }
            }
        }
        true
    }

    override suspend fun pause(): Boolean {
        if (_state.value != AudioRecorderState.RECORDING) return false
        _state.value = AudioRecorderState.PAUSED
        audioRecord?.stop()
        recordJob?.cancel()
        return true
    }

    override suspend fun resume(): Boolean {
        if (_state.value != AudioRecorderState.PAUSED) return false
        val cb = activeCallback ?: return false
        return start(cb)
    }

    override suspend fun stop(): Boolean {
        if (_state.value == AudioRecorderState.UNINITIALIZED) return false
        recordJob?.cancel()
        audioRecord?.stop()
        _state.value = AudioRecorderState.READY
        return true
    }

    override suspend fun release() {
        recordJob?.cancel()
        audioRecord?.release()
        audioRecord = null
        activeCallback = null
        _state.value = AudioRecorderState.UNINITIALIZED
    }
}

class AndroidAudioPlayer : AudioPlayer {
    private val outputMutex = Mutex()
    private var audioTrack: AudioTrack? = null
    private var playing = false

    override suspend fun play(samples: ShortArray, onComplete: () -> Unit) {
        playWithFeedback(samples, onComplete, onFailure = onComplete)
    }

    override suspend fun playWithFeedback(
        samples: ShortArray,
        onComplete: () -> Unit,
        onFailure: () -> Unit
    ) = withContext(Dispatchers.IO) {
        stop()
        try {
            val sampleRate = 16000
            outputMutex.withLock {
                currentCoroutineContext().ensureActive()
                val track = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                    samples.size * 2,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                audioTrack = track
                check(track.state == AudioTrack.STATE_INITIALIZED)
                check(track.write(samples, 0, samples.size) == samples.size)
                track.play()
                playing = true
            }

            // Wait for duration
            val durationMs = (samples.size * 1000L) / sampleRate
            kotlinx.coroutines.delay(durationMs + 50)

            stop()
            withContext(Dispatchers.Default) {
                onComplete()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            playing = false
            withContext(Dispatchers.Default) {
                onFailure()
            }
        } finally {
            withContext(NonCancellable) { runCatching { stop() } }
        }
    }

    override suspend fun stop(): Unit = withContext(Dispatchers.IO) {
        outputMutex.withLock {
            playing = false
            val track = audioTrack
            audioTrack = null
            try { track?.stop() } finally { track?.release() }
        }
    }

    override fun isPlaying(): Boolean = playing
}

actual fun createAudioRecorder(): AudioRecorder = AndroidAudioRecorder()

actual fun createAudioPlayer(): AudioPlayer = AndroidAudioPlayer()

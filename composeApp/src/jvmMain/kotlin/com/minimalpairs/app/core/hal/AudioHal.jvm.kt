package com.minimalpairs.app.core.hal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine

class JvmAudioRecorder(private val inputDeviceName: String? = null) : AudioRecorder {
    private val _state = MutableStateFlow(AudioRecorderState.UNINITIALIZED)
    override val state: StateFlow<AudioRecorderState> = _state.asStateFlow()

    private var targetLine: TargetDataLine? = null
    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var activeCallback: ((ShortArray) -> Unit)? = null

    private val audioFormat = AudioFormat(
        16000.0f,
        16,
        1,
        true,
        false // Little endian
    )

    override suspend fun prepare(): Boolean = withContext(Dispatchers.IO) {
        try {
            val info = DataLine.Info(TargetDataLine::class.java, audioFormat)
            if (!AudioSystem.isLineSupported(info)) {
                _state.value = AudioRecorderState.ERROR
                return@withContext false
            }
            val line = if (inputDeviceName == null) AudioSystem.getLine(info) as TargetDataLine else {
                val device = AudioSystem.getMixerInfo().first { it.name == inputDeviceName &&
                    AudioSystem.getMixer(it).isLineSupported(info) }
                AudioSystem.getMixer(device).getLine(info) as TargetDataLine
            }
            line.open(audioFormat, 1024)
            targetLine = line
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
        val line = targetLine ?: return@withContext false
        activeCallback = onBuffer
        line.flush()
        line.start()
        _state.value = AudioRecorderState.RECORDING

        recordJob?.cancel()
        recordJob = scope.launch {
            val byteBuffer = ByteArray(1024) // 512 frames * 2 bytes
            val shortBuffer = ShortArray(512)
            while (isActive && _state.value == AudioRecorderState.RECORDING) {
                val bytesRead = line.read(byteBuffer, 0, byteBuffer.size)
                if (bytesRead > 0) {
                    val frames = bytesRead / 2
                    for (i in 0 until frames) {
                        val low = byteBuffer[i * 2].toInt() and 0xff
                        val high = byteBuffer[i * 2 + 1].toInt() shl 8
                        shortBuffer[i] = (low or high).toShort()
                    }
                    val packet = if (frames == 512) shortBuffer.copyOf() else shortBuffer.copyOf(frames)
                    activeCallback?.invoke(packet)
                }
            }
        }
        true
    }

    override suspend fun pause(): Boolean {
        if (_state.value != AudioRecorderState.RECORDING) return false
        _state.value = AudioRecorderState.PAUSED
        targetLine?.stop()
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
        targetLine?.stop()
        targetLine?.flush()
        _state.value = AudioRecorderState.READY
        return true
    }

    override suspend fun release() {
        recordJob?.cancel()
        targetLine?.stop()
        targetLine?.close()
        targetLine = null
        activeCallback = null
        _state.value = AudioRecorderState.UNINITIALIZED
    }
}

class JvmAudioPlayer : AudioPlayer {
    private var sourceLine: SourceDataLine? = null
    private var playing = false

    private val audioFormat = AudioFormat(16000.0f, 16, 1, true, false)

    override suspend fun play(samples: ShortArray, onComplete: () -> Unit) = withContext(Dispatchers.IO) {
        stop()
        try {
            val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
            val line = AudioSystem.getLine(info) as SourceDataLine
            line.open(audioFormat)
            line.start()
            sourceLine = line
            playing = true

            val byteBuffer = ByteArray(samples.size * 2)
            for (i in samples.indices) {
                val s = samples[i].toInt()
                byteBuffer[i * 2] = (s and 0xff).toByte()
                byteBuffer[i * 2 + 1] = ((s shr 8) and 0xff).toByte()
            }

            line.write(byteBuffer, 0, byteBuffer.size)
            line.drain()
            line.stop()
            line.close()
            sourceLine = null
            playing = false
            withContext(Dispatchers.Default) {
                onComplete()
            }
        } catch (e: Exception) {
            playing = false
            withContext(Dispatchers.Default) {
                onComplete()
            }
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        playing = false
        sourceLine?.stop()
        sourceLine?.flush()
        sourceLine?.close()
        sourceLine = null
    }

    override fun isPlaying(): Boolean = playing
}

actual fun createAudioRecorder(): AudioRecorder = JvmAudioRecorder()

actual fun createAudioPlayer(): AudioPlayer = JvmAudioPlayer()

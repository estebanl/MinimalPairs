package com.minimalpairs.app.core.hal

import com.minimalpairs.app.core.dsp.WavCodec
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.value
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioConverter
import platform.AVFAudio.AVAudioConverterInputStatus_NoDataNow
import platform.AVFAudio.AVAudioConverterInputStatus_HaveData
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.AVFAudio.AVAudioPCMBuffer
import kotlin.coroutines.resume
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.darwin.NSObject
import platform.Foundation.create
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalForeignApi::class)
class IosAudioRecorder : AudioRecorder {
    private val _state = MutableStateFlow(AudioRecorderState.UNINITIALIZED)
    override val state: StateFlow<AudioRecorderState> = _state.asStateFlow()
    private var engine: AVAudioEngine? = null
    private var hasTap = false
    private var sessionActive = false
    private var activeCallback: ((ShortArray) -> Unit)? = null

    override suspend fun prepare(): Boolean {
        release()
        val session = AVAudioSession.sharedInstance()
        val granted = suspendCancellableCoroutine<Boolean> { continuation ->
            session.requestRecordPermission { allowed ->
                if (continuation.isActive) continuation.resume(allowed)
            }
        }
        if (!granted) {
            _state.value = AudioRecorderState.ERROR
            return false
        }
        if (!session.setCategory(AVAudioSessionCategoryPlayAndRecord,
                withOptions = AVAudioSessionCategoryOptionDefaultToSpeaker, error = null) ||
            !session.setActive(true, error = null)) {
            _state.value = AudioRecorderState.ERROR
            return false
        }
        sessionActive = true
        engine = AVAudioEngine()
        _state.value = AudioRecorderState.READY
        return true
    }

    override suspend fun start(onBuffer: (ShortArray) -> Unit): Boolean {
        if (_state.value != AudioRecorderState.READY && _state.value != AudioRecorderState.PAUSED) return false
        val audioEngine = engine ?: return false
        val input = audioEngine.inputNode
        val inputFormat = input.outputFormatForBus(0u)
        if (inputFormat.sampleRate <= 0.0 || inputFormat.channelCount == 0u) {
            _state.value = AudioRecorderState.ERROR
            return false
        }
        val outputFormat = AVAudioFormat(commonFormat = AVAudioPCMFormatInt16,
            sampleRate = 16000.0, channels = 1u, interleaved = false)
        val converter = AVAudioConverter(fromFormat = inputFormat, toFormat = outputFormat)
        activeCallback = onBuffer
        var packet = ShortArray(bufferFrames)
        var packetSize = 0
        input.installTapOnBus(0u, bufferSize = 1024u, format = inputFormat) { buffer, _ ->
            if (buffer != null && _state.value == AudioRecorderState.RECORDING) {
                val capacity = (buffer.frameLength.toDouble() * 16000.0 / inputFormat.sampleRate).toUInt() + 32u
                val output = AVAudioPCMBuffer(pCMFormat = outputFormat, frameCapacity = capacity)
                var supplied = false
                converter.convertToBuffer(output, error = null) { _, status ->
                    if (supplied) {
                        status?.pointed?.value = AVAudioConverterInputStatus_NoDataNow
                        null
                    } else {
                        supplied = true
                        status?.pointed?.value = AVAudioConverterInputStatus_HaveData
                        buffer
                    }
                }
                val samples = output.int16ChannelData?.get(0)
                if (samples != null) {
                    for (i in 0 until output.frameLength.toInt()) {
                        packet[packetSize++] = samples[i]
                        if (packetSize == bufferFrames) {
                            onBuffer(packet)
                            packet = ShortArray(bufferFrames)
                            packetSize = 0
                        }
                    }
                }
            }
        }
        hasTap = true
        _state.value = AudioRecorderState.RECORDING
        audioEngine.prepare()
        if (!audioEngine.startAndReturnError(null)) {
            removeTap()
            _state.value = AudioRecorderState.ERROR
            return false
        }
        return true
    }

    private fun removeTap() {
        engine?.stop()
        if (hasTap) engine?.inputNode?.removeTapOnBus(0u)
        hasTap = false
    }

    override suspend fun pause(): Boolean {
        if (_state.value != AudioRecorderState.RECORDING) return false
        _state.value = AudioRecorderState.PAUSED
        removeTap()
        return true
    }

    override suspend fun resume(): Boolean {
        if (_state.value != AudioRecorderState.PAUSED) return false
        return activeCallback?.let { start(it) } ?: false
    }

    override suspend fun stop(): Boolean {
        if (_state.value == AudioRecorderState.UNINITIALIZED) return false
        _state.value = AudioRecorderState.READY
        removeTap()
        return true
    }

    override suspend fun release() {
        _state.value = AudioRecorderState.UNINITIALIZED
        removeTap()
        activeCallback = null
        engine = null
        if (sessionActive) AVAudioSession.sharedInstance().setActive(false, error = null)
        sessionActive = false
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosAudioPlayer : AudioPlayer {
    private var playing = false
    private var activePlayer: AVAudioPlayer? = null
    // AVAudioPlayer keeps a weak delegate; retain it until completion or cancellation.
    private var activeDelegate: AVAudioPlayerDelegateProtocol? = null

    override suspend fun play(samples: ShortArray, onComplete: () -> Unit) {
        playWithFeedback(samples, onComplete, onFailure = onComplete)
    }

    override suspend fun playWithFeedback(
        samples: ShortArray,
        onComplete: () -> Unit,
        onFailure: () -> Unit
    ) {
        stop()
        if (samples.isEmpty()) {
            onFailure()
            return
        }
        try {
            val session = AVAudioSession.sharedInstance()
            check(session.setCategory(
                AVAudioSessionCategoryPlayAndRecord,
                withOptions = AVAudioSessionCategoryOptionDefaultToSpeaker,
                error = null
            ))
            check(session.setActive(true, error = null))

            val wavBytes = WavCodec.encodePcmToWav(samples)
            val nsData = wavBytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = wavBytes.size.toULong())
            }
            val player = AVAudioPlayer(data = nsData, error = null)
            activePlayer = player
            check(player.prepareToPlay())
            val durationMs = ((samples.size * 1000L) / 16000L).coerceAtLeast(50L)
            withTimeout(durationMs + 2_000L) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val delegate = object : NSObject(), AVAudioPlayerDelegateProtocol {
                        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
                            if (!continuation.isActive) return
                            if (successfully) continuation.resume(Unit)
                            else continuation.resumeWithException(IllegalStateException("Playback interrupted"))
                        }

                        override fun audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer, error: NSError?) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(IllegalStateException("Audio decoding failed"))
                            }
                        }
                    }
                    activeDelegate = delegate
                    player.delegate = delegate
                    continuation.invokeOnCancellation { player.stop() }
                    playing = true
                    check(player.play())
                }
            }
            playing = false
            onComplete()
        } catch (_: TimeoutCancellationException) {
            playing = false
            onFailure()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            playing = false
            onFailure()
        } finally {
            withContext(NonCancellable) { runCatching { stop() } }
        }
    }

    override suspend fun stop() {
        try {
            activePlayer?.stop()
        } catch (_: Throwable) {
        }
        activePlayer = null
        activeDelegate = null
        playing = false
    }

    override fun isPlaying(): Boolean = playing
}

actual fun createAudioRecorder(): AudioRecorder = IosAudioRecorder()

actual fun createAudioPlayer(): AudioPlayer = IosAudioPlayer()

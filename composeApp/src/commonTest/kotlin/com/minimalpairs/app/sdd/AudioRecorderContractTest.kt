package com.minimalpairs.app.sdd

import com.minimalpairs.app.core.hal.AudioRecorderState
import com.minimalpairs.app.core.hal.SimulatedAudioRecorder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AudioRecorderContractTest {

    @Test
    fun testAudioRecorderLifecycleTransitions() = runTest {
        val recorder = SimulatedAudioRecorder(this)
        assertEquals(AudioRecorderState.UNINITIALIZED, recorder.state.value)

        // 1. prepare() -> READY
        val prepared = recorder.prepare()
        assertTrue(prepared)
        assertEquals(AudioRecorderState.READY, recorder.state.value)

        // 2. start() -> RECORDING & receives 512-frame buffers
        val receivedBuffers = mutableListOf<ShortArray>()
        val started = recorder.start { buffer ->
            receivedBuffers.add(buffer)
        }
        assertTrue(started)
        assertEquals(AudioRecorderState.RECORDING, recorder.state.value)

        // Advance 100ms: at 32ms per buffer, should receive ~3 packets of 512 frames
        advanceTimeBy(100)
        assertTrue(receivedBuffers.isNotEmpty())
        assertEquals(512, receivedBuffers.first().size, "Buffer size must strictly be 512 frames")

        // 3. pause() -> PAUSED
        val paused = recorder.pause()
        assertTrue(paused)
        assertEquals(AudioRecorderState.PAUSED, recorder.state.value)

        // 4. resume() -> RECORDING
        val resumed = recorder.resume()
        assertTrue(resumed)
        assertEquals(AudioRecorderState.RECORDING, recorder.state.value)

        // 5. stop() -> READY
        val stopped = recorder.stop()
        assertTrue(stopped)
        assertEquals(AudioRecorderState.READY, recorder.state.value)

        // 6. release() -> UNINITIALIZED
        recorder.release()
        assertEquals(AudioRecorderState.UNINITIALIZED, recorder.state.value)
    }

    @Test
    fun testAudioRecorderHardwareErrorTransition() = runTest {
        val recorder = SimulatedAudioRecorder(this)
        recorder.prepare()
        recorder.triggerError()
        assertEquals(AudioRecorderState.ERROR, recorder.state.value)

        // Once in ERROR state, prepare fails
        val prepared = recorder.prepare()
        assertTrue(!prepared)
    }
}

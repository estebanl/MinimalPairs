package com.minimalpairs.app.ui.navigation

import com.minimalpairs.app.core.hal.AudioPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ModulePlaybackSessionTest {
    @Test
    fun stoppingPendingLoadPreventsLateReferenceAudioDuringCapture() = runTest {
        val gate = CompletableDeferred<Unit>()
        val player = Player()
        var playing = false
        val playback = ModulePlaybackSession(this, player) { playing = it }
        playback.play({ true }) { gate.await(); shortArrayOf(1, 2, 3) }
        runCurrent()
        playback.stop()
        gate.complete(Unit)
        runCurrent()
        assertEquals(0, player.plays)
        assertFalse(playing)
        assertEquals(1, player.stops)
        playback.play({ true }) { shortArrayOf(1) }
        runCurrent()
        assertEquals(1, player.plays)
    }

    @Test
    fun recordingOrEvaluationBlocksPlaybackAndRechecksAfterLoading() = runTest {
        val gate = CompletableDeferred<Unit>()
        val player = Player()
        var allowed = false
        var loads = 0
        val playback = ModulePlaybackSession(this, player) {}
        playback.play({ allowed }) { loads++; shortArrayOf(1) }
        runCurrent()
        assertEquals(0, loads)
        allowed = true
        playback.play({ allowed }) { loads++; gate.await(); shortArrayOf(1) }
        runCurrent()
        allowed = false
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, loads)
        assertEquals(0, player.plays)
    }

    private class Player : AudioPlayer {
        var plays = 0
        var stops = 0
        override suspend fun play(samples: ShortArray, onComplete: () -> Unit) { plays++; onComplete() }
        override suspend fun stop() { stops++ }
        override fun isPlaying() = false
    }
}

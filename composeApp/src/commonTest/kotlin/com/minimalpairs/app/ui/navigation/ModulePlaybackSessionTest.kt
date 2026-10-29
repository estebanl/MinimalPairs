package com.minimalpairs.app.ui.navigation

import com.minimalpairs.app.core.hal.AudioPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ModulePlaybackSessionTest {
    @Test
    fun stoppingPlaybackReleasesNativeOutputBeforeWaitingForItsCoroutine() = runTest {
        val released = CompletableDeferred<Unit>()
        var playing = false
        val player = object : AudioPlayer {
            override suspend fun play(samples: ShortArray, onComplete: () -> Unit) {
                // Simulate a native write that cannot finish until its output closes.
                withContext(NonCancellable) { released.await() }
            }
            override suspend fun stop() { released.complete(Unit) }
            override fun isPlaying() = playing
        }
        val playback = ModulePlaybackSession(this, player) { playing = it }
        playback.play({ true }) { shortArrayOf(1) }
        runCurrent()
        assertTrue(playing)
        val stopping = launch { playback.stop() }
        try {
            runCurrent()
            assertTrue(stopping.isCompleted, "Stopping must close the native output before joining playback")
            assertFalse(playing)
        } finally {
            released.complete(Unit)
            runCurrent()
        }
    }

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

    @Test
    fun unavailableAndFailedLoadsReportDistinctErrorsAndCanRetry() = runTest {
        val failures = mutableListOf<ModulePlaybackSession.Failure>()
        val player = Player()
        var playing = false
        val playback = ModulePlaybackSession(this, player) { playing = it }
        playback.play({ true }, failures::add) { null }
        runCurrent()
        playback.play({ true }, failures::add) { shortArrayOf() }
        runCurrent()
        playback.play({ true }, failures::add) { error("Cannot decode audio") }
        runCurrent()
        assertEquals(listOf(ModulePlaybackSession.Failure.MISSING_AUDIO,
            ModulePlaybackSession.Failure.MISSING_AUDIO, ModulePlaybackSession.Failure.LOAD_FAILED), failures)
        assertFalse(playing)
        playback.play({ true }, failures::add) { shortArrayOf(1) }
        runCurrent()
        assertEquals(1, player.plays)
        assertFalse(playing)
    }

    @Test
    fun synchronousAndAsynchronousDeviceFailuresReturnToIdleAndAllowRetry() = runTest {
        val failures = mutableListOf<ModulePlaybackSession.Failure>()
        val player = ReportingPlayer()
        var playing = false
        val playback = ModulePlaybackSession(this, player) { playing = it }
        player.throwOnPlay = true
        playback.play({ true }, failures::add) { shortArrayOf(1) }
        runCurrent()
        assertFalse(playing)
        player.throwOnPlay = false
        playback.play({ true }, failures::add) { shortArrayOf(1) }
        runCurrent()
        assertTrue(playing)
        player.fail!!()
        runCurrent()
        assertFalse(playing)
        assertEquals(List(2) { ModulePlaybackSession.Failure.PLAYBACK_FAILED }, failures)
        playback.play({ true }, failures::add) { shortArrayOf(1) }
        runCurrent()
        player.complete!!()
        runCurrent()
        assertFalse(playing)
        assertEquals(2, failures.size)
    }

    @Test
    fun cancelledLoadsAndLateDeviceCallbacksNeverReportErrors() = runTest {
        val failures = mutableListOf<ModulePlaybackSession.Failure>()
        val player = ReportingPlayer()
        var playing = false
        val playback = ModulePlaybackSession(this, player) { playing = it }
        playback.play({ true }, failures::add) { throw CancellationException("Stopped") }
        runCurrent()
        assertTrue(failures.isEmpty())
        playback.play({ true }, failures::add) { shortArrayOf(1) }
        runCurrent()
        val staleFailure = player.fail!!
        val staleCompletion = player.complete!!
        playback.stop()
        playback.play({ true }, failures::add) { shortArrayOf(2) }
        runCurrent()
        staleFailure()
        staleCompletion()
        runCurrent()
        assertTrue(playing)
        assertTrue(failures.isEmpty())
        playback.stop()
        assertFalse(playing)
    }

    private class ReportingPlayer : AudioPlayer {
        var throwOnPlay = false
        var fail: (() -> Unit)? = null
        var complete: (() -> Unit)? = null
        override suspend fun play(samples: ShortArray, onComplete: () -> Unit) = error("Use feedback playback")
        override suspend fun playWithFeedback(samples: ShortArray, onComplete: () -> Unit, onFailure: () -> Unit) {
            if (throwOnPlay) error("Audio device unavailable")
            complete = onComplete
            fail = onFailure
        }
        override suspend fun stop() {}
        override fun isPlaying() = false
    }

    private class Player : AudioPlayer {
        var plays = 0
        var stops = 0
        override suspend fun play(samples: ShortArray, onComplete: () -> Unit) { plays++; onComplete() }
        override suspend fun stop() { stops++ }
        override fun isPlaying() = false
    }
}

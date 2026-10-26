package com.minimalpairs.app.data.progress

import androidx.room3.Room
import com.minimalpairs.app.data.local.*
import com.minimalpairs.app.domain.srs.ModifiedSm2Engine
import com.minimalpairs.app.domain.srs.Sm2State
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import java.nio.file.Files
import kotlin.test.*

class ProgressPersistenceTest {
    @Test
    fun answersAndMasterySurviveReopeningAndProgressUpdates() = runBlocking {
        val directory = Files.createTempDirectory("progress-test").toFile()
        val path = directory.resolve("progress.db").absolutePath
        fun open() = createDatabase(Room.databaseBuilder<AppDatabase>(name = path))
        var database = open()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = ProgressRepository(database.progressDao(), scope)
            withTimeout(5000) { repository.state.first { !it.loading } }
            repository.record(trial("i_vs_I", "sheep", "sheep", true), 5)
            val first = withTimeout(5000) { repository.state.first { it.attempts == 1 && it.contrasts.single().card != null } }
            assertEquals(100, first.accuracy)
            assertEquals(1, first.confusion["iː"]?.get("iː"))
            scope.coroutineContext[Job]!!.cancelAndJoin()
            database.close()
            database = open()
            val dao = database.progressDao()
            assertEquals(1, dao.observeTrials().first().size)
            val saved = dao.getCard("i_vs_I")!!
            assertEquals(ModifiedSm2Engine.review(Sm2State(), 5).masteryScore, saved.masteryScore)
            dao.record(trial("i_vs_I", "sheep", "ship", false), 2)
            dao.record(trial("p_vs_b", "pear", "pear", true), 5)
            val progress = summarizeProgress(dao.observeTrials().first(), dao.observeCards().first())
            assertEquals(3, progress.attempts)
            assertEquals(67, progress.accuracy)
            assertEquals("i_vs_I", progress.contrasts.first().contrastId)
            assertEquals(50, progress.contrasts.first().accuracy)
            assertEquals(1, progress.confusion["iː"]?.get("ɪ"))
            val expected = ModifiedSm2Engine.review(ModifiedSm2Engine.review(Sm2State(), 5), 2)
            assertEquals(expected.masteryScore, dao.getCard("i_vs_I")!!.masteryScore)
        } finally {
            scope.cancel()
            database.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun calendarChangesRefreshStreakWithoutAnotherAnswer() = runBlocking {
        val directory = Files.createTempDirectory("progress-calendar-test").toFile()
        val database = createDatabase(Room.databaseBuilder<AppDatabase>(name = directory.resolve("progress.db").absolutePath))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            database.progressDao().record(trial("i_vs_I", "sheep", "sheep", true).copy(
                timestampEpochMs = Instant.parse("2026-10-25T12:00:00Z").toEpochMilliseconds()
            ), 5)
            val calendar = MutableStateFlow(PracticeCalendar(LocalDate.parse("2026-10-26"), TimeZone.UTC))
            val repository = ProgressRepository(database.progressDao(), scope, calendar)
            val yesterday = withTimeout(5000) { repository.state.first { !it.loading } }
            assertEquals(1, yesterday.activity.currentStreak)
            calendar.value = PracticeCalendar(LocalDate.parse("2026-10-27"), TimeZone.UTC)
            val tomorrow = withTimeout(5000) {
                repository.state.first { it.activity.days.lastOrNull()?.date == LocalDate.parse("2026-10-27") }
            }
            assertEquals(0, tomorrow.activity.currentStreak)
            assertEquals(1, tomorrow.activity.longestStreak)
            assertEquals(1, tomorrow.attempts)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            database.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun dueReviewsRefreshWithTimeAndLeaveQueueAfterAnAnswer() = runBlocking {
        val directory = Files.createTempDirectory("review-clock-test").toFile()
        val database = createDatabase(Room.databaseBuilder<AppDatabase>(name = directory.resolve("progress.db").absolutePath))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val dao = database.progressDao()
            dao.record(trial("i_vs_I", "sheep", "sheep", true).copy(timestampEpochMs = 0), 5)
            val clock = MutableStateFlow(86_399_999L)
            val repository = ProgressRepository(dao, scope, nowEpochMs = { clock.value }, refresh = clock.map { Unit })
            val before = withTimeout(5000) { repository.state.first { !it.loading } }
            assertTrue(before.reviews.due.isEmpty())
            assertEquals("i_vs_I", before.reviews.next?.contrastId)
            clock.value = 86_400_000L
            withTimeout(5000) { repository.state.first { it.reviews.due.size == 1 } }
            // Device clock corrections must be reflected without modifying saved reviews.
            clock.value = 0
            withTimeout(5000) { repository.state.first { it.nowEpochMs == 0L && it.reviews.due.isEmpty() } }
            clock.value = 86_400_000L
            withTimeout(5000) { repository.state.first { it.reviews.due.size == 1 } }
            repository.record(trial("i_vs_I", "sheep", "ship", false).copy(timestampEpochMs = clock.value), 2)
            val after = withTimeout(5000) {
                repository.state.first { it.attempts == 2 && it.reviews.due.isEmpty() }
            }
            assertEquals(172_800_000L, after.reviews.next?.dueEpochMs)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            database.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun newAnswerUsesCurrentTimeEvenBeforeTheNextRefreshTick() = runBlocking {
        val directory = Files.createTempDirectory("review-fresh-clock").toFile()
        val database = createDatabase(Room.databaseBuilder<AppDatabase>(name = directory.resolve("progress.db").absolutePath))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val now = AtomicLong(0)
            val refresh = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
            val repository = ProgressRepository(database.progressDao(), scope, nowEpochMs = now::get, refresh = refresh)
            withTimeout(5000) { repository.state.first { !it.loading } }
            now.set(30_000)
            repository.record(trial("i_vs_I", "sheep", "sheep", true).copy(timestampEpochMs = now.get()), 5)
            val saved = withTimeout(5000) { repository.state.first { it.attempts == 1 && it.reviews.next != null && it.pendingAnswers == 0 } }
            assertEquals(now.get(), saved.nowEpochMs)
            assertEquals("Review in 1 day", reviewTiming(saved.reviews.next!!.dueEpochMs, saved.nowEpochMs))
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            database.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun emptyProgressHasNoInventedAccuracyOrMastery() {
        val state = summarizeProgress(emptyList(), emptyList())
        assertFalse(state.loading)
        assertEquals(0, state.attempts)
        assertTrue(state.contrasts.isEmpty())
        assertTrue(state.confusion.isEmpty())
    }

    private fun trial(id: String, target: String, selected: String, correct: Boolean) = TrialRecordEntity(
        contrastId = id, targetWord = target, selectedWord = selected, isCorrect = correct,
        latencyMs = 1000, drillType = "FORCED_CHOICE", timestampEpochMs = 1_000_000
    )
}

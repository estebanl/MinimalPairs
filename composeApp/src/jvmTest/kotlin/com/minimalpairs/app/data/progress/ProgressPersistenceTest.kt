package com.minimalpairs.app.data.progress

import androidx.room3.Room
import com.minimalpairs.app.data.local.*
import com.minimalpairs.app.domain.srs.ModifiedSm2Engine
import com.minimalpairs.app.domain.srs.Sm2State
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
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

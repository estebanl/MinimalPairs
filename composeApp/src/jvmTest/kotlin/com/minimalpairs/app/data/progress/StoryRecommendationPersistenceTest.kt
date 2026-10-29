package com.minimalpairs.app.data.progress

import androidx.room3.Room
import com.minimalpairs.app.data.catalog.MeaningTrapCatalog
import com.minimalpairs.app.data.local.*
import com.minimalpairs.app.domain.StoryContrastEvidence
import com.minimalpairs.app.domain.recommendStory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.nio.file.Files
import kotlin.test.*

class StoryRecommendationPersistenceTest {
    @Test
    fun savedListeningAndStoriesEvidenceSurvivesDatabaseReopening() = runBlocking {
        val directory = Files.createTempDirectory("story-recommendation").toFile()
        val path = directory.resolve("progress.db").absolutePath
        fun open() = createDatabase(Room.databaseBuilder<AppDatabase>(name = path))
        var database = open()
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = RoomProgressStore(database)
            store.record(TrialRecordEntity(1, "ae_vs_e", "pen", "pan", false, 500, "FORCED_CHOICE", 1000), 2)
            store.recordModule(ModulePracticeRecordEntity(2, "STORIES", "pen_vs_pan", "Exam", "ae_vs_e", 5f, false, 1000, 2000))
            val repository = ProgressRepository(store, scope)
            val before = withTimeout(5000) { repository.storyRecommendationEvidence.first { it.isNotEmpty() } }
            assertEquals(mapOf("ae_vs_e" to StoryContrastEvidence(2, 2)), before)
            assertEquals("pen_vs_pan", recommendStory(MeaningTrapCatalog.stories, before)?.storyId)
            scope.cancel()
            scope.coroutineContext[Job]!!.join()
            database.close()
            database = open()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val reopened = ProgressRepository(RoomProgressStore(database), scope)
            val after = withTimeout(5000) { reopened.storyRecommendationEvidence.first { it.isNotEmpty() } }
            assertEquals(before, after)
            assertEquals("pen_vs_pan", recommendStory(MeaningTrapCatalog.stories, after)?.storyId)
        } finally {
            scope.cancel()
            scope.coroutineContext[Job]!!.join()
            database.close()
            directory.deleteRecursively()
        }
    }
}

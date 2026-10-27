package com.minimalpairs.app.data.progress

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.minimalpairs.app.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class SpeakingPersistenceTest {
    @Test
    fun versionThreePreservesLegacyScoresAndPersistsSelectedPhonemeMethod() = runBlocking {
        val directory = Files.createTempDirectory("speaking-v3-migration").toFile()
        val path = directory.resolve("progress.db").absolutePath
        val schema = Json.parseToJsonElement(File("schemas/com.minimalpairs.app.data.local.AppDatabase/3.json").readText())
            .jsonObject.getValue("database").jsonObject
        BundledSQLiteDriver().open(path).use { connection ->
            fun execute(sql: String) { connection.prepare(sql).use { it.step() } }
            schema.getValue("entities").jsonArray.forEach { entity ->
                val table = entity.jsonObject
                execute(table.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table.getValue("tableName").jsonPrimitive.content))
                table["indices"]?.jsonArray.orEmpty().forEach { index ->
                    execute(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table.getValue("tableName").jsonPrimitive.content))
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach { execute(it.jsonPrimitive.content) }
            execute("INSERT INTO speaking_records VALUES (1, 'p_vs_b', 'pear', 'p', 80, 500, 1000)")
            execute("PRAGMA user_version = 3")
        }
        fun open() = createDatabase(Room.databaseBuilder<AppDatabase>(name = path))
        var database = open()
        try {
            val old = database.progressDao().readSpeaking().single()
            assertEquals(80f, old.score)
            assertEquals("unknown", old.scoringMethod)
            database.progressDao().recordSpeaking(old.copy(id = 2, score = 72f, scoringMethod = "neural_ctc_phoneme"))
            database.close()
            database = open()
            val records = database.progressDao().readSpeaking()
            assertEquals("neural_ctc_phoneme", records.single { it.id == 2L }.scoringMethod)
            assertEquals(2, summarizeSpeaking(records).size)
        } finally { database.close(); directory.deleteRecursively() }
    }

    @Test
    fun versionOneMigratesWithoutLosingListeningAndSpeakingSurvivesReopening() = runBlocking {
        val directory = Files.createTempDirectory("speaking-migration").toFile()
        val path = directory.resolve("progress.db").absolutePath
        val schema = Json.parseToJsonElement(File("schemas/com.minimalpairs.app.data.local.AppDatabase/1.json").readText())
            .jsonObject.getValue("database").jsonObject
        BundledSQLiteDriver().open(path).use { connection ->
            fun execute(sql: String) { connection.prepare(sql).use { it.step() } }
            schema.getValue("entities").jsonArray.forEach { entity ->
                val table = entity.jsonObject
                execute(table.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table.getValue("tableName").jsonPrimitive.content))
                table["indices"]?.jsonArray.orEmpty().forEach { index ->
                    execute(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table.getValue("tableName").jsonPrimitive.content))
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach { execute(it.jsonPrimitive.content) }
            execute("INSERT INTO trial_records VALUES (1, 'i_vs_I', 'sheep', 'ship', 0, 1000, 'FORCED_CHOICE', 1000)")
            execute("INSERT INTO sm2_cards VALUES ('i_vs_I', 0, 1, 2.3, 0.47, 1000)")
            execute("PRAGMA user_version = 1")
        }
        fun open() = createDatabase(Room.databaseBuilder<AppDatabase>(name = path))
        var database = open()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = ProgressRepository(RoomProgressStore(database), scope)
            val before = withTimeout(5000) { repository.state.first { !it.loading } }
            assertEquals(1, before.attempts)
            assertTrue(before.speaking.isEmpty())
            val record = SpeakingRecordEntity(0, "i_vs_I", "sheep", "iː", 88f, 200, 2000)
            repository.recordSpeaking(record)
            val after = withTimeout(5000) { repository.state.first { it.speaking.size == 1 && it.pendingAnswers == 0 } }
            assertEquals(before.attempts, after.attempts)
            assertEquals(before.accuracy, after.accuracy)
            assertEquals(before.activity, after.activity)
            assertEquals(before.reviews, after.reviews)
            assertEquals(88f, after.speaking.single().score)
            database.progressDao().recordSpeaking(after.speaking.single())
            assertEquals(1, database.progressDao().observeSpeaking().first().size)
            assertFailsWith<IllegalArgumentException> { repository.recordSpeaking(record.copy(score = Float.NaN)) }
            scope.coroutineContext[Job]!!.cancelAndJoin()
            database.close()
            database = open()
            assertEquals(88f, database.progressDao().observeSpeaking().first().single().score)
            assertEquals(1, database.progressDao().observeTrials().first().size)
            assertEquals(.47f, database.progressDao().getCard("i_vs_I")!!.masteryScore)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            database.close()
            directory.deleteRecursively()
        }
    }
}

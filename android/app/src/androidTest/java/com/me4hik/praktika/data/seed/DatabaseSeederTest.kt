// 04.08.2026 Seed Data cursor by Me4Hik START - instrumented-тесты DatabaseSeeder
// 05.08.2026 Idempotent Seed Fix cursor by Me4Hik START - idempotent seed scenarios
package com.me4hik.praktika.data.seed

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseSeederTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var seeder: DatabaseSeeder
    private lateinit var assetJson: String
    private var persistentDatabaseFile: File? = null

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            PraktikaDatabase::class.java,
        ).build()
        seeder = DatabaseSeeder()
        assetJson = context.assets.open("questions.json").bufferedReader().use { it.readText() }
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
        persistentDatabaseFile?.let { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        persistentDatabaseFile = null
    }

    @Test
    fun readsQuestionsJsonFromAssets() {
        assertTrue(assetJson.contains("\"seedVersion\": 1"))
        assertTrue(assetJson.contains("Как мои стопы ощущают контакт с землёй?"))
    }

    @Test
    fun preservesCyrillicTextLiterally() = runBlocking {
        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)

        val firstQuestion = database.questionDao().getById(1)
        assertEquals("Как мои стопы ощущают контакт с землёй?", firstQuestion!!.text)
    }

    @Test
    fun jsonContainsTwentyOneQuestions() {
        val root = JSONObject(assetJson)
        assertEquals(21, root.getJSONArray("questions").length())
    }

    @Test
    fun idsAndPositionsAreOneToTwentyOne() {
        val questions = JSONObject(assetJson).getJSONArray("questions")
        for (index in 0 until questions.length()) {
            val question = questions.getJSONObject(index)
            val expected = index + 1
            assertEquals(expected, question.getInt("id"))
            assertEquals(expected, question.getInt("cyclePosition"))
        }
    }

    @Test
    fun defaultSlotsAre6609001140() {
        val slots = JSONObject(assetJson).getJSONArray("defaultSlots")
        assertEquals(660, slots.getJSONObject(0).getInt("timeOfDayMinutes"))
        assertEquals(900, slots.getJSONObject(1).getInt("timeOfDayMinutes"))
        assertEquals(1140, slots.getJSONObject(2).getInt("timeOfDayMinutes"))
    }

    @Test
    fun firstSeedInsertsQuestionsSlotsAndPracticeState() = runBlocking {
        val result = seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)

        assertEquals(SeedResult.Inserted, result)
        assertEquals(21, database.questionDao().count())
        assertEquals(3, database.scheduleSlotDao().count())
        assertEquals(1, database.practiceStateDao().get()!!.id)
    }

    @Test
    fun practiceStateHasExpectedInitialValues() = runBlocking {
        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)

        val state = database.practiceStateDao().get()
        assertEquals(false, state!!.isPracticeStarted)
        assertEquals(false, state.isPaused)
        assertEquals(null, state.practiceStartedAtEpochMillis)
        assertEquals(0, state.currentCycleNumber)
        assertEquals(1, state.nextCyclePosition)
        assertEquals(null, state.lastProcessedAtEpochMillis)
        assertEquals(null, state.pausedAtEpochMillis)
        assertEquals(TEST_ZONE_ID, state.activeZoneId)
        assertEquals(1, state.seedVersion)
    }

    @Test
    fun repeatedSeedDoesNotCreateDuplicates() = runBlocking {
        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)
        val secondResult = seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)

        assertEquals(SeedResult.AlreadyInitialized, secondResult)
        assertEquals(21, database.questionDao().count())
        assertEquals(3, database.scheduleSlotDao().count())
    }

    @Test
    fun concurrentSeedDoesNotCreateDuplicates() = runBlocking {
        val results = List(5) {
            async { seeder.seedFromJson(assetJson, database, TEST_ZONE_ID) }
        }.awaitAll()

        assertTrue(results.any { it is SeedResult.Inserted })
        assertTrue(results.all { it is SeedResult.Inserted || it is SeedResult.AlreadyInitialized })
        assertEquals(21, database.questionDao().count())
        assertEquals(3, database.scheduleSlotDao().count())
    }

    @Test
    fun invalidQuestionValidationPreventsInsert() = runBlocking {
        val invalidJson = JSONObject(assetJson).apply {
            getJSONArray("questions").getJSONObject(0).put("text", "")
        }.toString()

        try {
            seeder.seedFromJson(invalidJson, database, TEST_ZONE_ID)
            fail("Expected SeedValidationException")
        } catch (exception: SeedValidationException) {
            assertTrue(exception.message!!.contains("text"))
        }

        assertEquals(0, database.questionDao().count())
        assertEquals(0, database.scheduleSlotDao().count())
        assertEquals(null, database.practiceStateDao().get())
    }

    @Test
    fun invalidSlotValidationPreventsInsert() = runBlocking {
        val invalidJson = JSONObject(assetJson).apply {
            getJSONArray("defaultSlots").getJSONObject(0).put("timeOfDayMinutes", 900)
        }.toString()

        try {
            seeder.seedFromJson(invalidJson, database, TEST_ZONE_ID)
            fail("Expected SeedValidationException")
        } catch (exception: SeedValidationException) {
            assertTrue(exception.message!!.contains("defaultSlots"))
        }

        assertEquals(0, database.questionDao().count())
        assertEquals(0, database.scheduleSlotDao().count())
    }

    @Test
    fun partiallyFilledDatabaseThrowsSeedCorruptionException() = runBlocking {
        database.questionDao().insertAll(
            listOf(QuestionEntity(id = 1, cyclePosition = 1, text = "Partial", isActive = true)),
        )

        try {
            seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)
            fail("Expected SeedCorruptionException")
        } catch (exception: SeedCorruptionException) {
            assertTrue(exception.message!!.contains("Incompatible seed state"))
        }

        assertEquals(1, database.questionDao().count())
        assertEquals(0, database.scheduleSlotDao().count())
    }

    @Test
    fun mismatchedTextWithSameVersionThrowsSeedCorruptionException() = runBlocking {
        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)
        database.questionDao().updateText(id = 1, text = "Changed text")

        try {
            seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)
            fail("Expected SeedCorruptionException")
        } catch (exception: SeedCorruptionException) {
            assertTrue(exception.message!!.isNotEmpty())
        }
    }

    @Test
    fun olderDatabaseSeedVersionRequiresMigration() = runBlocking {
        seedFullDatabase(seedVersion = 0)

        try {
            seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)
            fail("Expected SeedMigrationRequiredException")
        } catch (exception: SeedMigrationRequiredException) {
            assertTrue(exception.message!!.contains("seedVersion=0"))
        }
    }

    @Test
    fun newerDatabaseSeedVersionIsNotOverwritten() = runBlocking {
        seedFullDatabase(seedVersion = 2)

        val result = seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)

        assertEquals(SeedResult.NewerSeedPresent, result)
        assertEquals(2, database.practiceStateDao().get()!!.seedVersion)
    }

    @Test
    fun closeAndReopenPersistentDatabaseKeepsSeedData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        persistentDatabaseFile = File(context.cacheDir, "praktika_seed_test.db")
        persistentDatabaseFile!!.delete()

        val firstDatabase = Room.databaseBuilder(
            context,
            PraktikaDatabase::class.java,
            persistentDatabaseFile!!.absolutePath,
        ).build()
        DatabaseSeeder().seedFromJson(assetJson, firstDatabase, TEST_ZONE_ID)
        firstDatabase.close()

        val reopenedDatabase = Room.databaseBuilder(
            context,
            PraktikaDatabase::class.java,
            persistentDatabaseFile!!.absolutePath,
        ).build()

        assertEquals(21, reopenedDatabase.questionDao().count())
        assertEquals(3, reopenedDatabase.scheduleSlotDao().count())
        assertEquals(1, reopenedDatabase.practiceStateDao().get()!!.seedVersion)
        reopenedDatabase.close()
    }

    @Test
    fun seedDoesNotCreateQuestionOccurrences() = runBlocking {
        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)
        assertEquals(0, database.questionOccurrenceDao().count())
    }

    @Test
    fun seedDoesNotCreateAnswers() = runBlocking {
        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)
        assertEquals(0, database.answerDao().count())
    }

    @Test
    fun seedFromAssetsUsesProductionJson() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val result = seeder.seedFromAssets(context, database)
        assertEquals(SeedResult.Inserted, result)
        assertFalse(database.questionDao().getAllOrderedByCyclePosition().isEmpty())
    }

    @Test
    fun startedPracticeReturnsAlreadyInitialized() = runBlocking {
        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)
        markPracticeStarted()

        val result = seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)

        assertEquals(SeedResult.AlreadyInitialized, result)
    }

    @Test
    fun changedCursorReturnsAlreadyInitializedAndPreservesValues() = runBlocking {
        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)
        val mutated = database.practiceStateDao().get()!!.copy(
            isPracticeStarted = true,
            currentCycleNumber = 2,
            nextCyclePosition = 5,
            practiceStartedAtEpochMillis = 1_700_000_000_000L,
            lastProcessedAtEpochMillis = 1_700_000_100_000L,
        )
        database.practiceStateDao().update(mutated)

        val result = seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)

        assertEquals(SeedResult.AlreadyInitialized, result)
        assertEquals(mutated, database.practiceStateDao().get())
    }

    @Test
    fun pausedPracticeReturnsAlreadyInitialized() = runBlocking {
        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)
        val paused = database.practiceStateDao().get()!!.copy(
            isPracticeStarted = true,
            isPaused = true,
            pausedAtEpochMillis = 1_700_000_000_000L,
        )
        database.practiceStateDao().update(paused)

        val result = seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)

        assertEquals(SeedResult.AlreadyInitialized, result)
        assertEquals(true, database.practiceStateDao().get()!!.isPaused)
    }

    @Test
    fun scheduledOccurrenceReturnsAlreadyInitialized() = runBlocking {
        seedWithOccurrence(status = QuestionOccurrenceStatus.SCHEDULED)

        val result = seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)

        assertEquals(SeedResult.AlreadyInitialized, result)
        assertEquals(1, database.questionOccurrenceDao().count())
    }

    @Test
    fun availableOccurrenceReturnsAlreadyInitialized() = runBlocking {
        seedWithOccurrence(status = QuestionOccurrenceStatus.AVAILABLE)

        val result = seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)

        assertEquals(SeedResult.AlreadyInitialized, result)
        assertEquals(1, database.questionOccurrenceDao().count())
    }

    @Test
    fun multipleMissedOccurrencesReturnAlreadyInitialized() = runBlocking {
        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)
        markPracticeStarted()
        val occurrences = listOf(
            buildOccurrence(cycleNumber = 1, cyclePosition = 1, status = QuestionOccurrenceStatus.MISSED_BY_TIME),
            buildOccurrence(cycleNumber = 1, cyclePosition = 2, status = QuestionOccurrenceStatus.MISSED_BY_TIME),
            buildOccurrence(cycleNumber = 1, cyclePosition = 3, status = QuestionOccurrenceStatus.MISSED_BY_TIME),
        )
        database.questionOccurrenceDao().insertAll(occurrences)

        val result = seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)

        assertEquals(SeedResult.AlreadyInitialized, result)
        assertEquals(3, database.questionOccurrenceDao().count())
    }

    @Test
    fun fullCycleOfTwentyTwoOccurrencesReturnsAlreadyInitialized() = runBlocking {
        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)
        markPracticeStarted()
        val occurrences = buildList {
            for (position in 1..21) {
                add(
                    buildOccurrence(
                        cycleNumber = 1,
                        cyclePosition = position,
                        status = QuestionOccurrenceStatus.MISSED_BY_TIME,
                    ),
                )
            }
            add(
                buildOccurrence(
                    cycleNumber = 2,
                    cyclePosition = 1,
                    status = QuestionOccurrenceStatus.SCHEDULED,
                ),
            )
        }
        database.questionOccurrenceDao().insertAll(occurrences)

        val result = seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)

        assertEquals(SeedResult.AlreadyInitialized, result)
        assertEquals(22, database.questionOccurrenceDao().count())
    }

    @Test
    fun existingAnswerReturnsAlreadyInitialized() = runBlocking {
        val occurrenceId = seedWithOccurrence(status = QuestionOccurrenceStatus.ANSWERED)
        database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = "Test answer",
                createdAtEpochMillis = 1_700_000_000_000L,
            ),
        )

        val result = seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)

        assertEquals(SeedResult.AlreadyInitialized, result)
        assertEquals(1, database.answerDao().count())
    }

    @Test
    fun multipleAnswersReturnAlreadyInitialized() = runBlocking {
        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)
        markPracticeStarted()
        val firstId = database.questionOccurrenceDao().insert(
            buildOccurrence(cycleNumber = 1, cyclePosition = 1, status = QuestionOccurrenceStatus.ANSWERED),
        )
        val secondId = database.questionOccurrenceDao().insert(
            buildOccurrence(cycleNumber = 1, cyclePosition = 2, status = QuestionOccurrenceStatus.ANSWERED),
        )
        database.answerDao().insert(
            AnswerEntity(occurrenceId = firstId, text = "A1", createdAtEpochMillis = 1L),
        )
        database.answerDao().insert(
            AnswerEntity(occurrenceId = secondId, text = "A2", createdAtEpochMillis = 2L),
        )

        val result = seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)

        assertEquals(SeedResult.AlreadyInitialized, result)
        assertEquals(2, database.answerDao().count())
    }

    @Test
    fun repeatSeedDoesNotMutateUserOccurrencesOrAnswers() = runBlocking {
        val occurrenceId = seedWithOccurrence(status = QuestionOccurrenceStatus.AVAILABLE)
        database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = "Preserved",
                createdAtEpochMillis = 99L,
            ),
        )
        val occurrenceBefore = database.questionOccurrenceDao().getById(occurrenceId)!!
        val answerBefore = database.answerDao().getByOccurrenceId(occurrenceId)!!
        val stateBefore = database.practiceStateDao().get()!!

        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)

        assertEquals(occurrenceBefore, database.questionOccurrenceDao().getById(occurrenceId))
        assertEquals(answerBefore, database.answerDao().getByOccurrenceId(occurrenceId))
        assertEquals(stateBefore, database.practiceStateDao().get())
    }

    @Test
    fun modifiedScheduleSlotsReturnAlreadyInitialized() = runBlocking {
        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)
        database.scheduleSlotDao().updateTime(slotIndex = 1, timeOfDayMinutes = 480)
        database.scheduleSlotDao().updateTime(slotIndex = 2, timeOfDayMinutes = 720)
        database.scheduleSlotDao().updateTime(slotIndex = 3, timeOfDayMinutes = 1020)

        val result = seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)

        assertEquals(SeedResult.AlreadyInitialized, result)
    }

    @Test
    fun repeatSeedDoesNotRestoreDefaultScheduleTimes() = runBlocking {
        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)
        database.scheduleSlotDao().updateTime(slotIndex = 1, timeOfDayMinutes = 480)
        database.scheduleSlotDao().updateTime(slotIndex = 2, timeOfDayMinutes = 720)
        database.scheduleSlotDao().updateTime(slotIndex = 3, timeOfDayMinutes = 1020)
        val slotsBefore = database.scheduleSlotDao().getAllOrderedByTime()

        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)

        assertEquals(slotsBefore, database.scheduleSlotDao().getAllOrderedByTime())
    }

    @Test
    fun twoScheduleSlotsThrowsSeedCorruptionException() = runBlocking {
        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)
        database.scheduleSlotDao().deleteAll()
        database.scheduleSlotDao().insertAll(
            listOf(
                ScheduleSlotEntity(slotIndex = 1, timeOfDayMinutes = 480),
                ScheduleSlotEntity(slotIndex = 2, timeOfDayMinutes = 720),
            ),
        )

        assertSeedCorruptionOnRepeat()
    }

    @Test
    fun duplicateScheduleSlotTimesAreRejectedByUniqueConstraint() = runBlocking {
        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)
        database.scheduleSlotDao().updateTime(slotIndex = 1, timeOfDayMinutes = 480)

        try {
            database.scheduleSlotDao().updateTime(slotIndex = 2, timeOfDayMinutes = 480)
            fail("Expected SQLiteConstraintException")
        } catch (exception: SQLiteConstraintException) {
            assertTrue(exception.message!!.contains("UNIQUE"))
        }
    }

    @Test
    fun missingPracticeStateThrowsSeedCorruptionException() = runBlocking {
        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)
        database.practiceStateDao().delete()

        assertSeedCorruptionOnRepeat()
    }

    @Test
    fun twentyQuestionsThrowsSeedCorruptionException() = runBlocking {
        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)
        val twentyQuestions = database.questionDao().getAllOrderedByCyclePosition().take(20)
        database.questionDao().deleteAll()
        database.questionDao().insertAll(twentyQuestions)

        assertSeedCorruptionOnRepeat()
    }

    @Test
    fun answerWithoutValidOccurrenceIsRejectedByForeignKey() = runBlocking {
        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)

        try {
            database.answerDao().insert(
                AnswerEntity(
                    occurrenceId = 999L,
                    text = "Orphan",
                    createdAtEpochMillis = 1L,
                ),
            )
            fail("Expected SQLiteConstraintException")
        } catch (exception: SQLiteConstraintException) {
            assertTrue(exception.message!!.contains("FOREIGN KEY"))
        }
    }

    private suspend fun seedWithOccurrence(
        status: QuestionOccurrenceStatus,
    ): Long {
        seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)
        markPracticeStarted()
        return database.questionOccurrenceDao().insert(
            buildOccurrence(cycleNumber = 1, cyclePosition = 1, status = status),
        )
    }

    private suspend fun markPracticeStarted() {
        val state = database.practiceStateDao().get()!!
        database.practiceStateDao().update(
            state.copy(
                isPracticeStarted = true,
                practiceStartedAtEpochMillis = 1_700_000_000_000L,
            ),
        )
    }

    private suspend fun buildOccurrence(
        cycleNumber: Int,
        cyclePosition: Int,
        status: QuestionOccurrenceStatus,
    ): QuestionOccurrenceEntity {
        val question = database.questionDao().getById(cyclePosition)!!
        return QuestionOccurrenceEntity(
            questionId = question.id,
            questionTextSnapshot = question.text,
            cycleNumber = cycleNumber,
            cyclePosition = cyclePosition,
            scheduleSlotIndex = 1,
            plannedAtEpochMillis = 1_700_000_000_000L + cyclePosition,
            availableUntilEpochMillis = 1_700_003_600_000L + cyclePosition,
            status = status,
            zoneId = TEST_ZONE_ID,
        )
    }

    private suspend fun assertSeedCorruptionOnRepeat() {
        try {
            seeder.seedFromJson(assetJson, database, TEST_ZONE_ID)
            fail("Expected SeedCorruptionException")
        } catch (exception: SeedCorruptionException) {
            assertTrue(exception.message!!.contains("Incompatible seed state"))
        }
    }
    // 05.08.2026 Idempotent Seed Fix cursor by Me4Hik END

    private suspend fun seedFullDatabase(seedVersion: Int) {
        val root = JSONObject(assetJson)
        val questions = root.getJSONArray("questions")
        database.questionDao().insertAll(
            buildList {
                for (index in 0 until questions.length()) {
                    val item = questions.getJSONObject(index)
                    add(
                        QuestionEntity(
                            id = item.getInt("id"),
                            cyclePosition = item.getInt("cyclePosition"),
                            text = item.getString("text"),
                            isActive = item.getBoolean("isActive"),
                        ),
                    )
                }
            },
        )
        val slots = root.getJSONArray("defaultSlots")
        database.scheduleSlotDao().insertAll(
            buildList {
                for (index in 0 until slots.length()) {
                    val item = slots.getJSONObject(index)
                    add(
                        ScheduleSlotEntity(
                            slotIndex = item.getInt("slotIndex"),
                            timeOfDayMinutes = item.getInt("timeOfDayMinutes"),
                        ),
                    )
                }
            },
        )
        database.practiceStateDao().insert(
            PracticeStateEntity(
                id = 1,
                isPracticeStarted = false,
                isPaused = false,
                practiceStartedAtEpochMillis = null,
                currentCycleNumber = 0,
                nextCyclePosition = 1,
                lastProcessedAtEpochMillis = null,
                pausedAtEpochMillis = null,
                activeZoneId = TEST_ZONE_ID,
                seedVersion = seedVersion,
            ),
        )
    }

    private companion object {
        const val TEST_ZONE_ID = "UTC"
    }
}
// 04.08.2026 Seed Data cursor by Me4Hik END

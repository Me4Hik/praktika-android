// 11.08.2026 DATA VAULT Stage 2 cursor by Me4Hik START - Room backup exporter instrumented tests
package com.me4hik.praktika.data.backup.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.cycle.ScheduleSlotUpdate
import com.me4hik.praktika.data.delete.RoomAnswerDeleteRepository
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RoomBackupExporterTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var exporter: RoomBackupExporter
    private lateinit var repository: CycleRepository
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var deleteRepository: RoomAnswerDeleteRepository

    @Before
    fun setUp() = runBlocking {
        database = RoomBackupExporterTestSupport.createInMemoryDatabase()
        RoomBackupExporterTestSupport.seedBaseData(database)
        exporter = RoomBackupExporter(database)
        timeProvider = FakeTimeProvider(
            RoomBackupExporterTestSupport.epochAt(2026, 8, 4, 8, 0),
            RoomBackupExporterTestSupport.ZONE_KIEV,
        )
        repository = CycleRepository(database, timeProvider, com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink)
        deleteRepository = RoomAnswerDeleteRepository(database, com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun test01_freshSeededDb_exportsEmptyPracticeState() = runBlocking {
        val payload = RoomBackupExporterTestSupport.assertSuccess(exporter.export()).payload

        assertFalse(payload.practiceState.isPracticeStarted)
        assertEquals(0, payload.occurrences.size)
        assertEquals(0, payload.answers.size)
        assertEquals(3, payload.scheduleSlots.size)
        assertEquals(1, payload.scheduleSlots[0].slotIndex)
        assertEquals(2, payload.scheduleSlots[1].slotIndex)
        assertEquals(3, payload.scheduleSlots[2].slotIndex)
        assertEquals(660, payload.scheduleSlots[0].timeOfDayMinutes)
        assertEquals(900, payload.scheduleSlots[1].timeOfDayMinutes)
        assertEquals(1140, payload.scheduleSlots[2].timeOfDayMinutes)
    }

    @Test
    fun test02_startedPractice_exportsExactStateAndOccurrence() = runBlocking {
        timeProvider.setEpochMillis(RoomBackupExporterTestSupport.epochAt(2026, 8, 4, 8, 0))
        repository.startPractice()

        val entityState = database.practiceStateDao().get()!!
        val entityOccurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        val payload = RoomBackupExporterTestSupport.assertSuccess(exporter.export()).payload

        assertTrue(payload.practiceState.isPracticeStarted)
        assertEquals(entityState.currentCycleNumber, payload.practiceState.currentCycleNumber)
        assertEquals(entityState.nextCyclePosition, payload.practiceState.nextCyclePosition)
        assertEquals(entityState.practiceStartedAtEpochMillis, payload.practiceState.practiceStartedAtEpochMillis)
        assertEquals(entityState.activeZoneId, payload.practiceState.activeZoneId)
        assertEquals(entityState.seedVersion, payload.practiceState.seedVersion)

        assertEquals(1, payload.occurrences.size)
        val exportedOccurrence = payload.occurrences.single()
        assertEquals(entityOccurrence.questionId, exportedOccurrence.questionId)
        assertEquals(entityOccurrence.cycleNumber, exportedOccurrence.cycleNumber)
        assertEquals(entityOccurrence.cyclePosition, exportedOccurrence.cyclePosition)
        assertEquals(entityOccurrence.status.name, exportedOccurrence.status)
        assertEquals(entityOccurrence.questionTextSnapshot, exportedOccurrence.questionTextSnapshot)
    }

    @Test
    fun test03_answerExport_mapsStableCyclePositionKeys() = runBlocking {
        timeProvider.setEpochMillis(RoomBackupExporterTestSupport.epochAt(2026, 8, 4, 11, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        timeProvider.setEpochMillis(RoomBackupExporterTestSupport.epochAt(2026, 8, 4, 11, 30))
        repository.saveAnswer(occurrence.id, "test answer")

        val payload = RoomBackupExporterTestSupport.assertSuccess(exporter.export()).payload
        val exportedOccurrence = payload.occurrences.single { it.cycleNumber == 1 && it.cyclePosition == 1 }
        val exportedAnswer = payload.answers.single()

        assertEquals(QuestionOccurrenceStatus.ANSWERED.name, exportedOccurrence.status)
        assertEquals(1, exportedAnswer.cycleNumber)
        assertEquals(1, exportedAnswer.cyclePosition)
        assertEquals("test answer", exportedAnswer.text)
    }

    @Test
    fun test04_deletedAnswer_exportsAnsweredOccurrenceWithoutBackupAnswer() = runBlocking {
        timeProvider.setEpochMillis(RoomBackupExporterTestSupport.epochAt(2026, 8, 4, 11, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        timeProvider.setEpochMillis(RoomBackupExporterTestSupport.epochAt(2026, 8, 4, 11, 30))
        repository.saveAnswer(occurrence.id, "to delete")
        val answerId = database.answerDao().getByOccurrenceId(occurrence.id)!!.id
        deleteRepository.deleteAnswer(answerId)

        val payload = RoomBackupExporterTestSupport.assertSuccess(exporter.export()).payload
        val exportedOccurrence = payload.occurrences.single { it.cycleNumber == 1 && it.cyclePosition == 1 }

        assertEquals(QuestionOccurrenceStatus.ANSWERED.name, exportedOccurrence.status)
        assertTrue(exportedOccurrence.completedAtEpochMillis != null)
        assertTrue(
            payload.answers.none {
                it.cycleNumber == exportedOccurrence.cycleNumber &&
                    it.cyclePosition == exportedOccurrence.cyclePosition
            },
        )
    }

    @Test
    fun test05_multipleCycles_preservesStableKeysAndCanonicalOrder() = runBlocking {
        val occurrenceIds = mutableListOf<Long>()
        for (index in 0 until 25) {
            val cycleNumber = index / 21 + 1
            val cyclePosition = index % 21 + 1
            occurrenceIds += RoomBackupExporterTestSupport.insertOccurrence(
                database = database,
                cycleNumber = cycleNumber,
                cyclePosition = cyclePosition,
                status = QuestionOccurrenceStatus.ANSWERED,
                completedAtEpochMillis = RoomBackupExporterTestSupport.epochAt(2026, 8, 4, 11, 0) + index,
            )
        }
        for (index in occurrenceIds.indices) {
            RoomBackupExporterTestSupport.insertAnswer(
                database = database,
                occurrenceId = occurrenceIds[index],
                createdAtEpochMillis = RoomBackupExporterTestSupport.epochAt(2026, 8, 4, 11, 30) + index,
            )
        }
        database.practiceStateDao().update(
            database.practiceStateDao().get()!!.copy(
                isPracticeStarted = true,
                practiceStartedAtEpochMillis = RoomBackupExporterTestSupport.epochAt(2026, 8, 4, 8, 0),
                currentCycleNumber = 2,
                nextCyclePosition = 5,
            ),
        )

        val payload = RoomBackupExporterTestSupport.assertSuccess(exporter.export()).payload

        assertEquals(25, payload.occurrences.size)
        assertEquals(25, payload.answers.size)
        assertTrue(payload.occurrences.zipWithNext().all { (left, right) ->
            left.cycleNumber < right.cycleNumber ||
                (left.cycleNumber == right.cycleNumber && left.cyclePosition < right.cyclePosition)
        })
        assertTrue(payload.answers.zipWithNext().all { (left, right) ->
            left.cycleNumber < right.cycleNumber ||
                (left.cycleNumber == right.cycleNumber && left.cyclePosition < right.cyclePosition)
        })
        assertEquals(2, payload.occurrences.last().cycleNumber)
        assertEquals(4, payload.occurrences.last().cyclePosition)
    }

    @Test
    fun test06_customSchedule_exportsThreeSlotsInSlotIndexOrder() = runBlocking {
        repository.updateSchedule(
            listOf(
                ScheduleSlotUpdate(1, 630),
                ScheduleSlotUpdate(2, 860),
                ScheduleSlotUpdate(3, 1210),
            ),
        )

        val payload = RoomBackupExporterTestSupport.assertSuccess(exporter.export()).payload

        assertEquals(3, payload.scheduleSlots.size)
        assertEquals(listOf(1, 2, 3), payload.scheduleSlots.map { it.slotIndex })
        assertEquals(listOf(630, 860, 1210), payload.scheduleSlots.map { it.timeOfDayMinutes })
    }

    @Test
    fun test07_pausedState_exportsPausedFields() = runBlocking {
        timeProvider.setEpochMillis(RoomBackupExporterTestSupport.epochAt(2026, 8, 4, 8, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(RoomBackupExporterTestSupport.epochAt(2026, 8, 4, 10, 0))
        repository.pausePractice()

        val entityState = database.practiceStateDao().get()!!
        val payload = RoomBackupExporterTestSupport.assertSuccess(exporter.export()).payload

        assertTrue(payload.practiceState.isPaused)
        assertEquals(entityState.pausedAtEpochMillis, payload.practiceState.pausedAtEpochMillis)
        assertEquals(entityState.lastProcessedAtEpochMillis, payload.practiceState.lastProcessedAtEpochMillis)
    }

    @Test
    fun test08_unsafeAnswerForNonAnsweredOccurrence_refused() = runBlocking {
        val occurrenceId = RoomBackupExporterTestSupport.insertOccurrence(
            database = database,
            cycleNumber = 1,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.AVAILABLE,
        )
        RoomBackupExporterTestSupport.insertAnswer(database, occurrenceId)
        markPracticeStarted()

        RoomBackupExporterTestSupport.assertUnsafe(
            exporter.export(),
            BackupDatabaseUnsafeReason.ANSWER_FOR_NON_ANSWERED_OCCURRENCE,
        )
    }

    @Test
    fun test09_unsafeMultipleIncompleteOccurrences_refused() = runBlocking {
        RoomBackupExporterTestSupport.insertOccurrence(
            database = database,
            cycleNumber = 1,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.SCHEDULED,
        )
        RoomBackupExporterTestSupport.insertOccurrence(
            database = database,
            cycleNumber = 1,
            cyclePosition = 2,
            status = QuestionOccurrenceStatus.AVAILABLE,
        )
        markPracticeStarted()

        RoomBackupExporterTestSupport.assertUnsafe(
            exporter.export(),
            BackupDatabaseUnsafeReason.MULTIPLE_INCOMPLETE_OCCURRENCES,
        )
    }

    @Test
    fun test10_unsafeQuestionMappingMismatch_refused() = runBlocking {
        RoomBackupExporterTestSupport.insertOccurrence(
            database = database,
            cycleNumber = 1,
            cyclePosition = 1,
            questionId = 2,
            status = QuestionOccurrenceStatus.SCHEDULED,
        )
        markPracticeStarted()

        RoomBackupExporterTestSupport.assertUnsafe(
            exporter.export(),
            BackupDatabaseUnsafeReason.QUESTION_MAPPING_MISMATCH,
        )
    }

    @Test
    fun test11_unsafeAnsweredWithoutCompletedAt_refused() = runBlocking {
        RoomBackupExporterTestSupport.insertOccurrence(
            database = database,
            cycleNumber = 1,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.ANSWERED,
            completedAtEpochMillis = null,
        )
        markPracticeStarted()

        RoomBackupExporterTestSupport.assertUnsafe(
            exporter.export(),
            BackupDatabaseUnsafeReason.INVALID_TERMINAL_COMPLETION,
        )
    }

    @Test
    fun test12_unsafeBlankAnswer_refused() = runBlocking {
        val occurrenceId = RoomBackupExporterTestSupport.insertOccurrence(
            database = database,
            cycleNumber = 1,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.ANSWERED,
            completedAtEpochMillis = RoomBackupExporterTestSupport.epochAt(2026, 8, 4, 11, 30),
        )
        database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = "   ",
                createdAtEpochMillis = RoomBackupExporterTestSupport.epochAt(2026, 8, 4, 11, 31),
            ),
        )
        markPracticeStarted()

        RoomBackupExporterTestSupport.assertUnsafe(
            exporter.export(),
            BackupDatabaseUnsafeReason.BLANK_ANSWER_TEXT,
        )
    }

    @Test
    fun test13_largeHistory_exportsSuccessfully() = runBlocking {
        val occurrenceCount = 1_000
        val occurrences = ArrayList<QuestionOccurrenceEntity>(occurrenceCount)
        for (index in 0 until occurrenceCount) {
            val cycleNumber = index / 21 + 1
            val cyclePosition = index % 21 + 1
            occurrences += QuestionOccurrenceEntity(
                questionId = cyclePosition,
                questionTextSnapshot = "Question $cyclePosition",
                cycleNumber = cycleNumber,
                cyclePosition = cyclePosition,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = RoomBackupExporterTestSupport.epochAt(2026, 8, 4, 11, 0) + index,
                availableUntilEpochMillis = RoomBackupExporterTestSupport.epochAt(2026, 8, 4, 14, 0) + index,
                openedAtEpochMillis = null,
                completedAtEpochMillis = RoomBackupExporterTestSupport.epochAt(2026, 8, 4, 11, 30) + index,
                status = QuestionOccurrenceStatus.ANSWERED,
                zoneId = RoomBackupExporterTestSupport.ZONE_KIEV,
            )
        }
        database.questionOccurrenceDao().insertAll(occurrences)

        val insertedOccurrences = database.questionOccurrenceDao().getAllOrderedByPlannedAt()
        val answers = insertedOccurrences.mapIndexed { index, occurrence ->
            AnswerEntity(
                occurrenceId = occurrence.id,
                text = "answer-$index",
                createdAtEpochMillis = RoomBackupExporterTestSupport.epochAt(2026, 8, 4, 11, 31) + index,
            )
        }
        answers.forEach { database.answerDao().insert(it) }

        database.practiceStateDao().update(
            PracticeStateEntity(
                id = 1,
                isPracticeStarted = true,
                isPaused = false,
                practiceStartedAtEpochMillis = RoomBackupExporterTestSupport.epochAt(2026, 8, 4, 8, 0),
                currentCycleNumber = 48,
                nextCyclePosition = 13,
                lastProcessedAtEpochMillis = null,
                pausedAtEpochMillis = null,
                activeZoneId = RoomBackupExporterTestSupport.ZONE_KIEV,
                seedVersion = 1,
            ),
        )

        val startedAt = System.currentTimeMillis()
        val payload = RoomBackupExporterTestSupport.assertSuccess(exporter.export()).payload
        val elapsedMillis = System.currentTimeMillis() - startedAt

        assertEquals(occurrenceCount, payload.occurrences.size)
        assertEquals(occurrenceCount, payload.answers.size)
        assertTrue("export took ${elapsedMillis}ms", elapsedMillis < 60_000L)
    }

    private suspend fun markPracticeStarted() {
        database.practiceStateDao().update(
            database.practiceStateDao().get()!!.copy(
                isPracticeStarted = true,
                practiceStartedAtEpochMillis = RoomBackupExporterTestSupport.epochAt(2026, 8, 4, 8, 0),
                currentCycleNumber = 1,
                nextCyclePosition = 2,
            ),
        )
    }
}
// 11.08.2026 DATA VAULT Stage 2 cursor by Me4Hik END

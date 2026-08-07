// 05.08.2026 Main Screen cursor by Me4Hik START - instrumented tests PracticeReadRepository
// 05.08.2026 Atomic Practice Snapshot cursor by Me4Hik START - атомарный переход startPractice
package com.me4hik.praktika.data.read

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.data.cycle.CycleCorruptionException
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PracticeReadRepositoryTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var repository: RoomPracticeReadRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
        repository = RoomPracticeReadRepository(database)
        runBlocking { seedBase() }
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun seedStateNotStartedAndNoOccurrence() = runBlocking {
        val snapshot = repository.observeSnapshot().first()
        assertEquals(false, snapshot.practiceState.isPracticeStarted)
        assertEquals(null, snapshot.incompleteOccurrence)
    }

    @Test
    fun startedWithOneScheduled() = runBlocking {
        insertOccurrence(status = QuestionOccurrenceStatus.SCHEDULED)
        markStarted()
        val snapshot = repository.observeSnapshot().first()
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, snapshot.incompleteOccurrence?.status)
    }

    @Test
    fun startedWithOneAvailable() = runBlocking {
        insertOccurrence(status = QuestionOccurrenceStatus.AVAILABLE)
        markStarted()
        val snapshot = repository.observeSnapshot().first()
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, snapshot.incompleteOccurrence?.status)
    }

    @Test
    fun pausedWithScheduled() = runBlocking {
        insertOccurrence(status = QuestionOccurrenceStatus.SCHEDULED)
        markStarted(paused = true)
        val snapshot = repository.observeSnapshot().first()
        assertEquals(true, snapshot.practiceState.isPaused)
    }

    @Test
    fun pausedWithAvailable() = runBlocking {
        insertOccurrence(status = QuestionOccurrenceStatus.AVAILABLE)
        markStarted(paused = true)
        val snapshot = repository.observeSnapshot().first()
        assertEquals(true, snapshot.practiceState.isPaused)
    }

    @Test
    fun missingStateIsCorruption() = runBlocking {
        database.practiceStateDao().delete()
        try {
            repository.observeSnapshot().first()
            fail("Expected CycleCorruptionException")
        } catch (_: CycleCorruptionException) {
        }
    }

    @Test
    fun notStartedWithOccurrenceIsCorruption() = runBlocking {
        insertOccurrence(status = QuestionOccurrenceStatus.SCHEDULED)
        try {
            repository.observeSnapshot().first()
            fail("Expected CycleCorruptionException")
        } catch (_: CycleCorruptionException) {
        }
    }

    @Test
    fun startedWithoutOccurrenceIsCorruption() = runBlocking {
        markStarted()
        try {
            repository.observeSnapshot().first()
            fail("Expected CycleCorruptionException")
        } catch (_: CycleCorruptionException) {
        }
    }

    @Test
    fun twoIncompleteIsCorruption() = runBlocking {
        insertOccurrence(status = QuestionOccurrenceStatus.SCHEDULED, cyclePosition = 1)
        insertOccurrence(status = QuestionOccurrenceStatus.AVAILABLE, cyclePosition = 2)
        markStarted()
        try {
            repository.observeSnapshot().first()
            fail("Expected CycleCorruptionException")
        } catch (_: CycleCorruptionException) {
        }
    }

    @Test
    fun roomUpdateEmitsNewSnapshot() = runBlocking {
        insertOccurrence(status = QuestionOccurrenceStatus.SCHEDULED)
        markStarted()
        val first = repository.observeSnapshot().first()
        database.questionOccurrenceDao().update(
            first.incompleteOccurrence!!.copy(status = QuestionOccurrenceStatus.AVAILABLE),
        )
        val second = repository.observeSnapshot().first {
            it.incompleteOccurrence?.status == QuestionOccurrenceStatus.AVAILABLE
        }
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, second.incompleteOccurrence?.status)
    }

    @Test
    fun questionTextChangeDoesNotAffectOccurrenceSnapshot() = runBlocking {
        insertOccurrence(status = QuestionOccurrenceStatus.AVAILABLE, textSnapshot = "Snapshot A")
        markStarted()
        val before = repository.observeSnapshot().first()
        database.questionDao().updateText(1, "Changed question")
        val after = repository.observeSnapshot().first()
        assertEquals("Snapshot A", after.incompleteOccurrence?.questionTextSnapshot)
        assertNotEquals(
            database.questionDao().getByCyclePosition(1)!!.text,
            after.incompleteOccurrence?.questionTextSnapshot,
        )
    }

    @Test
    fun startPracticeEmitsConsistentStartedSnapshotWithoutTransientCorruption() = runBlocking {
        val timeProvider = FakeTimeProvider(epochAt(8, 0), ZONE)
        val cycleRepository = CycleRepository(database, timeProvider)
        val snapshots = mutableListOf<PracticeReadSnapshot>()
        var collectionError: Throwable? = null
        val job: Job = launch {
            try {
                repository.observeSnapshot().collect { snapshot ->
                    snapshots.add(snapshot)
                }
            } catch (exception: Throwable) {
                collectionError = exception
            }
        }

        awaitSnapshotCount(snapshots, atLeast = 1)
        assertEquals(false, snapshots.last().practiceState.isPracticeStarted)
        assertNull(snapshots.last().incompleteOccurrence)

        cycleRepository.startPractice()

        awaitStartedSnapshot(snapshots)
        assertNull(collectionError)

        snapshots.forEach { snapshot ->
            assertSnapshotInvariants(snapshot)
        }

        val startedSnapshot = snapshots.last { it.practiceState.isPracticeStarted }
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, startedSnapshot.incompleteOccurrence?.status)
        assertEquals(1, database.questionOccurrenceDao().count())

        job.cancel()
    }

    @Test
    fun rapidSequentialTransactionsEmitOnlyValidSnapshots() = runBlocking {
        val timeProvider = FakeTimeProvider(epochAt(8, 0), ZONE)
        val cycleRepository = CycleRepository(database, timeProvider)
        val snapshots = mutableListOf<PracticeReadSnapshot>()
        var collectionError: Throwable? = null
        val job: Job = launch {
            try {
                repository.observeSnapshot().collect { snapshot ->
                    snapshots.add(snapshot)
                }
            } catch (exception: Throwable) {
                collectionError = exception
            }
        }

        awaitSnapshotCount(snapshots, atLeast = 1)
        cycleRepository.startPractice()
        awaitStartedSnapshot(snapshots)
        cycleRepository.reconcile()
        cycleRepository.pausePractice()
        awaitUntil { snapshots.last().practiceState.isPaused }
        cycleRepository.resumePractice()
        awaitUntil {
            val last = snapshots.last()
            last.practiceState.isPracticeStarted && !last.practiceState.isPaused
        }

        assertNull(collectionError)
        snapshots.forEach { snapshot ->
            assertSnapshotInvariants(snapshot)
        }

        val roomState = database.practiceStateDao().get()!!
        val lastSnapshot = snapshots.last()
        assertEquals(roomState.isPracticeStarted, lastSnapshot.practiceState.isPracticeStarted)
        assertEquals(roomState.isPaused, lastSnapshot.practiceState.isPaused)
        assertEquals(
            database.questionOccurrenceDao().getIncompleteOrdered().singleOrNull()?.id,
            lastSnapshot.incompleteOccurrence?.id,
        )

        job.cancel()
    }

    private suspend fun awaitSnapshotCount(
        snapshots: List<PracticeReadSnapshot>,
        atLeast: Int,
    ) {
        withTimeout(10_000) {
            while (snapshots.size < atLeast) {
                delay(10)
            }
        }
    }

    private suspend fun awaitStartedSnapshot(snapshots: List<PracticeReadSnapshot>) {
        withTimeout(10_000) {
            while (snapshots.none { it.practiceState.isPracticeStarted }) {
                delay(10)
            }
        }
    }

    private suspend fun awaitUntil(condition: () -> Boolean) {
        withTimeout(10_000) {
            while (!condition()) {
                delay(10)
            }
        }
    }

    private fun assertSnapshotInvariants(snapshot: PracticeReadSnapshot) {
        if (!snapshot.practiceState.isPracticeStarted) {
            assertNull(snapshot.incompleteOccurrence)
            return
        }
        val incomplete = snapshot.incompleteOccurrence
            ?: throw AssertionError("Started snapshot must include incomplete occurrence")
        assertTrue(
            incomplete.status == QuestionOccurrenceStatus.SCHEDULED ||
                incomplete.status == QuestionOccurrenceStatus.AVAILABLE,
        )
        if (snapshot.practiceState.isPaused) {
            assertTrue(incomplete.status == QuestionOccurrenceStatus.SCHEDULED ||
                incomplete.status == QuestionOccurrenceStatus.AVAILABLE)
        }
    }

    private suspend fun seedBase() {
        val questions = (1..21).map { index ->
            QuestionEntity(id = index, cyclePosition = index, text = "Question $index", isActive = true)
        }
        database.questionDao().insertAll(questions)
        database.scheduleSlotDao().insertAll(
            listOf(
                ScheduleSlotEntity(slotIndex = 1, timeOfDayMinutes = 660),
                ScheduleSlotEntity(slotIndex = 2, timeOfDayMinutes = 900),
                ScheduleSlotEntity(slotIndex = 3, timeOfDayMinutes = 1140),
            ),
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
                activeZoneId = ZONE,
                seedVersion = 1,
            ),
        )
    }

    private suspend fun markStarted(paused: Boolean = false) {
        val current = database.practiceStateDao().get()!!
        database.practiceStateDao().update(
            current.copy(
                isPracticeStarted = true,
                isPaused = paused,
                practiceStartedAtEpochMillis = epochAt(8, 0),
                currentCycleNumber = 1,
                nextCyclePosition = 2,
            ),
        )
    }

    private suspend fun insertOccurrence(
        status: QuestionOccurrenceStatus,
        cyclePosition: Int = 1,
        textSnapshot: String = "Snapshot $cyclePosition",
    ) {
        database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = cyclePosition,
                questionTextSnapshot = textSnapshot,
                cycleNumber = 1,
                cyclePosition = cyclePosition,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = epochAt(11, 0),
                availableUntilEpochMillis = epochAt(15, 0),
                status = status,
                zoneId = ZONE,
            ),
        )
    }

    private fun epochAt(hour: Int, minute: Int): Long {
        return ZonedDateTime.of(2026, 8, 5, hour, minute, 0, 0, ZoneId.of(ZONE))
            .toInstant()
            .toEpochMilli()
    }

    private companion object {
        const val ZONE = "Europe/Kiev"
    }
}
// 05.08.2026 Atomic Practice Snapshot cursor by Me4Hik END
// 05.08.2026 Main Screen cursor by Me4Hik END

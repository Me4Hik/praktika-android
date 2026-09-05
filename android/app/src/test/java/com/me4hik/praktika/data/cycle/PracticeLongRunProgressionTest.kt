package com.me4hik.praktika.data.cycle

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.read.RoomPracticeReadRepository
import com.me4hik.praktika.notification.NotificationDeliveryCapability
import com.me4hik.praktika.notification.NotificationPermissionPolicy
import com.me4hik.praktika.notification.NotificationPermissionUiState
import com.me4hik.praktika.notification.NotificationPlan
import com.me4hik.praktika.notification.NotificationPlanner
import com.me4hik.praktika.notification.NotificationPlanningInput
import com.me4hik.praktika.notification.NotificationOccurrenceSnapshot
import com.me4hik.praktika.notification.NotificationOpenRequestStore
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.notification.PlatformAlarmScheduler
import com.me4hik.praktika.notification.PracticeNotificationCoordinator
import com.me4hik.praktika.notification.PracticeNotificationKind
import com.me4hik.praktika.notification.PracticeNotificationPresenter
import com.me4hik.praktika.notification.BoundaryAlarmPlan
import com.me4hik.praktika.notification.NotificationShowPlan
import com.me4hik.praktika.notification.ResidualPlannedRecoveryResult
import com.me4hik.praktika.runtime.PraktikaRuntimeInitializer
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class PracticeLongRunProgressionTest {
    private lateinit var context: Context
    private lateinit var database: PraktikaDatabase
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var cycleRepository: CycleRepository
    private lateinit var coordinator: PracticeNotificationCoordinator
    private lateinit var alarmScheduler: RecordingPlatformAlarmScheduler

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seedBaseData()
        timeProvider = FakeTimeProvider(epochAt(8, 0, 0), ZONE_KIEV)
        cycleRepository = CycleRepository(database, timeProvider, NoOpBackupMutationRequestSink)
        alarmScheduler = RecordingPlatformAlarmScheduler()
        val initializer = PraktikaRuntimeInitializer(context) { error("runtime unused") }
        initializer.completeActivityInit(true)
        coordinator = PracticeNotificationCoordinator(
            initializer = initializer,
            cycleRepository = cycleRepository,
            practiceReadRepository = RoomPracticeReadRepository(database),
            permissionRepository = EnabledNotificationPermissionPolicy(),
            soundEnabledProvider = { true },
            alarmScheduler = alarmScheduler,
            notificationPresenter = NoOpPracticeNotificationPresenter(),
            openRequestStore = NotificationOpenRequestStore(),
            timeProvider = timeProvider,
        )
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun allMissed_42_twoCycles_neverLosesLiveOccurrenceOrPlannerAlarm() = runBlocking {
        startPracticeBeforeFirstSlot()
        assertLiveKey(1, "after start")
        assertPlannerHasFutureDecision(0, "after start")

        repeat(MISSES_TWO_CYCLES) { index ->
            missCurrent(iteration = index + 1)
        }

        assertEq(MISSES_TWO_CYCLES, "missed count", MISSES_TWO_CYCLES, countByStatus(QuestionOccurrenceStatus.MISSED_BY_TIME))
        assertLiveKey(MISSES_TWO_CYCLES + 1, "after 42 misses")
        assertEq(MISSES_TWO_CYCLES, "live cycle after 42 misses", 3, liveOccurrence().cycleNumber)
        assertEq(MISSES_TWO_CYCLES, "live position after 42 misses", 1, liveOccurrence().cyclePosition)
        assertNoIllegalPositions("after 42 misses")
        assertUniqueOccurrenceKeys("after 42 misses")
        assertPlannerHasFutureDecision(MISSES_TWO_CYCLES, "after 42 misses")
        assertForegroundReconcileIdempotent(MISSES_TWO_CYCLES)
    }

    @Test
    fun catchUpJump_14Days_singleReconcile_thenForegroundIdempotent() = runBlocking {
        startPracticeBeforeFirstSlot()
        val startedLive = liveOccurrence()
        timeProvider.setEpochMillis(epochAtOnDate(2024, 6, 15, 12, 0, 0))
        cycleRepository.syncEnvironmentAndReconcile()

        val live = requireLive(0, "after 14-day jump")
        assertThat(0, "jump must wrap into a later cycle", live.cycleNumber >= 2)
        assertThat(0, "jump must not keep the start row as live", live.id != startedLive.id)
        val missed = countByStatus(QuestionOccurrenceStatus.MISSED_BY_TIME)
        assertThat(0, "jump must mark a full history of misses", missed >= CycleCursor.MAX_POSITION)
        assertEq(0, "live key must follow missed-count cursor", expectedKey(missed + 1), live.cycleNumber to live.cyclePosition)
        assertEq(0, "jump incomplete", 1, incomplete().size)
        assertThat(
            0,
            "jump live status ${live.status}",
            live.status == QuestionOccurrenceStatus.SCHEDULED ||
                live.status == QuestionOccurrenceStatus.AVAILABLE,
        )
        allOccurrences().filter { it.id != live.id }.forEach { row ->
            assertEq(0, "non-live ${row.cycleNumber},${row.cyclePosition}", QuestionOccurrenceStatus.MISSED_BY_TIME, row.status)
        }
        assertUniqueOccurrenceKeys("after jump")
        assertNoIllegalPositions("after jump")
        assertPlannerHasFutureDecision(0, "after jump")
        assertForegroundReconcileIdempotent(0)
    }

    @Test
    fun mixedAnswerMiss_42_patternMamma() = runBlocking {
        startPracticeBeforeFirstSlot()
        val pattern = charArrayOf('M', 'A', 'M', 'M', 'A')

        repeat(MIXED_STEPS) { index ->
            val iteration = index + 1
            when (pattern[index % pattern.size]) {
                'M' -> missCurrent(iteration)
                'A' -> answerCurrent(iteration)
                else -> error("unexpected mixed pattern")
            }
        }

        val all = allOccurrences()
        val live = requireLive(MIXED_STEPS, "after mixed 42")
        assertEq(MIXED_STEPS, "mixed live key", expectedKey(MIXED_STEPS + 1), live.cycleNumber to live.cyclePosition)
        assertEq(
            MIXED_STEPS,
            "mixed answered+missed",
            MIXED_STEPS,
            all.count {
                it.status == QuestionOccurrenceStatus.ANSWERED ||
                    it.status == QuestionOccurrenceStatus.MISSED_BY_TIME
            },
        )
        assertThat(MIXED_STEPS, "mixed must include answers", countByStatus(QuestionOccurrenceStatus.ANSWERED) >= 1)
        assertThat(MIXED_STEPS, "mixed must include misses", countByStatus(QuestionOccurrenceStatus.MISSED_BY_TIME) >= 1)
        all.filter { it.id != live.id }.forEach { row ->
            assertThat(
                MIXED_STEPS,
                "completed ${row.cycleNumber},${row.cyclePosition} ${row.status}",
                row.status == QuestionOccurrenceStatus.ANSWERED ||
                    row.status == QuestionOccurrenceStatus.MISSED_BY_TIME,
            )
        }
        assertUniqueOccurrenceKeys("after mixed 42")
        assertNoIllegalPositions("after mixed 42")
        assertPlannerHasFutureDecision(MIXED_STEPS, "after mixed 42")
        assertForegroundReconcileIdempotent(MIXED_STEPS)
    }

    @Test
    fun wrapFocus_1_20_to_2_2() = runBlocking {
        startPracticeBeforeFirstSlot()
        repeat(19) { index ->
            missCurrent(iteration = index + 1)
        }
        assertLiveKey(20, "before wrap")
        assertEq(20, "live cycle before wrap", 1, liveOccurrence().cycleNumber)
        assertEq(20, "live position before wrap", 20, liveOccurrence().cyclePosition)

        missCurrent(iteration = 20)
        assertEq(20, "after (1,20)", 1 to 21, liveOccurrence().cycleNumber to liveOccurrence().cyclePosition)

        missCurrent(iteration = 21)
        assertEq(21, "after (1,21)", 2 to 1, liveOccurrence().cycleNumber to liveOccurrence().cyclePosition)
        assertThat(21, "position 0 forbidden", liveOccurrence().cyclePosition != 0)

        missCurrent(iteration = 22)
        assertEq(22, "after (2,1)", 2 to 2, liveOccurrence().cycleNumber to liveOccurrence().cyclePosition)

        val keys = allOccurrences().map { it.cycleNumber to it.cyclePosition }.toSet()
        assertThat(22, "missing (1,20)", (1 to 20) in keys)
        assertThat(22, "missing (1,21)", (1 to 21) in keys)
        assertThat(22, "missing (2,1)", (2 to 1) in keys)
        assertThat(22, "missing (2,2)", (2 to 2) in keys)
        assertThat(22, "illegal (1,0)", (1 to 0) !in keys)
        assertThat(22, "illegal (1,22)", (1 to 22) !in keys)
        assertNoIllegalPositions("wrap focus")
        assertUniqueOccurrenceKeys("wrap focus")
        assertPlannerHasFutureDecision(22, "wrap focus")
    }

    @Test
    fun allMissed_210_tenCycles_historyDoesNotPoisonCurrent() = runBlocking {
        startPracticeBeforeFirstSlot()
        repeat(MISSES_TEN_CYCLES) { index ->
            missCurrent(iteration = index + 1)
        }

        val live = requireLive(MISSES_TEN_CYCLES, "after 210 misses")
        assertEq(MISSES_TEN_CYCLES, "210 missed", MISSES_TEN_CYCLES, countByStatus(QuestionOccurrenceStatus.MISSED_BY_TIME))
        assertEq(MISSES_TEN_CYCLES, "210 live key", 11 to 1, live.cycleNumber to live.cyclePosition)
        assertEq(MISSES_TEN_CYCLES, "210 incomplete", 1, incomplete().size)
        incomplete().forEach { row ->
            assertThat(
                MISSES_TEN_CYCLES,
                "poisoned current ${row.status}",
                row.status == QuestionOccurrenceStatus.SCHEDULED ||
                    row.status == QuestionOccurrenceStatus.AVAILABLE,
            )
        }
        assertUniqueOccurrenceKeys("after 210 misses")
        assertNoIllegalPositions("after 210 misses")
        assertPlannerHasFutureDecision(MISSES_TEN_CYCLES, "after 210 misses")
        assertForegroundReconcileIdempotent(MISSES_TEN_CYCLES)
    }

    private suspend fun startPracticeBeforeFirstSlot() {
        timeProvider.setEpochMillis(epochAt(8, 0, 0))
        cycleRepository.startPractice()
        val state = requirePracticeState()
        assertThat(0, "practice must start", state.isPracticeStarted)
        assertThat(0, "practice must not start paused", !state.isPaused)
    }

    private suspend fun missCurrent(iteration: Int) {
        val previous = requireLive(iteration, "before miss")
        assertEq(iteration, "miss previous key", expectedKey(iteration), previous.cycleNumber to previous.cyclePosition)
        promoteIfScheduled(iteration)
        val available = requireLive(iteration, "before expiry miss")
        assertEq(iteration, "miss id stable", previous.id, available.id)
        timeProvider.setEpochMillis(available.availableUntilEpochMillis)
        cycleRepository.syncEnvironmentAndReconcile()

        val missed = requireOccurrence(previous.id, iteration, "missed row")
        assertEq(iteration, "expected MISSED_BY_TIME", QuestionOccurrenceStatus.MISSED_BY_TIME, missed.status)
        assertThat(iteration, "missed completedAt", missed.completedAtEpochMillis != null)

        val live = requireLive(iteration, "after miss")
        assertThat(iteration, "miss must create a new live row", live.id != previous.id)
        assertEq(iteration, "miss next key", expectedKey(iteration + 1), live.cycleNumber to live.cyclePosition)
        assertNoIllegalPosition(live, iteration)
        assertPlannerHasFutureDecision(iteration, "after miss")
        assertThat(iteration, "practice remains infinite", requirePracticeState().isPracticeStarted)
    }

    private suspend fun answerCurrent(iteration: Int) {
        val previous = requireLive(iteration, "before answer")
        assertEq(iteration, "answer previous key", expectedKey(iteration), previous.cycleNumber to previous.cyclePosition)
        promoteIfScheduled(iteration)
        val available = requireLive(iteration, "before saveAnswer")
        assertEq(iteration, "answer requires AVAILABLE", QuestionOccurrenceStatus.AVAILABLE, available.status)
        assertThat(
            iteration,
            "answer window closed",
            timeProvider.nowEpochMillis() < available.availableUntilEpochMillis,
        )

        val result = cycleRepository.saveAnswer(available.id, "answer-$iteration")
        assertEq(iteration, "saveAnswer result", CycleResult.AnswerSaved, result)

        val answered = requireOccurrence(previous.id, iteration, "answered row")
        assertEq(iteration, "expected ANSWERED", QuestionOccurrenceStatus.ANSWERED, answered.status)
        assertThat(iteration, "answered completedAt", answered.completedAtEpochMillis != null)
        assertThat(
            iteration,
            "answer row missing",
            database.answerDao().getByOccurrenceId(previous.id) != null,
        )

        val live = requireLive(iteration, "after answer")
        assertThat(iteration, "answer must create a new live row", live.id != previous.id)
        assertEq(iteration, "answer next key", expectedKey(iteration + 1), live.cycleNumber to live.cyclePosition)
        assertNoIllegalPosition(live, iteration)
        assertPlannerHasFutureDecision(iteration, "after answer")
        assertThat(iteration, "practice remains infinite", requirePracticeState().isPracticeStarted)
    }

    private suspend fun promoteIfScheduled(iteration: Int) {
        val current = requireLive(iteration, "promote")
        if (current.status != QuestionOccurrenceStatus.SCHEDULED) {
            return
        }
        timeProvider.setEpochMillis(current.plannedAtEpochMillis)
        cycleRepository.syncEnvironmentAndReconcile()
        val promoted = requireLive(iteration, "after planned boundary")
        assertEq(iteration, "promote id", current.id, promoted.id)
        assertEq(iteration, "promote to AVAILABLE", QuestionOccurrenceStatus.AVAILABLE, promoted.status)
    }

    private suspend fun assertForegroundReconcileIdempotent(iteration: Int) {
        val before = requireLive(iteration, "before foreground")
        val beforeStatus = before.status
        val beforeCount = allOccurrences().size
        cycleRepository.syncEnvironmentAndReconcile()
        coordinator.sync(NotificationSyncReason.FOREGROUND)
        val after = requireLive(iteration, "after foreground")
        assertEq(iteration, "foreground must not replace live id", before.id, after.id)
        assertEq(iteration, "foreground must not change live status", beforeStatus, after.status)
        assertEq(iteration, "foreground must not insert rows", beforeCount, allOccurrences().size)
        assertPlannerHasFutureDecision(iteration, "after foreground")
        val scheduled = alarmScheduler.lastPlan
        assertThat(iteration, "foreground coordinator plan", scheduled != null)
        assertThat(iteration, "foreground coordinator cancelled all alarms", scheduled?.cancelAllAlarms != true)
        assertThat(
            iteration,
            "foreground coordinator has no future alarm",
            scheduled?.plannedBoundaryAlarm != null || scheduled?.expiryBoundaryAlarm != null,
        )
    }

    private suspend fun assertLiveKey(occurrenceIndex: Int, label: String) {
        val live = requireLive(occurrenceIndex, label)
        assertEq(occurrenceIndex, label, expectedKey(occurrenceIndex), live.cycleNumber to live.cyclePosition)
    }

    private suspend fun assertPlannerHasFutureDecision(iteration: Int, label: String) {
        val live = requireLive(iteration, label)
        assertThat(
            iteration,
            "$label live status ${live.status}",
            live.status == QuestionOccurrenceStatus.SCHEDULED ||
                live.status == QuestionOccurrenceStatus.AVAILABLE,
        )
        val state = requirePracticeState()
        assertThat(iteration, "$label started", state.isPracticeStarted)
        assertThat(iteration, "$label paused", !state.isPaused)
        val plan = planFor(live)
        assertThat(iteration, "$label cancelAllAlarms", !plan.cancelAllAlarms)
        assertThat(
            iteration,
            "$label no future alarm planned=${plan.plannedBoundaryAlarm} expiry=${plan.expiryBoundaryAlarm}",
            plan.plannedBoundaryAlarm != null || plan.expiryBoundaryAlarm != null,
        )
        val now = timeProvider.nowEpochMillis()
        if (live.status == QuestionOccurrenceStatus.SCHEDULED && now < live.plannedAtEpochMillis) {
            assertThat(iteration, "$label missing planned alarm", plan.plannedBoundaryAlarm != null)
            assertEq(iteration, "$label planned occurrenceId", live.id, plan.plannedBoundaryAlarm!!.occurrenceId)
            assertThat(
                iteration,
                "$label planned trigger not in the future",
                plan.plannedBoundaryAlarm.triggerAtEpochMillis > now,
            )
        }
        if (live.status == QuestionOccurrenceStatus.AVAILABLE) {
            assertThat(iteration, "$label missing expiry alarm", plan.expiryBoundaryAlarm != null)
            assertEq(iteration, "$label expiry occurrenceId", live.id, plan.expiryBoundaryAlarm!!.occurrenceId)
            assertThat(
                iteration,
                "$label expiry trigger not in the future",
                plan.expiryBoundaryAlarm.triggerAtEpochMillis > now,
            )
        }
        val alarm = plan.plannedBoundaryAlarm ?: plan.expiryBoundaryAlarm
        assertThat(iteration, "$label alarm", alarm != null)
        assertEq(iteration, "$label alarm occurrenceId", live.id, alarm!!.occurrenceId)
    }

    private fun planFor(occurrence: QuestionOccurrenceEntity): NotificationPlan {
        return NotificationPlanner.plan(
            input = NotificationPlanningInput(
                isPracticeStarted = true,
                isPaused = false,
                nowEpochMillis = timeProvider.nowEpochMillis(),
                currentOccurrence = NotificationOccurrenceSnapshot(
                    occurrenceId = occurrence.id,
                    status = occurrence.status,
                    plannedAtEpochMillis = occurrence.plannedAtEpochMillis,
                    availableUntilEpochMillis = occurrence.availableUntilEpochMillis,
                    questionTextSnapshot = occurrence.questionTextSnapshot,
                    openedAtEpochMillis = occurrence.openedAtEpochMillis,
                ),
                notificationCapability = NotificationDeliveryCapability.ENABLED,
                activeNotificationOccurrenceId = null,
            ),
            soundEnabled = true,
        )
    }

    private suspend fun assertUniqueOccurrenceKeys(label: String) {
        val keys = allOccurrences().map { it.cycleNumber to it.cyclePosition }
        assertEq(0, "$label duplicate keys $keys", keys.size, keys.toSet().size)
    }

    private suspend fun assertNoIllegalPositions(label: String) {
        allOccurrences().forEach { row ->
            assertNoIllegalPosition(row, 0, label)
        }
    }

    private suspend fun assertNoIllegalPosition(
        row: QuestionOccurrenceEntity,
        iteration: Int,
        label: String = "position",
    ) {
        assertThat(iteration, "$label illegal cycle ${row.cycleNumber}", row.cycleNumber >= 1)
        assertThat(
            iteration,
            "$label illegal position ${row.cyclePosition}",
            row.cyclePosition in CycleCursor.MIN_POSITION..CycleCursor.MAX_POSITION,
        )
    }

    private suspend fun requireLive(iteration: Int, label: String): QuestionOccurrenceEntity {
        val incomplete = incomplete()
        assertEq(iteration, "$label incomplete count", 1, incomplete.size)
        val live = incomplete.single()
        assertThat(
            iteration,
            "$label not live ${live.status}",
            live.status == QuestionOccurrenceStatus.SCHEDULED ||
                live.status == QuestionOccurrenceStatus.AVAILABLE,
        )
        return live
    }

    private suspend fun liveOccurrence(): QuestionOccurrenceEntity = incomplete().single()

    private suspend fun incomplete(): List<QuestionOccurrenceEntity> {
        return database.questionOccurrenceDao().getIncompleteOrdered()
    }

    private suspend fun allOccurrences(): List<QuestionOccurrenceEntity> {
        return database.questionOccurrenceDao().getAllOrderedByPlannedAt()
    }

    private suspend fun countByStatus(status: QuestionOccurrenceStatus): Int {
        return database.questionOccurrenceDao().countByStatus(status)
    }

    private suspend fun requireOccurrence(id: Long, iteration: Int, label: String): QuestionOccurrenceEntity {
        val row = database.questionOccurrenceDao().getById(id)
        assertThat(iteration, "$label missing id=$id", row != null)
        return row!!
    }

    private suspend fun requirePracticeState(): PracticeStateEntity {
        val state = database.practiceStateDao().get()
        assertNotNull("practice_state missing", state)
        return state!!
    }

    private suspend fun assertEq(iteration: Int, label: String, expected: Any?, actual: Any?) {
        if (expected != actual) {
            fail("${dump(iteration, label)} expected=$expected actual=$actual")
        }
    }

    private suspend fun assertThat(iteration: Int, label: String, condition: Boolean) {
        if (!condition) {
            fail(dump(iteration, label))
        }
    }

    private suspend fun dump(iteration: Int, label: String): String {
        val state = database.practiceStateDao().get()
        val incomplete = database.questionOccurrenceDao().getIncompleteOrdered()
        val all = database.questionOccurrenceDao().getAllOrderedByPlannedAt()
        val counts = all.groupingBy { it.status }.eachCount()
        val live = incomplete.singleOrNull()
        val plan = live?.let { planFor(it) }
        return "iteration=$iteration label=$label now=${timeProvider.nowEpochMillis()} " +
            "started=${state?.isPracticeStarted} paused=${state?.isPaused} " +
            "cursor=(${state?.currentCycleNumber},${state?.nextCyclePosition}) " +
            "incomplete=${incomplete.size} " +
            "live=${live?.let { "(${it.cycleNumber},${it.cyclePosition}) id=${it.id} ${it.status} plannedAt=${it.plannedAtEpochMillis} until=${it.availableUntilEpochMillis}" }} " +
            "counts=$counts " +
            "plan cancelAll=${plan?.cancelAllAlarms} planned=${plan?.plannedBoundaryAlarm} expiry=${plan?.expiryBoundaryAlarm}"
    }

    private fun expectedKey(occurrenceIndex: Int): Pair<Int, Int> {
        val cycleNumber = (occurrenceIndex - 1) / CycleCursor.MAX_POSITION + 1
        val cyclePosition = (occurrenceIndex - 1) % CycleCursor.MAX_POSITION + 1
        return cycleNumber to cyclePosition
    }

    private suspend fun seedBaseData() {
        val questions = (1..21).map { index ->
            QuestionEntity(
                id = index,
                cyclePosition = index,
                text = "Question $index",
                isActive = true,
            )
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
                activeZoneId = ZONE_KIEV,
                seedVersion = 1,
            ),
        )
    }

    private fun epochAt(hour: Int, minute: Int, second: Int): Long {
        return epochAtOnDate(2024, 6, 1, hour, minute, second)
    }

    private fun epochAtOnDate(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneId.of(ZONE_KIEV))
            .toInstant()
            .toEpochMilli()
    }

    private companion object {
        const val ZONE_KIEV = "Europe/Kyiv"
        const val MISSES_TWO_CYCLES = 42
        const val MIXED_STEPS = 42
        const val MISSES_TEN_CYCLES = 210
    }
}

private class RecordingPlatformAlarmScheduler : PlatformAlarmScheduler {
    var lastPlan: NotificationPlan? = null

    override fun scheduleAlarms(plan: NotificationPlan, previousAlarms: List<BoundaryAlarmPlan>) {
        lastPlan = plan
    }

    override fun cancelAlarms(alarms: List<BoundaryAlarmPlan>) = Unit

    override fun scheduleResidualPlannedRecovery(
        recoveryPlan: BoundaryAlarmPlan,
        useAlarmClock: Boolean,
    ): ResidualPlannedRecoveryResult = ResidualPlannedRecoveryResult.ALARM_CLOCK_SCHEDULED
}

private class NoOpPracticeNotificationPresenter : PracticeNotificationPresenter {
    override fun ensureChannelsCreated() = Unit

    override fun findActivePracticeNotificationOccurrenceId(): Long? = null

    override fun findActivePracticeNotificationKind(): PracticeNotificationKind? = null

    override fun showNotification(plan: NotificationShowPlan) = Unit

    override fun cancelCurrentPracticeNotification() = Unit

    override fun cancelLegacyPracticeNotifications(
        currentOccurrenceId: Long?,
        syncReason: String?,
    ) = Unit

    override fun cancelAllPracticeNotifications() = Unit
}

private class EnabledNotificationPermissionPolicy : NotificationPermissionPolicy {
    private val revision = MutableStateFlow(0L)
    override val permissionStateRevision: StateFlow<Long> = revision.asStateFlow()
    override fun notifyPermissionStateChanged(source: String) = Unit
    override val permissionRequested: Flow<Boolean> = flowOf(true)
    override suspend fun markPermissionRequested() = Unit
    override fun evaluateUiState(
        permissionRequested: Boolean,
        soundEnabled: Boolean,
        selectedSoundId: String,
    ): NotificationPermissionUiState = NotificationPermissionUiState.ENABLED
    override fun toDeliveryCapability(
        state: NotificationPermissionUiState,
    ): NotificationDeliveryCapability = NotificationDeliveryCapability.ENABLED
    override fun createAppNotificationSettingsIntent(): Intent = Intent()
    override fun createChannelSettingsIntent(
        soundEnabled: Boolean,
        selectedSoundId: String,
    ): Intent = Intent()
    override fun shouldRequestRuntimePermission(): Boolean = false
    override fun hasRuntimePermission(): Boolean = true
    override fun areAppNotificationsEnabled(): Boolean = true
    override fun shouldShowRequestPermissionRationale(): Boolean = false
    override fun isSelectedChannelEnabled(
        soundEnabled: Boolean,
        selectedSoundId: String,
    ): Boolean = true
}

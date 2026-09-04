package com.me4hik.praktika.notification

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.read.RoomPracticeReadRepository
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class PracticeNotificationCoordinatorStableIdTest {
    private lateinit var context: Context
    private lateinit var database: PraktikaDatabase
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var cycleRepository: CycleRepository
    private lateinit var presenter: RecordingPracticeNotificationPresenter
    private lateinit var alarmScheduler: RecordingPlatformAlarmScheduler
    private lateinit var coordinator: PracticeNotificationCoordinator

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seedBaseData()
        timeProvider = FakeTimeProvider(epochAt(11, 0, 0), ZONE_KIEV)
        cycleRepository = CycleRepository(database, timeProvider, NoOpBackupMutationRequestSink)
        presenter = RecordingPracticeNotificationPresenter()
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
            notificationPresenter = presenter,
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
    fun currentAvailable_prunesLegacyThenShowsStableCurrent() = runBlocking {
        startAvailableOccurrence()

        coordinator.sync(NotificationSyncReason.APP_START)

        assertEquals(1, presenter.legacyCancelCalls)
        assertEquals(1, presenter.shown.size)
        assertEquals(currentOccurrenceId(), presenter.shown.single().occurrenceId)
        assertTrue(presenter.shown.single().suppressAlert)
        assertEquals(0, presenter.cancelAllCalls)
    }

    @Test
    fun foregroundCatchUp_postsQuietDueWhenShadeEmpty() = runBlocking {
        startAvailableOccurrence()
        presenter.reset()

        coordinator.sync(NotificationSyncReason.FOREGROUND)

        assertEquals(1, presenter.shown.size)
        val shown = presenter.shown.single()
        assertEquals(PracticeNotificationKind.QUESTION, shown.kind)
        assertTrue(shown.suppressAlert)
        assertTrue(shown.soundEnabled)
    }

    @Test
    fun plannedAlarm_postsAlertingDue() = runBlocking {
        startAvailableOccurrence()
        presenter.reset()

        coordinator.sync(NotificationSyncReason.PLANNED_ALARM)

        assertEquals(1, presenter.shown.size)
        assertEquals(PracticeNotificationKind.QUESTION, presenter.shown.single().kind)
        assertFalse(presenter.shown.single().suppressAlert)
    }

    @Test
    fun deferredAlarmMaturity_postsAlertingDue() = runBlocking {
        startAvailableOccurrence()
        val occurrence = currentOccurrence()
        cycleRepository.deferAvailableOccurrence(occurrence.id, durationMinutes = 5)
        timeProvider.setEpochMillis(epochAt(11, 40, 0))
        presenter.resetShowsOnly()
        presenter.activeOccurrenceId = occurrence.id
        presenter.activeKind = PracticeNotificationKind.SNOOZED

        coordinator.sync(NotificationSyncReason.DEFERRED_ALARM)

        assertEquals(1, presenter.shown.size)
        assertEquals(PracticeNotificationKind.QUESTION, presenter.shown.single().kind)
        assertFalse(presenter.shown.single().suppressAlert)
    }

    @Test
    fun foregroundCatchUp_keepsDedupeWhenActiveQuestionPresent() = runBlocking {
        startAvailableOccurrence()
        val occurrence = currentOccurrence()
        presenter.activeOccurrenceId = occurrence.id
        presenter.activeKind = PracticeNotificationKind.QUESTION
        presenter.resetShowsOnly()

        coordinator.sync(NotificationSyncReason.FOREGROUND)

        assertTrue(presenter.shown.isEmpty())
    }

    @Test
    fun noCurrentNotification_removesAllPracticeNotifications() = runBlocking {
        startAvailableOccurrence()
        coordinator.sync(NotificationSyncReason.APP_START)
        presenter.reset()
        cycleRepository.pausePractice()

        coordinator.sync(NotificationSyncReason.APP_START)

        assertEquals(1, presenter.cancelAllCalls)
        assertTrue(presenter.shown.isEmpty())
        assertEquals(0, presenter.legacyCancelCalls)
    }

    @Test
    fun missedA_availableB_finalNotificationIsBOnly() = runBlocking {
        startAvailableOccurrence()
        coordinator.sync(NotificationSyncReason.APP_START)
        val occurrenceA = currentOccurrence()
        presenter.reset()
        timeProvider.setEpochMillis(epochAt(15, 30, 0))

        coordinator.sync(NotificationSyncReason.EXPIRY_ALARM)

        val occurrenceB = currentOccurrence()
        assertEquals(QuestionOccurrenceStatus.MISSED_BY_TIME, occurrenceById(occurrenceA.id).status)
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, occurrenceB.status)
        assertEquals(listOf(occurrenceB.id), presenter.shown.map { it.occurrenceId })
        assertEquals(1, presenter.legacyCancelCalls)
        assertEquals(1, presenter.shown.size)
    }

    @Test
    fun multipleStaleOccurrences_convergeToOneCurrentNotification() = runBlocking {
        startAvailableOccurrence()
        coordinator.sync(NotificationSyncReason.APP_START)
        val occurrenceA = currentOccurrence()
        presenter.reset()
        timeProvider.setEpochMillis(epochAt(19, 30, 0))

        coordinator.sync(NotificationSyncReason.EXPIRY_ALARM)

        val current = currentOccurrence()
        val missed = database.questionOccurrenceDao().getAllOrderedByPlannedAt()
            .filter { it.status == QuestionOccurrenceStatus.MISSED_BY_TIME }
        assertTrue(missed.size >= 2)
        assertTrue(missed.any { it.id == occurrenceA.id })
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, current.status)
        assertEquals(listOf(current.id), presenter.shown.map { it.occurrenceId })
        assertEquals(1, presenter.shown.size)
    }

    @Test
    fun repeatedSync_doesNotAccumulateShowsOnceActiveMatches() = runBlocking {
        startAvailableOccurrence()
        coordinator.sync(NotificationSyncReason.APP_START)
        val firstShow = presenter.shown.single()
        presenter.activeOccurrenceId = firstShow.occurrenceId

        coordinator.sync(NotificationSyncReason.APP_START)
        coordinator.sync(NotificationSyncReason.APP_START)

        assertEquals(1, presenter.shown.size)
        assertEquals(3, presenter.legacyCancelCalls)
    }

    @Test
    fun tapCurrent_cancelsStableNotification() = runBlocking {
        startAvailableOccurrence()
        coordinator.sync(NotificationSyncReason.APP_START)
        val occurrence = currentOccurrence()

        val decision = coordinator.handleNotificationTap(
            occurrenceId = occurrence.id,
            plannedAtEpochMillis = occurrence.plannedAtEpochMillis,
        )

        assertEquals(NotificationTapDecision.Open(occurrence.id), decision)
        assertEquals(1, presenter.cancelCurrentCalls)
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, currentOccurrence().status)
        assertTrue(currentOccurrence().openedAtEpochMillis != null)
    }

    @Test
    fun tapSnoozed_restoresSnoozedAfterOpenedAt() = runBlocking {
        startAvailableOccurrence()
        val occurrence = currentOccurrence()
        cycleRepository.deferAvailableOccurrence(occurrence.id, durationMinutes = 5)
        coordinator.sync(NotificationSyncReason.MUTATION)
        presenter.resetShowsOnly()
        presenter.activeOccurrenceId = occurrence.id
        presenter.activeKind = PracticeNotificationKind.SNOOZED

        val decision = coordinator.handleNotificationTap(
            occurrenceId = occurrence.id,
            plannedAtEpochMillis = occurrence.plannedAtEpochMillis,
        )

        assertEquals(NotificationTapDecision.Open(occurrence.id), decision)
        assertTrue(currentOccurrence().openedAtEpochMillis != null)
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, currentOccurrence().status)
        assertEquals(1, presenter.cancelCurrentCalls)
        val lastShow = presenter.shown.last()
        assertEquals(PracticeNotificationKind.SNOOZED, lastShow.kind)
        assertEquals(occurrence.id, lastShow.occurrenceId)
    }

    @Test
    fun beforeMaturity_keepsSnoozeAndDeferredAlarm() = runBlocking {
        startAvailableOccurrence()
        val occurrence = currentOccurrence()
        cycleRepository.deferAvailableOccurrence(occurrence.id, durationMinutes = 5)
        cycleRepository.markOccurrenceOpened(occurrence.id)

        coordinator.sync(NotificationSyncReason.MUTATION)

        val after = currentOccurrence()
        assertTrue(after.deferredUntilEpochMillis != null)
        assertTrue(after.openedAtEpochMillis != null)
        assertEquals(PracticeNotificationKind.SNOOZED, presenter.shown.last().kind)
        assertEquals(
            after.deferredUntilEpochMillis,
            alarmScheduler.lastPlan?.deferredReminderAlarm?.triggerAtEpochMillis,
        )
    }

    @Test
    fun maturity_sameSyncConsumesAndShowsQuestionWithoutSecondSync() = runBlocking {
        startAvailableOccurrence()
        val occurrence = currentOccurrence()
        cycleRepository.deferAvailableOccurrence(occurrence.id, durationMinutes = 5)
        cycleRepository.markOccurrenceOpened(occurrence.id)
        assertTrue(currentOccurrence().openedAtEpochMillis != null)
        assertTrue(currentOccurrence().deferredUntilEpochMillis != null)

        // Past deferredUntil, still before availableUntil (15:00).
        timeProvider.setEpochMillis(epochAt(11, 40, 0))
        presenter.resetShowsOnly()
        presenter.activeOccurrenceId = occurrence.id
        presenter.activeKind = PracticeNotificationKind.SNOOZED

        coordinator.sync(NotificationSyncReason.DEFERRED_ALARM)

        val after = currentOccurrence()
        assertNull(after.deferredUntilEpochMillis)
        assertNull(after.openedAtEpochMillis)
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, after.status)
        assertEquals(1, presenter.shown.size)
        assertEquals(PracticeNotificationKind.QUESTION, presenter.shown.single().kind)
        assertNull(alarmScheduler.lastPlan?.deferredReminderAlarm)
    }

    @Test
    fun maturityConsume_secondReconcileIsNoOp() = runBlocking {
        startAvailableOccurrence()
        val occurrence = currentOccurrence()
        cycleRepository.deferAvailableOccurrence(occurrence.id, durationMinutes = 5)
        cycleRepository.markOccurrenceOpened(occurrence.id)
        timeProvider.setEpochMillis(epochAt(11, 40, 0))

        cycleRepository.reconcile()
        val afterFirst = currentOccurrence()
        assertNull(afterFirst.deferredUntilEpochMillis)
        assertNull(afterFirst.openedAtEpochMillis)

        cycleRepository.reconcile()
        val afterSecond = currentOccurrence()
        assertNull(afterSecond.deferredUntilEpochMillis)
        assertNull(afterSecond.openedAtEpochMillis)
        assertEquals(afterFirst.id, afterSecond.id)
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, afterSecond.status)
    }

    @Test
    fun maturedQuestionTap_nextSyncDoesNotResurrect() = runBlocking {
        startAvailableOccurrence()
        val occurrence = currentOccurrence()
        cycleRepository.deferAvailableOccurrence(occurrence.id, durationMinutes = 5)
        timeProvider.setEpochMillis(epochAt(11, 40, 0))
        coordinator.sync(NotificationSyncReason.DEFERRED_ALARM)
        assertEquals(PracticeNotificationKind.QUESTION, presenter.shown.last().kind)
        assertNull(currentOccurrence().deferredUntilEpochMillis)

        presenter.resetShowsOnly()
        presenter.activeOccurrenceId = occurrence.id
        presenter.activeKind = PracticeNotificationKind.QUESTION

        val decision = coordinator.handleNotificationTap(
            occurrenceId = occurrence.id,
            plannedAtEpochMillis = occurrence.plannedAtEpochMillis,
        )
        assertEquals(NotificationTapDecision.Open(occurrence.id), decision)
        assertTrue(currentOccurrence().openedAtEpochMillis != null)
        assertNull(currentOccurrence().deferredUntilEpochMillis)

        presenter.resetShowsOnly()
        coordinator.sync(NotificationSyncReason.FOREGROUND)

        assertTrue(presenter.shown.isEmpty())
        assertNull(currentOccurrence().deferredUntilEpochMillis)
        assertTrue(currentOccurrence().openedAtEpochMillis != null)
    }

    @Test
    fun reDeferAfterMaturity_showsSnoozedAgain() = runBlocking {
        startAvailableOccurrence()
        val occurrence = currentOccurrence()
        cycleRepository.deferAvailableOccurrence(occurrence.id, durationMinutes = 5)
        timeProvider.setEpochMillis(epochAt(11, 40, 0))
        coordinator.sync(NotificationSyncReason.DEFERRED_ALARM)
        assertNull(currentOccurrence().deferredUntilEpochMillis)

        cycleRepository.deferAvailableOccurrence(occurrence.id, durationMinutes = 10)
        coordinator.sync(NotificationSyncReason.MUTATION)

        val after = currentOccurrence()
        assertTrue(after.deferredUntilEpochMillis != null)
        assertNull(after.openedAtEpochMillis)
        assertEquals(PracticeNotificationKind.SNOOZED, presenter.shown.last().kind)
        assertEquals(
            after.deferredUntilEpochMillis,
            alarmScheduler.lastPlan?.deferredReminderAlarm?.triggerAtEpochMillis,
        )
    }

    @Test
    fun semanticExpiry_beatsDeferMaturity_doesNotResurrectOldOccurrence() = runBlocking {
        startAvailableOccurrence()
        val occurrenceA = currentOccurrence()
        cycleRepository.deferAvailableOccurrence(occurrenceA.id, durationMinutes = 30)
        cycleRepository.markOccurrenceOpened(occurrenceA.id)
        // Past availableUntil (15:00) while deferredUntil would still be "in future" relative to defer time,
        // but semantic expiry must win.
        timeProvider.setEpochMillis(epochAt(15, 30, 0))
        presenter.reset()

        coordinator.sync(NotificationSyncReason.EXPIRY_ALARM)

        assertEquals(QuestionOccurrenceStatus.MISSED_BY_TIME, occurrenceById(occurrenceA.id).status)
        val current = currentOccurrence()
        assertTrue(current.id != occurrenceA.id)
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, current.status)
        assertEquals(listOf(current.id), presenter.shown.map { it.occurrenceId })
        assertTrue(presenter.shown.none { it.occurrenceId == occurrenceA.id })
        assertFalse(presenter.shown.single().suppressAlert)
    }

    @Test
    fun tapStaleMissedOccurrence_doesNotResurrectAnswerableA() = runBlocking {
        startAvailableOccurrence()
        coordinator.sync(NotificationSyncReason.APP_START)
        val occurrenceA = currentOccurrence()
        timeProvider.setEpochMillis(epochAt(15, 30, 0))
        coordinator.sync(NotificationSyncReason.EXPIRY_ALARM)
        val occurrenceB = currentOccurrence()
        presenter.reset()

        val decision = coordinator.handleNotificationTap(
            occurrenceId = occurrenceA.id,
            plannedAtEpochMillis = occurrenceA.plannedAtEpochMillis,
        )

        assertEquals(NotificationTapDecision.Ignore, decision)
        assertEquals(0, presenter.cancelCurrentCalls)
        assertEquals(QuestionOccurrenceStatus.MISSED_BY_TIME, occurrenceById(occurrenceA.id).status)
        assertEquals(occurrenceB.id, currentOccurrence().id)
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, currentOccurrence().status)
        assertTrue(currentOccurrence().openedAtEpochMillis == null)
    }

    @Test
    fun cleanupFailure_stillShowsCurrentNotification() = runBlocking {
        startAvailableOccurrence()
        presenter.throwOnLegacyCancel = true

        coordinator.sync(NotificationSyncReason.APP_START)

        assertEquals(1, presenter.legacyCancelCalls)
        assertEquals(1, presenter.shown.size)
        assertEquals(currentOccurrenceId(), presenter.shown.single().occurrenceId)
    }

    @Test
    fun staleActiveOccurrenceDoesNotRepostAWhenBIsCurrent() = runBlocking {
        startAvailableOccurrence()
        coordinator.sync(NotificationSyncReason.APP_START)
        val occurrenceA = currentOccurrence()
        timeProvider.setEpochMillis(epochAt(15, 30, 0))
        presenter.activeOccurrenceId = occurrenceA.id
        presenter.resetShowsOnly()

        coordinator.sync(NotificationSyncReason.PLANNED_ALARM)

        val occurrenceB = currentOccurrence()
        assertEquals(occurrenceB.id, presenter.shown.single().occurrenceId)
        assertTrue(presenter.shown.none { it.occurrenceId == occurrenceA.id })
    }

    private suspend fun startAvailableOccurrence() {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        cycleRepository.startPractice()
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        cycleRepository.syncEnvironmentAndReconcile()
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, currentOccurrence().status)
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

    private suspend fun currentOccurrence(): QuestionOccurrenceEntity {
        return database.questionOccurrenceDao().getIncompleteOrdered().single()
    }

    private suspend fun currentOccurrenceId(): Long = currentOccurrence().id

    private suspend fun occurrenceById(id: Long): QuestionOccurrenceEntity {
        return checkNotNull(database.questionOccurrenceDao().getById(id))
    }

    private fun epochAt(hour: Int, minute: Int, second: Int): Long {
        return ZonedDateTime.of(2024, 6, 1, hour, minute, second, 0, ZoneId.of(ZONE_KIEV))
            .toInstant()
            .toEpochMilli()
    }

    private companion object {
        const val ZONE_KIEV = "Europe/Kyiv"
    }
}

private class RecordingPracticeNotificationPresenter : PracticeNotificationPresenter {
    val shown = mutableListOf<NotificationShowPlan>()
    var cancelCurrentCalls = 0
    var cancelAllCalls = 0
    var legacyCancelCalls = 0
    var throwOnLegacyCancel = false
    var activeOccurrenceId: Long? = null
    var activeKind: PracticeNotificationKind? = null

    override fun ensureChannelsCreated() = Unit

    override fun findActivePracticeNotificationOccurrenceId(): Long? = activeOccurrenceId

    override fun findActivePracticeNotificationKind(): PracticeNotificationKind? = activeKind

    override fun showNotification(plan: NotificationShowPlan) {
        shown += plan
        activeOccurrenceId = plan.occurrenceId
        activeKind = plan.kind
    }

    override fun cancelCurrentPracticeNotification() {
        cancelCurrentCalls += 1
        activeOccurrenceId = null
        activeKind = null
    }

    override fun cancelLegacyPracticeNotifications(
        currentOccurrenceId: Long?,
        syncReason: String?,
    ) {
        legacyCancelCalls += 1
        if (throwOnLegacyCancel) {
            throwOnLegacyCancel = false
            throw IllegalStateException("legacy cleanup failed")
        }
    }

    override fun cancelAllPracticeNotifications() {
        cancelAllCalls += 1
        activeOccurrenceId = null
        activeKind = null
    }

    fun reset() {
        shown.clear()
        cancelCurrentCalls = 0
        cancelAllCalls = 0
        legacyCancelCalls = 0
        throwOnLegacyCancel = false
    }

    fun resetShowsOnly() {
        shown.clear()
        cancelCurrentCalls = 0
        cancelAllCalls = 0
        legacyCancelCalls = 0
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
    ) = ResidualPlannedRecoveryResult.ALARM_CLOCK_SCHEDULED
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
    ): NotificationPermissionUiState = NotificationPermissionUiState.ENABLED
    override fun toDeliveryCapability(
        state: NotificationPermissionUiState,
    ): NotificationDeliveryCapability = NotificationDeliveryCapability.ENABLED
    override fun createAppNotificationSettingsIntent(): Intent = Intent()
    override fun createChannelSettingsIntent(soundEnabled: Boolean): Intent = Intent()
    override fun shouldRequestRuntimePermission(): Boolean = false
    override fun hasRuntimePermission(): Boolean = true
    override fun areAppNotificationsEnabled(): Boolean = true
    override fun shouldShowRequestPermissionRationale(): Boolean = false
    override fun isSelectedChannelEnabled(soundEnabled: Boolean): Boolean = true
}

package com.me4hik.praktika.notification

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.read.ArchiveOccurrenceOutcome
import com.me4hik.praktika.data.read.RoomArchiveReadRepository
import com.me4hik.praktika.data.read.RoomPracticeReadRepository
import com.me4hik.praktika.runtime.PraktikaRuntimeInitializer
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    private lateinit var archiveReadRepository: RoomArchiveReadRepository
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
        presenter = RecordingPracticeNotificationPresenter()
        alarmScheduler = RecordingPlatformAlarmScheduler()
        wireRepositoriesAndCoordinator()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun appStart_availableDue_doesNotPost_butSchedulesAlarms() = runBlocking {
        startAvailableOccurrence()
        presenter.reset()
        alarmScheduler.lastPlan = null

        coordinator.sync(NotificationSyncReason.APP_START)

        assertTrue(presenter.shown.isEmpty())
        assertEquals(0, presenter.legacyCancelCalls)
        assertEquals(0, presenter.cancelAllCalls)
        assertNull(presenter.activeOccurrenceId)
        val plan = checkNotNull(alarmScheduler.lastPlan)
        assertEquals(
            currentOccurrence().availableUntilEpochMillis,
            plan.expiryBoundaryAlarm?.triggerAtEpochMillis,
        )
    }

    @Test
    fun soundChanged_activeQuestion_forceRefreshesQuietKeepRouting() = runBlocking {
        startAvailableOccurrence()
        val occurrenceId = currentOccurrenceId()
        presenter.reset()
        presenter.activeOccurrenceId = occurrenceId
        presenter.activeKind = PracticeNotificationKind.QUESTION
        presenter.activeChannelId = "practice_due_custom_v1_03"

        val coordinatorWithSound = PracticeNotificationCoordinator(
            initializer = PraktikaRuntimeInitializer(context) { error("runtime unused") }.also {
                it.completeActivityInit(true)
            },
            cycleRepository = cycleRepository,
            practiceReadRepository = RoomPracticeReadRepository(database),
            permissionRepository = EnabledNotificationPermissionPolicy(),
            soundEnabledProvider = { true },
            selectedSoundIdProvider = {
                com.me4hik.praktika.sound.SoundAssetIds.builtin(8)
            },
            alarmScheduler = alarmScheduler,
            notificationPresenter = presenter,
            openRequestStore = NotificationOpenRequestStore(),
            timeProvider = timeProvider,
        )

        coordinatorWithSound.sync(NotificationSyncReason.SOUND_CHANGED)

        assertEquals(1, presenter.shown.size)
        val shown = presenter.shown.single()
        assertEquals(PracticeNotificationKind.QUESTION, shown.kind)
        assertFalse(shown.suppressAlert)
        assertTrue(shown.quietUpdateKeepRouting)
        assertEquals(com.me4hik.praktika.sound.SoundAssetIds.builtin(8), shown.selectedSoundId)
        assertEquals(1, presenter.pruneCalls.size)
        assertEquals(emptySet<String>(), presenter.pruneCalls.single().additionalKeep)
    }

    @Test
    fun appStart_activeQuestion_preservesHostingCustomChannelInPrune() = runBlocking {
        startAvailableOccurrence()
        presenter.reset()
        presenter.activeOccurrenceId = currentOccurrenceId()
        presenter.activeKind = PracticeNotificationKind.QUESTION
        presenter.activeChannelId = "practice_due_custom_v1_03"

        coordinatorWithSelected(com.me4hik.praktika.sound.SoundAssetIds.builtin(8))
            .sync(NotificationSyncReason.APP_START)

        assertTrue(presenter.shown.isEmpty())
        assertEquals(1, presenter.pruneCalls.size)
        assertEquals(
            setOf("practice_due_custom_v1_03"),
            presenter.pruneCalls.single().additionalKeep,
        )
    }

    @Test
    fun foreground_noRepost_preservesActiveCustomHostingChannel() = runBlocking {
        startAvailableOccurrence()
        presenter.reset()
        presenter.activeOccurrenceId = currentOccurrenceId()
        presenter.activeKind = PracticeNotificationKind.QUESTION
        presenter.activeChannelId = "practice_due_custom_v1_03"

        coordinatorWithSelected(com.me4hik.praktika.sound.SoundAssetIds.builtin(8))
            .sync(NotificationSyncReason.FOREGROUND)

        assertTrue(presenter.shown.isEmpty())
        assertEquals(
            setOf("practice_due_custom_v1_03"),
            presenter.pruneCalls.single().additionalKeep,
        )
    }

    @Test
    fun mutation_noRepost_preservesActiveCustomHostingChannel() = runBlocking {
        startAvailableOccurrence()
        presenter.reset()
        presenter.activeOccurrenceId = currentOccurrenceId()
        presenter.activeKind = PracticeNotificationKind.QUESTION
        presenter.activeChannelId = "practice_due_custom_v1_01"

        coordinatorWithSelected(com.me4hik.praktika.sound.SoundAssetIds.SYSTEM_DEFAULT)
            .sync(NotificationSyncReason.MUTATION)

        assertTrue(presenter.shown.isEmpty())
        assertEquals(
            setOf("practice_due_custom_v1_01"),
            presenter.pruneCalls.single().additionalKeep,
        )
    }

    @Test
    fun upgradePath_appStartThenForeground_keepsStaleHostingUntilReplace() = runBlocking {
        startAvailableOccurrence()
        presenter.reset()
        presenter.activeOccurrenceId = currentOccurrenceId()
        presenter.activeKind = PracticeNotificationKind.QUESTION
        presenter.activeChannelId = "practice_due_custom_v1_01"
        val c = coordinatorWithSelected(com.me4hik.praktika.sound.SoundAssetIds.SYSTEM_DEFAULT)

        c.sync(NotificationSyncReason.APP_START)
        assertEquals(
            setOf("practice_due_custom_v1_01"),
            presenter.pruneCalls.last().additionalKeep,
        )

        c.sync(NotificationSyncReason.FOREGROUND)
        assertTrue(presenter.shown.isEmpty())
        assertEquals(
            setOf("practice_due_custom_v1_01"),
            presenter.pruneCalls.last().additionalKeep,
        )
    }

    @Test
    fun soundChanged_successfulReplace_clearsHostingKeep() = runBlocking {
        startAvailableOccurrence()
        val occurrenceId = currentOccurrenceId()
        presenter.reset()
        presenter.activeOccurrenceId = occurrenceId
        presenter.activeKind = PracticeNotificationKind.QUESTION
        presenter.activeChannelId = "practice_due_custom_v1_03"

        coordinatorWithSelected(com.me4hik.praktika.sound.SoundAssetIds.builtin(8))
            .sync(NotificationSyncReason.SOUND_CHANGED)

        assertEquals(1, presenter.shown.size)
        assertTrue(presenter.shown.single().quietUpdateKeepRouting)
        assertEquals(emptySet<String>(), presenter.pruneCalls.single().additionalKeep)
    }

    @Test
    fun cancelActiveQuestion_allowsPruneOfFormerHostingChannel() = runBlocking {
        startAvailableOccurrence()
        presenter.reset()
        presenter.activeOccurrenceId = currentOccurrenceId()
        presenter.activeKind = PracticeNotificationKind.QUESTION
        presenter.activeChannelId = "practice_due_custom_v1_03"
        cycleRepository.pausePractice()

        coordinatorWithSelected(com.me4hik.praktika.sound.SoundAssetIds.SYSTEM_DEFAULT)
            .sync(NotificationSyncReason.MUTATION)

        assertEquals(1, presenter.cancelAllCalls)
        assertTrue(presenter.shown.isEmpty())
        assertEquals(emptySet<String>(), presenter.pruneCalls.single().additionalKeep)
    }

    @Test
    fun noActiveQuestion_orphanCleanupHasEmptyAdditionalKeep() = runBlocking {
        startAvailableOccurrence()
        presenter.reset()
        assertNull(presenter.activeOccurrenceId)

        coordinatorWithSelected(com.me4hik.praktika.sound.SoundAssetIds.SYSTEM_DEFAULT)
            .sync(NotificationSyncReason.FOREGROUND)

        assertEquals(1, presenter.shown.size)
        assertEquals(emptySet<String>(), presenter.pruneCalls.single().additionalKeep)
    }

    @Test
    fun presentationFailure_preservesHostingCustomChannel() = runBlocking {
        startAvailableOccurrence()
        val occurrenceId = currentOccurrenceId()
        presenter.reset()
        presenter.activeOccurrenceId = occurrenceId
        presenter.activeKind = PracticeNotificationKind.QUESTION
        presenter.activeChannelId = "practice_due_custom_v1_03"
        presenter.throwOnShow = true

        coordinatorWithSelected(com.me4hik.praktika.sound.SoundAssetIds.builtin(8))
            .sync(NotificationSyncReason.SOUND_CHANGED)

        // Outer sync catch: prune may be skipped entirely on thrown show — either no prune
        // or keep hosting. throwOnShow propagates from recording presenter unless caught.
        // Coordinator calls showNotification which throws → syncLocked catch → no prune.
        assertTrue(presenter.shown.isEmpty())
        assertTrue(presenter.pruneCalls.isEmpty())
    }

    @Test
    fun presentationReturnsFalse_preservesHostingCustomChannel() = runBlocking {
        startAvailableOccurrence()
        val occurrenceId = currentOccurrenceId()
        presenter.reset()
        presenter.activeOccurrenceId = occurrenceId
        presenter.activeKind = PracticeNotificationKind.QUESTION
        presenter.activeChannelId = "practice_due_custom_v1_03"
        presenter.showSucceeds = false

        coordinatorWithSelected(com.me4hik.praktika.sound.SoundAssetIds.builtin(8))
            .sync(NotificationSyncReason.SOUND_CHANGED)

        assertTrue(presenter.shown.isEmpty())
        assertEquals(
            setOf("practice_due_custom_v1_03"),
            presenter.pruneCalls.single().additionalKeep,
        )
    }

    @Test
    fun afterSuccessfulReplace_laterNoShowPruneDoesNotKeepOldA() = runBlocking {
        startAvailableOccurrence()
        val occurrenceId = currentOccurrenceId()
        presenter.reset()
        presenter.activeOccurrenceId = occurrenceId
        presenter.activeKind = PracticeNotificationKind.QUESTION
        presenter.activeChannelId = "practice_due_custom_v1_03"
        val c = coordinatorWithSelected(com.me4hik.praktika.sound.SoundAssetIds.builtin(8))

        c.sync(NotificationSyncReason.SOUND_CHANGED)
        assertEquals("practice_due_custom_v1_08", presenter.activeChannelId)
        assertEquals(emptySet<String>(), presenter.pruneCalls.last().additionalKeep)

        // Now active is already on B; no-repost keeps B (selected), not old A.
        c.sync(NotificationSyncReason.FOREGROUND)
        assertTrue(presenter.shown.size == 1) // only first SOUND_CHANGED show
        assertEquals(
            setOf("practice_due_custom_v1_08"),
            presenter.pruneCalls.last().additionalKeep,
        )
    }

    @Test
    fun cancelFailure_preservesHostingCustomChannel() = runBlocking {
        startAvailableOccurrence()
        presenter.reset()
        presenter.activeOccurrenceId = currentOccurrenceId()
        presenter.activeKind = PracticeNotificationKind.QUESTION
        presenter.activeChannelId = "practice_due_custom_v1_03"
        presenter.throwOnCancelAll = true
        cycleRepository.pausePractice()

        coordinatorWithSelected(com.me4hik.praktika.sound.SoundAssetIds.SYSTEM_DEFAULT)
            .sync(NotificationSyncReason.MUTATION)

        assertEquals(1, presenter.cancelAllCalls)
        assertEquals(
            setOf("practice_due_custom_v1_03"),
            presenter.pruneCalls.single().additionalKeep,
        )
    }

    private fun coordinatorWithSelected(selectedSoundId: String): PracticeNotificationCoordinator {
        return PracticeNotificationCoordinator(
            initializer = PraktikaRuntimeInitializer(context) { error("runtime unused") }.also {
                it.completeActivityInit(true)
            },
            cycleRepository = cycleRepository,
            practiceReadRepository = RoomPracticeReadRepository(database),
            permissionRepository = EnabledNotificationPermissionPolicy(),
            soundEnabledProvider = { true },
            selectedSoundIdProvider = { selectedSoundId },
            alarmScheduler = alarmScheduler,
            notificationPresenter = presenter,
            openRequestStore = NotificationOpenRequestStore(),
            timeProvider = timeProvider,
        )
    }

    @Test
    fun appStartThenForeground_quietRestoreAfterDismiss() = runBlocking {
        startAvailableOccurrence()
        presenter.reset()

        coordinator.sync(NotificationSyncReason.APP_START)
        assertTrue(presenter.shown.isEmpty())

        coordinator.sync(NotificationSyncReason.FOREGROUND)

        assertEquals(1, presenter.shown.size)
        val shown = presenter.shown.single()
        assertEquals(PracticeNotificationKind.QUESTION, shown.kind)
        assertTrue(shown.suppressAlert)
        assertTrue(shown.soundEnabled)
    }

    @Test
    fun appStartThenExpiry_alertingDueWithoutSilentOwnershipSteal() = runBlocking {
        startAvailableOccurrence()
        presenter.reset()

        coordinator.sync(NotificationSyncReason.APP_START)
        assertTrue(presenter.shown.isEmpty())
        assertNull(presenter.activeOccurrenceId)

        coordinator.sync(NotificationSyncReason.EXPIRY_ALARM)

        assertEquals(1, presenter.shown.size)
        assertEquals(PracticeNotificationKind.QUESTION, presenter.shown.single().kind)
        assertFalse(presenter.shown.single().suppressAlert)
    }

    @Test
    fun appStartThenPlanned_alertingDue() = runBlocking {
        startAvailableOccurrence()
        presenter.reset()

        coordinator.sync(NotificationSyncReason.APP_START)
        assertTrue(presenter.shown.isEmpty())

        coordinator.sync(NotificationSyncReason.PLANNED_ALARM)

        assertEquals(1, presenter.shown.size)
        assertEquals(PracticeNotificationKind.QUESTION, presenter.shown.single().kind)
        assertFalse(presenter.shown.single().suppressAlert)
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
    fun appStartThenDeferred_alertingMaturity() = runBlocking {
        startAvailableOccurrence()
        val occurrence = currentOccurrence()
        cycleRepository.deferAvailableOccurrence(occurrence.id, durationMinutes = 5)
        timeProvider.setEpochMillis(epochAt(11, 40, 0))
        presenter.reset()
        presenter.activeOccurrenceId = occurrence.id
        presenter.activeKind = PracticeNotificationKind.SNOOZED

        coordinator.sync(NotificationSyncReason.APP_START)
        assertTrue(presenter.shown.isEmpty())
        // Shade still shows SNOOZED from before process wake; APP_START must not touch it.
        assertEquals(PracticeNotificationKind.SNOOZED, presenter.activeKind)

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
        coordinator.sync(NotificationSyncReason.FOREGROUND)
        presenter.reset()
        cycleRepository.pausePractice()

        coordinator.sync(NotificationSyncReason.FOREGROUND)

        assertEquals(1, presenter.cancelAllCalls)
        assertTrue(presenter.shown.isEmpty())
        assertEquals(0, presenter.legacyCancelCalls)
    }

    @Test
    fun appStart_whilePaused_doesNotCancelNotifications() = runBlocking {
        startAvailableOccurrence()
        coordinator.sync(NotificationSyncReason.FOREGROUND)
        presenter.reset()
        cycleRepository.pausePractice()

        coordinator.sync(NotificationSyncReason.APP_START)

        assertEquals(0, presenter.cancelAllCalls)
        assertTrue(presenter.shown.isEmpty())
        assertNotNull(alarmScheduler.lastPlan)
    }

    @Test
    fun missedA_availableB_finalNotificationIsBOnly() = runBlocking {
        startAvailableOccurrence()
        coordinator.sync(NotificationSyncReason.FOREGROUND)
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
        coordinator.sync(NotificationSyncReason.FOREGROUND)
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
    fun repeatedForegroundSync_doesNotAccumulateShowsOnceActiveMatches() = runBlocking {
        startAvailableOccurrence()
        coordinator.sync(NotificationSyncReason.FOREGROUND)
        val firstShow = presenter.shown.single()
        presenter.activeOccurrenceId = firstShow.occurrenceId

        coordinator.sync(NotificationSyncReason.FOREGROUND)
        coordinator.sync(NotificationSyncReason.FOREGROUND)

        assertEquals(1, presenter.shown.size)
        assertEquals(3, presenter.legacyCancelCalls)
    }

    @Test
    fun tapCurrent_cancelsStableNotification() = runBlocking {
        startAvailableOccurrence()
        coordinator.sync(NotificationSyncReason.FOREGROUND)
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
        coordinator.sync(NotificationSyncReason.FOREGROUND)
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
    fun notificationDefer_writesSameDeferEvent() = runBlocking {
        startAvailableOccurrence()
        coordinator.sync(NotificationSyncReason.FOREGROUND)
        val occurrence = currentOccurrence()
        timeProvider.setEpochMillis(epochAt(11, 20, 0))

        val decision = coordinator.handleNotificationDefer(
            occurrenceId = occurrence.id,
            plannedAtEpochMillis = occurrence.plannedAtEpochMillis,
            durationMinutes = 15,
        )

        assertTrue(decision is NotificationDeferDecision.Deferred)
        val events = database.deferEventDao().getForOccurrenceOrdered(occurrence.id)
        assertEquals(1, events.size)
        assertEquals(15, events.single().durationMinutes)
        assertEquals(epochAt(11, 20, 0), events.single().occurredAtEpochMillis)
        assertEquals(epochAt(11, 35, 0), events.single().deferredUntilEpochMillis)
        assertEquals(ZONE_KIEV, events.single().zoneId)
    }

    @Test
    fun cleanupFailure_stillShowsCurrentNotification() = runBlocking {
        startAvailableOccurrence()
        presenter.throwOnLegacyCancel = true

        coordinator.sync(NotificationSyncReason.FOREGROUND)

        assertEquals(1, presenter.legacyCancelCalls)
        assertEquals(1, presenter.shown.size)
        assertEquals(currentOccurrenceId(), presenter.shown.single().occurrenceId)
    }

    @Test
    fun staleActiveOccurrenceDoesNotRepostAWhenBIsCurrent() = runBlocking {
        startAvailableOccurrence()
        coordinator.sync(NotificationSyncReason.FOREGROUND)
        val occurrenceA = currentOccurrence()
        timeProvider.setEpochMillis(epochAt(15, 30, 0))
        presenter.activeOccurrenceId = occurrenceA.id
        presenter.resetShowsOnly()

        coordinator.sync(NotificationSyncReason.PLANNED_ALARM)

        val occurrenceB = currentOccurrence()
        assertEquals(occurrenceB.id, presenter.shown.single().occurrenceId)
        assertTrue(presenter.shown.none { it.occurrenceId == occurrenceA.id })
    }

    // 01.10.2026 Archive Acceptance F deferred boot host cursor by Me4Hik START
    @Test
    fun deferredOccurrence_afterBoot_keepsSameOccurrenceAndAppearsOnceAfterTerminal() = runBlocking {
        startAvailableOccurrence()
        val occurrence = currentOccurrence()
        val occurrenceId = occurrence.id
        assertTrue(occurrence.availableUntilEpochMillis > epochAt(11, 45, 0))

        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        val deferResult = cycleRepository.deferAvailableOccurrence(occurrenceId, durationMinutes = 15)
        assertTrue(deferResult is CycleResult.DeferCompleted)

        val deferredUntil = epochAt(11, 45, 0)
        val afterDefer = occurrenceById(occurrenceId)
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, afterDefer.status)
        assertEquals(deferredUntil, afterDefer.deferredUntilEpochMillis)
        val preBootDeferEvents = database.deferEventDao().getForOccurrenceOrdered(occurrenceId)
        assertEquals(1, preBootDeferEvents.size)
        assertEquals(deferredUntil, preBootDeferEvents.single().deferredUntilEpochMillis)
        assertTrue(archiveReadRepository.observeAllOccurrenceHistory().first().isEmpty())
        assertTrue(archiveReadRepository.observeOccurrenceHistoryForQuestion(occurrence.questionId).first().isEmpty())

        val occurrenceCountBeforeBoot = database.questionOccurrenceDao().count()
        // Simulate process restart: new repository/coordinator instances on the same Room DB.
        wireRepositoriesAndCoordinator()
        presenter.reset()
        alarmScheduler.lastPlan = null
        // now < deferredUntil < availableUntil — avoid Missed branch.
        timeProvider.setEpochMillis(epochAt(11, 35, 0))

        coordinator.sync(NotificationSyncReason.BOOT)

        val incomplete = database.questionOccurrenceDao().getIncompleteOrdered()
        assertEquals(1, incomplete.size)
        val afterBoot = incomplete.single()
        assertEquals(occurrenceId, afterBoot.id)
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, afterBoot.status)
        assertEquals(deferredUntil, afterBoot.deferredUntilEpochMillis)
        assertEquals(occurrenceCountBeforeBoot, database.questionOccurrenceDao().count())
        assertEquals(1, database.deferEventDao().getForOccurrenceOrdered(occurrenceId).size)
        assertTrue(archiveReadRepository.observeAllOccurrenceHistory().first().isEmpty())
        assertEquals(PracticeNotificationKind.SNOOZED, presenter.shown.single().kind)
        assertEquals(occurrenceId, presenter.shown.single().occurrenceId)
        assertEquals(
            deferredUntil,
            alarmScheduler.lastPlan?.deferredReminderAlarm?.triggerAtEpochMillis,
        )

        val answerResult = cycleRepository.saveAnswer(occurrenceId, "post-boot answer")
        assertTrue(answerResult is CycleResult.AnswerSaved)

        val history = archiveReadRepository.observeAllOccurrenceHistory().first()
        assertEquals(1, history.size)
        val unit = history.single()
        assertEquals(occurrenceId, unit.occurrenceId)
        assertEquals(ArchiveOccurrenceOutcome.ANSWERED, unit.outcome)
        assertEquals(1, unit.deferCount)
        assertEquals(1, database.deferEventDao().getForOccurrenceOrdered(occurrenceId).size)
        // Occurrence-centric Archive: one terminal unit only — no standalone Deferred cards.
        assertEquals(listOf(ArchiveOccurrenceOutcome.ANSWERED), history.map { it.outcome })
    }
    // 01.10.2026 Archive Acceptance F deferred boot host cursor by Me4Hik END

    private fun wireRepositoriesAndCoordinator() {
        cycleRepository = CycleRepository(database, timeProvider, NoOpBackupMutationRequestSink)
        archiveReadRepository = RoomArchiveReadRepository(database)
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
    data class PruneCall(val selectedSoundId: String, val additionalKeep: Set<String>)

    val shown = mutableListOf<NotificationShowPlan>()
    val pruneCalls = mutableListOf<PruneCall>()
    var cancelCurrentCalls = 0
    var cancelAllCalls = 0
    var legacyCancelCalls = 0
    var throwOnLegacyCancel = false
    var activeOccurrenceId: Long? = null
    var activeKind: PracticeNotificationKind? = null
    var activeChannelId: String? = null

    var throwOnShow = false
    var showSucceeds = true
    var throwOnCancelAll = false

    override fun ensureChannelsCreated() = Unit

    override fun pruneCustomDueSoundChannels(
        selectedSoundId: String,
        additionalKeepChannelIds: Set<String>,
    ) {
        pruneCalls += PruneCall(selectedSoundId, additionalKeepChannelIds)
    }

    override fun findActivePracticeNotificationOccurrenceId(): Long? = activeOccurrenceId

    override fun findActivePracticeNotificationKind(): PracticeNotificationKind? = activeKind

    override fun findActivePracticeNotificationChannelId(): String? = activeChannelId

    override fun showNotification(plan: NotificationShowPlan): Boolean {
        if (throwOnShow) {
            throwOnShow = false
            throw IllegalStateException("showNotification failed")
        }
        if (!showSucceeds) {
            return false
        }
        shown += plan
        activeOccurrenceId = plan.occurrenceId
        activeKind = plan.kind
        activeChannelId = when {
            plan.kind == PracticeNotificationKind.SNOOZED -> PracticeNotificationChannels.SNOOZED
            plan.suppressAlert || !plan.soundEnabled -> PracticeNotificationChannels.DUE_SILENT
            else -> PracticeDueSoundChannelRouter.dueChannelId(true, plan.selectedSoundId)
        }
        return true
    }

    override fun cancelCurrentPracticeNotification() {
        cancelCurrentCalls += 1
        activeOccurrenceId = null
        activeKind = null
        activeChannelId = null
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
        if (throwOnCancelAll) {
            throwOnCancelAll = false
            throw IllegalStateException("cancelAll failed")
        }
        activeOccurrenceId = null
        activeKind = null
        activeChannelId = null
    }

    fun reset() {
        shown.clear()
        pruneCalls.clear()
        cancelCurrentCalls = 0
        cancelAllCalls = 0
        legacyCancelCalls = 0
        throwOnLegacyCancel = false
        throwOnShow = false
        showSucceeds = true
        throwOnCancelAll = false
        activeOccurrenceId = null
        activeKind = null
        activeChannelId = null
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

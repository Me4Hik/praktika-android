// 06.08.2026 Settings Schedule cursor by Me4Hik START - JVM tests SettingsViewModel
package com.me4hik.praktika.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.data.cycle.ScheduleUpdateResult
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.read.PracticeReadRepository
import com.me4hik.praktika.data.read.PracticeReadSnapshot
import com.me4hik.praktika.data.read.ScheduleReadRepository
import com.me4hik.praktika.data.read.ScheduleReadSnapshot
import com.me4hik.praktika.data.read.ScheduleSlotReadModel
import com.me4hik.praktika.data.preferences.SoundPreferenceRepository
import com.me4hik.praktika.notification.NotificationPermissionPolicy
import com.me4hik.praktika.notification.NotificationPermissionUiState
import com.me4hik.praktika.notification.NotificationDeliveryCapability
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.notification.NotificationSyncRequester
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var scheduleRepository: MutableFakeScheduleReadRepository
    private lateinit var practiceRepository: MutableFakePracticeReadRepository
    private lateinit var updateScheduleCommand: RecordingUpdateScheduleCommand
    private lateinit var pausePracticeCommand: RecordingPausePracticeCommand
    private lateinit var resumePracticeCommand: RecordingResumePracticeCommand
    private lateinit var soundRepository: MutableFakeSoundPreferenceRepository
    private lateinit var notificationPermissionRepository: FakeNotificationPermissionPolicy
    private lateinit var notificationSyncRequester: RecordingNotificationSyncRequester
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        scheduleRepository = MutableFakeScheduleReadRepository(defaultSnapshot())
        practiceRepository = MutableFakePracticeReadRepository(defaultPracticeSnapshot(paused = false))
        updateScheduleCommand = RecordingUpdateScheduleCommand()
        pausePracticeCommand = RecordingPausePracticeCommand()
        resumePracticeCommand = RecordingResumePracticeCommand()
        soundRepository = MutableFakeSoundPreferenceRepository()
        notificationPermissionRepository = FakeNotificationPermissionPolicy()
        notificationSyncRequester = RecordingNotificationSyncRequester()
        savedStateHandle = SavedStateHandle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel = SettingsViewModel(
            scheduleReadRepository = scheduleRepository,
            practiceReadRepository = practiceRepository,
            updateScheduleCommand = updateScheduleCommand,
            pausePracticeCommand = pausePracticeCommand,
            resumePracticeCommand = resumePracticeCommand,
            soundPreferenceRepository = soundRepository,
            notificationPermissionRepository = notificationPermissionRepository,
            notificationSyncRequester = notificationSyncRequester,
            savedStateHandle = savedStateHandle,
            commandDispatcher = testDispatcher,
        )
    }

    @Test
    fun initialLoadingThenContent() = runTest {
        createViewModel()
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertEquals("11:00", content.slots[0].timeText)
    }

    @Test
    fun editSlotMarksDirtyAndEnablesSave() = runTest {
        createViewModel()
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(1, 630)
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertTrue(content.isDirty)
        assertTrue(content.isScheduleValid)
    }

    @Test
    fun duplicateValidationBlocksSave() = runTest {
        createViewModel()
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(2, 660)
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertFalse(content.isScheduleValid)
        assertEquals(SettingsScheduleError.DUPLICATE_TIME, content.scheduleError)
    }

    @Test
    fun saveSuccessClearsDirtyAndRunsCommand() = runTest {
        createViewModel()
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(1, 630)
        viewModel.saveSchedule()
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertFalse(content.isDirty)
        assertEquals(1, updateScheduleCommand.calls)
    }

    @Test
    fun doubleSaveRunsOneCommand() = runTest {
        createViewModel()
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(1, 630)
        viewModel.saveSchedule()
        viewModel.saveSchedule()
        advanceUntilIdle()
        assertEquals(1, updateScheduleCommand.calls)
    }

    @Test
    fun dirtyBackLeavesDraftDirty() = runTest {
        createViewModel()
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(1, 630)
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertTrue(content.isDirty)
    }

    @Test
    fun cleanBackHasNoDirtyDraft() = runTest {
        createViewModel()
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertFalse(content.isDirty)
    }

    @Test
    fun soundTogglePersistsImmediately() = runTest {
        createViewModel()
        advanceUntilIdle()
        viewModel.onSoundEnabledChanged(false)
        advanceUntilIdle()
        assertFalse(soundRepository.currentValue)
    }

    @Test
    fun draftRestoredFromSavedStateHandle() = runTest {
        savedStateHandle[SettingsSavedStateKeys.DRAFT_SLOT_1] = 630
        savedStateHandle[SettingsSavedStateKeys.DRAFT_SLOT_2] = 900
        savedStateHandle[SettingsSavedStateKeys.DRAFT_SLOT_3] = 1140
        savedStateHandle[SettingsSavedStateKeys.DRAFT_INITIALIZED] = true
        createViewModel()
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertEquals(630, content.slots.first { it.slotIndex == 1 }.timeOfDayMinutes)
        assertTrue(content.isDirty)
    }

    @Test
    fun pauseSuccessInvokesCommandOnce() = runTest {
        createViewModel()
        advanceUntilIdle()
        pausePracticeCommand.onCall = { practiceRepository.setPaused(true) }
        viewModel.togglePauseState()
        advanceUntilIdle()
        assertEquals(1, pausePracticeCommand.calls)
        assertEquals(0, resumePracticeCommand.calls)
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertTrue(content.isPracticePaused)
    }

    @Test
    fun resumeSuccessInvokesCommandOnce() = runTest {
        practiceRepository.setPaused(true)
        createViewModel()
        advanceUntilIdle()
        resumePracticeCommand.onCall = { practiceRepository.setPaused(false) }
        viewModel.togglePauseState()
        advanceUntilIdle()
        assertEquals(1, resumePracticeCommand.calls)
        assertEquals(0, pausePracticeCommand.calls)
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertFalse(content.isPracticePaused)
    }

    @Test
    fun pauseFailureKeepsContentState() = runTest {
        createViewModel()
        advanceUntilIdle()
        pausePracticeCommand.shouldFail = true
        viewModel.togglePauseState()
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertFalse(content.isPracticePaused)
        assertEquals(1, pausePracticeCommand.calls)
    }

    @Test
    fun resumeFailureKeepsContentState() = runTest {
        practiceRepository.setPaused(true)
        createViewModel()
        advanceUntilIdle()
        resumePracticeCommand.shouldFail = true
        viewModel.togglePauseState()
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertTrue(content.isPracticePaused)
        assertEquals(1, resumePracticeCommand.calls)
    }

    @Test
    fun cancellationIsNotUiError() = runTest {
        createViewModel()
        advanceUntilIdle()
        updateScheduleCommand.hangIndefinitely = true
        viewModel.onSlotTimeChanged(1, 630)
        val job = launch { viewModel.saveSchedule() }
        advanceUntilIdle()
        job.cancel()
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertFalse(content.scheduleError == SettingsScheduleError.SAVE_FAILED)
    }

    @Test
    fun roomEmissionDoesNotOverwriteDirtyDraft() = runTest {
        createViewModel()
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(1, 630)
        scheduleRepository.emit(defaultSnapshot().copy(
            slots = listOf(
                ScheduleSlotReadModel(1, 480),
                ScheduleSlotReadModel(2, 900),
                ScheduleSlotReadModel(3, 1140),
            ),
        ))
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertEquals(630, content.slots.first { it.slotIndex == 1 }.timeOfDayMinutes)
        assertTrue(content.isDirty)
    }

    @Test
    fun cleanDraftAcceptsExternalRoomEmission() = runTest {
        createViewModel()
        advanceUntilIdle()
        scheduleRepository.emit(
            ScheduleReadSnapshot(
                slots = listOf(
                    ScheduleSlotReadModel(1, 480),
                    ScheduleSlotReadModel(2, 720),
                    ScheduleSlotReadModel(3, 1020),
                ),
            ),
        )
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertEquals(480, content.slots.first { it.slotIndex == 1 }.timeOfDayMinutes)
        assertFalse(content.isDirty)
    }

    @Test
    fun saveSuccessSynchronizesDraftAndPersisted() = runTest {
        createViewModel()
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(1, 630)
        viewModel.saveSchedule()
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertFalse(content.isDirty)
        assertEquals(630, savedStateHandle.get<Int>(SettingsSavedStateKeys.DRAFT_SLOT_1))
    }

    @Test
    fun snackbarEventIsOneShot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        scheduleRepository = MutableFakeScheduleReadRepository(defaultSnapshot())
        practiceRepository = MutableFakePracticeReadRepository(defaultPracticeSnapshot(paused = false))
        updateScheduleCommand = RecordingUpdateScheduleCommand()
        pausePracticeCommand = RecordingPausePracticeCommand()
        resumePracticeCommand = RecordingResumePracticeCommand()
        soundRepository = MutableFakeSoundPreferenceRepository()
        notificationPermissionRepository = FakeNotificationPermissionPolicy()
        notificationSyncRequester = RecordingNotificationSyncRequester()
        savedStateHandle = SavedStateHandle()
        viewModel = SettingsViewModel(
            scheduleReadRepository = scheduleRepository,
            practiceReadRepository = practiceRepository,
            updateScheduleCommand = updateScheduleCommand,
            pausePracticeCommand = pausePracticeCommand,
            resumePracticeCommand = resumePracticeCommand,
            soundPreferenceRepository = soundRepository,
            notificationPermissionRepository = notificationPermissionRepository,
            notificationSyncRequester = notificationSyncRequester,
            savedStateHandle = savedStateHandle,
            commandDispatcher = dispatcher,
        )
        val events = mutableListOf<SettingsSnackbarEvent>()
        val collector = launch { viewModel.snackbar.collect { events.add(it) } }
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(1, 630)
        viewModel.saveSchedule()
        advanceUntilIdle()
        collector.cancel()
        assertEquals(1, events.count { it == SettingsSnackbarEvent.ScheduleSaved })
    }

    @Test
    fun soundFailureRollsBackSwitchValue() = runTest {
        createViewModel()
        advanceUntilIdle()
        soundRepository.failNextWrite = true
        viewModel.onSoundEnabledChanged(false)
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertTrue(content.soundEnabled)
        assertTrue(soundRepository.currentValue)
    }

    private fun defaultSnapshot() = ScheduleReadSnapshot(
        slots = listOf(
            ScheduleSlotReadModel(1, 660),
            ScheduleSlotReadModel(2, 900),
            ScheduleSlotReadModel(3, 1140),
        ),
    )

    private fun defaultPracticeSnapshot(paused: Boolean) = PracticeReadSnapshot(
        practiceState = PracticeStateEntity(
            id = 1,
            isPracticeStarted = true,
            isPaused = paused,
            practiceStartedAtEpochMillis = 1L,
            currentCycleNumber = 1,
            nextCyclePosition = 2,
            lastProcessedAtEpochMillis = 1L,
            pausedAtEpochMillis = if (paused) 2L else null,
            activeZoneId = "Europe/Kiev",
            seedVersion = 1,
        ),
        incompleteOccurrence = QuestionOccurrenceEntity(
            id = 1L,
            questionId = 1,
            questionTextSnapshot = "Q1",
            cycleNumber = 1,
            cyclePosition = 1,
            scheduleSlotIndex = 1,
            plannedAtEpochMillis = 1L,
            availableUntilEpochMillis = 2L,
            openedAtEpochMillis = null,
            completedAtEpochMillis = null,
            status = QuestionOccurrenceStatus.SCHEDULED,
            zoneId = "Europe/Kiev",
        ),
    )

    private class MutableFakeScheduleReadRepository(
        initial: ScheduleReadSnapshot,
    ) : ScheduleReadRepository {
        private val state = MutableStateFlow(initial)
        override fun observeSchedule(): Flow<ScheduleReadSnapshot> = state
        fun emit(snapshot: ScheduleReadSnapshot) {
            state.value = snapshot
        }
    }

    private class MutableFakePracticeReadRepository(
        initial: PracticeReadSnapshot,
    ) : PracticeReadRepository {
        private val state = MutableStateFlow(initial)
        override fun observeSnapshot(): Flow<PracticeReadSnapshot> = state
        fun setPaused(paused: Boolean) {
            state.value = state.value.copy(
                practiceState = state.value.practiceState.copy(
                    isPaused = paused,
                    pausedAtEpochMillis = if (paused) 2L else null,
                ),
            )
        }
    }

    private class RecordingUpdateScheduleCommand : UpdateScheduleCommand {
        var calls = 0
        var hangIndefinitely = false
        override suspend fun updateSchedule(
            updates: List<com.me4hik.praktika.data.cycle.ScheduleSlotUpdate>,
        ): ScheduleUpdateResult {
            calls += 1
            if (hangIndefinitely) {
                kotlinx.coroutines.awaitCancellation()
            }
            return ScheduleUpdateResult.Success
        }
    }

    private class RecordingPausePracticeCommand : PausePracticeCommand {
        var calls = 0
        var shouldFail = false
        var onCall: (() -> Unit)? = null
        override suspend fun pausePractice(): CycleResult {
            calls += 1
            if (shouldFail) {
                throw IOException("pause failed")
            }
            onCall?.invoke()
            return CycleResult.PauseEnabled
        }
    }

    private class RecordingResumePracticeCommand : ResumePracticeCommand {
        var calls = 0
        var shouldFail = false
        var onCall: (() -> Unit)? = null
        override suspend fun resumePractice(): CycleResult {
            calls += 1
            if (shouldFail) {
                throw IOException("resume failed")
            }
            onCall?.invoke()
            return CycleResult.PracticeResumed
        }
    }

    private class FakeNotificationPermissionPolicy : NotificationPermissionPolicy {
        override val permissionRequested = MutableStateFlow(false)

        override suspend fun markPermissionRequested() = Unit

        override fun evaluateUiState(
            permissionRequested: Boolean,
            soundEnabled: Boolean,
        ): NotificationPermissionUiState = NotificationPermissionUiState.ENABLED

        override fun toDeliveryCapability(state: NotificationPermissionUiState): NotificationDeliveryCapability {
            return NotificationDeliveryCapability.ENABLED
        }

        override fun createAppNotificationSettingsIntent() = android.content.Intent()

        override fun createChannelSettingsIntent(soundEnabled: Boolean) = android.content.Intent()
        override fun shouldRequestRuntimePermission(): Boolean = false
        override fun hasRuntimePermission(): Boolean = true
        override fun areAppNotificationsEnabled(): Boolean = true
    }

    private class RecordingNotificationSyncRequester : NotificationSyncRequester {
        val reasons = mutableListOf<NotificationSyncReason>()

        override suspend fun requestSync(reason: NotificationSyncReason) {
            reasons.add(reason)
        }
    }

    private class MutableFakeSoundPreferenceRepository : SoundPreferenceRepository {
        var currentValue = true
        var failNextWrite = false
        private val state = MutableStateFlow(true)
        override val soundEnabled: Flow<Boolean> = state
        override suspend fun setSoundEnabled(enabled: Boolean) {
            if (failNextWrite) {
                failNextWrite = false
                throw IOException("sound write failed")
            }
            currentValue = enabled
            state.value = enabled
        }
    }
}
// 06.08.2026 Settings Schedule cursor by Me4Hik END

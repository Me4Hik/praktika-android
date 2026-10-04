// 06.08.2026 Settings Schedule cursor by Me4Hik START - JVM tests SettingsViewModel
// 09.08.2026 Post-release fixes cursor by Me4Hik START - schedule autosave tests
package com.me4hik.praktika.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.data.cycle.ScheduleSlotUpdate
import com.me4hik.praktika.data.cycle.ScheduleUpdateResult
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.read.PracticeReadRepository
import com.me4hik.praktika.data.read.PracticeReadSnapshot
import com.me4hik.praktika.data.read.ScheduleReadRepository
import com.me4hik.praktika.data.read.ScheduleReadSnapshot
import com.me4hik.praktika.data.read.ScheduleSlotReadModel
import com.me4hik.praktika.data.preferences.AppLanguage
import com.me4hik.praktika.data.preferences.DeferDurationOptions
import com.me4hik.praktika.data.preferences.DeferDurationPreferenceRepository
import com.me4hik.praktika.data.preferences.LanguagePreferenceRepository
import com.me4hik.praktika.data.preferences.QuestionWordingMode
import com.me4hik.praktika.data.preferences.QuestionWordingPreferenceRepository
import com.me4hik.praktika.data.preferences.SoundPreferenceRepository
import com.me4hik.praktika.notification.NotificationPermissionPolicy
import com.me4hik.praktika.notification.NotificationPermissionUiState
import com.me4hik.praktika.notification.NotificationDeliveryCapability
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.notification.NotificationSyncRequester
import com.me4hik.praktika.diagnostics.BugReportSendResult
import com.me4hik.praktika.diagnostics.DiagnosticReportSubmitter
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private lateinit var deferDurationRepository: MutableFakeDeferDurationPreferenceRepository
    private lateinit var wordingRepository: MutableFakeQuestionWordingPreferenceRepository
    private lateinit var applyWordingCalls: MutableList<QuestionWordingMode>
    private lateinit var notificationPermissionRepository: FakeNotificationPermissionPolicy
    private lateinit var notificationSyncRequester: RecordingNotificationSyncRequester
    private lateinit var diagnosticReportSubmitter: FakeDiagnosticReportSubmitter
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
        deferDurationRepository = MutableFakeDeferDurationPreferenceRepository()
        wordingRepository = MutableFakeQuestionWordingPreferenceRepository()
        applyWordingCalls = mutableListOf()
        notificationPermissionRepository = FakeNotificationPermissionPolicy()
        notificationSyncRequester = RecordingNotificationSyncRequester()
        diagnosticReportSubmitter = FakeDiagnosticReportSubmitter()
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
            deferDurationPreferenceRepository = deferDurationRepository,
            languagePreferenceRepository = MutableFakeLanguagePreferenceRepository(),
            questionWordingPreferenceRepository = wordingRepository,
            applyQuestionWordingMode = { mode ->
                applyWordingCalls.add(mode)
                true
            },
            notificationPermissionRepository = notificationPermissionRepository,
            notificationSyncRequester = notificationSyncRequester,
            diagnosticReportSubmitter = diagnosticReportSubmitter,
            savedStateHandle = savedStateHandle,
            backupSettingsActions = RecordingBackupSettingsActions(),
            commandDispatcher = testDispatcher,
        )
    }


    @Test
    fun wordingModeDefaultsToMasculineAndSavesFeminine() = runTest {
        createViewModel()
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertEquals(QuestionWordingMode.MASCULINE, content.questionWordingMode)

        viewModel.onQuestionWordingModeChanged(QuestionWordingMode.FEMININE)
        advanceUntilIdle()
        val updated = viewModel.uiState.value as SettingsUiState.Content
        assertEquals(QuestionWordingMode.FEMININE, updated.questionWordingMode)
        assertEquals(listOf(QuestionWordingMode.FEMININE), applyWordingCalls)
        assertTrue(notificationSyncRequester.reasons.contains(NotificationSyncReason.MUTATION))
        assertEquals(QuestionWordingMode.FEMININE, wordingRepository.current)
    }

    @Test
    fun wordingModeSameValue_noWrite() = runTest {
        createViewModel()
        advanceUntilIdle()
        viewModel.onQuestionWordingModeChanged(QuestionWordingMode.MASCULINE)
        advanceUntilIdle()
        assertTrue(applyWordingCalls.isEmpty())
        assertTrue(notificationSyncRequester.reasons.isEmpty())
    }

    @Test
    fun initialLoadingThenContent() = runTest {
        createViewModel()
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertEquals("11:00", content.slots[0].timeText)
    }

    @Test
    fun timeChangeValidDraftAutosaves() = runTest {
        createViewModel()
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(1, 630)
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertFalse(content.isDirty)
        assertEquals(1, updateScheduleCommand.calls)
        assertEquals(listOf(630, 900, 1140), updateScheduleCommand.lastUpdatesMinutes())
    }

    @Test
    fun duplicateDraftDoesNotPersist() = runTest {
        createViewModel()
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(2, 660)
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertFalse(content.isScheduleValid)
        assertEquals(SettingsScheduleError.DUPLICATE_TIME, content.scheduleError)
        assertEquals(0, updateScheduleCommand.calls)
        assertTrue(content.isDirty)
    }

    @Test
    fun duplicateResolvedLaterAutosavesFullTriple() = runTest {
        createViewModel()
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(1, 900)
        advanceUntilIdle()
        assertEquals(0, updateScheduleCommand.calls)
        viewModel.onSlotTimeChanged(2, 660)
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertFalse(content.isDirty)
        assertEquals(1, updateScheduleCommand.calls)
        assertEquals(listOf(900, 660, 1140), updateScheduleCommand.lastUpdatesMinutes())
    }

    @Test
    fun rapidValidChangesLatestDraftWins() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        updateScheduleCommand = RecordingUpdateScheduleCommand().apply {
            suspendUntilGate = CompletableDeferred()
        }
        viewModel = SettingsViewModel(
            scheduleReadRepository = scheduleRepository,
            practiceReadRepository = practiceRepository,
            updateScheduleCommand = updateScheduleCommand,
            pausePracticeCommand = pausePracticeCommand,
            resumePracticeCommand = resumePracticeCommand,
            soundPreferenceRepository = soundRepository,
            deferDurationPreferenceRepository = deferDurationRepository,
            languagePreferenceRepository = MutableFakeLanguagePreferenceRepository(),
            questionWordingPreferenceRepository = wordingRepository,
            applyQuestionWordingMode = { mode ->
                applyWordingCalls.add(mode)
                true
            },
            notificationPermissionRepository = notificationPermissionRepository,
            notificationSyncRequester = notificationSyncRequester,
            diagnosticReportSubmitter = diagnosticReportSubmitter,
            savedStateHandle = savedStateHandle,
            backupSettingsActions = RecordingBackupSettingsActions(),
            commandDispatcher = dispatcher,
        )
        advanceUntilIdle()

        viewModel.onSlotTimeChanged(1, 630)
        advanceUntilIdle()
        assertEquals(1, updateScheduleCommand.calls)
        assertTrue((viewModel.uiState.value as SettingsUiState.Content).isSavingSchedule)

        viewModel.onSlotTimeChanged(2, 860)
        viewModel.onSlotTimeChanged(3, 1210)
        updateScheduleCommand.suspendUntilGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, updateScheduleCommand.calls)
        assertEquals(listOf(630, 860, 1210), updateScheduleCommand.lastUpdatesMinutes())
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertFalse(content.isDirty)
        assertFalse(content.isSavingSchedule)
    }

    @Test
    fun saveFailureLeavesDraftDirtyAndShowsError() = runTest {
        createViewModel()
        advanceUntilIdle()
        updateScheduleCommand.shouldFail = true
        viewModel.onSlotTimeChanged(1, 630)
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertTrue(content.isDirty)
        assertEquals(SettingsScheduleError.SAVE_FAILED, content.scheduleError)
        assertEquals(1, updateScheduleCommand.calls)
    }

    @Test
    fun externalRoomEmissionDoesNotOverwriteDirtyInvalidDraft() = runTest {
        createViewModel()
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(2, 660)
        scheduleRepository.emit(defaultSnapshot().copy(
            slots = listOf(
                ScheduleSlotReadModel(1, 480),
                ScheduleSlotReadModel(2, 900),
                ScheduleSlotReadModel(3, 1140),
            ),
        ))
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertEquals(660, content.slots.first { it.slotIndex == 2 }.timeOfDayMinutes)
        assertTrue(content.isDirty)
        assertFalse(content.isScheduleValid)
    }

    @Test
    fun successfulAutosaveSynchronizesPersistedSnapshot() = runTest {
        createViewModel()
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(1, 630)
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertFalse(content.isDirty)
        assertEquals(630, savedStateHandle.get<Int>(SettingsSavedStateKeys.DRAFT_SLOT_1))
    }

    @Test
    fun cancellationIsNotUiError() = runTest {
        createViewModel()
        advanceUntilIdle()
        updateScheduleCommand.shouldThrowCancellation = true
        viewModel.onSlotTimeChanged(1, 630)
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertFalse(content.scheduleError == SettingsScheduleError.SAVE_FAILED)
    }

    @Test
    fun scheduleSuccessSnackbarIsNotEmitted() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        viewModel = SettingsViewModel(
            scheduleReadRepository = scheduleRepository,
            practiceReadRepository = practiceRepository,
            updateScheduleCommand = updateScheduleCommand,
            pausePracticeCommand = pausePracticeCommand,
            resumePracticeCommand = resumePracticeCommand,
            soundPreferenceRepository = soundRepository,
            deferDurationPreferenceRepository = deferDurationRepository,
            languagePreferenceRepository = MutableFakeLanguagePreferenceRepository(),
            questionWordingPreferenceRepository = wordingRepository,
            applyQuestionWordingMode = { mode ->
                applyWordingCalls.add(mode)
                true
            },
            notificationPermissionRepository = notificationPermissionRepository,
            notificationSyncRequester = notificationSyncRequester,
            diagnosticReportSubmitter = diagnosticReportSubmitter,
            savedStateHandle = savedStateHandle,
            backupSettingsActions = RecordingBackupSettingsActions(),
            commandDispatcher = dispatcher,
        )
        val events = mutableListOf<SettingsSnackbarEvent>()
        val collector = launch { viewModel.snackbar.collect { events.add(it) } }
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(1, 630)
        advanceUntilIdle()
        collector.cancel()
        assertTrue(events.isEmpty())
    }

    @Test
    fun autosaveCallsUpdateScheduleWithAllThreeSlots() = runTest {
        createViewModel()
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(3, 1200)
        advanceUntilIdle()
        assertEquals(1, updateScheduleCommand.calls)
        assertEquals(
            listOf(
                ScheduleSlotUpdate(1, 660),
                ScheduleSlotUpdate(2, 900),
                ScheduleSlotUpdate(3, 1200),
            ),
            updateScheduleCommand.lastUpdates,
        )
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
    fun notificationSettingsClick_emitsOpenNotificationSettings() = runTest {
        createViewModel()
        advanceUntilIdle()
        val events = mutableListOf<SettingsNavigationEvent>()
        val collector = launch { viewModel.navigation.collect { events.add(it) } }
        advanceUntilIdle()
        viewModel.onNotificationSettingsClicked()
        advanceUntilIdle()
        collector.cancel()
        assertTrue(events.single() is SettingsNavigationEvent.OpenNotificationSettings)
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
    fun confirmDiscardRestoresPersistedDraft() = runTest {
        createViewModel()
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(2, 660)
        advanceUntilIdle()
        viewModel.confirmDiscardChanges()
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertEquals(900, content.slots.first { it.slotIndex == 2 }.timeOfDayMinutes)
        assertFalse(content.isDirty)
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
        override suspend fun readSnapshot(): PracticeReadSnapshot = state.value
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
        var shouldFail = false
        var shouldThrowCancellation = false
        var suspendUntilGate: CompletableDeferred<Unit>? = null
        var lastUpdates: List<ScheduleSlotUpdate>? = null

        override suspend fun updateSchedule(
            updates: List<ScheduleSlotUpdate>,
        ): ScheduleUpdateResult {
            calls += 1
            lastUpdates = updates
            suspendUntilGate?.await()
            if (shouldThrowCancellation) {
                shouldThrowCancellation = false
                throw CancellationException("cancelled")
            }
            if (hangIndefinitely) {
                kotlinx.coroutines.awaitCancellation()
            }
            if (shouldFail) {
                throw IOException("schedule save failed")
            }
            return ScheduleUpdateResult.Success
        }

        fun lastUpdatesMinutes(): List<Int>? {
            return lastUpdates?.sortedBy { it.slotIndex }?.map { it.timeOfDayMinutes }
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
        private val _permissionStateRevision = MutableStateFlow(0L)
        override val permissionStateRevision: StateFlow<Long> = _permissionStateRevision.asStateFlow()

        override fun notifyPermissionStateChanged(source: String) {
            _permissionStateRevision.value = _permissionStateRevision.value + 1L
        }

        override val permissionRequested = MutableStateFlow(false)

        override suspend fun markPermissionRequested() = Unit

        override fun evaluateUiState(
            permissionRequested: Boolean,
            soundEnabled: Boolean,
            selectedSoundId: String,
        ): NotificationPermissionUiState = NotificationPermissionUiState.ENABLED

        override fun toDeliveryCapability(state: NotificationPermissionUiState): NotificationDeliveryCapability {
            return NotificationDeliveryCapability.ENABLED
        }

        override fun createAppNotificationSettingsIntent() = android.content.Intent()

        override fun createChannelSettingsIntent(
            soundEnabled: Boolean,
            selectedSoundId: String,
        ) = android.content.Intent()
        override fun shouldRequestRuntimePermission(): Boolean = false
        override fun hasRuntimePermission(): Boolean = true
        override fun areAppNotificationsEnabled(): Boolean = true
        override fun shouldShowRequestPermissionRationale(): Boolean = false
        override fun isSelectedChannelEnabled(
            soundEnabled: Boolean,
            selectedSoundId: String,
        ): Boolean = true
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
        override val selectedSoundId =
            MutableStateFlow(com.me4hik.praktika.sound.SoundAssetIds.SYSTEM_DEFAULT)
        override val hiddenBuiltinIds = MutableStateFlow(emptySet<String>())
        override suspend fun setSoundEnabled(enabled: Boolean) {
            if (failNextWrite) {
                failNextWrite = false
                throw IOException("sound write failed")
            }
            currentValue = enabled
            state.value = enabled
        }
        override suspend fun selectSound(id: String) {
            selectedSoundId.value = com.me4hik.praktika.sound.BuiltinSoundCatalog.resolveOrDefault(id).id
        }
        override suspend fun hideBuiltin(id: String) = Unit
        override suspend fun restoreBuiltin(id: String) = Unit
        override suspend fun restoreAllHidden() = Unit
    }

    private class MutableFakeQuestionWordingPreferenceRepository : QuestionWordingPreferenceRepository {
        private val state = MutableStateFlow(QuestionWordingMode.DEFAULT)
        override val wordingMode = state
        val current: QuestionWordingMode get() = state.value
        override suspend fun setWordingMode(mode: QuestionWordingMode) {
            state.value = mode
        }
    }

    private class MutableFakeLanguagePreferenceRepository : LanguagePreferenceRepository {
        private val languageState = MutableStateFlow(AppLanguage.DEFAULT)
        private val selectedState = MutableStateFlow(true)
        override val language = languageState
        override val languageSelected = selectedState
        override suspend fun setLanguage(language: AppLanguage) {
            languageState.value = language
        }
        override suspend fun markLanguageSelected() {
            selectedState.value = true
        }
        override suspend fun ensureExistingUserDefault(isPracticeStarted: Boolean) = Unit
    }

    private class MutableFakeDeferDurationPreferenceRepository : DeferDurationPreferenceRepository {
        private val state = MutableStateFlow(DeferDurationOptions.DEFAULT_MINUTES)
        override val deferDurationMinutes: Flow<Int> = state
        override suspend fun setDeferDurationMinutes(minutes: Int) {
            state.value = DeferDurationOptions.sanitize(minutes)
        }
    }

    private class FakeDiagnosticReportSubmitter : DiagnosticReportSubmitter {
        var lastComment: String? = null
        var result: BugReportSendResult = BugReportSendResult.SAVED_LOCALLY_NO_DSN

        override suspend fun submitManualReport(testerComment: String?): BugReportSendResult {
            lastComment = testerComment
            return result
        }
    }
}
// 09.08.2026 Post-release fixes cursor by Me4Hik END
// 06.08.2026 Settings Schedule cursor by Me4Hik END

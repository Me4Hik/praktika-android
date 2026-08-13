// 06.08.2026 Settings Schedule cursor by Me4Hik START - ViewModel экрана настроек
// 09.08.2026 Post-release fixes cursor by Me4Hik START - schedule autosave state machine
// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika.ui.settings

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.me4hik.praktika.data.cycle.CycleCorruptionException
import com.me4hik.praktika.data.cycle.ScheduleSlotUpdate
import com.me4hik.praktika.data.cycle.ScheduleUpdateResult
import com.me4hik.praktika.data.cycle.ScheduleValidationException
import com.me4hik.praktika.data.cycle.ScheduleValidationReason
import com.me4hik.praktika.data.preferences.SoundPreferenceRepository
import com.me4hik.praktika.data.read.PracticeReadRepository
import com.me4hik.praktika.data.read.ScheduleReadRepository
import com.me4hik.praktika.data.read.ScheduleSlotReadModel
import com.me4hik.praktika.diagnostics.BugReportSendResult
import com.me4hik.praktika.diagnostics.DiagnosticReportSubmitter
import com.me4hik.praktika.diagnostics.TargetedBugDiagnostics
import com.me4hik.praktika.notification.NotificationPermissionPolicy
import com.me4hik.praktika.notification.NotificationPermissionUiState
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.notification.NotificationSyncRequester
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val scheduleReadRepository: ScheduleReadRepository,
    private val practiceReadRepository: PracticeReadRepository,
    private val updateScheduleCommand: UpdateScheduleCommand,
    private val pausePracticeCommand: PausePracticeCommand,
    private val resumePracticeCommand: ResumePracticeCommand,
    private val soundPreferenceRepository: SoundPreferenceRepository,
    private val notificationPermissionRepository: NotificationPermissionPolicy,
    private val notificationSyncRequester: NotificationSyncRequester,
    private val diagnosticReportSubmitter: DiagnosticReportSubmitter,
    private val savedStateHandle: SavedStateHandle,
    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3B Settings backup UI
    private val backupSettingsActions: BackupSettingsActions,
    // 10.08.2026 Post-release fixes cursor by Me4Hik END
    private val commandDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val snackbarEvents = MutableSharedFlow<SettingsSnackbarEvent>(extraBufferCapacity = 1)
    private val navigationEvents = MutableSharedFlow<SettingsNavigationEvent>(extraBufferCapacity = 1)
    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3B Settings backup UI
    private val backupEffectsChannel = Channel<SettingsBackupEffect>(Channel.BUFFERED)
    val backupEffects = backupEffectsChannel.receiveAsFlow()
    private val backupEffectsBridge = MutableSharedFlow<SettingsBackupEffect>(extraBufferCapacity = 1)
    private val backupSession = SettingsBackupSession(
        facade = backupSettingsActions,
        scope = viewModelScope,
        publish = { republishWithBackup() },
        emitSnackbar = { resId -> snackbarEvents.emit(SettingsSnackbarEvent.BackupMessage(resId)) },
        effects = backupEffectsBridge,
    )
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    private val persistedMinutes = MutableStateFlow<List<Int>?>(null)
    private var isSavingSchedule = false
    private var pendingAutosaveAfterCurrent = false
    private var isChangingSound = false
    private var isChangingPauseState = false

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _bugReportState = MutableStateFlow<BugReportUiState>(BugReportUiState.Idle)
    val bugReportState: StateFlow<BugReportUiState> = _bugReportState.asStateFlow()

    val snackbar: SharedFlow<SettingsSnackbarEvent> = snackbarEvents.asSharedFlow()
    val navigation: SharedFlow<SettingsNavigationEvent> = navigationEvents.asSharedFlow()

    init {
        ensureDraftInitialized(emptyList())
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3B Settings backup UI
        backupSession.startObserving()
        viewModelScope.launch {
            backupEffectsBridge.collect { effect ->
                backupEffectsChannel.send(effect)
            }
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        viewModelScope.launch {
            combine(
                scheduleReadRepository.observeSchedule(),
                practiceReadRepository.observeSnapshot(),
                soundPreferenceRepository.soundEnabled,
            ) { scheduleSnapshot, practiceSnapshot, soundEnabled ->
                Triple(scheduleSnapshot.slots, practiceSnapshot, soundEnabled)
            }.collect { (slots, practiceSnapshot, soundEnabled) ->
                onSourcesUpdated(slots, practiceSnapshot.practiceState.isPaused, soundEnabled)
            }
        }
    }

    fun draftMinutesBySlotIndex(): Map<Int, Int> {
        return mapOf(
            1 to requireDraftMinutes(SettingsSavedStateKeys.DRAFT_SLOT_1),
            2 to requireDraftMinutes(SettingsSavedStateKeys.DRAFT_SLOT_2),
            3 to requireDraftMinutes(SettingsSavedStateKeys.DRAFT_SLOT_3),
        )
    }

    fun onSlotTimeChanged(slotIndex: Int, timeOfDayMinutes: Int) {
        val key = draftKeyForSlot(slotIndex)
        savedStateHandle[key] = timeOfDayMinutes
        publishContentFromDraft(
            isPracticePaused = currentPauseState(),
            soundEnabled = currentSoundEnabled(),
        )
        requestAutosaveIfNeeded()
    }

    fun onSoundEnabledChanged(enabled: Boolean) {
        if (isChangingSound) {
            return
        }
        val content = _uiState.value as? SettingsUiState.Content ?: return
        isChangingSound = true
        publishContentFromDraft(
            isPracticePaused = content.isPracticePaused,
            soundEnabled = enabled,
            isChangingSound = true,
            soundError = null,
        )

        viewModelScope.launch {
            try {
                withContext(commandDispatcher) {
                    soundPreferenceRepository.setSoundEnabled(enabled)
                    // 07.08.2026 Stage 26 Release cursor by Me4Hik START - sound sync off Main
                    notificationSyncRequester.requestSync(NotificationSyncReason.SOUND_CHANGED)
                    // 07.08.2026 Stage 26 Release cursor by Me4Hik END
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Sound preference save failed", exception)
                publishContentFromDraft(
                    isPracticePaused = content.isPracticePaused,
                    soundEnabled = content.soundEnabled,
                    soundError = SettingsSoundError.SAVE_FAILED,
                )
                viewModelScope.launch {
                    snackbarEvents.emit(SettingsSnackbarEvent.SoundChangeFailed)
                }
            } finally {
                isChangingSound = false
                publishContentFromDraft(
                    isPracticePaused = currentPauseState(),
                    soundEnabled = currentSoundEnabled(),
                )
            }
        }
    }

    fun togglePauseState() {
        if (isChangingPauseState) {
            return
        }
        val content = _uiState.value as? SettingsUiState.Content ?: return
        isChangingPauseState = true
        publishContentFromDraft(
            isPracticePaused = content.isPracticePaused,
            soundEnabled = content.soundEnabled,
            isChangingPauseState = true,
            pauseError = null,
        )

        viewModelScope.launch {
            try {
                withContext(commandDispatcher) {
                    if (content.isPracticePaused) {
                        resumePracticeCommand.resumePractice()
                    } else {
                        pausePracticeCommand.pausePractice()
                    }
                }
                viewModelScope.launch {
                    snackbarEvents.emit(
                        if (content.isPracticePaused) {
                            SettingsSnackbarEvent.PracticeResumed
                        } else {
                            SettingsSnackbarEvent.PracticePaused
                        },
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Pause state change failed", exception)
                publishContentFromDraft(
                    isPracticePaused = content.isPracticePaused,
                    soundEnabled = content.soundEnabled,
                    pauseError = if (content.isPracticePaused) {
                        SettingsPauseError.RESUME_FAILED
                    } else {
                        SettingsPauseError.PAUSE_FAILED
                    },
                )
                viewModelScope.launch {
                    snackbarEvents.emit(SettingsSnackbarEvent.PauseStateChangeFailed)
                }
            } finally {
                isChangingPauseState = false
                publishContentFromDraft(
                    isPracticePaused = currentPauseState(),
                    soundEnabled = currentSoundEnabled(),
                )
            }
        }
    }

    fun onNotificationSettingsClicked() {
        val content = _uiState.value as? SettingsUiState.Content ?: return
        viewModelScope.launch {
            val permissionRequested = notificationPermissionRepository.permissionRequested.first()
            val uiState = notificationPermissionRepository.evaluateUiState(
                permissionRequested = permissionRequested,
                soundEnabled = content.soundEnabled,
            )
            val intent = when (uiState) {
                NotificationPermissionUiState.SELECTED_CHANNEL_DISABLED ->
                    notificationPermissionRepository.createChannelSettingsIntent(content.soundEnabled)
                else ->
                    notificationPermissionRepository.createAppNotificationSettingsIntent()
            }
            navigationEvents.tryEmit(SettingsNavigationEvent.OpenNotificationSettings(intent))
        }
    }

    fun submitBugReport(comment: String) {
        if (_bugReportState.value is BugReportUiState.Sending) {
            return
        }
        viewModelScope.launch {
            _bugReportState.value = BugReportUiState.Sending
            val result = withContext(commandDispatcher) {
                diagnosticReportSubmitter.submitManualReport(
                    testerComment = comment.takeIf { it.isNotBlank() },
                )
            }
            _bugReportState.value = BugReportUiState.Completed(
                result = when (result) {
                    BugReportSendResult.REMOTE_SENT -> BugReportResultKind.REMOTE_SENT
                    BugReportSendResult.SAVED_LOCALLY_NO_DSN -> BugReportResultKind.SAVED_LOCALLY
                    BugReportSendResult.SAVED_LOCALLY_SEND_FAILED -> BugReportResultKind.SEND_FAILED
                },
            )
        }
    }

    fun resetBugReportState() {
        _bugReportState.value = BugReportUiState.Idle
    }

    fun onBackRequested() {
        val content = _uiState.value as? SettingsUiState.Content ?: return
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3B Settings backup UI
        if (backupSession.onBackRequested()) {
            return
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        viewModelScope.launch {
            if (content.isDirty && !content.isScheduleValid) {
                navigationEvents.emit(SettingsNavigationEvent.ConfirmDiscardChanges)
            } else {
                navigationEvents.emit(SettingsNavigationEvent.NavigateBackClean)
            }
        }
    }

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3B Settings backup UI
    fun onBackupSetupRequested() = backupSession.onSetupRequested()
    fun onBackupReplacementRequested() = backupSession.onReplacementRequested()
    fun onBackupDisclosureConfirmed() = backupSession.onDisclosureConfirmed()
    fun onBackupDisclosureCancelled() = backupSession.onDisclosureCancelled()
    fun onBackupFolderPickerResult(uri: Uri?, grantFlags: Int) =
        backupSession.onFolderPickerResult(uri, grantFlags)
    fun onBackupCandidateConfirmed() = backupSession.onCandidateConfirmed()
    fun onBackupCandidateCancelled() = backupSession.onCandidateCancelled()
    fun onBackupChooseAnotherFolder() = backupSession.onChooseAnotherFolder()
    fun onBackupCommitRetry() = backupSession.onCommitRetry()
    fun onBackupReconnectRequested() = backupSession.onReconnectRequested()
    fun onBackupNowRequested() = backupSession.onBackupNowRequested()
    fun onBackupDisableRequested() = backupSession.onDisableRequested()
    fun onBackupDisableConfirmed() = backupSession.onDisableConfirmed()
    fun onBackupDisableCancelled() = backupSession.onDisableCancelled()
    fun onBackupReconnectDifferentUseAsNew() = backupSession.onReconnectDifferentUseAsNew()
    fun onBackupReconnectDifferentCancelled() = backupSession.onReconnectDifferentCancelled()

    override fun onCleared() {
        backupSession.onCleared()
        super.onCleared()
    }

    /** Host-test seam for onCleared backup abandon without ViewModelStore. */
    internal fun onClearedForTests() {
        onCleared()
    }

    private fun republishWithBackup() {
        val content = _uiState.value as? SettingsUiState.Content ?: return
        _uiState.value = content.copy(backup = backupSession.uiState)
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    fun confirmDiscardChanges() {
        persistedMinutes.value?.let { writeDraftMinutes(it) }
        publishContentFromDraft(
            isPracticePaused = currentPauseState(),
            soundEnabled = currentSoundEnabled(),
        )
        viewModelScope.launch {
            navigationEvents.emit(SettingsNavigationEvent.NavigateBackClean)
        }
    }

    fun retryLoad() {
        _uiState.value = SettingsUiState.Loading
    }

    private fun requestAutosaveIfNeeded() {
        if (isSavingSchedule) {
            pendingAutosaveAfterCurrent = true
            return
        }
        startNextAutosaveIfNeeded()
    }

    private fun startNextAutosaveIfNeeded() {
        val content = _uiState.value as? SettingsUiState.Content ?: return
        if (!content.isScheduleValid || !content.isDirty) {
            pendingAutosaveAfterCurrent = false
            return
        }
        if (isSavingSchedule) {
            pendingAutosaveAfterCurrent = true
            return
        }
        executeAutosave(readDraftSnapshot())
    }

    private fun executeAutosave(snapshot: List<Int>) {
        isSavingSchedule = true
        pendingAutosaveAfterCurrent = false
        publishContentFromDraft(
            isPracticePaused = currentPauseState(),
            soundEnabled = currentSoundEnabled(),
            isSavingSchedule = true,
            scheduleError = null,
        )

        viewModelScope.launch {
            var chainAutosaveAfterSuccess = false
            var autosaveError: SettingsScheduleError? = null
            val slotMinutesBefore = persistedMinutes.value?.toList() ?: emptyList()
            try {
                val updates = snapshot.toScheduleUpdates()
                withContext(commandDispatcher) {
                    when (updateScheduleCommand.updateSchedule(updates)) {
                        ScheduleUpdateResult.Success -> Unit
                    }
                }
                markPersistedSnapshot(snapshot)
                val practiceSnapshot = practiceReadRepository.observeSnapshot().first()
                val occurrence = practiceSnapshot.incompleteOccurrence
                TargetedBugDiagnostics.recordScheduleChanged(
                    slotMinutesBefore = slotMinutesBefore,
                    slotMinutesAfter = snapshot,
                    occurrenceId = occurrence?.id,
                    occurrenceStatus = occurrence?.status?.name,
                )
                chainAutosaveAfterSuccess = true
            } catch (exception: ScheduleValidationException) {
                TargetedBugDiagnostics.recordScheduleChangeResult(
                    result = "invalid",
                    detail = exception.reason.name,
                )
                handleScheduleValidationFailure(exception)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Schedule autosave failed", exception)
                TargetedBugDiagnostics.recordScheduleChangeResult(
                    result = "failed",
                    detail = exception.javaClass.simpleName,
                )
                autosaveError = SettingsScheduleError.SAVE_FAILED
                snackbarEvents.emit(SettingsSnackbarEvent.ScheduleSaveFailed)
            } finally {
                isSavingSchedule = false
                publishContentFromDraft(
                    isPracticePaused = currentPauseState(),
                    soundEnabled = currentSoundEnabled(),
                    scheduleError = autosaveError,
                )
            }
            if (chainAutosaveAfterSuccess &&
                (pendingAutosaveAfterCurrent || shouldContinueAutosave())
            ) {
                pendingAutosaveAfterCurrent = false
                startNextAutosaveIfNeeded()
            }
        }
    }

    private fun shouldContinueAutosave(): Boolean {
        val content = _uiState.value as? SettingsUiState.Content ?: return false
        return content.isScheduleValid && content.isDirty
    }

    private fun readDraftSnapshot(): List<Int> {
        return listOf(
            requireDraftMinutes(SettingsSavedStateKeys.DRAFT_SLOT_1),
            requireDraftMinutes(SettingsSavedStateKeys.DRAFT_SLOT_2),
            requireDraftMinutes(SettingsSavedStateKeys.DRAFT_SLOT_3),
        )
    }

    private fun List<Int>.toScheduleUpdates(): List<ScheduleSlotUpdate> {
        return listOf(1, 2, 3).map { slotIndex ->
            ScheduleSlotUpdate(
                slotIndex = slotIndex,
                timeOfDayMinutes = this[slotIndex - 1],
            )
        }
    }

    private fun markPersistedSnapshot(snapshot: List<Int>) {
        persistedMinutes.value = snapshot.toList()
    }

    private fun onSourcesUpdated(
        slots: List<ScheduleSlotReadModel>,
        isPracticePaused: Boolean,
        soundEnabled: Boolean,
    ) {
        try {
            val ordered = slots.sortedBy { it.slotIndex }.map { it.timeOfDayMinutes }
            val previousPersisted = persistedMinutes.value
            persistedMinutes.value = ordered

            when {
                previousPersisted == null && savedStateHandle.get<Boolean>(SettingsSavedStateKeys.DRAFT_INITIALIZED) != true -> {
                    writeDraftMinutes(ordered)
                    markDraftCleanFromSnapshot(ordered)
                }

                previousPersisted != null && !isDraftDirty(previousPersisted) -> {
                    writeDraftMinutes(ordered)
                    markDraftCleanFromSnapshot(ordered)
                }
            }

            publishContentFromDraft(
                isPracticePaused = isPracticePaused,
                soundEnabled = soundEnabled,
            )
        } catch (_: CycleCorruptionException) {
            _uiState.value = SettingsUiState.FatalError(SettingsFatalError.SCHEDULE_CORRUPTION)
        }
    }

    private fun publishContentFromDraft(
        isPracticePaused: Boolean,
        soundEnabled: Boolean,
        isSavingSchedule: Boolean = this.isSavingSchedule,
        isChangingSound: Boolean = this.isChangingSound,
        isChangingPauseState: Boolean = this.isChangingPauseState,
        scheduleError: SettingsScheduleError? = null,
        soundError: SettingsSoundError? = null,
        pauseError: SettingsPauseError? = null,
    ) {
        val draft = readDraftSnapshot()
        val persisted = persistedMinutes.value
        val isDirty = persisted != null && draft != persisted
        val duplicateError = if (draft.toSet().size != draft.size) {
            SettingsScheduleError.DUPLICATE_TIME
        } else {
            null
        }
        val resolvedScheduleError = scheduleError ?: duplicateError

        _uiState.value = SettingsUiState.Content(
            slots = listOf(1, 2, 3).map { slotIndex ->
                SettingsSlotUiModel(
                    slotIndex = slotIndex,
                    timeOfDayMinutes = draft[slotIndex - 1],
                    timeText = formatTimeOfDayMinutes(draft[slotIndex - 1]),
                )
            },
            isDirty = isDirty,
            isScheduleValid = duplicateError == null,
            isSavingSchedule = isSavingSchedule,
            soundEnabled = soundEnabled,
            isChangingSound = isChangingSound,
            isPracticePaused = isPracticePaused,
            isChangingPauseState = isChangingPauseState,
            scheduleError = resolvedScheduleError,
            soundError = soundError,
            pauseError = pauseError,
            // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3B Settings backup UI
            backup = backupSession.uiState,
            // 10.08.2026 Post-release fixes cursor by Me4Hik END
        )
    }

    private fun handleScheduleValidationFailure(exception: ScheduleValidationException) {
        val error = when (exception.reason) {
            ScheduleValidationReason.DUPLICATE_TIME -> SettingsScheduleError.DUPLICATE_TIME
            else -> SettingsScheduleError.VALIDATION_FAILED
        }
        publishContentFromDraft(
            isPracticePaused = currentPauseState(),
            soundEnabled = currentSoundEnabled(),
            scheduleError = error,
        )
    }

    private fun ensureDraftInitialized(defaultMinutes: List<Int>) {
        if (savedStateHandle.get<Boolean>(SettingsSavedStateKeys.DRAFT_INITIALIZED) == true) {
            return
        }
        val fallback = if (defaultMinutes.size == 3) {
            defaultMinutes
        } else {
            listOf(660, 900, 1140)
        }
        writeDraftMinutes(fallback)
        markDraftCleanFromSnapshot(fallback)
    }

    private fun writeDraftMinutes(minutes: List<Int>) {
        savedStateHandle[SettingsSavedStateKeys.DRAFT_SLOT_1] = minutes[0]
        savedStateHandle[SettingsSavedStateKeys.DRAFT_SLOT_2] = minutes[1]
        savedStateHandle[SettingsSavedStateKeys.DRAFT_SLOT_3] = minutes[2]
        savedStateHandle[SettingsSavedStateKeys.DRAFT_INITIALIZED] = true
    }

    private fun markDraftCleanFromSnapshot(snapshot: List<Int>) {
        persistedMinutes.value = snapshot.toList()
    }

    private fun isDraftDirty(persisted: List<Int>): Boolean {
        return readDraftSnapshot() != persisted
    }

    private fun requireDraftMinutes(key: String): Int {
        return savedStateHandle.get<Int>(key)
            ?: throw IllegalStateException("Draft minutes missing for $key")
    }

    private fun draftKeyForSlot(slotIndex: Int): String {
        return when (slotIndex) {
            1 -> SettingsSavedStateKeys.DRAFT_SLOT_1
            2 -> SettingsSavedStateKeys.DRAFT_SLOT_2
            3 -> SettingsSavedStateKeys.DRAFT_SLOT_3
            else -> throw IllegalArgumentException("Invalid slotIndex $slotIndex")
        }
    }

    private fun currentPauseState(): Boolean {
        return (_uiState.value as? SettingsUiState.Content)?.isPracticePaused ?: false
    }

    private fun currentSoundEnabled(): Boolean {
        return (_uiState.value as? SettingsUiState.Content)?.soundEnabled ?: true
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
// 09.08.2026 Post-release fixes cursor by Me4Hik END
// 06.08.2026 Settings Schedule cursor by Me4Hik END

// 06.08.2026 Settings Schedule cursor by Me4Hik START - ViewModel экрана настроек
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
import com.me4hik.praktika.notification.NotificationPermissionPolicy
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.notification.NotificationSyncRequester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    private val savedStateHandle: SavedStateHandle,
    private val commandDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val snackbarEvents = MutableSharedFlow<SettingsSnackbarEvent>(extraBufferCapacity = 1)
    private val navigationEvents = MutableSharedFlow<SettingsNavigationEvent>(extraBufferCapacity = 1)

    private val persistedMinutes = MutableStateFlow<List<Int>?>(null)
    private var isSavingSchedule = false
    private var isChangingSound = false
    private var isChangingPauseState = false

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val snackbar: SharedFlow<SettingsSnackbarEvent> = snackbarEvents.asSharedFlow()
    val navigation: SharedFlow<SettingsNavigationEvent> = navigationEvents.asSharedFlow()

    init {
        ensureDraftInitialized(emptyList())
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
    }

    fun saveSchedule() {
        if (isSavingSchedule) {
            return
        }
        val content = _uiState.value as? SettingsUiState.Content ?: return
        if (!content.isDirty || !content.isScheduleValid) {
            return
        }

        isSavingSchedule = true
        publishContentFromDraft(
            isPracticePaused = content.isPracticePaused,
            soundEnabled = content.soundEnabled,
            isSavingSchedule = true,
            scheduleError = null,
        )

        viewModelScope.launch {
            try {
                val updates = listOf(1, 2, 3).map { slotIndex ->
                    ScheduleSlotUpdate(
                        slotIndex = slotIndex,
                        timeOfDayMinutes = requireDraftMinutes(draftKeyForSlot(slotIndex)),
                    )
                }
                withContext(commandDispatcher) {
                    when (updateScheduleCommand.updateSchedule(updates)) {
                        ScheduleUpdateResult.Success -> Unit
                    }
                }
                markDraftCleanFromDraft()
                viewModelScope.launch {
                    snackbarEvents.emit(SettingsSnackbarEvent.ScheduleSaved)
                }
            } catch (exception: ScheduleValidationException) {
                handleScheduleValidationFailure(exception)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Schedule save failed", exception)
                publishContentFromDraft(
                    isPracticePaused = content.isPracticePaused,
                    soundEnabled = content.soundEnabled,
                    scheduleError = SettingsScheduleError.SAVE_FAILED,
                )
                viewModelScope.launch {
                    snackbarEvents.emit(SettingsSnackbarEvent.ScheduleSaveFailed)
                }
            } finally {
                isSavingSchedule = false
                publishContentFromDraft(
                    isPracticePaused = currentPauseState(),
                    soundEnabled = currentSoundEnabled(),
                )
            }
        }
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
        val intent = notificationPermissionRepository.createChannelSettingsIntent(content.soundEnabled)
        navigationEvents.tryEmit(SettingsNavigationEvent.OpenNotificationSettings(intent))
    }

    fun onBackRequested() {
        val content = _uiState.value as? SettingsUiState.Content ?: return
        viewModelScope.launch {
            if (content.isDirty) {
                navigationEvents.emit(SettingsNavigationEvent.ConfirmDiscardChanges)
            } else {
                navigationEvents.emit(SettingsNavigationEvent.NavigateBackClean)
            }
        }
    }

    fun confirmDiscardChanges() {
        viewModelScope.launch {
            navigationEvents.emit(SettingsNavigationEvent.NavigateBackClean)
        }
    }

    fun retryLoad() {
        _uiState.value = SettingsUiState.Loading
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
                    markDraftClean()
                }

                previousPersisted != null && !isDraftDirty(previousPersisted) -> {
                    writeDraftMinutes(ordered)
                    markDraftClean()
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
        val draft = listOf(
            requireDraftMinutes(SettingsSavedStateKeys.DRAFT_SLOT_1),
            requireDraftMinutes(SettingsSavedStateKeys.DRAFT_SLOT_2),
            requireDraftMinutes(SettingsSavedStateKeys.DRAFT_SLOT_3),
        )
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
                    timeOfDayMinutes = requireDraftMinutes(draftKeyForSlot(slotIndex)),
                    timeText = formatTimeOfDayMinutes(requireDraftMinutes(draftKeyForSlot(slotIndex))),
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
        markDraftClean()
    }

    private fun writeDraftMinutes(minutes: List<Int>) {
        savedStateHandle[SettingsSavedStateKeys.DRAFT_SLOT_1] = minutes[0]
        savedStateHandle[SettingsSavedStateKeys.DRAFT_SLOT_2] = minutes[1]
        savedStateHandle[SettingsSavedStateKeys.DRAFT_SLOT_3] = minutes[2]
        savedStateHandle[SettingsSavedStateKeys.DRAFT_INITIALIZED] = true
    }

    private fun markDraftClean() {
        val draft = listOf(
            requireDraftMinutes(SettingsSavedStateKeys.DRAFT_SLOT_1),
            requireDraftMinutes(SettingsSavedStateKeys.DRAFT_SLOT_2),
            requireDraftMinutes(SettingsSavedStateKeys.DRAFT_SLOT_3),
        )
        persistedMinutes.value = draft
    }

    private fun markDraftCleanFromDraft() {
        markDraftClean()
    }

    private fun isDraftDirty(persisted: List<Int>): Boolean {
        val draft = listOf(
            requireDraftMinutes(SettingsSavedStateKeys.DRAFT_SLOT_1),
            requireDraftMinutes(SettingsSavedStateKeys.DRAFT_SLOT_2),
            requireDraftMinutes(SettingsSavedStateKeys.DRAFT_SLOT_3),
        )
        return draft != persisted
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
// 06.08.2026 Settings Schedule cursor by Me4Hik END

// 06.08.2026 Settings Schedule cursor by Me4Hik START - UI models Settings
package com.me4hik.praktika.ui.settings

data class SettingsSlotUiModel(
    val slotIndex: Int,
    val timeOfDayMinutes: Int,
    val timeText: String,
)

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Content(
        val slots: List<SettingsSlotUiModel>,
        val isDirty: Boolean,
        val isScheduleValid: Boolean,
        val isSavingSchedule: Boolean,
        val soundEnabled: Boolean,
        val isChangingSound: Boolean,
        val isPracticePaused: Boolean,
        val isChangingPauseState: Boolean,
        val scheduleError: SettingsScheduleError?,
        val soundError: SettingsSoundError?,
        val pauseError: SettingsPauseError?,
    ) : SettingsUiState

    data class FatalError(
        val error: SettingsFatalError,
    ) : SettingsUiState
}

enum class SettingsScheduleError {
    DUPLICATE_TIME,
    SAVE_FAILED,
    VALIDATION_FAILED,
}

enum class SettingsSoundError {
    SAVE_FAILED,
}

enum class SettingsPauseError {
    PAUSE_FAILED,
    RESUME_FAILED,
}

enum class SettingsFatalError {
    SCHEDULE_CORRUPTION,
    PRACTICE_CORRUPTION,
}

sealed interface SettingsSnackbarEvent {
    data object ScheduleSaved : SettingsSnackbarEvent
    data object PracticePaused : SettingsSnackbarEvent
    data object PracticeResumed : SettingsSnackbarEvent
    data object ScheduleSaveFailed : SettingsSnackbarEvent
    data object SoundChangeFailed : SettingsSnackbarEvent
    data object PauseStateChangeFailed : SettingsSnackbarEvent
}

sealed interface SettingsNavigationEvent {
    data object NavigateBackClean : SettingsNavigationEvent
    data object ConfirmDiscardChanges : SettingsNavigationEvent
    data class OpenNotificationSettings(val intent: android.content.Intent) : SettingsNavigationEvent
}

object SettingsSavedStateKeys {
    const val DRAFT_SLOT_1 = "settings_draft_slot_1"
    const val DRAFT_SLOT_2 = "settings_draft_slot_2"
    const val DRAFT_SLOT_3 = "settings_draft_slot_3"
    const val DRAFT_INITIALIZED = "settings_draft_initialized"
}

object SettingsTestTags {
    const val SETTINGS_SCREEN = "settings_screen"
    const val SETTINGS_LOADING = "settings_loading"
    const val SETTINGS_SLOT_1 = "settings_slot_1"
    const val SETTINGS_SLOT_2 = "settings_slot_2"
    const val SETTINGS_SLOT_3 = "settings_slot_3"
    const val SETTINGS_TIME_PICKER = "settings_time_picker"
    const val SETTINGS_SCHEDULE_SAVE = "settings_schedule_save"
    const val SETTINGS_SCHEDULE_PROGRESS = "settings_schedule_progress"
    const val SETTINGS_SCHEDULE_ERROR = "settings_schedule_error"
    const val SETTINGS_SOUND_SWITCH = "settings_sound_switch"
    const val SETTINGS_NOTIFICATION_SETTINGS = "settings_notification_settings"
    const val SETTINGS_PAUSE_RESUME = "settings_pause_resume"
    const val SETTINGS_PAUSE_PROGRESS = "settings_pause_progress"
    const val SETTINGS_ABOUT = "settings_about"
    const val SETTINGS_BACK = "settings_back"
    const val SETTINGS_DIRTY_DIALOG = "settings_dirty_dialog"
    const val SETTINGS_DISCARD = "settings_discard"
    const val SETTINGS_STAY = "settings_stay"
    const val SETTINGS_SNACKBAR = "settings_snackbar"
    const val SETTINGS_FATAL_ERROR = "settings_fatal_error"
}

fun formatTimeOfDayMinutes(minutes: Int): String {
    val hour = minutes / 60
    val minute = minutes % 60
    return "%02d:%02d".format(hour, minute)
}
// 06.08.2026 Settings Schedule cursor by Me4Hik END

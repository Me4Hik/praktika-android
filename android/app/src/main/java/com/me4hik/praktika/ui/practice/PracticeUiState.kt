// 05.08.2026 Main Screen cursor by Me4Hik START - UI state machine главного экрана
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik START - NotStarted с schedule draft
package com.me4hik.praktika.ui.practice

import com.me4hik.praktika.data.model.QuestionOccurrenceStatus

enum class PracticeUiError {
    LOAD_FAILED,
    START_FAILED,
    CORRUPTION,
}

sealed interface PracticeUiState {
    data object Loading : PracticeUiState

    data class NotStarted(
        val slots: List<OnboardingScheduleUiModel>,
        val isScheduleLoading: Boolean,
        val isScheduleDirty: Boolean,
        val isScheduleValid: Boolean,
        val scheduleError: OnboardingScheduleError?,
        val isStarting: Boolean,
        val startError: PracticeUiError?,
    ) : PracticeUiState

    data class Started(
        val content: MainContentUiState,
        val notificationCard: HomeNotificationCardState? = null,
    ) : PracticeUiState

    data class FatalError(
        val error: PracticeUiError,
    ) : PracticeUiState
}

sealed interface MainContentUiState {
    val occurrence: CurrentOccurrenceUiModel

    data class Scheduled(
        override val occurrence: CurrentOccurrenceUiModel,
    ) : MainContentUiState

    data class Available(
        override val occurrence: CurrentOccurrenceUiModel,
    ) : MainContentUiState

    data class PausedScheduled(
        override val occurrence: CurrentOccurrenceUiModel,
    ) : MainContentUiState

    data class PausedAvailable(
        override val occurrence: CurrentOccurrenceUiModel,
    ) : MainContentUiState
}

data class CurrentOccurrenceUiModel(
    val occurrenceId: Long,
    val questionId: Int,
    val cyclePosition: Int,
    val questionText: String,
    val status: QuestionOccurrenceStatus,
    val plannedAtText: String,
    val availableUntilText: String,
)

enum class HomeNotificationCardState {
    REQUEST_PERMISSION,
    OPEN_SETTINGS,
}
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik END
// 05.08.2026 Main Screen cursor by Me4Hik END

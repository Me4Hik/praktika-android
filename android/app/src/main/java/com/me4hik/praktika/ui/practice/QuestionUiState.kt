// 05.08.2026 Question And Skip cursor by Me4Hik START - UI state экрана вопроса
package com.me4hik.praktika.ui.practice

import com.me4hik.praktika.data.model.QuestionOccurrenceStatus

enum class QuestionBlockedReason {
    NOT_FOUND,
    NOT_AVAILABLE_YET,
    ALREADY_COMPLETED,
    NOT_CURRENT,
    PRACTICE_PAUSED,
}

enum class QuestionFatalError {
    CORRUPTION,
    LOAD_FAILED,
}

enum class QuestionSkipError {
    NOT_ALLOWED,
    FAILED,
}

enum class QuestionDeferError {
    NOT_ALLOWED,
    FAILED,
}

data class QuestionUiModel(
    val occurrenceId: Long,
    val questionId: Int,
    val questionTextSnapshot: String,
    val cyclePosition: Int,
    val status: QuestionOccurrenceStatus,
)

sealed interface QuestionUiState {
    data object Loading : QuestionUiState

    data class Interactive(val question: QuestionUiModel) : QuestionUiState

    data class Blocked(
        val question: QuestionUiModel?,
        val reason: QuestionBlockedReason,
    ) : QuestionUiState

    data class FatalError(val error: QuestionFatalError) : QuestionUiState
}

data class QuestionCommandState(
    val isSkipping: Boolean = false,
    val skipError: QuestionSkipError? = null,
    val isDeferring: Boolean = false,
    val deferError: QuestionDeferError? = null,
) {
    val isBusy: Boolean
        get() = isSkipping || isDeferring
}

sealed interface QuestionNavigationEvent {
    data class OpenAnswer(val occurrenceId: Long) : QuestionNavigationEvent

    data object ReturnHomeAfterSkip : QuestionNavigationEvent

    data class ReturnHomeAfterDefer(val durationMinutes: Int) : QuestionNavigationEvent
}
// 05.08.2026 Question And Skip cursor by Me4Hik END

// 05.08.2026 Answer Save cursor by Me4Hik START - UI state экрана ответа
package com.me4hik.praktika.ui.practice

import com.me4hik.praktika.data.model.MoodLevel
import com.me4hik.praktika.data.preferences.QuestionWordingMode

sealed interface AnswerUiState {
    data object Loading : AnswerUiState

    data class Interactive(
        // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik START - questionId для repeat check
        val questionId: Int,
        // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik END
        val questionText: String,
        val draftText: String,
        val isSaving: Boolean,
        val canSave: Boolean,
        val saveError: AnswerSaveError?,
    ) : AnswerUiState

    data class Blocked(
        val questionText: String?,
        val reason: AnswerBlockedReason,
    ) : AnswerUiState

    data class FatalError(
        val error: AnswerFatalError,
    ) : AnswerUiState
}

enum class AnswerBlockedReason {
    NOT_FOUND,
    NOT_AVAILABLE_YET,
    PRACTICE_PAUSED,
    ALREADY_COMPLETED,
    NOT_CURRENT,
}

enum class AnswerSaveError {
    BLANK,
    OCCURRENCE_CHANGED,
    SAVE_FAILED,
}

enum class AnswerFatalError {
    CORRUPTION,
    LOAD_FAILED,
}

// 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik START - post-save snackbar state
sealed interface PendingSaveConfirmation {
    data object ConfirmationOnly : PendingSaveConfirmation

    data class WithHistoryOffer(val questionId: Int) : PendingSaveConfirmation
}
// 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik END

sealed interface AnswerNavigationEvent {
    // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik START - optional history offer payload
    data class ReturnHomeAfterSave(
        val historyOfferQuestionId: Int? = null,
    ) : AnswerNavigationEvent
    // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik END

    data object ReturnHomeAfterStale : AnswerNavigationEvent
}

object AnswerSavedStateKeys {
    const val DRAFT_TEXT = "draft_text"
}

data class AnswerMoodUiState(
    val selectedLevel: MoodLevel? = null,
    val wordingMode: QuestionWordingMode = QuestionWordingMode.DEFAULT,
    val isExpanded: Boolean = false,
    val isSaving: Boolean = false,
)
// 05.08.2026 Answer Save cursor by Me4Hik END

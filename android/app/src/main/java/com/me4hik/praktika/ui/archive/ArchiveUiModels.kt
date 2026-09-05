// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - UI models и test tags архива
package com.me4hik.praktika.ui.archive

sealed interface ArchiveDatesUiState {
    data object Loading : ArchiveDatesUiState
    data object Empty : ArchiveDatesUiState
    data class Content(
        val dates: List<ArchiveDateListItem>,
    ) : ArchiveDatesUiState
    data class Error(
        val message: String,
    ) : ArchiveDatesUiState
}

data class ArchiveDateListItem(
    val epochDay: Long,
    val dateText: String,
    val answerCount: Int,
)

sealed interface ArchiveDayUiState {
    data object Loading : ArchiveDayUiState
    data object Empty : ArchiveDayUiState
    data class Content(
        val titleDateText: String,
        val entries: List<ArchiveDayEntryUi>,
    ) : ArchiveDayUiState
    data class Error(
        val message: String,
    ) : ArchiveDayUiState
}

data class ArchiveDayEntryUi(
    val answerId: Long,
    val questionText: String,
    val answerText: String,
    val dateText: String,
    val timeText: String,
)

object ArchiveTestTags {
    const val DATES_SCREEN = "archive_dates_screen"
    const val DATES_EMPTY = "archive_dates_empty"
    const val DATES_LIST = "archive_dates_list"
    const val DATE_ITEM_PREFIX = "archive_date_item_"
    const val DAY_SCREEN = "archive_day_screen"
    const val DAY_LIST = "archive_day_list"
    const val DAY_ENTRY_PREFIX = "archive_day_entry_"
    const val BACK = "archive_back_button"
    // 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - test tags архива по вопросам
    const val OPEN_QUESTIONS = "archive_open_questions_button"
    // PROMPT 137 — analytics insights entry from Archive root
    const val OPEN_INSIGHTS = "archive_open_insights_button"
    const val QUESTIONS_SCREEN = "archive_questions_screen"
    const val QUESTIONS_EMPTY = "archive_questions_empty"
    const val QUESTIONS_LIST = "archive_questions_list"
    const val QUESTION_ITEM_PREFIX = "archive_question_item_"
    const val QUESTION_HISTORY_SCREEN = "archive_question_history_screen"
    const val QUESTION_HISTORY_LIST = "archive_question_history_list"
    const val QUESTION_HISTORY_ENTRY_PREFIX = "archive_question_history_entry_"
    // 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END
    // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - test tags удаления Answer
    const val DELETE_BUTTON_PREFIX = "archive_delete_button_"
    const val DELETE_DIALOG = "archive_delete_dialog"
    const val DELETE_CANCEL = "archive_delete_cancel"
    const val DELETE_CONFIRM_PREFIX = "archive_delete_confirm_"
    const val DELETE_ERROR = "archive_delete_error"
    // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik END
    // 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - test tags export
    const val EXPORT_ALL = "archive_export_all_button"
    const val EXPORT_PERIOD = "archive_export_period_button"
    const val EXPORT_DAY = "archive_export_day_button"
    const val EXPORT_HISTORY = "archive_export_history_button"
    const val EXPORT_PERIOD_DIALOG = "archive_export_period_dialog"
    const val EXPORT_PERIOD_PICKER = "archive_export_period_picker"
    const val EXPORT_PERIOD_CONFIRM = "archive_export_period_confirm"
    const val EXPORT_PERIOD_CANCEL = "archive_export_period_cancel"
    // 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
    // 07.08.2026 Stage 20 CSV Export cursor by Me4Hik START - test tags format dialog
    const val EXPORT_FORMAT_DIALOG = "archive_export_format_dialog"
    const val EXPORT_FORMAT_MARKDOWN = "archive_export_format_markdown"
    const val EXPORT_FORMAT_CSV = "archive_export_format_csv"
    const val EXPORT_FORMAT_CANCEL = "archive_export_format_cancel"
    // 07.08.2026 Stage 20 CSV Export cursor by Me4Hik END
    // 07.08.2026 Stage 21 Share cursor by Me4Hik START - test tags share
    const val SHARE_ALL = "archive_share_all_button"
    const val SHARE_PERIOD = "archive_share_period_button"
    const val SHARE_DAY = "archive_share_day_button"
    const val SHARE_HISTORY = "archive_share_history_button"
    const val SHARE_PERIOD_DIALOG = "archive_share_period_dialog"
    const val SHARE_PERIOD_CONFIRM = "archive_share_period_confirm"
    const val SHARE_FORMAT_DIALOG = "archive_share_format_dialog"
    const val SHARE_BUTTON_PREFIX = "archive_share_button_"
    const val ENTRY_SHARE_DIALOG = "archive_entry_share_dialog"
    const val ENTRY_SHARE_QUESTION_ONLY = "archive_entry_share_question_only"
    const val ENTRY_SHARE_QUESTION_AND_ANSWER = "archive_entry_share_question_and_answer"
    const val ENTRY_SHARE_CANCEL = "archive_entry_share_cancel"
    // 07.08.2026 Stage 21 Share cursor by Me4Hik END
}
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END

// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - UI models архива по вопросам
sealed interface ArchiveQuestionsUiState {
    data object Loading : ArchiveQuestionsUiState
    data object Empty : ArchiveQuestionsUiState
    data class Content(
        val questions: List<ArchiveQuestionListItem>,
    ) : ArchiveQuestionsUiState
    data class Error(
        val message: String,
    ) : ArchiveQuestionsUiState
}

data class ArchiveQuestionListItem(
    val questionId: Int,
    val questionText: String,
    val cyclePosition: Int,
    val latestEventAtEpochMillis: Long,
    val answerCount: Int,
    val rejectedCount: Int,
    val missedCount: Int,
    val deferredCount: Int,
)

sealed interface ArchiveQuestionHistoryUiState {
    data object Loading : ArchiveQuestionHistoryUiState
    data object Empty : ArchiveQuestionHistoryUiState
    data class Content(
        val entries: List<ArchiveQuestionHistoryItem>,
    ) : ArchiveQuestionHistoryUiState
    data class Error(
        val message: String,
    ) : ArchiveQuestionHistoryUiState
}

enum class ArchiveHistoryItemKind {
    Answer,
    Rejected,
    Missed,
    Deferred,
}

data class ArchiveQuestionHistoryItem(
    val stableKey: String,
    val kind: ArchiveHistoryItemKind,
    val occurrenceId: Long,
    val questionText: String,
    val answerId: Long?,
    val answerText: String?,
    val dateTimeText: String,
    val cycleNumber: Int,
    val cyclePosition: Int,
    val durationMinutes: Int?,
    val canShare: Boolean,
    val canDelete: Boolean,
)
// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END

// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - UI state confirmation delete
data class ArchiveDeleteUiState(
    val pendingDeleteAnswerId: Long? = null,
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
) {
    val isDialogVisible: Boolean
        get() = pendingDeleteAnswerId != null
}
// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik END

// 07.08.2026 Stage 21 Share cursor by Me4Hik START - pending entry share request
data class EntryShareRequest(
    val questionText: String,
    val answerText: String,
)
// 07.08.2026 Stage 21 Share cursor by Me4Hik END

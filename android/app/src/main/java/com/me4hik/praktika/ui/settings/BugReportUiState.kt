// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika.ui.settings

sealed interface BugReportUiState {
    data object Idle : BugReportUiState

    data object Sending : BugReportUiState

    data class Completed(
        val result: BugReportResultKind,
    ) : BugReportUiState
}

enum class BugReportResultKind {
    REMOTE_SENT,
    SAVED_LOCALLY,
    SEND_FAILED,
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END

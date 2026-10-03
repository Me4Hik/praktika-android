// QUESTION_DEFER_FINAL_V1 — unified Snackbar duration helpers (Short info / Long action)
// 03.10.2026 Snackbar one-row compact cursor by Me4Hik START - compact action label visuals
package com.me4hik.praktika.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals

/**
 * Action snackbar visuals with a short compact label for the stacked one-row branch.
 * [actionLabel] stays the full wide/accessibility label.
 */
internal data class PracticeActionSnackbarVisuals(
    override val message: String,
    override val actionLabel: String,
    val compactActionLabel: String,
    override val withDismissAction: Boolean = true,
    override val duration: SnackbarDuration = SnackbarDuration.Long,
) : SnackbarVisuals

suspend fun SnackbarHostState.showPracticeInfoSnackbar(message: String): SnackbarResult {
    return showSnackbar(
        message = message,
        actionLabel = null,
        withDismissAction = false,
        duration = SnackbarDuration.Short,
    )
}

suspend fun SnackbarHostState.showPracticeActionSnackbar(
    message: String,
    actionLabel: String,
    compactActionLabel: String = actionLabel,
): SnackbarResult {
    return showSnackbar(
        PracticeActionSnackbarVisuals(
            message = message,
            actionLabel = actionLabel,
            compactActionLabel = compactActionLabel,
        ),
    )
}
// 03.10.2026 Snackbar one-row compact cursor by Me4Hik END

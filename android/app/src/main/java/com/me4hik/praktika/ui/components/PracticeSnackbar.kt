// QUESTION_DEFER_FINAL_V1 — unified Snackbar duration helpers (Short info / Long action)
package com.me4hik.praktika.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

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
): SnackbarResult {
    return showSnackbar(
        message = message,
        actionLabel = actionLabel,
        withDismissAction = true,
        duration = SnackbarDuration.Long,
    )
}

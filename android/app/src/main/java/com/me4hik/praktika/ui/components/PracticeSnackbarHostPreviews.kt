// 03.10.2026 Snackbar one-row compact cursor by Me4Hik START - Studio Preview @360dp
package com.me4hik.praktika.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.me4hik.praktika.ui.theme.PraktikaTheme
import kotlinx.coroutines.yield

@Composable
private fun PracticeActionSnackbarPreviewHost(
    message: String,
    actionLabel: String,
    compactActionLabel: String,
) {
    PraktikaTheme {
        val hostState = remember { SnackbarHostState() }
        Box(modifier = Modifier.fillMaxSize()) {
            PracticeSnackbarHost(
                hostState = hostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        LaunchedEffect(hostState) {
            yield()
            hostState.showPracticeActionSnackbar(
                message = message,
                actionLabel = actionLabel,
                compactActionLabel = compactActionLabel,
            )
        }
    }
}

@Preview(
    name = "Compact one-row 360",
    widthDp = 360,
    heightDp = 200,
    showBackground = true,
    backgroundColor = 0xFF080B12,
)
@Composable
private fun PreviewCompactOneRowActionSnackbar360() {
    PracticeActionSnackbarPreviewHost(
        message = "Ответ сохранён",
        actionLabel = "Посмотреть историю",
        compactActionLabel = "Смотреть",
    )
}
// 03.10.2026 Snackbar one-row compact cursor by Me4Hik END

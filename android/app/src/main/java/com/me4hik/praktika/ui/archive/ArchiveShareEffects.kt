// 07.08.2026 Stage 21 Share cursor by Me4Hik START - Compose launch chooser для share events
package com.me4hik.praktika.ui.archive

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.me4hik.praktika.share.ShareIntentFactory

@Composable
fun ArchiveShareEffects(
    coordinator: ArchiveShareCoordinator,
    snackbarHostState: SnackbarHostState,
    context: Context,
    noAnswersMessage: String,
    prepareFailedMessage: String,
    chooserFailedMessage: String,
    textChooserTitle: String,
    fileChooserTitle: String,
) {
    LaunchedEffect(
        coordinator,
        snackbarHostState,
        context,
        noAnswersMessage,
        prepareFailedMessage,
        chooserFailedMessage,
        textChooserTitle,
        fileChooserTitle,
    ) {
        coordinator.events.collect { event ->
            when (event) {
                is ArchiveShareUiEvent.ShareText -> {
                    launchShareChooser(
                        context = context,
                        shareIntent = ShareIntentFactory.createTextShareIntent(event.payload),
                        chooserTitle = textChooserTitle,
                        chooserFailedMessage = chooserFailedMessage,
                        snackbarHostState = snackbarHostState,
                    )
                }
                is ArchiveShareUiEvent.ShareFile -> {
                    launchShareChooser(
                        context = context,
                        shareIntent = ShareIntentFactory.createFileShareIntent(
                            context = context,
                            file = event.file,
                            mimeType = event.mimeType,
                        ),
                        chooserTitle = fileChooserTitle,
                        chooserFailedMessage = chooserFailedMessage,
                        snackbarHostState = snackbarHostState,
                    )
                }
                ArchiveShareUiEvent.NoAnswers -> {
                    snackbarHostState.showSnackbar(noAnswersMessage)
                }
                ArchiveShareUiEvent.PrepareFailed -> {
                    snackbarHostState.showSnackbar(prepareFailedMessage)
                }
            }
        }
    }
}

private suspend fun launchShareChooser(
    context: Context,
    shareIntent: android.content.Intent,
    chooserTitle: String,
    chooserFailedMessage: String,
    snackbarHostState: SnackbarHostState,
) {
    try {
        context.startActivity(
            ShareIntentFactory.createChooser(
                shareIntent = shareIntent,
                title = chooserTitle,
            ),
        )
    } catch (_: ActivityNotFoundException) {
        snackbarHostState.showSnackbar(chooserFailedMessage)
    }
}
// 07.08.2026 Stage 21 Share cursor by Me4Hik END

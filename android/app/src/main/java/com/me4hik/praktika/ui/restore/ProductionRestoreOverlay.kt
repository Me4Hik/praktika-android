// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 5.2 production restore overlay
package com.me4hik.praktika.ui.restore

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.R
import com.me4hik.praktika.ui.components.PracticeBackground
import com.me4hik.praktika.ui.components.PracticeBackgroundStyle
import com.me4hik.praktika.ui.components.PracticePrimaryButton
import com.me4hik.praktika.ui.components.PracticeSurface
import com.me4hik.praktika.ui.theme.TextPrimary
import com.me4hik.praktika.ui.theme.TextSecondary
import com.me4hik.praktika.ui.theme.TitleSerifStyle

@Composable
fun ProductionRestoreOverlay(
    uiState: ProductionRestoreUiState,
    onChooseFolder: () -> Unit,
    onRestoreConfirmed: () -> Unit,
    onDismiss: () -> Unit,
    onSuccessAcknowledged: () -> Unit,
    onRuntimeWarningContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backDismissible = ProductionRestoreOverlayPolicy.isBackDismissible(uiState)
    BackHandler(enabled = uiState != ProductionRestoreUiState.Hidden) {
        if (backDismissible) {
            onDismiss()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(ProductionRestoreTestTags.RESTORE_OVERLAY)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        PracticeBackground(style = PracticeBackgroundStyle.Subdued)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            RestoreOverlayTopBar(
                showClose = ProductionRestoreOverlayPolicy.showsCloseIcon(uiState),
                onDismiss = onDismiss,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RestoreOverlayBody(
                    uiState = uiState,
                    onChooseFolder = onChooseFolder,
                    onRestoreConfirmed = onRestoreConfirmed,
                    onDismiss = onDismiss,
                    onSuccessAcknowledged = onSuccessAcknowledged,
                    onRuntimeWarningContinue = onRuntimeWarningContinue,
                )
            }
        }
        if (ProductionRestoreOverlayPolicy.showsBusyIndicator(uiState)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(ProductionRestoreTestTags.RESTORE_BUSY),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun RestoreOverlayTopBar(
    showClose: Boolean,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        if (showClose) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(ProductionRestoreTestTags.RESTORE_CLOSE),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.restore_close_content_description),
                    tint = TextSecondary,
                )
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun RestoreOverlayBody(
    uiState: ProductionRestoreUiState,
    onChooseFolder: () -> Unit,
    onRestoreConfirmed: () -> Unit,
    onDismiss: () -> Unit,
    onSuccessAcknowledged: () -> Unit,
    onRuntimeWarningContinue: () -> Unit,
) {
    when (uiState) {
        ProductionRestoreUiState.Hidden -> Unit
        ProductionRestoreUiState.Idle -> IdleContent(onChooseFolder, onDismiss)
        ProductionRestoreUiState.CheckingEligibility -> BusyTitleContent(
            title = stringResource(R.string.restore_checking_title),
        )
        ProductionRestoreUiState.ChoosingFolder -> BusyTitleContent(
            title = stringResource(R.string.restore_choosing_folder_title),
        )
        ProductionRestoreUiState.Connecting -> BusyTitleContent(
            title = stringResource(R.string.restore_connecting_title),
            body = stringResource(R.string.restore_connecting_body),
        )
        ProductionRestoreUiState.Inspecting -> BusyTitleContent(
            title = stringResource(R.string.restore_inspecting_title),
            body = stringResource(R.string.restore_inspecting_body),
        )
        is ProductionRestoreUiState.Blocked -> BlockedContent(uiState.reason, onDismiss)
        is ProductionRestoreUiState.PreviewReady -> PreviewContent(
            preview = uiState.preview,
            staleNotice = uiState.staleNotice,
            onRestoreConfirmed = onRestoreConfirmed,
            onChooseFolder = onChooseFolder,
        )
        is ProductionRestoreUiState.Restoring -> TerminalBusyContent(
            title = stringResource(R.string.restore_restoring_title),
            body = stringResource(R.string.restore_restoring_body),
        )
        is ProductionRestoreUiState.RestoreSuccess -> TerminalAckContent(
            title = stringResource(R.string.restore_success_title),
            body = stringResource(R.string.restore_success_body),
            buttonText = stringResource(R.string.restore_continue),
            onAcknowledge = onSuccessAcknowledged,
        )
        is ProductionRestoreUiState.RuntimeSyncWarning -> TerminalAckContent(
            title = stringResource(R.string.restore_runtime_warning_title),
            body = stringResource(R.string.restore_runtime_warning_body),
            buttonText = stringResource(R.string.restore_continue),
            onAcknowledge = onRuntimeWarningContinue,
        )
        is ProductionRestoreUiState.Error -> ErrorContent(
            error = uiState.error,
            onChooseFolder = onChooseFolder,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun IdleContent(
    onChooseFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    RestoreTitle(text = stringResource(R.string.restore_idle_title))
    RestoreBody(text = stringResource(R.string.restore_idle_body))
    Text(
        text = stringResource(R.string.restore_idle_disclosure),
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
    Spacer(modifier = Modifier.height(24.dp))
    PracticePrimaryButton(
        text = stringResource(R.string.restore_choose_folder),
        onClick = onChooseFolder,
        enabled = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ProductionRestoreTestTags.RESTORE_CHOOSE_FOLDER),
    )
    TextButton(
        onClick = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.restore_cancel),
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BusyTitleContent(
    title: String,
    body: String? = null,
) {
    RestoreTitle(text = title)
    body?.let {
        RestoreBody(text = it)
    }
}

@Composable
private fun BlockedContent(
    reason: ProductionRestoreTargetBlockReason,
    onDismiss: () -> Unit,
) {
    RestoreTitle(text = stringResource(R.string.restore_blocked_title))
    RestoreBody(
        text = when (reason) {
            ProductionRestoreTargetBlockReason.TargetNotEmpty ->
                stringResource(R.string.restore_blocked_not_empty_body)
            ProductionRestoreTargetBlockReason.TargetUnsafe ->
                stringResource(R.string.restore_blocked_unsafe_body)
        },
        modifier = Modifier.testTag(ProductionRestoreTestTags.RESTORE_ERROR),
    )
    Spacer(modifier = Modifier.height(24.dp))
    PracticePrimaryButton(
        text = stringResource(R.string.restore_close),
        onClick = onDismiss,
        enabled = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorContent(
    error: ProductionRestoreError,
    onChooseFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title: String
    val body: String
    val primaryText: String
    val primaryAction: () -> Unit
    val secondaryText: String?
    val secondaryAction: (() -> Unit)?

    when (error) {
        ProductionRestoreError.NoBackupFound -> {
            title = stringResource(R.string.restore_error_no_backup_title)
            body = stringResource(R.string.restore_error_no_backup_body)
            primaryText = stringResource(R.string.restore_choose_other_folder)
            primaryAction = onChooseFolder
            secondaryText = stringResource(R.string.restore_close)
            secondaryAction = onDismiss
        }
        ProductionRestoreError.InvalidBackup -> {
            title = stringResource(R.string.restore_error_invalid_title)
            body = stringResource(R.string.restore_error_invalid_body)
            primaryText = stringResource(R.string.restore_close)
            primaryAction = onDismiss
            secondaryText = null
            secondaryAction = null
        }
        ProductionRestoreError.IncompatibleBackup -> {
            title = stringResource(R.string.restore_error_incompatible_title)
            body = stringResource(R.string.restore_error_incompatible_body)
            primaryText = stringResource(R.string.restore_close)
            primaryAction = onDismiss
            secondaryText = null
            secondaryAction = null
        }
        ProductionRestoreError.PermissionLost -> {
            title = stringResource(R.string.restore_error_permission_title)
            body = stringResource(R.string.restore_error_permission_body)
            primaryText = stringResource(R.string.restore_choose_folder_again)
            primaryAction = onChooseFolder
            secondaryText = stringResource(R.string.restore_close)
            secondaryAction = onDismiss
        }
        ProductionRestoreError.ReadFailure -> {
            title = stringResource(R.string.restore_error_read_title)
            body = stringResource(R.string.restore_error_read_body)
            primaryText = stringResource(R.string.restore_choose_other_folder)
            primaryAction = onChooseFolder
            secondaryText = stringResource(R.string.restore_close)
            secondaryAction = onDismiss
        }
        ProductionRestoreError.ActiveFolderSaveFailure -> {
            title = stringResource(R.string.restore_error_active_folder_title)
            body = stringResource(R.string.restore_error_active_folder_body)
            primaryText = stringResource(R.string.restore_choose_folder_again)
            primaryAction = onChooseFolder
            secondaryText = stringResource(R.string.restore_close)
            secondaryAction = onDismiss
        }
        ProductionRestoreError.RestoreWriteFailure -> {
            title = stringResource(R.string.restore_error_restore_write_title)
            body = stringResource(R.string.restore_error_restore_write_body)
            primaryText = stringResource(R.string.restore_close)
            primaryAction = onDismiss
            secondaryText = null
            secondaryAction = null
        }
        ProductionRestoreError.Unexpected -> {
            title = stringResource(R.string.restore_error_unexpected_title)
            body = stringResource(R.string.restore_error_unexpected_body)
            primaryText = stringResource(R.string.restore_choose_other_folder)
            primaryAction = onChooseFolder
            secondaryText = stringResource(R.string.restore_close)
            secondaryAction = onDismiss
        }
        ProductionRestoreError.UnsafeTarget,
        ProductionRestoreError.TargetNotEmpty,
        -> {
            title = stringResource(R.string.restore_blocked_title)
            body = stringResource(R.string.restore_blocked_unsafe_body)
            primaryText = stringResource(R.string.restore_close)
            primaryAction = onDismiss
            secondaryText = null
            secondaryAction = null
        }
    }

    RestoreTitle(text = title)
    RestoreBody(
        text = body,
        modifier = Modifier.testTag(ProductionRestoreTestTags.RESTORE_ERROR),
    )
    Spacer(modifier = Modifier.height(24.dp))
    PracticePrimaryButton(
        text = primaryText,
        onClick = primaryAction,
        enabled = true,
        modifier = Modifier.fillMaxWidth(),
    )
    secondaryText?.let { text ->
        secondaryAction?.let { action ->
            TextButton(
                onClick = action,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(
                    text = text,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PreviewContent(
    preview: ProductionRestorePreviewUiModel,
    staleNotice: Boolean,
    onRestoreConfirmed: () -> Unit,
    onChooseFolder: () -> Unit,
) {
    RestoreTitle(
        text = stringResource(R.string.restore_preview_title, preview.createdAtText),
        modifier = Modifier.testTag(ProductionRestoreTestTags.RESTORE_PREVIEW_DATE),
    )
    if (staleNotice) {
        PracticeSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .testTag(ProductionRestoreTestTags.RESTORE_STALE_NOTICE),
        ) {
            Text(
                text = stringResource(R.string.restore_stale_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
            )
        }
    }
    PracticeSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.restore_preview_answers, preview.answerCount),
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                modifier = Modifier.testTag(ProductionRestoreTestTags.RESTORE_PREVIEW_ANSWERS),
            )
            Text(
                text = stringResource(R.string.restore_preview_completed, preview.completedCount),
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag(ProductionRestoreTestTags.RESTORE_PREVIEW_COMPLETED),
            )
            Text(
                text = if (preview.practiceStarted) {
                    stringResource(R.string.restore_preview_practice_started)
                } else {
                    stringResource(R.string.restore_preview_practice_not_started)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag(ProductionRestoreTestTags.RESTORE_PREVIEW_STATE),
            )
            if (preview.scheduleWillBeRestored) {
                Text(
                    text = stringResource(R.string.restore_preview_schedule),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag(ProductionRestoreTestTags.RESTORE_PREVIEW_SCHEDULE),
                )
            }
            if (preview.deletedTextCount > 0) {
                Text(
                    text = stringResource(
                        R.string.restore_preview_deleted_text,
                        preview.deletedTextCount,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag(ProductionRestoreTestTags.RESTORE_PREVIEW_DELETED_TEXT),
                )
            }
        }
    }
    Text(
        text = stringResource(R.string.restore_preview_folder_note),
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
    Spacer(modifier = Modifier.height(24.dp))
    PracticePrimaryButton(
        text = stringResource(R.string.restore_confirm),
        onClick = onRestoreConfirmed,
        enabled = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ProductionRestoreTestTags.RESTORE_CONFIRM),
    )
    TextButton(
        onClick = onChooseFolder,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag(ProductionRestoreTestTags.RESTORE_CHOOSE_FOLDER),
    ) {
        Text(
            text = stringResource(R.string.restore_choose_other_folder),
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TerminalBusyContent(
    title: String,
    body: String,
) {
    RestoreTitle(text = title)
    RestoreBody(text = body)
}

@Composable
private fun TerminalAckContent(
    title: String,
    body: String,
    buttonText: String,
    onAcknowledge: () -> Unit,
) {
    RestoreTitle(text = title)
    RestoreBody(text = body)
    Spacer(modifier = Modifier.height(24.dp))
    PracticePrimaryButton(
        text = buttonText,
        onClick = onAcknowledge,
        enabled = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ProductionRestoreTestTags.RESTORE_CONTINUE),
    )
}

@Composable
private fun RestoreTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = TitleSerifStyle,
        color = TextPrimary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}

@Composable
private fun RestoreBody(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = TextSecondary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END

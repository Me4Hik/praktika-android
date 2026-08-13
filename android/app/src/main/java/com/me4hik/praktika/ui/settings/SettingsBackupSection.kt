// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3B Settings backup section
package com.me4hik.praktika.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.R
import com.me4hik.praktika.data.backup.settings.BackupSettingsOperationalState
import com.me4hik.praktika.data.backup.setup.SetupCandidateClassification
import com.me4hik.praktika.ui.components.PracticeGlassActionRow
import com.me4hik.praktika.ui.components.PracticeSectionHeader
import com.me4hik.praktika.ui.components.PracticeSurface
import com.me4hik.praktika.ui.theme.TextMuted
import com.me4hik.praktika.ui.theme.TextPrimary
import com.me4hik.praktika.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsBackupSection(
    backup: BackupSettingsUiState,
    onSetup: () -> Unit,
    onBackupNow: () -> Unit,
    onChangeFolder: () -> Unit,
    onReconnect: () -> Unit,
    onDisable: () -> Unit,
) {
    val actionsEnabled = backup.backupActionsEnabled
    val showProgress = backup.operation == BackupSettingsOperation.Inspecting ||
        backup.operation == BackupSettingsOperation.Configuring ||
        backup.operation == BackupSettingsOperation.Reconnecting ||
        backup.operation == BackupSettingsOperation.BackingUpNow ||
        backup.operation == BackupSettingsOperation.Disabling ||
        backup.operation == BackupSettingsOperation.Abandoning ||
        backup.operation == BackupSettingsOperation.AwaitingPicker

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SettingsTestTags.SETTINGS_BACKUP_SECTION),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PracticeSectionHeader(
            title = stringResource(R.string.settings_backup_section),
            icon = Icons.Outlined.Folder,
        )
        PracticeSurface {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = backupStatusText(backup),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BACKUP_STATUS),
                )
                backupLastSuccessMillis(backup)?.let { millis ->
                    Text(
                        text = stringResource(
                            R.string.settings_backup_last_success,
                            formatBackupLastSuccess(millis),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BACKUP_LAST_SUCCESS),
                    )
                }
                when (val status = backup.operationalStatus) {
                    BackupSettingsOperationalState.NotConfigured -> {
                        PracticeGlassActionRow(
                            title = stringResource(R.string.settings_backup_setup),
                            icon = Icons.Outlined.Folder,
                            onClick = onSetup,
                            enabled = actionsEnabled,
                            showChevron = false,
                            modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BACKUP_PRIMARY),
                        )
                    }
                    is BackupSettingsOperationalState.NeedsReconnect -> {
                        PracticeGlassActionRow(
                            title = stringResource(R.string.settings_backup_reconnect),
                            icon = Icons.Outlined.Folder,
                            onClick = onReconnect,
                            enabled = actionsEnabled,
                            showChevron = false,
                            modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BACKUP_PRIMARY),
                        )
                        PracticeGlassActionRow(
                            title = stringResource(R.string.settings_backup_change_folder),
                            icon = Icons.Outlined.Folder,
                            onClick = onChangeFolder,
                            enabled = actionsEnabled,
                            showChevron = false,
                            modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BACKUP_CHANGE_FOLDER),
                        )
                        PracticeGlassActionRow(
                            title = stringResource(R.string.settings_backup_disable),
                            icon = Icons.Outlined.Folder,
                            onClick = onDisable,
                            enabled = actionsEnabled,
                            showChevron = false,
                            modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BACKUP_DISABLE),
                        )
                    }
                    is BackupSettingsOperationalState.NeedsAttention -> {
                        PracticeGlassActionRow(
                            title = stringResource(R.string.settings_backup_change_folder),
                            icon = Icons.Outlined.Folder,
                            onClick = onChangeFolder,
                            enabled = actionsEnabled,
                            showChevron = false,
                            modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BACKUP_PRIMARY),
                        )
                        PracticeGlassActionRow(
                            title = stringResource(R.string.settings_backup_now),
                            icon = Icons.Outlined.Folder,
                            onClick = onBackupNow,
                            enabled = actionsEnabled,
                            showChevron = false,
                        )
                        PracticeGlassActionRow(
                            title = stringResource(R.string.settings_backup_disable),
                            icon = Icons.Outlined.Folder,
                            onClick = onDisable,
                            enabled = actionsEnabled,
                            showChevron = false,
                            modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BACKUP_DISABLE),
                        )
                    }
                    is BackupSettingsOperationalState.DataProblem -> {
                        PracticeGlassActionRow(
                            title = stringResource(R.string.settings_backup_now),
                            icon = Icons.Outlined.Folder,
                            onClick = onBackupNow,
                            enabled = actionsEnabled,
                            showChevron = false,
                            modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BACKUP_PRIMARY),
                        )
                        PracticeGlassActionRow(
                            title = stringResource(R.string.settings_backup_disable),
                            icon = Icons.Outlined.Folder,
                            onClick = onDisable,
                            enabled = actionsEnabled,
                            showChevron = false,
                            modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BACKUP_DISABLE),
                        )
                    }
                    else -> {
                        // Healthy / ConfiguredNoSuccessCache / TransientFailure
                        PracticeGlassActionRow(
                            title = stringResource(R.string.settings_backup_now),
                            icon = Icons.Outlined.Folder,
                            onClick = onBackupNow,
                            enabled = actionsEnabled && SettingsBackupSession.isBackupNowAvailable(status),
                            showChevron = false,
                            modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BACKUP_PRIMARY),
                        )
                        PracticeGlassActionRow(
                            title = stringResource(R.string.settings_backup_change_folder),
                            icon = Icons.Outlined.Folder,
                            onClick = onChangeFolder,
                            enabled = actionsEnabled,
                            showChevron = false,
                            modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BACKUP_CHANGE_FOLDER),
                        )
                        PracticeGlassActionRow(
                            title = stringResource(R.string.settings_backup_disable),
                            icon = Icons.Outlined.Folder,
                            onClick = onDisable,
                            enabled = actionsEnabled,
                            showChevron = false,
                            modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BACKUP_DISABLE),
                        )
                    }
                }
                if (showProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .testTag(SettingsTestTags.SETTINGS_BACKUP_PROGRESS),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsBackupDialogs(
    backup: BackupSettingsUiState,
    onDisclosureConfirm: () -> Unit,
    onDisclosureCancel: () -> Unit,
    onCandidateConfirm: () -> Unit,
    onCandidateCancel: () -> Unit,
    onChooseAnother: () -> Unit,
    onCommitRetry: () -> Unit,
    onDisableConfirm: () -> Unit,
    onDisableCancel: () -> Unit,
    onReconnectDifferentUseAsNew: () -> Unit,
    onReconnectDifferentCancel: () -> Unit,
) {
    when (backup.operation) {
        BackupSettingsOperation.AwaitingDisclosureSetup,
        BackupSettingsOperation.AwaitingDisclosureReplace,
        -> {
            AlertDialog(
                onDismissRequest = onDisclosureCancel,
                modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BACKUP_DISCLOSURE),
                title = { Text(text = stringResource(R.string.settings_backup_disclosure_title)) },
                text = { Text(text = stringResource(R.string.settings_backup_disclosure_body)) },
                confirmButton = {
                    TextButton(onClick = onDisclosureConfirm) {
                        Text(text = stringResource(R.string.settings_backup_continue))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDisclosureCancel) {
                        Text(text = stringResource(R.string.settings_backup_cancel))
                    }
                },
            )
        }
        BackupSettingsOperation.AwaitingDisableConfirmation -> {
            AlertDialog(
                onDismissRequest = onDisableCancel,
                modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BACKUP_DISABLE_DIALOG),
                text = { Text(text = stringResource(R.string.settings_backup_disable_body)) },
                confirmButton = {
                    TextButton(onClick = onDisableConfirm) {
                        Text(text = stringResource(R.string.settings_backup_disable_confirm_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDisableCancel) {
                        Text(text = stringResource(R.string.settings_backup_cancel))
                    }
                },
            )
        }
        BackupSettingsOperation.AwaitingReconnectDifferentFolder -> {
            AlertDialog(
                onDismissRequest = onReconnectDifferentCancel,
                modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BACKUP_RECONNECT_DIFFERENT),
                text = { Text(text = stringResource(R.string.settings_backup_reconnect_different_body)) },
                confirmButton = {
                    TextButton(onClick = onReconnectDifferentUseAsNew) {
                        Text(text = stringResource(R.string.settings_backup_use_as_new_folder))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onReconnectDifferentCancel) {
                        Text(text = stringResource(R.string.settings_backup_cancel))
                    }
                },
            )
        }
        BackupSettingsOperation.AwaitingCommitRetry -> {
            AlertDialog(
                onDismissRequest = onCandidateCancel,
                modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BACKUP_COMMIT_RETRY),
                text = { Text(text = stringResource(R.string.settings_backup_commit_retry_body)) },
                confirmButton = {
                    TextButton(onClick = onCommitRetry) {
                        Text(text = stringResource(R.string.settings_backup_retry))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onCandidateCancel) {
                        Text(text = stringResource(R.string.settings_backup_cancel))
                    }
                },
            )
        }
        BackupSettingsOperation.AwaitingCandidateConfirmation -> {
            when (val pending = backup.pendingConfirmation) {
                is BackupPendingConfirmation.CandidateWritable -> {
                    AlertDialog(
                        onDismissRequest = onCandidateCancel,
                        modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BACKUP_CANDIDATE),
                        text = { Text(text = candidateWritableText(pending.classification)) },
                        confirmButton = {
                            TextButton(onClick = onCandidateConfirm) {
                                Text(text = stringResource(R.string.settings_backup_use_folder))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = onCandidateCancel) {
                                Text(text = stringResource(R.string.settings_backup_cancel))
                            }
                        },
                    )
                }
                is BackupPendingConfirmation.CandidateBlocked -> {
                    AlertDialog(
                        onDismissRequest = onCandidateCancel,
                        modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BACKUP_CANDIDATE),
                        text = { Text(text = stringResource(R.string.settings_backup_confirm_blocked)) },
                        confirmButton = {
                            TextButton(onClick = onChooseAnother) {
                                Text(text = stringResource(R.string.settings_backup_choose_another))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = onCandidateCancel) {
                                Text(text = stringResource(R.string.settings_backup_cancel))
                            }
                        },
                    )
                }
                BackupPendingConfirmation.UnsafeDatabase -> {
                    AlertDialog(
                        onDismissRequest = onCandidateCancel,
                        modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BACKUP_CANDIDATE),
                        text = { Text(text = stringResource(R.string.settings_backup_confirm_unsafe_database)) },
                        confirmButton = {
                            TextButton(onClick = onCandidateCancel) {
                                Text(text = stringResource(R.string.settings_backup_close))
                            }
                        },
                    )
                }
                else -> Unit
            }
        }
        else -> Unit
    }
}

@Composable
private fun backupStatusText(backup: BackupSettingsUiState): String {
    if (backup.statusUnavailable) {
        return stringResource(R.string.settings_backup_status_unavailable)
    }
    return when (backup.operationalStatus) {
        BackupSettingsOperationalState.NotConfigured ->
            stringResource(R.string.settings_backup_status_not_configured)
        is BackupSettingsOperationalState.Healthy ->
            stringResource(R.string.settings_backup_status_healthy)
        BackupSettingsOperationalState.ConfiguredNoSuccessCache ->
            stringResource(R.string.settings_backup_status_configured_no_cache)
        is BackupSettingsOperationalState.NeedsReconnect ->
            stringResource(R.string.settings_backup_status_needs_reconnect)
        is BackupSettingsOperationalState.TransientFailure ->
            stringResource(R.string.settings_backup_status_transient)
        is BackupSettingsOperationalState.NeedsAttention ->
            stringResource(R.string.settings_backup_status_needs_attention)
        is BackupSettingsOperationalState.DataProblem ->
            stringResource(R.string.settings_backup_status_data_problem)
    }
}

private fun backupLastSuccessMillis(backup: BackupSettingsUiState): Long? {
    return when (val status = backup.operationalStatus) {
        is BackupSettingsOperationalState.Healthy -> status.lastSuccessAtEpochMillis
        is BackupSettingsOperationalState.NeedsReconnect -> status.lastSuccessAtEpochMillis
        is BackupSettingsOperationalState.TransientFailure -> status.lastSuccessAtEpochMillis
        is BackupSettingsOperationalState.NeedsAttention -> status.lastSuccessAtEpochMillis
        is BackupSettingsOperationalState.DataProblem -> status.lastSuccessAtEpochMillis
        else -> null
    }
}

private fun formatBackupLastSuccess(epochMillis: Long): String {
    val formatter = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}

@Composable
private fun candidateWritableText(classification: SetupCandidateClassification): String {
    return when (classification) {
        SetupCandidateClassification.VALID_EQUAL ->
            stringResource(R.string.settings_backup_confirm_equal)
        SetupCandidateClassification.VALID_DIFFERENT ->
            stringResource(R.string.settings_backup_confirm_different)
        SetupCandidateClassification.INVALID_PLUS_MISSING ->
            stringResource(R.string.settings_backup_confirm_invalid_plus_missing)
        else -> stringResource(R.string.settings_backup_confirm_equal)
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END

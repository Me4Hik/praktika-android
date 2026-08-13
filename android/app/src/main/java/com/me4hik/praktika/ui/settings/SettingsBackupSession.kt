// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3B Settings backup session
package com.me4hik.praktika.ui.settings

import android.net.Uri
import com.me4hik.praktika.R
import com.me4hik.praktika.data.backup.settings.BackupNowPhysicalResult
import com.me4hik.praktika.data.backup.settings.BackupNowResult
import com.me4hik.praktika.data.backup.settings.BackupReconnectResult
import com.me4hik.praktika.data.backup.settings.BackupSettingsAbandonResult
import com.me4hik.praktika.data.backup.settings.BackupSettingsDisableResult
import com.me4hik.praktika.data.backup.settings.BackupSettingsOperationalState
import com.me4hik.praktika.data.backup.settings.BackupSettingsSetupCommitResult
import com.me4hik.praktika.data.backup.settings.BackupSettingsSetupInspectResult
import com.me4hik.praktika.data.backup.settings.BackupSettingsSetupVerifyResult
import com.me4hik.praktika.data.backup.setup.SetupCandidateClassification
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Process ephemeral backup UI session for Settings. Owns picker mode and setup intent;
 * never stores URI/token/receipt in published state.
 */
internal class SettingsBackupSession(
    private val facade: BackupSettingsActions,
    private val scope: CoroutineScope,
    private val publish: () -> Unit,
    private val emitSnackbar: suspend (Int) -> Unit,
    private val effects: MutableSharedFlow<SettingsBackupEffect>,
) {
    @Volatile
    var uiState: BackupSettingsUiState = BackupSettingsUiState()
        private set

    private var pendingPickerMode: BackupPickerMode? = null
    private var setupIntent: BackupSetupIntent = BackupSetupIntent.SETUP

    fun startObserving() {
        scope.launch {
            facade.observeOperationalStatus()
                .catch {
                    update { copy(statusUnavailable = true) }
                }
                .collect { status ->
                    update {
                        copy(
                            operationalStatus = status,
                            statusUnavailable = false,
                        )
                    }
                }
        }
    }

    fun onCleared() {
        facade.requestAbandonSetupSession()
    }

    fun onSetupRequested() {
        if (!uiState.backupActionsEnabled) return
        setupIntent = BackupSetupIntent.SETUP
        update { copy(operation = BackupSettingsOperation.AwaitingDisclosureSetup, pendingConfirmation = null) }
    }

    fun onReplacementRequested() {
        if (!uiState.backupActionsEnabled) return
        if (uiState.operationalStatus is BackupSettingsOperationalState.NotConfigured) return
        setupIntent = BackupSetupIntent.REPLACE
        update { copy(operation = BackupSettingsOperation.AwaitingDisclosureReplace, pendingConfirmation = null) }
    }

    fun onDisclosureConfirmed() {
        val mode = when (uiState.operation) {
            BackupSettingsOperation.AwaitingDisclosureSetup -> BackupPickerMode.SETUP
            BackupSettingsOperation.AwaitingDisclosureReplace -> BackupPickerMode.REPLACE
            else -> return
        }
        if (pendingPickerMode != null) return
        pendingPickerMode = mode
        update { copy(operation = BackupSettingsOperation.AwaitingPicker, pendingConfirmation = null) }
        effects.tryEmit(SettingsBackupEffect.LaunchFolderPicker(mode))
    }

    fun onDisclosureCancelled() {
        if (uiState.operation != BackupSettingsOperation.AwaitingDisclosureSetup &&
            uiState.operation != BackupSettingsOperation.AwaitingDisclosureReplace
        ) {
            return
        }
        update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
    }

    fun onReconnectRequested() {
        if (!uiState.backupActionsEnabled) return
        if (uiState.operationalStatus !is BackupSettingsOperationalState.NeedsReconnect) return
        if (pendingPickerMode != null) return
        pendingPickerMode = BackupPickerMode.RECONNECT
        update { copy(operation = BackupSettingsOperation.AwaitingPicker, pendingConfirmation = null) }
        effects.tryEmit(SettingsBackupEffect.LaunchFolderPicker(BackupPickerMode.RECONNECT))
    }

    fun onFolderPickerResult(uri: Uri?, grantFlags: Int) {
        val mode = pendingPickerMode
        pendingPickerMode = null
        if (mode == null) {
            update { copy(operation = BackupSettingsOperation.Idle) }
            return
        }
        if (uri == null) {
            update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
            return
        }
        when (mode) {
            BackupPickerMode.SETUP, BackupPickerMode.REPLACE -> {
                setupIntent = if (mode == BackupPickerMode.SETUP) {
                    BackupSetupIntent.SETUP
                } else {
                    BackupSetupIntent.REPLACE
                }
                scope.launch { inspectCandidate(uri.toString(), grantFlags) }
            }
            BackupPickerMode.RECONNECT -> {
                scope.launch { reconnect(uri.toString(), grantFlags) }
            }
        }
    }

    fun onCandidateConfirmed() {
        val pending = uiState.pendingConfirmation as? BackupPendingConfirmation.CandidateWritable ?: return
        if (uiState.operation != BackupSettingsOperation.AwaitingCandidateConfirmation) return
        scope.launch { verifyAndMaybeCommit() }
    }

    fun onCandidateCancelled() {
        if (!isQuiescentSessionOp()) return
        scope.launch { awaitAbandonToIdle() }
    }

    fun onChooseAnotherFolder() {
        val pending = uiState.pendingConfirmation
        if (pending !is BackupPendingConfirmation.CandidateBlocked &&
            pending !is BackupPendingConfirmation.UnsafeDatabase &&
            pending !is BackupPendingConfirmation.CandidateWritable
        ) {
            return
        }
        if (uiState.operation != BackupSettingsOperation.AwaitingCandidateConfirmation) return
        val restoreOp = uiState.operation
        val restorePending = uiState.pendingConfirmation
        scope.launch {
            when (awaitAbandon()) {
                BackupSettingsAbandonResult.Success -> {
                    val disclosure = when (setupIntent) {
                        BackupSetupIntent.SETUP -> BackupSettingsOperation.AwaitingDisclosureSetup
                        BackupSetupIntent.REPLACE -> BackupSettingsOperation.AwaitingDisclosureReplace
                    }
                    update {
                        copy(
                            operation = disclosure,
                            pendingConfirmation = null,
                        )
                    }
                }
                BackupSettingsAbandonResult.Busy -> {
                    update {
                        copy(
                            operation = restoreOp,
                            pendingConfirmation = restorePending,
                        )
                    }
                    emitSnackbar(R.string.settings_backup_snackbar_abandon_busy)
                }
            }
        }
    }

    fun onCommitRetry() {
        if (uiState.operation != BackupSettingsOperation.AwaitingCommitRetry) return
        scope.launch { commitOnly() }
    }

    fun onBackupNowRequested() {
        if (!uiState.backupActionsEnabled) return
        if (!isBackupNowAvailable(uiState.operationalStatus)) return
        scope.launch { runBackupNow() }
    }

    fun onDisableRequested() {
        if (!uiState.backupActionsEnabled) return
        if (uiState.operationalStatus is BackupSettingsOperationalState.NotConfigured) return
        update {
            copy(
                operation = BackupSettingsOperation.AwaitingDisableConfirmation,
                pendingConfirmation = BackupPendingConfirmation.Disable,
            )
        }
    }

    fun onDisableConfirmed() {
        if (uiState.operation != BackupSettingsOperation.AwaitingDisableConfirmation) return
        scope.launch { disable() }
    }

    fun onDisableCancelled() {
        if (uiState.operation != BackupSettingsOperation.AwaitingDisableConfirmation) return
        update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
    }

    fun onReconnectDifferentUseAsNew() {
        if (uiState.operation != BackupSettingsOperation.AwaitingReconnectDifferentFolder) return
        setupIntent = BackupSetupIntent.REPLACE
        update {
            copy(
                operation = BackupSettingsOperation.AwaitingDisclosureReplace,
                pendingConfirmation = null,
            )
        }
    }

    fun onReconnectDifferentCancelled() {
        if (uiState.operation != BackupSettingsOperation.AwaitingReconnectDifferentFolder) return
        update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
    }

    /** @return true if Back was consumed by backup modal/session. */
    fun onBackRequested(): Boolean {
        return when (uiState.operation) {
            BackupSettingsOperation.AwaitingDisclosureSetup,
            BackupSettingsOperation.AwaitingDisclosureReplace,
            -> {
                update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
                true
            }
            BackupSettingsOperation.AwaitingDisableConfirmation -> {
                update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
                true
            }
            BackupSettingsOperation.AwaitingReconnectDifferentFolder -> {
                update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
                true
            }
            BackupSettingsOperation.AwaitingCandidateConfirmation,
            BackupSettingsOperation.AwaitingCommitRetry,
            -> {
                scope.launch { awaitAbandonToIdle() }
                true
            }
            BackupSettingsOperation.AwaitingPicker,
            BackupSettingsOperation.Inspecting,
            BackupSettingsOperation.Configuring,
            BackupSettingsOperation.Reconnecting,
            BackupSettingsOperation.BackingUpNow,
            BackupSettingsOperation.Disabling,
            BackupSettingsOperation.Abandoning,
            -> true
            BackupSettingsOperation.Idle -> false
        }
    }

    private fun isQuiescentSessionOp(): Boolean {
        return uiState.operation == BackupSettingsOperation.AwaitingCandidateConfirmation ||
            uiState.operation == BackupSettingsOperation.AwaitingCommitRetry
    }

    private suspend fun inspectCandidate(uriString: String, grantFlags: Int) {
        update { copy(operation = BackupSettingsOperation.Inspecting, pendingConfirmation = null) }
        val result = try {
            facade.inspectCandidate(uriString, grantFlags)
        } catch (cancel: CancellationException) {
            update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
            throw cancel
        } catch (_: Exception) {
            emitSnackbar(replaceAwareFailureMessage())
            update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
            return
        }
        when (result) {
            is BackupSettingsSetupInspectResult.Ready -> handleReady(result.classification)
            BackupSettingsSetupInspectResult.PermissionLost -> {
                emitSnackbar(R.string.settings_backup_snackbar_inspect_permission)
                update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
            }
            BackupSettingsSetupInspectResult.Unavailable -> {
                emitSnackbar(R.string.settings_backup_snackbar_inspect_unavailable)
                update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
            }
            BackupSettingsSetupInspectResult.ProviderFailure -> {
                emitSnackbar(R.string.settings_backup_snackbar_inspect_provider)
                update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
            }
            BackupSettingsSetupInspectResult.Busy -> {
                emitSnackbar(R.string.settings_backup_snackbar_busy)
                update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
            }
        }
    }

    private fun replaceAwareFailureMessage(): Int {
        if (setupIntent != BackupSetupIntent.REPLACE) {
            return R.string.settings_backup_snackbar_setup_failed
        }
        return if (SettingsBackupSession.isConfigured(uiState.operationalStatus)) {
            R.string.settings_backup_snackbar_replace_failed_still_active
        } else {
            R.string.settings_backup_snackbar_replace_failed
        }
    }

    private suspend fun handleReady(classification: SetupCandidateClassification) {
        when {
            classification == SetupCandidateClassification.EMPTY -> verifyAndMaybeCommit()
            classification == SetupCandidateClassification.UNSAFE_DATABASE -> {
                update {
                    copy(
                        operation = BackupSettingsOperation.AwaitingCandidateConfirmation,
                        pendingConfirmation = BackupPendingConfirmation.UnsafeDatabase,
                    )
                }
            }
            classification.isWritableCandidate() -> {
                update {
                    copy(
                        operation = BackupSettingsOperation.AwaitingCandidateConfirmation,
                        pendingConfirmation = BackupPendingConfirmation.CandidateWritable(classification),
                    )
                }
            }
            else -> {
                update {
                    copy(
                        operation = BackupSettingsOperation.AwaitingCandidateConfirmation,
                        pendingConfirmation = BackupPendingConfirmation.CandidateBlocked(classification),
                    )
                }
            }
        }
    }

    private suspend fun verifyAndMaybeCommit() {
        update { copy(operation = BackupSettingsOperation.Configuring, pendingConfirmation = null) }
        val result = try {
            facade.verifyCurrentCandidate()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            emitSnackbar(replaceAwareFailureMessage())
            update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
            return
        }
        when (result) {
            BackupSettingsSetupVerifyResult.Verified -> commitOnly()
            is BackupSettingsSetupVerifyResult.CandidateStale -> handleReady(result.classification)
            is BackupSettingsSetupVerifyResult.Blocked -> {
                update {
                    copy(
                        operation = BackupSettingsOperation.AwaitingCandidateConfirmation,
                        pendingConfirmation = if (result.classification == SetupCandidateClassification.UNSAFE_DATABASE) {
                            BackupPendingConfirmation.UnsafeDatabase
                        } else {
                            BackupPendingConfirmation.CandidateBlocked(result.classification)
                        },
                    )
                }
            }
            BackupSettingsSetupVerifyResult.WriteFailed -> {
                emitSnackbar(R.string.settings_backup_snackbar_write_failed)
                update {
                    copy(
                        operation = BackupSettingsOperation.AwaitingCandidateConfirmation,
                        pendingConfirmation = BackupPendingConfirmation.CandidateBlocked(
                            SetupCandidateClassification.UNREADABLE,
                        ),
                    )
                }
            }
            BackupSettingsSetupVerifyResult.ExportFailed -> {
                emitSnackbar(R.string.settings_backup_snackbar_export_failed)
                update {
                    copy(
                        operation = BackupSettingsOperation.AwaitingCandidateConfirmation,
                        pendingConfirmation = BackupPendingConfirmation.UnsafeDatabase,
                    )
                }
            }
            BackupSettingsSetupVerifyResult.UnsafeDatabase -> {
                update {
                    copy(
                        operation = BackupSettingsOperation.AwaitingCandidateConfirmation,
                        pendingConfirmation = BackupPendingConfirmation.UnsafeDatabase,
                    )
                }
            }
            BackupSettingsSetupVerifyResult.NoSession -> {
                update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
            }
            BackupSettingsSetupVerifyResult.Busy -> {
                emitSnackbar(R.string.settings_backup_snackbar_busy)
                update {
                    copy(
                        operation = BackupSettingsOperation.AwaitingCommitRetry,
                        pendingConfirmation = BackupPendingConfirmation.CommitRetry,
                    )
                }
            }
        }
    }

    private suspend fun commitOnly() {
        update {
            copy(
                operation = BackupSettingsOperation.Configuring,
                pendingConfirmation = null,
            )
        }
        val result = try {
            facade.commitVerifiedCandidate()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            emitSnackbar(replaceAwareFailureMessage())
            update {
                copy(
                    operation = BackupSettingsOperation.AwaitingCommitRetry,
                    pendingConfirmation = BackupPendingConfirmation.CommitRetry,
                )
            }
            return
        }
        when (result) {
            is BackupSettingsSetupCommitResult.Success -> {
                val msg = when (setupIntent) {
                    BackupSetupIntent.SETUP -> R.string.settings_backup_snackbar_enabled
                    BackupSetupIntent.REPLACE -> R.string.settings_backup_snackbar_folder_changed
                }
                emitSnackbar(msg)
                update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
            }
            BackupSettingsSetupCommitResult.CommitFailed,
            BackupSettingsSetupCommitResult.Busy,
            -> {
                update {
                    copy(
                        operation = BackupSettingsOperation.AwaitingCommitRetry,
                        pendingConfirmation = BackupPendingConfirmation.CommitRetry,
                    )
                }
            }
            BackupSettingsSetupCommitResult.ReceiptStale,
            BackupSettingsSetupCommitResult.ReceiptMissing,
            -> verifyAndMaybeCommit()
            BackupSettingsSetupCommitResult.InvalidConfiguration -> {
                when (awaitAbandon()) {
                    BackupSettingsAbandonResult.Success -> {
                        emitSnackbar(replaceAwareFailureMessage())
                        update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
                    }
                    BackupSettingsAbandonResult.Busy -> {
                        emitSnackbar(R.string.settings_backup_snackbar_abandon_busy)
                    }
                }
            }
        }
    }

    private suspend fun reconnect(uriString: String, grantFlags: Int) {
        update { copy(operation = BackupSettingsOperation.Reconnecting, pendingConfirmation = null) }
        val result = try {
            facade.reconnectFolderAccess(uriString, grantFlags)
        } catch (cancel: CancellationException) {
            update { copy(operation = BackupSettingsOperation.Idle) }
            throw cancel
        } catch (_: Exception) {
            emitSnackbar(R.string.settings_backup_snackbar_permission_failure)
            update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
            return
        }
        when (result) {
            BackupReconnectResult.Success -> {
                emitSnackbar(R.string.settings_backup_snackbar_reconnect_success)
                update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
            }
            BackupReconnectResult.DifferentFolder -> {
                update {
                    copy(
                        operation = BackupSettingsOperation.AwaitingReconnectDifferentFolder,
                        pendingConfirmation = BackupPendingConfirmation.ReconnectDifferentFolder,
                    )
                }
            }
            BackupReconnectResult.NotReconnectRequired -> {
                update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
            }
            BackupReconnectResult.NotConfigured -> {
                update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
            }
            BackupReconnectResult.StaleConfiguration -> {
                emitSnackbar(R.string.settings_backup_snackbar_stale_configuration)
                update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
            }
            BackupReconnectResult.PermissionFailure -> {
                emitSnackbar(R.string.settings_backup_snackbar_permission_failure)
                update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
            }
            BackupReconnectResult.ValidationFailure -> {
                emitSnackbar(R.string.settings_backup_snackbar_validation_failure)
                update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
            }
        }
    }

    private suspend fun runBackupNow() {
        update { copy(operation = BackupSettingsOperation.BackingUpNow, pendingConfirmation = null) }
        val result = try {
            facade.runBackupNow()
        } catch (cancel: CancellationException) {
            update { copy(operation = BackupSettingsOperation.Idle) }
            throw cancel
        } catch (_: Exception) {
            emitSnackbar(R.string.settings_backup_snackbar_transient)
            update { copy(operation = BackupSettingsOperation.Idle) }
            return
        }
        val msg = when (result) {
            BackupNowResult.Written -> R.string.settings_backup_snackbar_created
            BackupNowResult.NoChange -> R.string.settings_backup_snackbar_already_current
            BackupNowResult.NotConfigured -> R.string.settings_backup_snackbar_not_configured
            BackupNowResult.NeedsReconnect -> R.string.settings_backup_snackbar_needs_reconnect
            BackupNowResult.TransientFailure -> R.string.settings_backup_snackbar_transient
            BackupNowResult.NeedsAttention -> R.string.settings_backup_snackbar_needs_attention
            BackupNowResult.DataProblem -> R.string.settings_backup_snackbar_data_problem
            is BackupNowResult.StatusTrackingIncomplete -> when (result.physicalResult) {
                BackupNowPhysicalResult.WRITTEN -> R.string.settings_backup_snackbar_tracking_written
                BackupNowPhysicalResult.NO_CHANGE -> R.string.settings_backup_snackbar_tracking_nochange
            }
            BackupNowResult.TemporarilyUnavailable -> R.string.settings_backup_snackbar_temporarily_unavailable
        }
        emitSnackbar(msg)
        update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
    }

    private suspend fun disable() {
        update { copy(operation = BackupSettingsOperation.Disabling, pendingConfirmation = null) }
        val result = try {
            facade.disableAutomaticBackup()
        } catch (cancel: CancellationException) {
            update { copy(operation = BackupSettingsOperation.Idle) }
            throw cancel
        } catch (_: Exception) {
            emitSnackbar(R.string.settings_backup_snackbar_disable_failed)
            update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
            return
        }
        when (result) {
            BackupSettingsDisableResult.Success ->
                emitSnackbar(R.string.settings_backup_snackbar_disabled)
            BackupSettingsDisableResult.PermissionCleanupWarning ->
                emitSnackbar(R.string.settings_backup_snackbar_disabled_cleanup_warning)
            BackupSettingsDisableResult.Failed ->
                emitSnackbar(R.string.settings_backup_snackbar_disable_failed)
            BackupSettingsDisableResult.Busy ->
                emitSnackbar(R.string.settings_backup_snackbar_disable_busy)
        }
        update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
    }

    private suspend fun awaitAbandonToIdle() {
        val restoreOp = uiState.operation
        val restorePending = uiState.pendingConfirmation
        when (awaitAbandon()) {
            BackupSettingsAbandonResult.Success -> {
                update { copy(operation = BackupSettingsOperation.Idle, pendingConfirmation = null) }
            }
            BackupSettingsAbandonResult.Busy -> {
                update {
                    copy(
                        operation = restoreOp,
                        pendingConfirmation = restorePending,
                    )
                }
                emitSnackbar(R.string.settings_backup_snackbar_abandon_busy)
            }
        }
    }

    private suspend fun awaitAbandon(): BackupSettingsAbandonResult {
        update { copy(operation = BackupSettingsOperation.Abandoning) }
        return try {
            facade.abandonSetupSession()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            BackupSettingsAbandonResult.Busy
        }
    }

    private fun update(block: BackupSettingsUiState.() -> BackupSettingsUiState) {
        uiState = uiState.block()
        publish()
    }

    companion object {
        fun isBackupNowAvailable(status: BackupSettingsOperationalState): Boolean {
            return when (status) {
                BackupSettingsOperationalState.NotConfigured -> false
                is BackupSettingsOperationalState.NeedsReconnect -> false
                is BackupSettingsOperationalState.Healthy,
                BackupSettingsOperationalState.ConfiguredNoSuccessCache,
                is BackupSettingsOperationalState.TransientFailure,
                is BackupSettingsOperationalState.NeedsAttention,
                is BackupSettingsOperationalState.DataProblem,
                -> true
            }
        }

        fun isConfigured(status: BackupSettingsOperationalState): Boolean {
            return status !is BackupSettingsOperationalState.NotConfigured
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END

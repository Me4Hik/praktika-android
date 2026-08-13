// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 5.0 production restore ViewModel
package com.me4hik.praktika.ui.restore

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.me4hik.praktika.data.backup.restore.BackupRestoreCoordinatorResult
import com.me4hik.praktika.data.backup.restore.BackupRestorePreview
import com.me4hik.praktika.data.backup.restore.BackupRestoreResult
import com.me4hik.praktika.data.backup.restore.BackupRestoreSelectedIdentity
import com.me4hik.praktika.data.backup.restore.RestoreTargetEligibility
import com.me4hik.praktika.data.backup.storage.BackupStorageProvider
import com.me4hik.praktika.data.backup.write.CommittedHandoffStartResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ProductionRestoreViewModel(
    private val gateway: ProductionRestoreGateway,
    private val previewMapper: BackupRestorePreviewMapper,
    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-B runtime bridge
    private val restoreSessionController: RestoreBackupRuntimeBridge =
        NoOpRestoreBackupSessionController,
    // 10.08.2026 Post-release fixes cursor by Me4Hik END
) : ViewModel() {
    private val _uiState = MutableStateFlow<ProductionRestoreUiState>(ProductionRestoreUiState.Hidden)
    val uiState: StateFlow<ProductionRestoreUiState> = _uiState.asStateFlow()

    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    private val effectsChannel = Channel<ProductionRestoreEffect>(Channel.BUFFERED)
    val effects: Flow<ProductionRestoreEffect> = effectsChannel.receiveAsFlow()

    private var storage: BackupStorageProvider? = null
    private var cachedPreview: BackupRestorePreview? = null
    private var selectedIdentity: BackupRestoreSelectedIdentity? = null
    private var candidateTreeUri: Uri? = null
    private var candidateGrantFlags: Int = 0
    private var candidateCommittedToActive: Boolean = false
    private var activeJob: Job? = null

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-B local session flags
    private var backupSessionBegun: Boolean = false
    private var committedHandoffStarted: Boolean = false
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    fun onOpenRestore() {
        if (_sessionActive.value) {
            return
        }
        _sessionActive.value = true
        _uiState.value = ProductionRestoreUiState.CheckingEligibility
        launchOperation {
            val eligibility = gateway.checkTargetEligibility()
            _uiState.value = mapEligibilityToState(eligibility)
        }
    }

    fun onChooseFolder() {
        if (!canChooseFolder()) {
            return
        }
        if (_uiState.value is ProductionRestoreUiState.PreviewReady) {
            candidateCommittedToActive = false
            candidateTreeUri = null
            candidateGrantFlags = 0
        }
        _uiState.value = ProductionRestoreUiState.ChoosingFolder
        viewModelScope.launch {
            val hint = gateway.readTreeUriHint()
            effectsChannel.send(ProductionRestoreEffect.LaunchFolderPicker(initialUriHint = hint))
        }
    }

    fun onFolderPickerResult(uri: Uri?, grantFlags: Int) {
        if (_uiState.value != ProductionRestoreUiState.ChoosingFolder) {
            return
        }
        if (uri == null) {
            _uiState.value = ProductionRestoreUiState.Idle
            return
        }
        connectAndInspect(uri, grantFlags)
    }

    fun onRetryInspect() {
        val currentStorage = storage ?: return
        if (_uiState.value !is ProductionRestoreUiState.Error) {
            return
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A retry keeps/reopens session
        beginBackupSessionIfNeeded()
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        launchOperation {
            inspectBackup(currentStorage)
        }
    }

    fun onRestoreConfirmed() {
        val state = _uiState.value as? ProductionRestoreUiState.PreviewReady ?: return
        if (activeJob?.isActive == true) {
            return
        }
        val currentStorage = storage ?: run {
            _uiState.value = ProductionRestoreUiState.Error(ProductionRestoreError.Unexpected)
            return
        }
        val identity = selectedIdentity ?: run {
            _uiState.value = ProductionRestoreUiState.Error(ProductionRestoreError.Unexpected)
            return
        }
        val technicalPreview = cachedPreview
        launchOperation {
            when (val eligibility = gateway.checkTargetEligibility()) {
                RestoreTargetEligibility.RestoreAvailable -> Unit
                RestoreTargetEligibility.TargetNotEmpty -> {
                    clearSessionRefs()
                    endBackupSessionNonCommitted()
                    _uiState.value = ProductionRestoreUiState.Blocked(
                        ProductionRestoreTargetBlockReason.TargetNotEmpty,
                    )
                    return@launchOperation
                }
                is RestoreTargetEligibility.TargetUnsafe -> {
                    clearSessionRefs()
                    endBackupSessionNonCommitted()
                    _uiState.value = ProductionRestoreUiState.Blocked(
                        ProductionRestoreTargetBlockReason.TargetUnsafe,
                    )
                    return@launchOperation
                }
            }
            _uiState.value = ProductionRestoreUiState.Restoring(state.preview)
            when (
                val result = gateway.executeRestore(
                    storage = currentStorage,
                    previewIdentity = identity,
                    preview = technicalPreview,
                )
            ) {
                is BackupRestoreCoordinatorResult.FullSuccess -> {
                    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-B handoff before effect
                    startCommittedRestoreHandoff()
                    emitRestoreCommitted(state.preview.practiceStarted)
                    _uiState.value = ProductionRestoreUiState.RestoreSuccess(state.preview)
                    // 10.08.2026 Post-release fixes cursor by Me4Hik END
                }

                is BackupRestoreCoordinatorResult.RestoreDataSuccessRuntimeSyncWarning -> {
                    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-B handoff before effect
                    startCommittedRestoreHandoff()
                    emitRestoreCommitted(state.preview.practiceStarted)
                    _uiState.value = ProductionRestoreUiState.RuntimeSyncWarning(state.preview)
                    // 10.08.2026 Post-release fixes cursor by Me4Hik END
                }

                is BackupRestoreCoordinatorResult.PreviewStale -> {
                    handlePreviewStale(currentStorage)
                }

                is BackupRestoreCoordinatorResult.RestoreCoreFailure -> {
                    endBackupSessionNonCommitted()
                    _uiState.value = mapRestoreCoreFailure(result.restoreResult)
                }

                is BackupRestoreCoordinatorResult.RestoreDataSuccess -> {
                    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-B handoff before effect
                    startCommittedRestoreHandoff()
                    emitRestoreCommitted(state.preview.practiceStarted)
                    _uiState.value = ProductionRestoreUiState.RestoreSuccess(state.preview)
                    // 10.08.2026 Post-release fixes cursor by Me4Hik END
                }

                is BackupRestoreCoordinatorResult.RestoreDataSuccessVerificationFailure -> {
                    // Room committed: handoff must start even without RestoreCommitted UI effect.
                    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-B verification failure handoff
                    startCommittedRestoreHandoff()
                    _uiState.value = ProductionRestoreUiState.Error(ProductionRestoreError.Unexpected)
                    // 10.08.2026 Post-release fixes cursor by Me4Hik END
                }

                BackupRestoreCoordinatorResult.NoValidBackup,
                is BackupRestoreCoordinatorResult.PreviewReady,
                is BackupRestoreCoordinatorResult.TargetNotEligible,
                -> {
                    endBackupSessionNonCommitted()
                    _uiState.value = ProductionRestoreUiState.Error(ProductionRestoreError.Unexpected)
                }
            }
        }
    }

    fun onSuccessAcknowledged() {
        if (_uiState.value !is ProductionRestoreUiState.RestoreSuccess) {
            return
        }
        resetSession()
    }

    fun onRuntimeWarningContinue() {
        if (_uiState.value !is ProductionRestoreUiState.RuntimeSyncWarning) {
            return
        }
        resetSession()
    }

    fun onDismiss() {
        when (_uiState.value) {
            is ProductionRestoreUiState.Restoring -> return
            ProductionRestoreUiState.Connecting,
            ProductionRestoreUiState.Inspecting,
            -> {
                activeJob?.cancel()
                activeJob = null
                resetSession(releaseUncommittedCandidate = true)
            }
            ProductionRestoreUiState.Hidden -> Unit
            else -> resetSession(releaseUncommittedCandidate = true)
        }
    }

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A VM clear cleanup
    override fun onCleared() {
        releaseBackupSessionOnAbnormalClear()
        super.onCleared()
    }

    /** Host-test seam mirroring [onCleared] restore-session cleanup without ViewModelStore. */
    internal fun releaseBackupSessionOnAbnormalClearForTests() {
        releaseBackupSessionOnAbnormalClear()
    }

    private fun releaseBackupSessionOnAbnormalClear() {
        if (backupSessionBegun && !committedHandoffStarted) {
            restoreSessionController.endNonCommittedSession()
            backupSessionBegun = false
        }
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    private fun connectAndInspect(uri: Uri, grantFlags: Int) {
        launchOperation {
            releaseUncommittedCandidateIfNeeded()
            candidateTreeUri = null
            candidateGrantFlags = 0
            candidateCommittedToActive = false
            // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A begin before candidate IO
            beginBackupSessionIfNeeded()
            // 10.08.2026 Post-release fixes cursor by Me4Hik END
            _uiState.value = ProductionRestoreUiState.Connecting
            when (val outcome = gateway.connectAndInspectCandidate(uri, grantFlags)) {
                is ProductionRestoreCandidateIoResult.Connected -> {
                    candidateTreeUri = outcome.treeUri
                    candidateGrantFlags = grantFlags
                    storage = outcome.storage
                    _uiState.value = ProductionRestoreUiState.Inspecting
                    applyInspectResult(outcome.inspect, outcome.treeUri, grantFlags)
                }

                ProductionRestoreCandidateIoResult.PermissionLost -> {
                    gateway.releaseRejectedTransientGrant(uri, grantFlags)
                    endBackupSessionNonCommitted()
                    _uiState.value = ProductionRestoreUiState.Error(ProductionRestoreError.PermissionLost)
                }

                ProductionRestoreCandidateIoResult.ReadFailure -> {
                    gateway.releaseRejectedTransientGrant(uri, grantFlags)
                    endBackupSessionNonCommitted()
                    _uiState.value = ProductionRestoreUiState.Error(ProductionRestoreError.ReadFailure)
                }

                ProductionRestoreCandidateIoResult.WriteFailure -> {
                    gateway.releaseRejectedTransientGrant(uri, grantFlags)
                    endBackupSessionNonCommitted()
                    _uiState.value = ProductionRestoreUiState.Error(ProductionRestoreError.ReadFailure)
                }

                ProductionRestoreCandidateIoResult.Unexpected -> {
                    gateway.releaseRejectedTransientGrant(uri, grantFlags)
                    endBackupSessionNonCommitted()
                    _uiState.value = ProductionRestoreUiState.Error(ProductionRestoreError.Unexpected)
                }
            }
        }
    }

    private suspend fun inspectBackup(currentStorage: BackupStorageProvider) {
        _uiState.value = ProductionRestoreUiState.Inspecting
        val candidate = candidateTreeUri ?: run {
            endBackupSessionNonCommitted()
            _uiState.value = ProductionRestoreUiState.Error(ProductionRestoreError.Unexpected)
            return
        }
        applyInspectResult(
            result = gateway.inspectLatest(currentStorage),
            candidate = candidate,
            grantFlags = candidateGrantFlags,
        )
    }

    private suspend fun applyInspectResult(
        result: BackupRestoreCoordinatorResult,
        candidate: Uri,
        grantFlags: Int,
    ) {
        when (result) {
            BackupRestoreCoordinatorResult.NoValidBackup -> {
                gateway.releaseRejectedTransientGrant(candidate, grantFlags)
                clearCachedPreviewIdentity()
                endBackupSessionNonCommitted()
                _uiState.value = ProductionRestoreUiState.Error(ProductionRestoreError.NoBackupFound)
            }

            is BackupRestoreCoordinatorResult.PreviewReady -> {
                if (!gateway.commitActiveBackupFolder(candidate)) {
                    gateway.releaseRejectedTransientGrant(candidate, grantFlags)
                    endBackupSessionNonCommitted()
                    _uiState.value = ProductionRestoreUiState.Error(
                        ProductionRestoreError.ActiveFolderSaveFailure,
                    )
                    return
                }
                candidateCommittedToActive = true
                cachePreviewIdentity(result.preview, result.identity)
                _uiState.value = ProductionRestoreUiState.PreviewReady(
                    preview = previewMapper.toUiModel(result.preview),
                    staleNotice = false,
                )
            }

            else -> {
                gateway.releaseRejectedTransientGrant(candidate, grantFlags)
                endBackupSessionNonCommitted()
                _uiState.value = ProductionRestoreUiState.Error(ProductionRestoreError.Unexpected)
            }
        }
    }

    private suspend fun handlePreviewStale(currentStorage: BackupStorageProvider) {
        _uiState.value = ProductionRestoreUiState.Inspecting
        when (val refreshed = gateway.inspectLatest(currentStorage)) {
            is BackupRestoreCoordinatorResult.PreviewReady -> {
                val candidate = candidateTreeUri
                if (candidate != null && !gateway.commitActiveBackupFolder(candidate)) {
                    endBackupSessionNonCommitted()
                    _uiState.value = ProductionRestoreUiState.Error(
                        ProductionRestoreError.ActiveFolderSaveFailure,
                    )
                    return
                }
                candidateCommittedToActive = true
                cachePreviewIdentity(refreshed.preview, refreshed.identity)
                // Session continues with refreshed PreviewReady — KEEP suppression.
                _uiState.value = ProductionRestoreUiState.PreviewReady(
                    preview = previewMapper.toUiModel(refreshed.preview),
                    staleNotice = true,
                )
            }

            BackupRestoreCoordinatorResult.NoValidBackup -> {
                gateway.clearTreeUri()
                clearCachedPreviewIdentity()
                candidateCommittedToActive = false
                endBackupSessionNonCommitted()
                _uiState.value = ProductionRestoreUiState.Error(ProductionRestoreError.NoBackupFound)
            }

            else -> {
                endBackupSessionNonCommitted()
                _uiState.value = ProductionRestoreUiState.Error(ProductionRestoreError.Unexpected)
            }
        }
    }

    private fun cachePreviewIdentity(
        preview: BackupRestorePreview,
        identity: BackupRestoreSelectedIdentity,
    ) {
        cachedPreview = preview
        selectedIdentity = identity
    }

    private fun clearCachedPreviewIdentity() {
        cachedPreview = null
        selectedIdentity = null
    }

    private fun clearSessionRefs() {
        storage = null
        candidateTreeUri = null
        candidateGrantFlags = 0
        candidateCommittedToActive = false
        clearCachedPreviewIdentity()
    }

    private suspend fun releaseUncommittedCandidateIfNeeded() {
        val candidate = candidateTreeUri ?: return
        if (candidateCommittedToActive) {
            return
        }
        gateway.releaseRejectedTransientGrant(candidate, candidateGrantFlags)
    }

    private fun resetSession(releaseUncommittedCandidate: Boolean = false) {
        activeJob?.cancel()
        activeJob = null
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-B end on dismiss/reset
        if (!committedHandoffStarted) {
            endBackupSessionNonCommitted()
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        val shouldReleaseCandidate =
            releaseUncommittedCandidate &&
                candidateTreeUri != null &&
                !candidateCommittedToActive
        if (shouldReleaseCandidate) {
            viewModelScope.launch {
                releaseUncommittedCandidateIfNeeded()
                finishResetSession()
            }
        } else {
            finishResetSession()
        }
    }

    private fun finishResetSession() {
        clearSessionRefs()
        committedHandoffStarted = false
        _sessionActive.value = false
        _uiState.value = ProductionRestoreUiState.Hidden
    }

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-B session helpers
    private fun beginBackupSessionIfNeeded() {
        if (backupSessionBegun || committedHandoffStarted) {
            return
        }
        restoreSessionController.beginRestoreSession()
        backupSessionBegun = true
    }

    private fun endBackupSessionNonCommitted() {
        if (!backupSessionBegun || committedHandoffStarted) {
            return
        }
        restoreSessionController.endNonCommittedSession()
        backupSessionBegun = false
    }

    private fun startCommittedRestoreHandoff() {
        when (val result = restoreSessionController.startCommittedHandoff()) {
            CommittedHandoffStartResult.Started,
            CommittedHandoffStartResult.AlreadyStarted,
            -> {
                committedHandoffStarted = true
                backupSessionBegun = false
            }
            CommittedHandoffStartResult.Unavailable -> {
                // Room already committed; ensure we do not cancel as noncommitted later.
                committedHandoffStarted = true
                backupSessionBegun = false
            }
        }
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    private fun launchOperation(block: suspend () -> Unit) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            try {
                block()
            } catch (cancel: CancellationException) {
                if (_uiState.value == ProductionRestoreUiState.Connecting ||
                    _uiState.value == ProductionRestoreUiState.Inspecting
                ) {
                    releaseUncommittedCandidateIfNeeded()
                    _uiState.value = ProductionRestoreUiState.Idle
                }
                throw cancel
            }
        }
    }

    private fun canChooseFolder(): Boolean {
        if (activeJob?.isActive == true) {
            return false
        }
        return when (_uiState.value) {
            ProductionRestoreUiState.Idle,
            is ProductionRestoreUiState.Error,
            is ProductionRestoreUiState.PreviewReady,
            -> true
            else -> false
        }
    }

    private fun mapEligibilityToState(
        eligibility: RestoreTargetEligibility,
    ): ProductionRestoreUiState {
        return when (eligibility) {
            RestoreTargetEligibility.RestoreAvailable -> ProductionRestoreUiState.Idle
            RestoreTargetEligibility.TargetNotEmpty -> ProductionRestoreUiState.Blocked(
                ProductionRestoreTargetBlockReason.TargetNotEmpty,
            )
            is RestoreTargetEligibility.TargetUnsafe -> ProductionRestoreUiState.Blocked(
                ProductionRestoreTargetBlockReason.TargetUnsafe,
            )
        }
    }

    private fun mapRestoreCoreFailure(
        result: BackupRestoreResult,
    ): ProductionRestoreUiState {
        return when (result) {
            BackupRestoreResult.TargetNotEmpty -> ProductionRestoreUiState.Blocked(
                ProductionRestoreTargetBlockReason.TargetNotEmpty,
            )
            is BackupRestoreResult.TargetDatabaseUnsafe -> ProductionRestoreUiState.Blocked(
                ProductionRestoreTargetBlockReason.TargetUnsafe,
            )
            is BackupRestoreResult.InvalidBackup,
            is BackupRestoreResult.StaticQuestionMismatch,
            is BackupRestoreResult.PostValidationFailure,
            -> ProductionRestoreUiState.Error(ProductionRestoreError.InvalidBackup)
            is BackupRestoreResult.IncompatibleSeed -> ProductionRestoreUiState.Error(
                ProductionRestoreError.IncompatibleBackup,
            )
            is BackupRestoreResult.DatabaseWriteFailure -> ProductionRestoreUiState.Error(
                ProductionRestoreError.RestoreWriteFailure,
            )
            BackupRestoreResult.Success -> ProductionRestoreUiState.Error(ProductionRestoreError.Unexpected)
        }
    }

    private suspend fun emitRestoreCommitted(practiceStarted: Boolean) {
        effectsChannel.send(
            ProductionRestoreEffect.RestoreCommitted(practiceStarted = practiceStarted),
        )
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END

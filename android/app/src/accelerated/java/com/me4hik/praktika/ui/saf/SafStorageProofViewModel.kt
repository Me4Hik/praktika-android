package com.me4hik.praktika.ui.saf

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.storage.BackupFolderAccessState
import com.me4hik.praktika.data.backup.storage.BackupFolderConnection
import com.me4hik.praktika.data.backup.storage.BackupFolderConnectionResult
import com.me4hik.praktika.data.backup.storage.BackupStorageProvider
import com.me4hik.praktika.data.backup.storage.BackupTreeConfigRepository
import com.me4hik.praktika.data.backup.storage.DataStoreBackupTreeConfigRepository
import com.me4hik.praktika.data.backup.storage.SlotInspectionResult
import com.me4hik.praktika.ui.restore.OpenTreeResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SafStorageProofViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val treeConfigRepository: BackupTreeConfigRepository =
        DataStoreBackupTreeConfigRepository(application)
    private val folderConnection: BackupFolderConnection =
        BackupFolderConnection(
            contentResolver = application.contentResolver,
            treeConfigRepository = treeConfigRepository,
        )
    private val envelopeFactory: SyntheticBackupEnvelopeFactory = SyntheticBackupEnvelopeFactory()
    private val orchestrator: SafStorageProofOrchestrator =
        SafStorageProofOrchestrator(envelopeFactory)

    private val contentResolver: ContentResolver = application.contentResolver

    private val _uiState = MutableStateFlow<SafStorageProofUiState>(SafStorageProofUiState.Disconnected)
    val uiState: StateFlow<SafStorageProofUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SafStorageProofUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SafStorageProofUiEvent> = _events.asSharedFlow()

    private var storage: BackupStorageProvider? = null

    init {
        viewModelScope.launch {
            restoreSavedConnection()
        }
    }

    fun onConnectClicked() {
        _events.tryEmit(SafStorageProofUiEvent.RequestPicker)
    }

    fun onInspectClicked() {
        val currentStorage = storage ?: return
        viewModelScope.launch {
            inspectSlots(currentStorage, fromUserAction = true)
        }
    }

    fun onRunProofClicked() {
        val currentStorage = storage ?: return
        viewModelScope.launch {
            _uiState.value = SafStorageProofUiState.Running
            SafStorageProofLogger.proofStep("RUN", "START")
            try {
                val result = orchestrator.runProof(currentStorage)
                when (result) {
                    is SafStorageProofRunResult.Passed -> {
                        val inspection = currentStorage.inspectSlots()
                        _uiState.value = SafStorageProofUiState.ProofPassed(
                            aSummary = SafStorageProofGate.summarize(inspection.slotA),
                            bSummary = SafStorageProofGate.summarize(inspection.slotB),
                            latest = result.latest,
                            documentUriRediscoveryWorked = result.documentUriRediscoveryWorked,
                        )
                        SafStorageProofLogger.proofStep("RUN", "PASS")
                    }

                    is SafStorageProofRunResult.Failed -> {
                        val reason = buildFailureReason(result.failure, result.detail)
                        _uiState.value = SafStorageProofUiState.Failure(reason)
                        SafStorageProofLogger.proofFailure(result.failure.code)
                    }
                }
            } catch (cancel: CancellationException) {
                _uiState.value = SafStorageProofUiState.Failure(
                    SafStorageProofFailure.Cancelled.code,
                )
                SafStorageProofLogger.proofFailure(SafStorageProofFailure.Cancelled.code)
                throw cancel
            }
        }
    }

    fun onReloadConnectionClicked() {
        viewModelScope.launch {
            restoreSavedConnection()
        }
    }

    fun onPickerResult(result: OpenTreeResult?) {
        if (result == null) {
            return
        }
        viewModelScope.launch {
            connectTree(result)
        }
    }

    private suspend fun connectTree(result: OpenTreeResult) {
        _uiState.value = SafStorageProofUiState.Connecting
        val providerAuthority = result.uri.authority
        when (
            val connectResult = folderConnection.connect(
                treeUri = result.uri,
                grantedFlags = result.flags,
            )
        ) {
            is BackupFolderConnectionResult.Success -> {
                storage = folderConnection.createStorage(connectResult.treeUri, contentResolver)
                val grants = readPersistedGrants(connectResult.treeUri)
                _uiState.value = SafStorageProofUiState.Connected(
                    providerAuthority = providerAuthority,
                    readGrant = grants.first,
                    writeGrant = grants.second,
                )
                SafStorageProofLogger.connectionSuccess(providerAuthority)
            }

            BackupFolderConnectionResult.PermissionLost -> {
                clearConnectionState()
                _uiState.value = SafStorageProofUiState.Failure(SafStorageProofFailure.PermissionLost.code)
                SafStorageProofLogger.connectionFailure(SafStorageProofFailure.PermissionLost.code)
            }

            BackupFolderConnectionResult.Unavailable -> {
                clearConnectionState()
                _uiState.value = SafStorageProofUiState.Failure(SafStorageProofFailure.Unavailable.code)
                SafStorageProofLogger.connectionFailure(SafStorageProofFailure.Unavailable.code)
            }

            is BackupFolderConnectionResult.ProviderError -> {
                clearConnectionState()
                _uiState.value = SafStorageProofUiState.Failure(SafStorageProofFailure.ProviderError.code)
                SafStorageProofLogger.connectionFailure(SafStorageProofFailure.ProviderError.code)
            }
        }
    }

    private suspend fun restoreSavedConnection() {
        val hint = treeConfigRepository.treeUriHint.first()
        if (hint.isNullOrBlank()) {
            clearConnectionState()
            _uiState.value = SafStorageProofUiState.Disconnected
            return
        }

        val treeUri = runCatching { Uri.parse(hint) }.getOrNull()
        if (treeUri == null) {
            clearConnectionState()
            _uiState.value = SafStorageProofUiState.Disconnected
            return
        }

        when (val access = folderConnection.validateAccess(treeUri)) {
            is BackupFolderAccessState.Connected -> {
                storage = folderConnection.createStorage(access.treeUri, contentResolver)
                val grants = readPersistedGrants(access.treeUri)
                _uiState.value = SafStorageProofUiState.Connected(
                    providerAuthority = access.treeUri.authority,
                    readGrant = grants.first,
                    writeGrant = grants.second,
                    restoredFromHint = true,
                )
                SafStorageProofLogger.restoredConnection(access.treeUri.authority)
                storage?.let { inspectSlots(it, fromUserAction = false) }
            }

            BackupFolderAccessState.PermissionLost -> {
                clearConnectionState()
                _uiState.value = SafStorageProofUiState.Failure(
                    "${SafStorageProofFailure.PermissionLost.code}: reconnect required",
                )
            }

            BackupFolderAccessState.Unavailable -> {
                clearConnectionState()
                _uiState.value = SafStorageProofUiState.Disconnected
            }

            is BackupFolderAccessState.ProviderError -> {
                clearConnectionState()
                _uiState.value = SafStorageProofUiState.Failure(SafStorageProofFailure.ProviderError.code)
            }
        }
    }

    private suspend fun inspectSlots(currentStorage: BackupStorageProvider, fromUserAction: Boolean) {
        if (fromUserAction) {
            _uiState.value = SafStorageProofUiState.Inspecting
        }
        val inspection = currentStorage.inspectSlots()
        applyInspectionState(inspection)
        logInspection(inspection)
    }

    private fun applyInspectionState(inspection: SlotInspectionResult) {
        val aSummary = SafStorageProofGate.summarize(inspection.slotA)
        val bSummary = SafStorageProofGate.summarize(inspection.slotB)

        if (SafStorageProofGate.hasAmbiguousSlot(inspection)) {
            _uiState.value = SafStorageProofUiState.FolderNotEmpty(
                aSummary = aSummary,
                bSummary = bSummary,
            )
            return
        }

        if (SafStorageProofGate.isEmptyFolder(inspection)) {
            _uiState.value = SafStorageProofUiState.ReadyEmptyFolder(
                aSummary = aSummary,
                bSummary = bSummary,
            )
            return
        }

        _uiState.value = SafStorageProofUiState.FolderNotEmpty(
            aSummary = aSummary,
            bSummary = bSummary,
        )
    }

    private fun logInspection(inspection: SlotInspectionResult) {
        SafStorageProofLogger.slotState("A", SafStorageProofGate.summarize(inspection.slotA).displayText(BackupSlotId.A))
        SafStorageProofLogger.slotState("B", SafStorageProofGate.summarize(inspection.slotB).displayText(BackupSlotId.B))
    }

    private fun readPersistedGrants(treeUri: Uri): Pair<Boolean, Boolean> {
        val permission = contentResolver.persistedUriPermissions.firstOrNull { it.uri == treeUri }
        return (permission?.isReadPermission == true) to (permission?.isWritePermission == true)
    }

    private fun clearConnectionState() {
        storage = null
    }

    private fun buildFailureReason(failure: SafStorageProofFailure, detail: String?): String {
        return if (detail.isNullOrBlank()) {
            failure.code
        } else {
            "${failure.code}: $detail"
        }
    }
}

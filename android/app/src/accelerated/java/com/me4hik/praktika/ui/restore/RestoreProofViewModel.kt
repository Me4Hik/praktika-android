package com.me4hik.praktika.ui.restore

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.me4hik.praktika.data.backup.export.RoomBackupExporter
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.restore.BackupRestoreCoordinator
import com.me4hik.praktika.data.backup.restore.BackupRestoreCoordinatorResult
import com.me4hik.praktika.data.backup.restore.BackupRestorePreview
import com.me4hik.praktika.data.backup.restore.BackupRestoreResult
import com.me4hik.praktika.data.backup.restore.BackupRestoreSelectedIdentity
import com.me4hik.praktika.data.backup.restore.RestoreTargetEligibility
import com.me4hik.praktika.data.backup.restore.RoomBackupRestorer
import com.me4hik.praktika.data.backup.storage.BackupFolderAccessState
import com.me4hik.praktika.data.backup.storage.BackupFolderConnection
import com.me4hik.praktika.data.backup.storage.BackupFolderConnectionResult
import com.me4hik.praktika.data.backup.storage.BackupStorageProvider
import com.me4hik.praktika.data.backup.storage.SlotInspectionResult
import com.me4hik.praktika.runtime.RuntimeFactory
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

class RestoreProofViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val treeConfigRepository = DataStoreRestoreTestTreeConfigRepository(application)
    private val folderConnection = BackupFolderConnection(
        contentResolver = application.contentResolver,
        treeConfigRepository = treeConfigRepository,
    )
    private val prepareOrchestrator = RestorePrepareOrchestrator(RichRestoreAcceptanceEnvelopeFactory())
    private val contentResolver: ContentResolver = application.contentResolver

    private val _uiState = MutableStateFlow<RestoreProofUiState>(RestoreProofUiState.Disconnected)
    val uiState: StateFlow<RestoreProofUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RestoreProofUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<RestoreProofUiEvent> = _events.asSharedFlow()

    private var storage: BackupStorageProvider? = null
    private var lastPreview: BackupRestorePreview? = null
    private var lastIdentity: BackupRestoreSelectedIdentity? = null
    private var restoreCompleted = false
    private var lastInspection: SlotInspectionResult? = null

    init {
        viewModelScope.launch {
            restoreSavedConnection()
        }
    }

    fun onConnectClicked() {
        _events.tryEmit(RestoreProofUiEvent.RequestPicker)
    }

    fun onInspectClicked() {
        val currentStorage = storage ?: return
        viewModelScope.launch {
            inspectSlots(currentStorage, fromUserAction = true)
        }
    }

    fun onPrepareClicked() {
        val currentStorage = storage ?: return
        viewModelScope.launch {
            _uiState.value = RestoreProofUiState.Preparing
            when (val result = prepareOrchestrator.prepare(currentStorage)) {
                is RestorePrepareResult.Prepared -> {
                    val inspection = currentStorage.inspectSlots()
                    lastInspection = inspection
                    _uiState.value = RestoreProofUiState.PrepareDone(
                        aSummary = RestorePrepareGate.summarize(inspection.slotA),
                        bSummary = RestorePrepareGate.summarize(inspection.slotB),
                        latestSlot = result.latestSlot,
                    )
                }

                is RestorePrepareResult.Failed -> {
                    _uiState.value = RestoreProofUiState.Failure(result.failure.code)
                }
            }
        }
    }

    fun onPreviewClicked() {
        val currentStorage = storage ?: return
        viewModelScope.launch {
            val coordinator = createCoordinator()
            when (val result = coordinator.inspectLatest(currentStorage)) {
                is BackupRestoreCoordinatorResult.PreviewReady -> {
                    lastPreview = result.preview
                    lastIdentity = result.identity
                    val inspection = currentStorage.inspectSlots()
                    lastInspection = inspection
                    val eligibility = coordinator.checkTargetEligibility()
                    _uiState.value = RestoreProofUiState.PreviewReady(
                        preview = result.preview,
                        aSummary = RestorePrepareGate.summarize(inspection.slotA),
                        bSummary = RestorePrepareGate.summarize(inspection.slotB),
                        targetEligibility = eligibility,
                    )
                }

                BackupRestoreCoordinatorResult.NoValidBackup -> {
                    _uiState.value = RestoreProofUiState.Failure("NO_VALID_BACKUP")
                }

                else -> {
                    _uiState.value = RestoreProofUiState.Failure("PREVIEW_FAILED")
                }
            }
        }
    }

    fun onRestoreClicked() {
        val currentStorage = storage ?: return
        val identity = lastIdentity ?: return
        val preview = lastPreview
        viewModelScope.launch {
            _uiState.value = RestoreProofUiState.Restoring
            val coordinator = createCoordinator(withVerifier = !restoreCompleted)
            try {
                when (
                    val result = coordinator.executeRestore(
                        storage = currentStorage,
                        previewIdentity = identity,
                        preview = preview,
                    )
                ) {
                    is BackupRestoreCoordinatorResult.FullSuccess -> {
                        restoreCompleted = true
                        val verifierEvidence = (result.verification as? com.me4hik.praktika.data.backup.restore.PostRestoreVerificationResult.Success)
                            ?.evidence
                            ?.summary()
                        val postExportEvidence = buildEvidenceSummary()
                        _uiState.value = RestoreProofUiState.RuntimeSynced(
                            preview = result.preview,
                            reconcileSummary = buildString {
                                append(result.reconcileResult.javaClass.simpleName)
                                verifierEvidence?.let { append(";verifier=$it") }
                                postExportEvidence?.let { append(";postExport=$it") }
                            },
                            verificationEvidence = verifierEvidence,
                        )
                    }

                    is BackupRestoreCoordinatorResult.RestoreCoreFailure -> {
                        _uiState.value = RestoreProofUiState.Failure(
                            mapRestoreFailure(result.restoreResult),
                        )
                    }

                    is BackupRestoreCoordinatorResult.PreviewStale -> {
                        lastPreview = null
                        lastIdentity = null
                        _uiState.value = RestoreProofUiState.Failure("PREVIEW_STALE")
                    }

                    is BackupRestoreCoordinatorResult.RestoreDataSuccessVerificationFailure -> {
                        restoreCompleted = true
                        _uiState.value = RestoreProofUiState.Failure("DEVICE_ROUNDTRIP_MISMATCH")
                    }

                    is BackupRestoreCoordinatorResult.RestoreDataSuccessRuntimeSyncWarning -> {
                        restoreCompleted = true
                        val verifierEvidence = (result.verification as? com.me4hik.praktika.data.backup.restore.PostRestoreVerificationResult.Success)
                            ?.evidence
                        if (verifierEvidence != null && result.dataRestoreCommitted) {
                            _uiState.value = RestoreProofUiState.RuntimeSyncFailed(
                                preview = result.preview,
                                verificationEvidence = verifierEvidence.summary(),
                                reconcileFailureClassName = result.reconcileFailureClassName.orEmpty(),
                                reconcileFailureMessage = result.reconcileFailureMessage,
                            )
                        } else {
                            _uiState.value = RestoreProofUiState.Failure("POST_RESTORE_RECONCILE_FAILED")
                        }
                    }

                    else -> {
                        _uiState.value = RestoreProofUiState.Failure("RESTORE_CORE_FAILED")
                    }
                }
            } catch (cancel: CancellationException) {
                _uiState.value = RestoreProofUiState.Failure("CANCELLED")
                throw cancel
            }
        }
    }

    fun onRetryRestoreClicked() {
        if (!restoreCompleted) {
            return
        }
        val currentStorage = storage ?: return
        val identity = lastIdentity ?: return
        viewModelScope.launch {
            _uiState.value = RestoreProofUiState.Restoring
            val coordinator = createCoordinator(withVerifier = false)
            when (
                val result = coordinator.executeRestore(
                    storage = currentStorage,
                    previewIdentity = identity,
                    preview = lastPreview,
                )
            ) {
                is BackupRestoreCoordinatorResult.RestoreCoreFailure -> {
                    if (result.restoreResult is BackupRestoreResult.TargetNotEmpty) {
                        _uiState.value = RestoreProofUiState.IdempotenceResult(
                            outcome = "SECOND_RESTORE_TARGET_NOT_EMPTY=PASS",
                        )
                    } else {
                        _uiState.value = RestoreProofUiState.Failure(
                            mapRestoreFailure(result.restoreResult),
                        )
                    }
                }

                is BackupRestoreCoordinatorResult.PreviewStale -> {
                    _uiState.value = RestoreProofUiState.Failure("PREVIEW_STALE")
                }

                else -> {
                    _uiState.value = RestoreProofUiState.Failure("SECOND_RESTORE_UNEXPECTED")
                }
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
        _uiState.value = RestoreProofUiState.Connecting
        when (
            val connectResult = folderConnection.connect(
                treeUri = result.uri,
                grantedFlags = result.flags,
            )
        ) {
            is BackupFolderConnectionResult.Success -> {
                storage = folderConnection.createStorage(connectResult.treeUri, contentResolver)
                val grants = readPersistedGrants(connectResult.treeUri)
                _uiState.value = RestoreProofUiState.Connected(
                    providerAuthority = result.uri.authority,
                    readGrant = grants.first,
                    writeGrant = grants.second,
                )
            }

            BackupFolderConnectionResult.PermissionLost -> {
                clearConnectionState()
                _uiState.value = RestoreProofUiState.Failure("PERMISSION_LOST")
            }

            BackupFolderConnectionResult.Unavailable -> {
                clearConnectionState()
                _uiState.value = RestoreProofUiState.Failure("UNAVAILABLE")
            }

            is BackupFolderConnectionResult.ProviderError -> {
                clearConnectionState()
                _uiState.value = RestoreProofUiState.Failure("PROVIDER_ERROR")
            }
        }
    }

    private suspend fun restoreSavedConnection() {
        val hint = treeConfigRepository.treeUriHint.first()
        if (hint.isNullOrBlank()) {
            clearConnectionState()
            _uiState.value = RestoreProofUiState.Disconnected
            return
        }

        val treeUri = runCatching { Uri.parse(hint) }.getOrNull()
        if (treeUri == null) {
            clearConnectionState()
            _uiState.value = RestoreProofUiState.Disconnected
            return
        }

        when (val access = folderConnection.validateAccess(treeUri)) {
            is BackupFolderAccessState.Connected -> {
                storage = folderConnection.createStorage(access.treeUri, contentResolver)
                val grants = readPersistedGrants(access.treeUri)
                _uiState.value = RestoreProofUiState.Connected(
                    providerAuthority = access.treeUri.authority,
                    readGrant = grants.first,
                    writeGrant = grants.second,
                    restoredFromHint = true,
                )
                storage?.let { inspectSlots(it, fromUserAction = false) }
            }

            BackupFolderAccessState.PermissionLost -> {
                clearConnectionState()
                _uiState.value = RestoreProofUiState.Failure("PERMISSION_LOST: reconnect required")
            }

            BackupFolderAccessState.Unavailable -> {
                clearConnectionState()
                _uiState.value = RestoreProofUiState.Disconnected
            }

            is BackupFolderAccessState.ProviderError -> {
                clearConnectionState()
                _uiState.value = RestoreProofUiState.Failure("PROVIDER_ERROR")
            }
        }
    }

    private suspend fun inspectSlots(currentStorage: BackupStorageProvider, fromUserAction: Boolean) {
        if (fromUserAction) {
            _uiState.value = RestoreProofUiState.Inspecting
        }
        val inspection = currentStorage.inspectSlots()
        lastInspection = inspection
        applyInspectionState(inspection)
    }

    private fun applyInspectionState(inspection: SlotInspectionResult) {
        val aSummary = RestorePrepareGate.summarize(inspection.slotA)
        val bSummary = RestorePrepareGate.summarize(inspection.slotB)

        if (RestorePrepareGate.hasAmbiguousSlot(inspection)) {
            _uiState.value = RestoreProofUiState.ReadyForRestore(
                aSummary = aSummary,
                bSummary = bSummary,
                latestSlot = null,
                targetEligibility = null,
            )
            return
        }

        if (RestorePrepareGate.isEmptyFolder(inspection)) {
            _uiState.value = RestoreProofUiState.ReadyForPrepare(
                aSummary = aSummary,
                bSummary = bSummary,
            )
            return
        }

        val latest = selectLatestSlot(inspection)
        _uiState.value = RestoreProofUiState.ReadyForRestore(
            aSummary = aSummary,
            bSummary = bSummary,
            latestSlot = latest,
            targetEligibility = null,
        )
    }

    private fun selectLatestSlot(inspection: SlotInspectionResult): BackupSlotId? {
        val candidates = listOf(
            com.me4hik.praktika.data.backup.slot.BackupSlotCandidate(
                BackupSlotId.A,
                inspection.slotA.toValidation(),
            ),
            com.me4hik.praktika.data.backup.slot.BackupSlotCandidate(
                BackupSlotId.B,
                inspection.slotB.toValidation(),
            ),
        )
        return com.me4hik.praktika.data.backup.slot.BackupSlotSelector.selectBest(candidates)
    }

    private fun com.me4hik.praktika.data.backup.storage.SlotReadResult.toValidation():
        com.me4hik.praktika.data.backup.validate.BackupFormatValidationResult {
        return when (this) {
            is com.me4hik.praktika.data.backup.storage.SlotReadResult.Valid ->
                com.me4hik.praktika.data.backup.validate.BackupFormatValidationResult.Valid(envelope)
            else -> com.me4hik.praktika.data.backup.validate.BackupFormatValidationResult.Invalid(
                com.me4hik.praktika.data.backup.validate.BackupFormatFailureReason.InvalidMetadata,
                "not-valid",
            )
        }
    }

    private fun createCoordinator(withVerifier: Boolean = true): BackupRestoreCoordinator {
        val runtime = RuntimeFactory.create(getApplication())
        val exporter = RoomBackupExporter(runtime.database)
        val verifier = if (withVerifier) {
            RestoreAcceptanceDeviceVerifier(exporter)
        } else {
            com.me4hik.praktika.data.backup.restore.NoOpPostRestoreDataVerifier
        }
        return BackupRestoreCoordinator(
            database = runtime.database,
            restorer = RoomBackupRestorer(runtime.database),
            cycleRepository = runtime.cycleRepository,
            notificationCoordinator = runtime.notificationCoordinator,
            postRestoreVerifier = verifier,
        )
    }

    private suspend fun buildEvidenceSummary(): String? {
        val runtime = RuntimeFactory.create(getApplication())
        val exporter = RoomBackupExporter(runtime.database)
        return when (val export = exporter.export()) {
            is com.me4hik.praktika.data.backup.export.BackupExportResult.Success -> {
                RestoreAcceptanceDeviceVerifier(exporter)
                    .buildEvidence(export.payload)
                    .summary()
            }

            else -> null
        }
    }

    private fun readPersistedGrants(treeUri: Uri): Pair<Boolean, Boolean> {
        val permission = contentResolver.persistedUriPermissions.firstOrNull { it.uri == treeUri }
        return (permission?.isReadPermission == true) to (permission?.isWritePermission == true)
    }

    private fun clearConnectionState() {
        storage = null
        lastPreview = null
        lastIdentity = null
        restoreCompleted = false
        lastInspection = null
    }

    private fun mapRestoreFailure(result: BackupRestoreResult): String {
        return when (result) {
            BackupRestoreResult.TargetNotEmpty -> "TARGET_NOT_EMPTY"
            is BackupRestoreResult.TargetDatabaseUnsafe -> "TARGET_UNSAFE"
            is BackupRestoreResult.InvalidBackup -> "RESTORE_CORE_FAILED"
            is BackupRestoreResult.IncompatibleSeed -> "RESTORE_CORE_FAILED"
            is BackupRestoreResult.StaticQuestionMismatch -> "RESTORE_CORE_FAILED"
            is BackupRestoreResult.PostValidationFailure -> "RESTORE_CORE_FAILED"
            is BackupRestoreResult.DatabaseWriteFailure -> "RESTORE_CORE_FAILED"
            BackupRestoreResult.Success -> "UNKNOWN"
        }
    }
}

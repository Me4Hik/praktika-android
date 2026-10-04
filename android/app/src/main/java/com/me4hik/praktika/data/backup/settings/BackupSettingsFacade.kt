// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A2 BackupSettingsFacade
package com.me4hik.praktika.data.backup.settings

import com.me4hik.praktika.data.backup.setup.BackupFolderSetupCoordinator
import com.me4hik.praktika.data.backup.setup.DisableAutomaticBackupResult
import com.me4hik.praktika.data.backup.setup.SetupAbandonResult
import com.me4hik.praktika.data.backup.setup.SetupCommitResult
import com.me4hik.praktika.data.backup.setup.SetupInspectResult
import com.me4hik.praktika.data.backup.setup.SetupVerifyWriteResult
import com.me4hik.praktika.data.backup.write.AuthorizedBackupService
import com.me4hik.praktika.data.backup.write.BackupAttemptTrigger
import com.me4hik.praktika.data.backup.write.BackupIoSessionGate
import com.me4hik.praktika.data.backup.write.BackupRequestReason
import com.me4hik.praktika.data.backup.write.BackupTreeUriSanitizer
import com.me4hik.praktika.data.backup.write.BackupWriteStateRepository
import com.me4hik.praktika.data.backup.write.ReconnectResult
import com.me4hik.praktika.measurement.AnalyticsTracker
import com.me4hik.praktika.measurement.NoOpAnalyticsTracker
import com.me4hik.praktika.measurement.ProductAnalyticsEvents
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-lifetime Settings-facing backup facade.
 * Hides write-state URIs, setup tokens/receipts, coordinator outcomes, and storage internals.
 */
class BackupSettingsFacade(
    private val writeStateRepository: BackupWriteStateRepository,
    private val authorizedBackupService: AuthorizedBackupService,
    private val setupCoordinator: BackupFolderSetupCoordinator,
    private val ioGate: BackupIoSessionGate,
    private val reconnectAccess: BackupReconnectAccess,
    private val backupScope: CoroutineScope,
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker,
) {
    private val setupBookkeeping = Mutex()
    private var retainedInspectionToken: Long? = null

    private val abandonRequested = AtomicBoolean(false)
    private val abandonInFlight = AtomicBoolean(false)

    fun observeOperationalStatus(): Flow<BackupSettingsOperationalState> {
        return writeStateRepository.observe().map(BackupSettingsOperationalStateMapper::map)
    }

    suspend fun inspectCandidate(
        uriString: String,
        grantFlags: Int,
    ): BackupSettingsSetupInspectResult {
        val result = setupCoordinator.inspectCandidate(uriString, grantFlags)
        return setupBookkeeping.withLock {
            when (result) {
                is SetupInspectResult.Ready -> {
                    retainedInspectionToken = result.inspection.token
                    BackupSettingsSetupInspectResult.Ready(result.inspection.classification)
                }
                SetupInspectResult.PermissionLost -> {
                    retainedInspectionToken = null
                    BackupSettingsSetupInspectResult.PermissionLost
                }
                SetupInspectResult.Unavailable -> {
                    retainedInspectionToken = null
                    BackupSettingsSetupInspectResult.Unavailable
                }
                SetupInspectResult.ProviderFailure -> {
                    retainedInspectionToken = null
                    BackupSettingsSetupInspectResult.ProviderFailure
                }
                SetupInspectResult.Busy -> BackupSettingsSetupInspectResult.Busy
            }
        }
    }

    suspend fun verifyCurrentCandidate(): BackupSettingsSetupVerifyResult {
        val token = setupBookkeeping.withLock { retainedInspectionToken }
            ?: return BackupSettingsSetupVerifyResult.NoSession
        val result = setupCoordinator.verifyWrite(token)
        return setupBookkeeping.withLock {
            when (result) {
                is SetupVerifyWriteResult.Verified -> BackupSettingsSetupVerifyResult.Verified
                is SetupVerifyWriteResult.CandidateStale -> {
                    retainedInspectionToken = result.inspection.token
                    BackupSettingsSetupVerifyResult.CandidateStale(result.inspection.classification)
                }
                is SetupVerifyWriteResult.Blocked ->
                    BackupSettingsSetupVerifyResult.Blocked(result.classification)
                SetupVerifyWriteResult.WriteFailed -> BackupSettingsSetupVerifyResult.WriteFailed
                SetupVerifyWriteResult.ExportFailed -> BackupSettingsSetupVerifyResult.ExportFailed
                SetupVerifyWriteResult.UnsafeDatabase -> BackupSettingsSetupVerifyResult.UnsafeDatabase
                SetupVerifyWriteResult.NoSession -> {
                    retainedInspectionToken = null
                    BackupSettingsSetupVerifyResult.NoSession
                }
                SetupVerifyWriteResult.Busy -> BackupSettingsSetupVerifyResult.Busy
            }
        }
    }

    suspend fun commitVerifiedCandidate(): BackupSettingsSetupCommitResult {
        val result = setupCoordinator.commitVerifiedCandidate()
        return setupBookkeeping.withLock {
            when (result) {
                is SetupCommitResult.Success -> {
                    retainedInspectionToken = null
                    BackupSettingsSetupCommitResult.Success(
                        verifiedCreatedAtEpochMillis = result.verifiedCreatedAtEpochMillis,
                        postSwitchCatchupScheduled = result.postSwitchCatchupScheduled,
                    )
                }
                SetupCommitResult.ReceiptMissing -> BackupSettingsSetupCommitResult.ReceiptMissing
                SetupCommitResult.ReceiptStale -> BackupSettingsSetupCommitResult.ReceiptStale
                SetupCommitResult.CommitFailed -> BackupSettingsSetupCommitResult.CommitFailed
                SetupCommitResult.InvalidUri -> BackupSettingsSetupCommitResult.InvalidConfiguration
                SetupCommitResult.Busy -> BackupSettingsSetupCommitResult.Busy
            }
        }
    }

    suspend fun abandonSetupSession(): BackupSettingsAbandonResult {
        return when (setupCoordinator.abandonSession()) {
            SetupAbandonResult.Success -> {
                clearRetainedToken()
                BackupSettingsAbandonResult.Success
            }
            SetupAbandonResult.Busy -> BackupSettingsAbandonResult.Busy
        }
    }

    /**
     * Non-suspending runtime-owned cleanup for Settings VM destruction.
     * Schedules [BackupFolderSetupCoordinator.abandonSessionWhenIdle] on [backupScope].
     */
    fun requestAbandonSetupSession() {
        abandonRequested.set(true)
        if (!abandonInFlight.compareAndSet(false, true)) {
            return
        }
        backupScope.launch {
            try {
                do {
                    abandonRequested.set(false)
                    try {
                        setupCoordinator.abandonSessionWhenIdle()
                        clearRetainedToken()
                    } catch (cancel: CancellationException) {
                        abandonRequested.set(true)
                        throw cancel
                    } catch (_: Exception) {
                        abandonRequested.set(true)
                    }
                } while (abandonRequested.get())
            } finally {
                abandonInFlight.set(false)
                if (abandonRequested.get()) {
                    requestAbandonSetupSession()
                }
            }
        }
    }

    suspend fun reconnectFolderAccess(
        selectedUriString: String,
        grantFlags: Int,
    ): BackupReconnectResult {
        val state = writeStateRepository.snapshot()
        val authorized = state.authorizedTreeUri
        if (!BackupTreeUriSanitizer.isUsableTreeUriString(authorized)) {
            return BackupReconnectResult.NotConfigured
        }
        if (!state.needsReconnect && state.lastFailureCategory !=
            com.me4hik.praktika.data.backup.write.BackupFailureStatus.PERMISSION_LOST
        ) {
            return BackupReconnectResult.NotReconnectRequired
        }
        if (selectedUriString != authorized) {
            return BackupReconnectResult.DifferentFolder
        }
        val authA = authorized
        return when (val acquired = reconnectAccess.acquireAndValidate(authA, grantFlags)) {
            BackupReconnectAcquireResult.PermissionAcquireFailed ->
                BackupReconnectResult.PermissionFailure

            BackupReconnectAcquireResult.ValidationFailed -> {
                applyPostTakeGrantOwnership(authA)
                BackupReconnectResult.ValidationFailure
            }

            BackupReconnectAcquireResult.AcquiredAndValid -> {
                when (val mark = writeStateRepository.markReconnectSucceeded(authA)) {
                    ReconnectResult.Success -> {
                        authorizedBackupService.requestBackup(
                            reason = BackupRequestReason.MANUAL,
                            trigger = BackupAttemptTrigger.MANUAL,
                        )
                        BackupReconnectResult.Success
                    }
                    ReconnectResult.UriMismatch,
                    ReconnectResult.NoAuthorizedFolder,
                    -> {
                        applyPostTakeGrantOwnership(authA)
                        BackupReconnectResult.StaleConfiguration
                    }
                }
            }
        }
    }

    suspend fun runBackupNow(): BackupNowResult {
        val result = authorizedBackupService.backupNow(
            reason = BackupRequestReason.MANUAL,
            trigger = BackupAttemptTrigger.MANUAL,
        )
        val mapped = BackupNowResultMapper.map(result)
        if (mapped == BackupNowResult.Written) {
            runCatching { analyticsTracker.track(ProductAnalyticsEvents.backupCreated()) }
        }
        return mapped
    }

    suspend fun disableAutomaticBackup(): BackupSettingsDisableResult {
        return when (setupCoordinator.disableAutomaticBackupAndReleasePermission()) {
            DisableAutomaticBackupResult.Success -> BackupSettingsDisableResult.Success
            DisableAutomaticBackupResult.DisabledWithPermissionCleanupWarning ->
                BackupSettingsDisableResult.PermissionCleanupWarning
            DisableAutomaticBackupResult.AuthClearFailed -> BackupSettingsDisableResult.Failed
            DisableAutomaticBackupResult.Busy -> BackupSettingsDisableResult.Busy
        }
    }

    /**
     * Post-take ownership: KEEP if auth still A or switched to other non-null;
     * RELEASE only if auth cleared to null (disable won), under [ioGate] with recheck.
     */
    private suspend fun applyPostTakeGrantOwnership(candidateUri: String) {
        ioGate.withExclusive {
            val current = writeStateRepository.snapshot().authorizedTreeUri
            when {
                !BackupTreeUriSanitizer.isUsableTreeUriString(current) -> {
                    try {
                        reconnectAccess.releasePersistedGrant(
                            uriString = candidateUri,
                            grantFlags = BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS,
                        )
                    } catch (_: Exception) {
                        // best-effort
                    }
                }
                current == candidateUri -> Unit
                else -> Unit // auth B/other: KEEP (replacement policy)
            }
        }
    }

    private suspend fun clearRetainedToken() {
        setupBookkeeping.withLock {
            retainedInspectionToken = null
        }
    }

    /** Host-test seam: retained opaque token presence (never the value). */
    internal fun hasRetainedInspectionTokenForTests(): Boolean = retainedInspectionToken != null
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END

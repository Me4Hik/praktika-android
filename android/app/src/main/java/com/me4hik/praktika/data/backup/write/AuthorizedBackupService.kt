// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2A authorized backup service
package com.me4hik.praktika.data.backup.write

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Isolated automatic-backup facade.
 * Owns cross-request coalescing and re-resolves authorized URI on every attempt/rerun.
 *
 * Stage 6.2B1: constructed once on [com.me4hik.praktika.runtime.PraktikaRuntime] and invoked
 * for STARTUP_CATCHUP after APP_START notification sync.
 * Stage 6.2B2-B: owns restore session phases + committed authorize handoff / RESTORE_CATCHUP.
 * Mutation sinks remain unwired (Stage 6.2C).
 *
 * [requestBackup] is non-throwing to callers and uses the injected [scope].
 * Per-attempt Stage 6.0 coordinator is created via [coordinatorFactory] and invoked with [ProductionBackupCoordinator.backupNow]
 * (not requestBackup) to avoid double coalescing.
 */
class AuthorizedBackupService(
    private val writeStateRepository: BackupWriteStateRepository,
    private val storageResolver: AuthorizedBackupStorageResolver,
    private val coordinatorFactory: ProductionBackupCoordinatorFactory,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val ioGate: BackupIoSessionGate = BackupIoSessionGate(),
) {
    private val writerMutex = Mutex()
    private val pendingLock = Any()

    @Volatile
    private var pendingRerun: Boolean = false

    @Volatile
    private var pendingReason: BackupRequestReason = BackupRequestReason.STARTUP_CATCHUP

    @Volatile
    private var pendingTrigger: BackupAttemptTrigger = BackupAttemptTrigger.STARTUP

    @Volatile
    private var requestLoopActive: Boolean = false

    @Volatile
    private var restoreSessionSuppressed: Boolean = false

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-B restore session phases
    private val restoreSessionLock = Any()

    private enum class RestoreSessionPhase {
        INACTIVE,
        UI_ACTIVE,
        COMMITTED_HANDOFF,
    }

    @Volatile
    private var restoreSessionPhase: RestoreSessionPhase = RestoreSessionPhase.INACTIVE

    @Volatile
    private var restoreSuppressedWorkPending: Boolean = false

    @Volatile
    private var committedHandoffJobActive: Boolean = false

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B1 initialization suppress
    @Volatile
    private var initializationActive: Boolean = false

    /** Exposed for runtime singleton / shared-gate identity tests and Stage 6.2B2 restore injection. */
    val sharedIoGate: BackupIoSessionGate
        get() = ioGate

    fun beginInitialization() {
        initializationActive = true
    }

    fun endInitialization() {
        initializationActive = false
    }

    fun isInitializationActive(): Boolean = initializationActive
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    fun beginRestoreSession() {
        synchronized(restoreSessionLock) {
            when (restoreSessionPhase) {
                RestoreSessionPhase.INACTIVE -> {
                    restoreSessionPhase = RestoreSessionPhase.UI_ACTIVE
                    restoreSessionSuppressed = true
                }
                RestoreSessionPhase.UI_ACTIVE -> {
                    // Idempotent duplicate begin.
                }
                RestoreSessionPhase.COMMITTED_HANDOFF -> {
                    // Reject: do not stomp committed ownership.
                }
            }
        }
    }

    /**
     * Noncommitted UI teardown only. From [RestoreSessionPhase.UI_ACTIVE]:
     * clears dirty + suppression; may enqueue dirty current-auth catch-up.
     *
     * [RestoreSessionEndReason.COMMITTED_DEFERRED_HANDOFF] from [RestoreSessionPhase.COMMITTED_HANDOFF]
     * is the auth-failure / start-fallback finalize (no RESTORE_CATCHUP).
     */
    fun endRestoreSession(reason: RestoreSessionEndReason) {
        val enqueueCatchUp = synchronized(restoreSessionLock) {
            when (restoreSessionPhase) {
                RestoreSessionPhase.INACTIVE -> false

                RestoreSessionPhase.UI_ACTIVE -> {
                    val dirty = restoreSuppressedWorkPending
                    restoreSuppressedWorkPending = false
                    restoreSessionPhase = RestoreSessionPhase.INACTIVE
                    restoreSessionSuppressed = false
                    reason == RestoreSessionEndReason.CANCELLED_OR_FAILED && dirty
                }

                RestoreSessionPhase.COMMITTED_HANDOFF -> {
                    // Fallback / deferred finalize without auth success.
                    val dirty = restoreSuppressedWorkPending
                    restoreSuppressedWorkPending = false
                    restoreSessionPhase = RestoreSessionPhase.INACTIVE
                    restoreSessionSuppressed = false
                    committedHandoffJobActive = false
                    dirty
                }
            }
        }
        if (enqueueCatchUp) {
            requestBackup(
                reason = BackupRequestReason.RUNTIME_RECONCILED,
                trigger = BackupAttemptTrigger.STARTUP,
            )
        }
    }

    /**
     * Synchronous ownership transfer: UI_ACTIVE → COMMITTED_HANDOFF.
     * Keeps suppression ON and retains dirty. Does not launch work.
     */
    fun beginCommittedRestoreHandoff(): CommittedHandoffStartResult {
        synchronized(restoreSessionLock) {
            return when (restoreSessionPhase) {
                RestoreSessionPhase.UI_ACTIVE -> {
                    restoreSessionPhase = RestoreSessionPhase.COMMITTED_HANDOFF
                    restoreSessionSuppressed = true
                    CommittedHandoffStartResult.Started
                }
                RestoreSessionPhase.COMMITTED_HANDOFF ->
                    CommittedHandoffStartResult.AlreadyStarted
                RestoreSessionPhase.INACTIVE ->
                    CommittedHandoffStartResult.Unavailable
            }
        }
    }

    /**
     * Claims COMMITTED_HANDOFF (if needed) and launches exactly one runtime-scope auth job.
     * Non-blocking: does not await DataStore / SAF / catch-up.
     */
    fun startCommittedHandoff(): CommittedHandoffStartResult {
        val claim = beginCommittedRestoreHandoff()
        if (claim == CommittedHandoffStartResult.Unavailable) {
            return CommittedHandoffStartResult.Unavailable
        }
        val shouldLaunch = synchronized(restoreSessionLock) {
            if (restoreSessionPhase != RestoreSessionPhase.COMMITTED_HANDOFF) {
                return@synchronized false
            }
            if (committedHandoffJobActive) {
                return@synchronized false
            }
            committedHandoffJobActive = true
            true
        }
        if (!shouldLaunch) {
            return CommittedHandoffStartResult.AlreadyStarted
        }
        try {
            scope.launch(ioDispatcher) {
                runCommittedRestoreHandoffJob()
            }
        } catch (_: Exception) {
            // Scheduling failure: Room already committed — release without RESTORE_CATCHUP.
            endRestoreSession(RestoreSessionEndReason.COMMITTED_DEFERRED_HANDOFF)
            return CommittedHandoffStartResult.Unavailable
        }
        return claim
    }

    private suspend fun runCommittedRestoreHandoffJob() {
        try {
            val authOutcome = try {
                writeStateRepository.authorizeCurrentTreeUriHint()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                null
            }
            when (authOutcome) {
                AuthorizeCurrentHintResult.Success -> finalizeCommittedAuthSuccess()
                AuthorizeCurrentHintResult.NoUsableHint, null -> finalizeCommittedAuthFailure()
            }
        } catch (cancel: CancellationException) {
            // Process still alive: do not leave suppression stuck; no RESTORE_CATCHUP without auth.
            finalizeCommittedAuthFailure()
            throw cancel
        } finally {
            synchronized(restoreSessionLock) {
                committedHandoffJobActive = false
            }
        }
    }

    private fun finalizeCommittedAuthSuccess() {
        val shouldCatchUp = synchronized(restoreSessionLock) {
            if (restoreSessionPhase != RestoreSessionPhase.COMMITTED_HANDOFF) {
                return@synchronized false
            }
            restoreSuppressedWorkPending = false
            restoreSessionPhase = RestoreSessionPhase.INACTIVE
            restoreSessionSuppressed = false
            true
        }
        if (shouldCatchUp) {
            requestBackup(
                reason = BackupRequestReason.RESTORE_CATCHUP,
                trigger = BackupAttemptTrigger.RESTORE_CATCHUP,
            )
        }
    }

    private fun finalizeCommittedAuthFailure() {
        val enqueueDirty = synchronized(restoreSessionLock) {
            if (restoreSessionPhase != RestoreSessionPhase.COMMITTED_HANDOFF) {
                return@synchronized false
            }
            val dirty = restoreSuppressedWorkPending
            restoreSuppressedWorkPending = false
            restoreSessionPhase = RestoreSessionPhase.INACTIVE
            restoreSessionSuppressed = false
            dirty
        }
        if (enqueueDirty) {
            requestBackup(
                reason = BackupRequestReason.RUNTIME_RECONCILED,
                trigger = BackupAttemptTrigger.STARTUP,
            )
        }
    }

    fun isRestoreSessionActive(): Boolean {
        return restoreSessionPhase != RestoreSessionPhase.INACTIVE
    }

    fun isRestoreSessionSuppressed(): Boolean = restoreSessionSuppressed

    /** Host-test seam: whether a suppressed request marked dirty work. */
    internal fun isRestoreSuppressedWorkPendingForTests(): Boolean = restoreSuppressedWorkPending

    /** Host-test seam: current restore session phase name. */
    internal fun restoreSessionPhaseForTests(): String = restoreSessionPhase.name

    /** Backward-compatible alias used by Stage 6.2A tests. */
    fun suppressRestoreSession() {
        beginRestoreSession()
    }

    /** Backward-compatible alias: ends as cancel/failure (dirty catch-up if pending). */
    fun releaseRestoreSession() {
        endRestoreSession(RestoreSessionEndReason.CANCELLED_OR_FAILED)
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B1 request tap for host tests
    @Volatile
    internal var onRequestBackupForTests: ((BackupRequestReason, BackupAttemptTrigger) -> Unit)? = null
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    /**
     * Fire-and-forget coalesced request. Never throws into the caller.
     * Does not await ContentResolver / Room export on the caller's thread.
     */
    fun requestBackup(
        reason: BackupRequestReason,
        trigger: BackupAttemptTrigger = BackupAttemptTrigger.MUTATION,
    ) {
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B1 request tap for host tests
        onRequestBackupForTests?.invoke(reason, trigger)
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        try {
            synchronized(pendingLock) {
                pendingRerun = true
                pendingReason = reason
                pendingTrigger = trigger
                if (requestLoopActive) {
                    return
                }
                requestLoopActive = true
            }
            scope.launch(ioDispatcher) {
                try {
                    backupNow(reason, trigger)
                } catch (_: CancellationException) {
                    // Supervisor-style: one cancelled attempt must not kill the service contract.
                } catch (_: Exception) {
                    // Backup is secondary durability; never propagate into mutation callers.
                } finally {
                    synchronized(pendingLock) {
                        requestLoopActive = false
                        if (pendingRerun) {
                            val nextReason = pendingReason
                            val nextTrigger = pendingTrigger
                            requestBackup(nextReason, nextTrigger)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Construction/scheduling failure must not reach mutation callers.
        }
    }

    suspend fun backupNow(
        reason: BackupRequestReason,
        trigger: BackupAttemptTrigger = BackupAttemptTrigger.MUTATION,
    ): AuthorizedBackupResult {
        return writerMutex.withLock {
            executeCoalesced(reason, trigger)
        }
    }

    private suspend fun executeCoalesced(
        initialReason: BackupRequestReason,
        initialTrigger: BackupAttemptTrigger,
    ): AuthorizedBackupResult {
        var reason = initialReason
        var trigger = initialTrigger
        var last: AuthorizedBackupResult
        do {
            synchronized(pendingLock) {
                pendingRerun = false
            }
            last = executeSingleAttempt(reason, trigger)
            val pending = synchronized(pendingLock) {
                if (pendingRerun) {
                    reason = pendingReason
                    trigger = pendingTrigger
                    true
                } else {
                    false
                }
            }
            if (!pending) {
                break
            }
        } while (true)
        return last
    }

    private suspend fun executeSingleAttempt(
        reason: BackupRequestReason,
        trigger: BackupAttemptTrigger,
    ): AuthorizedBackupResult {
        reason // diagnostics only; must not alter write policy
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A suppress + dirty
        if (restoreSessionSuppressed) {
            synchronized(restoreSessionLock) {
                if (restoreSessionSuppressed) {
                    restoreSuppressedWorkPending = true
                }
            }
            return AuthorizedBackupResult(AuthorizedBackupResultKind.Suppressed)
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END

        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B1 init mutation suppress
        if (initializationActive && trigger == BackupAttemptTrigger.MUTATION) {
            return AuthorizedBackupResult(AuthorizedBackupResultKind.SuppressedInitialization)
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END

        val state = writeStateRepository.snapshot()
        val authorizedRaw = state.authorizedTreeUri
        if (authorizedRaw == null) {
            return AuthorizedBackupResult(AuthorizedBackupResultKind.NotConfigured)
        }
        if (!BackupTreeUriSanitizer.isUsableTreeUriString(authorizedRaw)) {
            return AuthorizedBackupResult(AuthorizedBackupResultKind.InvalidConfiguration)
        }
        val attemptUri = authorizedRaw

        if (state.needsReconnect) {
            return AuthorizedBackupResult(AuthorizedBackupResultKind.NeedsReconnect)
        }

        if (
            !BackupFailureRetryPolicy.allowsAttempt(
                status = state.lastFailureCategory,
                trigger = trigger,
                needsReconnect = state.needsReconnect,
            )
        ) {
            return AuthorizedBackupResult(AuthorizedBackupResultKind.RetrySuppressed)
        }

        return ioGate.withExclusive {
            when (val resolved = storageResolver.resolve(attemptUri)) {
                AuthorizedStorageResolveResult.PermissionLost -> {
                    val status = persistNeedsReconnect(attemptUri)
                    AuthorizedBackupResult(
                        kind = AuthorizedBackupResultKind.NeedsReconnect,
                        statusUpdate = status.update,
                        statusPersistenceFailed = status.failed,
                    )
                }

                AuthorizedStorageResolveResult.Unavailable -> {
                    persistFailure(attemptUri, BackupFailureStatus.STORAGE_UNAVAILABLE)
                }

                AuthorizedStorageResolveResult.ProviderError -> {
                    persistFailure(attemptUri, BackupFailureStatus.STORAGE_UNAVAILABLE)
                }

                is AuthorizedStorageResolveResult.Ready -> {
                    val coordinator = coordinatorFactory.create(resolved.storage)
                    val outcome = coordinator.backupNow(reason)
                    persistCoordinatorOutcome(attemptUri, outcome)
                }
            }
        }
    }

    private suspend fun persistCoordinatorOutcome(
        attemptUri: String,
        outcome: BackupOutcome,
    ): AuthorizedBackupResult {
        return try {
            val statusUpdate = when (outcome) {
                is BackupOutcome.Written ->
                    writeStateRepository.markBackupSuccessIfAuthorized(
                        expectedAuthorizedUri = attemptUri,
                        successfulBackupAtEpochMillis = outcome.createdAtEpochMillis,
                    )

                is BackupOutcome.NoChange ->
                    writeStateRepository.markNoChangeHealthyIfAuthorized(
                        expectedAuthorizedUri = attemptUri,
                        latestValidBackupCreatedAtEpochMillis =
                            outcome.latestValidBackupCreatedAtEpochMillis,
                    )

                else -> {
                    val failure = BackupOutcomeStatusMapper.failureStatusOf(outcome)
                        ?: BackupFailureStatus.UNKNOWN
                    writeStateRepository.markBackupFailureIfAuthorized(
                        expectedAuthorizedUri = attemptUri,
                        category = failure,
                    )
                }
            }
            AuthorizedBackupResult(
                kind = AuthorizedBackupResultKind.CoordinatorCompleted,
                coordinatorOutcome = outcome,
                statusUpdate = statusUpdate,
                statusPersistenceFailed = false,
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            AuthorizedBackupResult(
                kind = AuthorizedBackupResultKind.CoordinatorCompleted,
                coordinatorOutcome = outcome,
                statusUpdate = null,
                statusPersistenceFailed = true,
            )
        }
    }

    private suspend fun persistFailure(
        attemptUri: String,
        category: BackupFailureStatus,
    ): AuthorizedBackupResult {
        return try {
            val status = writeStateRepository.markBackupFailureIfAuthorized(attemptUri, category)
            AuthorizedBackupResult(
                kind = if (category == BackupFailureStatus.PERMISSION_LOST) {
                    AuthorizedBackupResultKind.NeedsReconnect
                } else {
                    AuthorizedBackupResultKind.CoordinatorCompleted
                },
                coordinatorOutcome = null,
                statusUpdate = status,
                statusPersistenceFailed = false,
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            AuthorizedBackupResult(
                kind = AuthorizedBackupResultKind.CoordinatorCompleted,
                statusPersistenceFailed = true,
            )
        }
    }

    private data class PersistStatus(
        val update: ConditionalStatusUpdateResult?,
        val failed: Boolean,
    )

    private suspend fun persistNeedsReconnect(attemptUri: String): PersistStatus {
        return try {
            PersistStatus(
                update = writeStateRepository.markNeedsReconnectIfAuthorized(attemptUri),
                failed = false,
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            PersistStatus(update = null, failed = true)
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END

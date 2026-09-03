// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - idempotent init gate
package com.me4hik.praktika.runtime

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.me4hik.praktika.data.backup.write.BackupAttemptTrigger
import com.me4hik.praktika.data.backup.write.BackupRequestReason
import com.me4hik.praktika.data.seed.DatabaseSeeder
import com.me4hik.praktika.diagnostics.TargetedBugDiagnostics
import com.me4hik.praktika.notification.NotificationSyncReason
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PraktikaRuntimeInitializer(
    private val context: Context,
    private val runtimeProvider: () -> PraktikaRuntime,
) {
    private val initMutex = Mutex()
    private val lock = Any()

    @Volatile
    private var initDeferred: CompletableDeferred<Boolean> = CompletableDeferred()

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B1 startup sync test seam
    /**
     * When non-null, replaces [PracticeNotificationCoordinator.sync](APP_START) for host tests.
     * Production always leaves this null so real APP_START sync runs.
     */
    @Volatile
    internal var appStartSyncOverrideForTests: (suspend () -> Unit)? = null
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    // 03.09.2026 Case2 init internal observability cursor by Me4Hik START - seed/reconcile failure injection seams
    /**
     * When non-null, replaces [DatabaseSeeder.seedFromAssets] for host tests.
     * Production always leaves this null.
     */
    @Volatile
    internal var seedFromAssetsOverrideForTests: (suspend () -> Unit)? = null

    /**
     * When non-null, replaces [com.me4hik.praktika.data.cycle.CycleRepository.syncEnvironmentAndReconcile]
     * for host tests. Production always leaves this null.
     */
    @Volatile
    internal var syncEnvironmentAndReconcileOverrideForTests: (suspend () -> Unit)? = null
    // 03.09.2026 Case2 init internal observability cursor by Me4Hik END

    suspend fun ensureInitialized(): Boolean {
        val existing = initDeferred
        if (existing.isCompleted) {
            return existing.getCompleted()
        }
        return initMutex.withLock {
            if (initDeferred.isCompleted) {
                return@withLock initDeferred.getCompleted()
            }
            val deferred = initDeferred
            val runtime = runtimeProvider()
            val backupService = runtime.authorizedBackupService
            // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B1 init suppress + startup catch-up
            backupService.beginInitialization()
            // 03.09.2026 Case2 init internal observability cursor by Me4Hik START - stage tracking for sticky false
            var failureStage = STAGE_OTHER
            try {
                failureStage = STAGE_SEED
                val seedOverride = seedFromAssetsOverrideForTests
                if (seedOverride != null) {
                    seedOverride()
                } else {
                    DatabaseSeeder().seedFromAssets(context, runtime.database)
                }
                failureStage = STAGE_RECONCILE
                val reconcileOverride = syncEnvironmentAndReconcileOverrideForTests
                if (reconcileOverride != null) {
                    reconcileOverride()
                } else {
                    runtime.cycleRepository.syncEnvironmentAndReconcile()
                }
                // 06.08.2026 Stage 12 Connected Navigation Fix cursor by Me4Hik START - complete init before nested sync
                deferred.complete(true)
                val syncOverride = appStartSyncOverrideForTests
                if (syncOverride != null) {
                    syncOverride()
                } else {
                    runtime.notificationCoordinator.sync(NotificationSyncReason.APP_START)
                }
                // 06.08.2026 Stage 12 Connected Navigation Fix cursor by Me4Hik END
                // STARTUP_ROOM_QUIESCENT_POINT = after APP_START sync returns
                if (runtime.startupCatchupOnce.compareAndSet(false, true)) {
                    backupService.requestBackup(
                        reason = BackupRequestReason.STARTUP_CATCHUP,
                        trigger = BackupAttemptTrigger.STARTUP,
                    )
                }
                true
            } catch (exception: Exception) {
                if (!deferred.isCompleted) {
                    val recordedStage = if (exception is CancellationException) {
                        STAGE_CANCELLED
                    } else {
                        failureStage
                    }
                    try {
                        TargetedBugDiagnostics.recordRuntimeInitFailed(
                            failureStage = recordedStage,
                            exceptionClass = exception.javaClass.name,
                            wallClockMs = System.currentTimeMillis(),
                            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
                        )
                    } catch (_: Exception) {
                        // Diagnostic write must never alter sticky-false init outcome.
                    }
                }
                Log.e(TAG, "Runtime initialization failed", exception)
                deferred.complete(false)
                false
            } finally {
                backupService.endInitialization()
            }
            // 03.09.2026 Case2 init internal observability cursor by Me4Hik END
            // 10.08.2026 Post-release fixes cursor by Me4Hik END
        }
    }

    fun beginActivityInit() {
        synchronized(lock) {
            if (initDeferred.isCompleted) {
                initDeferred = CompletableDeferred()
            }
        }
    }

    suspend fun awaitActivityInit(): Boolean {
        return initDeferred.await()
    }

    fun completeActivityInit(success: Boolean) {
        synchronized(lock) {
            if (!initDeferred.isCompleted) {
                initDeferred.complete(success)
            }
        }
    }

    internal fun resetForTests() {
        synchronized(lock) {
            initDeferred = CompletableDeferred()
            appStartSyncOverrideForTests = null
            // 03.09.2026 Case2 init internal observability cursor by Me4Hik START - clear init failure seams
            seedFromAssetsOverrideForTests = null
            syncEnvironmentAndReconcileOverrideForTests = null
            // 03.09.2026 Case2 init internal observability cursor by Me4Hik END
        }
    }

    private companion object {
        const val TAG = "PraktikaRuntimeInit"
        // 03.09.2026 Case2 init internal observability cursor by Me4Hik START - failure stage labels
        const val STAGE_SEED = "SEED"
        const val STAGE_RECONCILE = "RECONCILE"
        const val STAGE_CANCELLED = "CANCELLED"
        const val STAGE_OTHER = "OTHER"
        // 03.09.2026 Case2 init internal observability cursor by Me4Hik END
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END

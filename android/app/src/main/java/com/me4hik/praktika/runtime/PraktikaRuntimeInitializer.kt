// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - idempotent init gate
package com.me4hik.praktika.runtime

import android.content.Context
import android.util.Log
import com.me4hik.praktika.data.seed.DatabaseSeeder
import com.me4hik.praktika.notification.NotificationSyncReason
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
            try {
                val runtime = runtimeProvider()
                DatabaseSeeder().seedFromAssets(context, runtime.database)
                runtime.cycleRepository.syncEnvironmentAndReconcile()
                // 06.08.2026 Stage 12 Connected Navigation Fix cursor by Me4Hik START - complete init before nested sync
                deferred.complete(true)
                runtime.notificationCoordinator.sync(NotificationSyncReason.APP_START)
                // 06.08.2026 Stage 12 Connected Navigation Fix cursor by Me4Hik END
                true
            } catch (exception: Exception) {
                Log.e(TAG, "Runtime initialization failed", exception)
                deferred.complete(false)
                false
            }
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
        }
    }

    private companion object {
        const val TAG = "PraktikaRuntimeInit"
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END

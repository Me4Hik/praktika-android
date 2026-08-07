// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - foreground cycle driver
// 05.08.2026 Accelerated Driver Fix cursor by Me4Hik START - reconcile/checkpoint на IO dispatcher
package com.me4hik.praktika.accelerated

import android.util.Log
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.notification.NotificationSyncRequester
import com.me4hik.praktika.runtime.RuntimeForegroundDriver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class AcceleratedCycleDriver(
    private val cycleRepository: CycleRepository,
    private val clockController: AcceleratedClockController,
    private val notificationSyncRequester: NotificationSyncRequester? = null,
) : RuntimeForegroundDriver {

    private val loopMutex = Mutex()
    private var lastLoggedResult: CycleResult? = null

    suspend fun tickOnce() {
        withContext(Dispatchers.IO) {
            val result = cycleRepository.reconcile()
            if (result != lastLoggedResult && result.isChangedState()) {
                Log.i(TAG, "reconcile result=$result virtualNow=${clockController.currentVirtualNow()}")
                lastLoggedResult = result
            }
            clockController.checkpoint()
            notificationSyncRequester?.requestSync(NotificationSyncReason.FOREGROUND)
        }
    }

    override suspend fun runWhileForeground() {
        loopMutex.withLock {
            while (coroutineContext.isActive) {
                try {
                    tickOnce()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.e(TAG, "${exception.javaClass.simpleName}: ${exception.message}", exception)
                }
                delay(TICK_INTERVAL_MILLIS)
            }
        }
    }

    override suspend fun onForegroundStopped() {
        withContext(Dispatchers.IO) {
            clockController.checkpoint()
        }
    }

    private fun CycleResult.isChangedState(): Boolean {
        return this != CycleResult.ReconcileNoChanges &&
            this != CycleResult.ReconcileNotStarted &&
            this != CycleResult.ReconcilePaused
    }

    companion object {
        private const val TAG = "AcceleratedDriver"
        private const val TICK_INTERVAL_MILLIS = 500L
    }
}
// 05.08.2026 Accelerated Driver Fix cursor by Me4Hik END
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END

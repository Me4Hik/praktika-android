// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - production foreground driver
package com.me4hik.praktika.runtime

import com.me4hik.praktika.data.read.PracticeReadRepository
import com.me4hik.praktika.data.read.PracticeReadSnapshot
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.notification.PracticeNotificationCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
import kotlin.math.max

@OptIn(ExperimentalCoroutinesApi::class)
class ProductionForegroundDriver(
    private val coordinator: PracticeNotificationCoordinator,
    private val practiceReadRepository: PracticeReadRepository,
    private val nowEpochMillis: () -> Long,
) : RuntimeForegroundDriver {

    private val loopMutex = Mutex()

    override suspend fun runWhileForeground() {
        loopMutex.withLock {
            coroutineScope {
                practiceReadRepository.observeSnapshot()
                    .flatMapLatest { snapshot ->
                        flow {
                            emit(snapshot)
                            val delayMillis = nextBoundaryDelayMillis(snapshot) ?: return@flow
                            if (delayMillis > 0L) {
                                delay(delayMillis)
                                emit(snapshot)
                            }
                        }
                    }
                    .collect {
                        if (!coroutineContext.isActive) {
                            return@collect
                        }
                        coordinator.sync(NotificationSyncReason.FOREGROUND)
                    }
            }
        }
    }

    override suspend fun onForegroundStopped() = Unit

    private fun nextBoundaryDelayMillis(snapshot: PracticeReadSnapshot): Long? {
        if (!snapshot.practiceState.isPracticeStarted || snapshot.practiceState.isPaused) {
            return null
        }
        val occurrence = snapshot.incompleteOccurrence ?: return null
        val now = nowEpochMillis()
        val nextBoundary = when {
            now < occurrence.plannedAtEpochMillis -> occurrence.plannedAtEpochMillis
            now < occurrence.availableUntilEpochMillis -> occurrence.availableUntilEpochMillis
            else -> now
        }
        return max(0L, nextBoundary - now)
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END

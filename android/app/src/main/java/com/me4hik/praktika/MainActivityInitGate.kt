// 05.08.2026 Accelerated Driver Fix cursor by Me4Hik START - gate seed/init перед foreground driver
package com.me4hik.praktika

import kotlinx.coroutines.CompletableDeferred

object MainActivityInitGate {
    private val lock = Any()

    @Volatile
    private var initDeferred: CompletableDeferred<Unit> = CompletableDeferred()

    fun beginInit() {
        synchronized(lock) {
            if (initDeferred.isCompleted) {
                initDeferred = CompletableDeferred()
            }
        }
    }

    fun completeInit() {
        synchronized(lock) {
            initDeferred.complete(Unit)
        }
    }

    suspend fun awaitInit() {
        initDeferred.await()
    }

    fun isCompleted(): Boolean = initDeferred.isCompleted

    internal fun resetForTests() {
        synchronized(lock) {
            initDeferred = CompletableDeferred()
        }
    }
}
// 05.08.2026 Accelerated Driver Fix cursor by Me4Hik END

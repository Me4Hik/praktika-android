// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - accelerated TimeProvider
package com.me4hik.praktika.accelerated

import android.util.Log
import com.me4hik.praktika.data.cycle.TimeProvider
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking

class AcceleratedTimeProvider(
    initialState: AcceleratedClockState,
    private val persister: AcceleratedClockPersister,
    private val monotonic: MonotonicTimeSource,
) : TimeProvider, AcceleratedClockController {

    private val stateRef = AtomicReference(initialState)
    private val mutationLock = Any()

    override fun nowEpochMillis(): Long = currentVirtualNow()

    override fun currentZoneId(): String = stateRef.get().zoneId

    override fun currentVirtualNow(): Long {
        val state = stateRef.get()
        if (state.isVirtualClockPaused) {
            return state.pausedVirtualEpochMillis
                ?: throw AcceleratedClockCorruptionException("Paused clock missing frozen virtual time")
        }
        return computeRunningVirtualNow(state)
    }

    override fun currentState(): AcceleratedClockState = stateRef.get()

    override fun pauseVirtualClock() {
        synchronized(mutationLock) {
            val state = stateRef.get()
            val current = currentVirtualNow()
            val updated = state.copy(
                isVirtualClockPaused = true,
                pausedVirtualEpochMillis = current,
                lastCheckpointVirtualEpochMillis = maxOf(state.lastCheckpointVirtualEpochMillis, current),
            )
            persist(updated)
        }
    }

    override fun resumeVirtualClock() {
        synchronized(mutationLock) {
            val state = stateRef.get()
            if (!state.isVirtualClockPaused) {
                return
            }
            val frozen = state.pausedVirtualEpochMillis
                ?: throw AcceleratedClockCorruptionException("Cannot resume: pausedVirtualEpochMillis is null")
            val updated = state.copy(
                isVirtualClockPaused = false,
                pausedVirtualEpochMillis = null,
                virtualAnchorEpochMillis = frozen,
                realAnchorElapsedRealtimeMillis = monotonic.elapsedRealtimeMillis(),
                lastCheckpointVirtualEpochMillis = maxOf(state.lastCheckpointVirtualEpochMillis, frozen),
            )
            persist(updated)
        }
    }

    override fun setSpeedMultiplier(multiplier: Int) {
        requireAllowedMultiplier(multiplier)
        synchronized(mutationLock) {
            reAnchorAtCurrentVirtualNow(stateRef.get().copy(speedMultiplier = multiplier))
        }
    }

    override fun advanceByVirtualMinutes(minutes: Long) {
        if (minutes <= 0L) {
            throw AcceleratedClockCommandException("minutes must be positive")
        }
        val millis = safeMultiply(minutes, 60_000L)
        advanceToVirtualEpochMillis(safeAdd(currentVirtualNow(), millis))
    }

    override fun advanceToVirtualEpochMillis(target: Long) {
        if (target <= 0L) {
            throw AcceleratedClockCommandException("target must be positive")
        }
        synchronized(mutationLock) {
            val current = currentVirtualNow()
            if (target <= current) {
                throw AcceleratedClockCommandException("target must be greater than current virtualNow")
            }
            val state = stateRef.get()
            val checkpoint = maxOf(state.lastCheckpointVirtualEpochMillis, target)
            val updated = if (state.isVirtualClockPaused) {
                state.copy(
                    pausedVirtualEpochMillis = target,
                    virtualAnchorEpochMillis = target,
                    lastCheckpointVirtualEpochMillis = checkpoint,
                )
            } else {
                state.copy(
                    virtualAnchorEpochMillis = target,
                    realAnchorElapsedRealtimeMillis = monotonic.elapsedRealtimeMillis(),
                    lastCheckpointVirtualEpochMillis = checkpoint,
                )
            }
            persist(updated)
        }
    }

    override fun checkpoint() {
        synchronized(mutationLock) {
            val current = currentVirtualNow()
            val state = stateRef.get()
            val updated = state.copy(
                lastCheckpointVirtualEpochMillis = maxOf(state.lastCheckpointVirtualEpochMillis, current),
            )
            if (updated != state) {
                persist(updated)
            } else {
                runBlocking { persister.save(state) }
            }
        }
    }

    private fun reAnchorAtCurrentVirtualNow(state: AcceleratedClockState) {
        val current = currentVirtualNow()
        val updated = state.copy(
            virtualAnchorEpochMillis = current,
            realAnchorElapsedRealtimeMillis = monotonic.elapsedRealtimeMillis(),
            lastCheckpointVirtualEpochMillis = maxOf(state.lastCheckpointVirtualEpochMillis, current),
        )
        persist(updated)
    }

    private fun persist(state: AcceleratedClockState) {
        state.validate()
        stateRef.set(state)
        runBlocking { persister.save(state) }
    }

    private fun computeRunningVirtualNow(state: AcceleratedClockState): Long {
        val elapsedNow = monotonic.elapsedRealtimeMillis()
        val elapsedDelta = elapsedNow - state.realAnchorElapsedRealtimeMillis
        if (elapsedDelta < 0) {
            throw AcceleratedClockCorruptionException(
                "elapsedRealtime moved backwards within boot: anchor=${state.realAnchorElapsedRealtimeMillis}, now=$elapsedNow",
            )
        }
        val scaledDelta = safeMultiply(elapsedDelta, state.speedMultiplier.toLong())
        val virtualNow = safeAdd(state.virtualAnchorEpochMillis, scaledDelta)
        return maxOf(virtualNow, state.lastCheckpointVirtualEpochMillis)
    }

    private fun requireAllowedMultiplier(multiplier: Int) {
        if (multiplier !in AcceleratedClockState.ALLOWED_MULTIPLIERS) {
            throw AcceleratedClockCommandException("Invalid multiplier: $multiplier")
        }
    }

    companion object {
        private const val TAG = "AcceleratedClock"

        fun applyBootPolicy(
            loaded: AcceleratedClockState,
            monotonic: MonotonicTimeSource,
            databaseFloorEpochMillis: Long,
        ): AcceleratedClockState {
            val currentBootCount = monotonic.bootCount()
            val savedBootCount = loaded.bootCount
            val rebootDetected = when {
                currentBootCount == AndroidMonotonicTimeSource.BOOT_COUNT_UNAVAILABLE ||
                    savedBootCount == AndroidMonotonicTimeSource.BOOT_COUNT_UNAVAILABLE -> {
                    val elapsedNow = monotonic.elapsedRealtimeMillis()
                    val suspectReboot = elapsedNow < loaded.realAnchorElapsedRealtimeMillis
                    if (suspectReboot) {
                        Log.w(TAG, "Suspected reboot: elapsedRealtime regressed")
                    }
                    suspectReboot
                }
                currentBootCount != savedBootCount -> true
                else -> false
            }

            if (!rebootDetected) {
                return loaded
            }

            val persistedFloor = loaded.lastCheckpointVirtualEpochMillis
            val safeVirtual = maxOf(persistedFloor, databaseFloorEpochMillis)
            return loaded.copy(
                bootCount = if (currentBootCount == AndroidMonotonicTimeSource.BOOT_COUNT_UNAVAILABLE) {
                    savedBootCount
                } else {
                    currentBootCount
                },
                isVirtualClockPaused = true,
                pausedVirtualEpochMillis = safeVirtual,
                virtualAnchorEpochMillis = safeVirtual,
                realAnchorElapsedRealtimeMillis = monotonic.elapsedRealtimeMillis(),
                lastCheckpointVirtualEpochMillis = safeVirtual,
            )
        }

        fun safeMultiply(left: Long, right: Long): Long {
            if (left == 0L || right == 0L) {
                return 0L
            }
            if (left > Long.MAX_VALUE / right) {
                throw AcceleratedClockOverflowException("Multiplication overflow: $left * $right")
            }
            return left * right
        }

        fun safeAdd(left: Long, right: Long): Long {
            if (right > 0 && left > Long.MAX_VALUE - right) {
                throw AcceleratedClockOverflowException("Addition overflow: $left + $right")
            }
            return left + right
        }
    }
}
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END

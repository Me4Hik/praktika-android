// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - состояние виртуальных часов
package com.me4hik.praktika.accelerated

import java.time.ZoneId

data class AcceleratedClockState(
    val schemaVersion: Int = SCHEMA_VERSION,
    val bootCount: Int,
    val realAnchorElapsedRealtimeMillis: Long,
    val virtualAnchorEpochMillis: Long,
    val speedMultiplier: Int,
    val isVirtualClockPaused: Boolean,
    val pausedVirtualEpochMillis: Long?,
    val zoneId: String,
    val lastCheckpointVirtualEpochMillis: Long,
) {
    init {
        validate()
    }

    fun validate() {
        if (schemaVersion != SCHEMA_VERSION) {
            throw AcceleratedClockCorruptionException("Unsupported schemaVersion: $schemaVersion")
        }
        if (speedMultiplier !in ALLOWED_MULTIPLIERS) {
            throw AcceleratedClockCorruptionException("Invalid speedMultiplier: $speedMultiplier")
        }
        if (realAnchorElapsedRealtimeMillis < 0) {
            throw AcceleratedClockCorruptionException("realAnchorElapsedRealtimeMillis must be non-negative")
        }
        if (virtualAnchorEpochMillis < 0) {
            throw AcceleratedClockCorruptionException("virtualAnchorEpochMillis must be non-negative")
        }
        if (lastCheckpointVirtualEpochMillis < 0) {
            throw AcceleratedClockCorruptionException("lastCheckpointVirtualEpochMillis must be non-negative")
        }
        try {
            ZoneId.of(zoneId)
        } catch (_: Exception) {
            throw AcceleratedClockCorruptionException("Invalid zoneId: $zoneId")
        }
        if (isVirtualClockPaused) {
            if (pausedVirtualEpochMillis == null) {
                throw AcceleratedClockCorruptionException("pausedVirtualEpochMillis required when clock is paused")
            }
            if (pausedVirtualEpochMillis < 0) {
                throw AcceleratedClockCorruptionException("pausedVirtualEpochMillis must be non-negative")
            }
        } else if (pausedVirtualEpochMillis != null) {
            throw AcceleratedClockCorruptionException("pausedVirtualEpochMillis must be null when clock is running")
        }
    }

    companion object {
        const val SCHEMA_VERSION = 1
        // 06.08.2026 Stage 12 Real Alarm Proof cursor by Me4Hik START - 1× wall-sync for OS alarm delivery proof
        val ALLOWED_MULTIPLIERS = setOf(1, 60, 240, 600)
        // 06.08.2026 Stage 12 Real Alarm Proof cursor by Me4Hik END
        const val DEFAULT_MULTIPLIER = 240

        fun createInitial(
            zoneId: String,
            virtualStartEpochMillis: Long,
            bootCount: Int,
            realAnchorElapsedRealtimeMillis: Long,
        ): AcceleratedClockState = AcceleratedClockState(
            bootCount = bootCount,
            realAnchorElapsedRealtimeMillis = realAnchorElapsedRealtimeMillis,
            virtualAnchorEpochMillis = virtualStartEpochMillis,
            speedMultiplier = DEFAULT_MULTIPLIER,
            isVirtualClockPaused = true,
            pausedVirtualEpochMillis = virtualStartEpochMillis,
            zoneId = zoneId,
            lastCheckpointVirtualEpochMillis = virtualStartEpochMillis,
        )
    }
}
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END

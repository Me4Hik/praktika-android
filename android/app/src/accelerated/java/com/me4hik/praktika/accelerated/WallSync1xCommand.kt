// 14.08.2026 Accelerated device-setup harness cursor by Me4Hik START
package com.me4hik.praktika.accelerated

import android.util.Log
import kotlin.math.abs

/**
 * Accelerated-only: align virtual TimeProvider to real wall clock at 1×.
 * Does not accept host epoch extras. Does not mutate Room or system clock.
 */
object WallSync1xCommand {
    const val COMMAND = "wall_sync_1x"
    const val REQUIRED_PACKAGE = "com.me4hik.praktika.accelerated"
    const val WALL_SYNC_MAX_SKEW_MS = 500L
    const val TARGET_SPEED = 1

    const val STATUS_WALL_SYNCED = "WALL_SYNCED"
    const val STATUS_REJECTED_WRONG_PACKAGE = "REJECTED_WRONG_PACKAGE"
    const val STATUS_REJECTED_SKEW_TOO_LARGE = "REJECTED_SKEW_TOO_LARGE"

    data class CommandResult(
        val status: String,
        val wallEpochMs: Long,
        val virtualEpochMs: Long,
        val skewMs: Long,
        val speedMultiplier: Int,
        val clockRunning: Boolean,
        val mutatedClock: Boolean,
    )

    fun execute(
        packageName: String,
        readWallMs: () -> Long,
        clock: AcceleratedClockController,
    ): CommandResult {
        if (packageName != REQUIRED_PACKAGE) {
            val state = clock.currentState()
            val wall = readWallMs()
            val virtual = clock.currentVirtualNow()
            return logAndReturn(
                CommandResult(
                    status = STATUS_REJECTED_WRONG_PACKAGE,
                    wallEpochMs = wall,
                    virtualEpochMs = virtual,
                    skewMs = virtual - wall,
                    speedMultiplier = state.speedMultiplier,
                    clockRunning = !state.isVirtualClockPaused,
                    mutatedClock = false,
                ),
            )
        }

        applyAlignment(readWallMs(), clock)
        var measured = measure(readWallMs, clock)
        if (abs(measured.skewMs) > WALL_SYNC_MAX_SKEW_MS) {
            applyAlignment(measured.wallEpochMs, clock)
            measured = measure(readWallMs, clock)
        }

        val status = if (abs(measured.skewMs) <= WALL_SYNC_MAX_SKEW_MS) {
            STATUS_WALL_SYNCED
        } else {
            STATUS_REJECTED_SKEW_TOO_LARGE
        }
        return logAndReturn(measured.copy(status = status, mutatedClock = true))
    }

    private fun applyAlignment(wallTargetMs: Long, clock: AcceleratedClockController) {
        clock.alignVirtualEpochMillis(wallTargetMs)
        clock.setSpeedMultiplier(TARGET_SPEED)
        if (clock.currentState().isVirtualClockPaused) {
            clock.resumeVirtualClock()
        }
        clock.checkpoint()
    }

    private fun measure(
        readWallMs: () -> Long,
        clock: AcceleratedClockController,
    ): CommandResult {
        val wall = readWallMs()
        val virtual = clock.currentVirtualNow()
        val state = clock.currentState()
        return CommandResult(
            status = "",
            wallEpochMs = wall,
            virtualEpochMs = virtual,
            skewMs = virtual - wall,
            speedMultiplier = state.speedMultiplier,
            clockRunning = !state.isVirtualClockPaused,
            mutatedClock = true,
        )
    }

    private fun logAndReturn(result: CommandResult): CommandResult {
        val lines = listOf(
            "command=$COMMAND",
            "status=${result.status}",
            "wall_epoch_ms=${result.wallEpochMs}",
            "virtual_epoch_ms=${result.virtualEpochMs}",
            "skew_ms=${result.skewMs}",
            "speed_multiplier=${result.speedMultiplier}",
            "clock_running=${result.clockRunning}",
        )
        lines.forEach { Log.i(TAG, it) }
        return result
    }

    private const val TAG = "AcceleratedCommand"
}
// 14.08.2026 Accelerated device-setup harness cursor by Me4Hik END

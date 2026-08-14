// 14.08.2026 Accelerated device-setup harness cursor by Me4Hik START
package com.me4hik.praktika.accelerated

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallSync1xCommandTest {
    private val zoneId = "Europe/Kiev"
    private val eightAm = 1_775_217_600_000L

    @Test
    fun forwardVirtualBehindWall_syncsToOneXRunning() {
        val clock = FakeClock(
            virtualNow = eightAm,
            speed = 240,
            paused = true,
            checkpointFloor = eightAm,
        )
        val wall = eightAm + 2 * 60 * 60_000L
        val result = WallSync1xCommand.execute(
            packageName = WallSync1xCommand.REQUIRED_PACKAGE,
            readWallMs = { wall },
            clock = clock,
        )
        assertEquals(WallSync1xCommand.STATUS_WALL_SYNCED, result.status)
        assertEquals(wall, clock.currentVirtualNow())
        assertEquals(1, clock.currentState().speedMultiplier)
        assertFalse(clock.currentState().isVirtualClockPaused)
        assertTrue(clock.checkpointed)
        assertTrue(abs(result.skewMs) <= WallSync1xCommand.WALL_SYNC_MAX_SKEW_MS)
        assertTrue(result.clockRunning)
        assertTrue(result.mutatedClock)
    }

    @Test
    fun backwardVirtualAheadOfWall_alignsAbsolute() {
        val clock = FakeClock(
            virtualNow = eightAm,
            speed = 240,
            paused = true,
            checkpointFloor = eightAm,
        )
        val wall = eightAm - 3 * 60 * 60_000L
        val result = WallSync1xCommand.execute(
            packageName = WallSync1xCommand.REQUIRED_PACKAGE,
            readWallMs = { wall },
            clock = clock,
        )
        assertEquals(WallSync1xCommand.STATUS_WALL_SYNCED, result.status)
        assertEquals(wall, clock.currentVirtualNow())
        assertEquals(1, result.speedMultiplier)
        assertTrue(result.clockRunning)
        assertEquals(0L, result.skewMs)
    }

    @Test
    fun hostEpochExtrasAreNotReadByCommandApi() {
        val clock = FakeClock(virtualNow = eightAm, speed = 240, paused = true)
        val derivedWall = eightAm + 60_000L
        val hostileEpoch = 1L
        val result = WallSync1xCommand.execute(
            packageName = WallSync1xCommand.REQUIRED_PACKAGE,
            readWallMs = { derivedWall },
            clock = clock,
        )
        assertEquals(derivedWall, result.wallEpochMs)
        assertEquals(derivedWall, clock.alignedTo)
        assertTrue(hostileEpoch != result.wallEpochMs)
    }

    @Test
    fun wrongPackage_rejectsWithoutClockMutation() {
        val clock = FakeClock(virtualNow = eightAm, speed = 240, paused = true)
        val result = WallSync1xCommand.execute(
            packageName = "com.me4hik.praktika",
            readWallMs = { eightAm + 1_000L },
            clock = clock,
        )
        assertEquals(WallSync1xCommand.STATUS_REJECTED_WRONG_PACKAGE, result.status)
        assertFalse(result.mutatedClock)
        assertFalse(clock.aligned)
        assertEquals(240, clock.currentState().speedMultiplier)
        assertTrue(clock.currentState().isVirtualClockPaused)
        assertEquals(eightAm, clock.currentVirtualNow())
    }

    @Test
    fun skewWithin500_succeeds() {
        val clock = FakeClock(virtualNow = eightAm, speed = 1, paused = false)
        var reads = 0
        val result = WallSync1xCommand.execute(
            packageName = WallSync1xCommand.REQUIRED_PACKAGE,
            readWallMs = {
                reads++
                if (reads <= 1) eightAm else eightAm + 400L
            },
            clock = clock,
        )
        assertEquals(WallSync1xCommand.STATUS_WALL_SYNCED, result.status)
        assertTrue(abs(result.skewMs) <= 500L)
    }

    @Test
    fun skewAbove500AfterRetry_rejected() {
        val clock = FakeClock(virtualNow = eightAm, speed = 1, paused = false)
        var reads = 0
        val result = WallSync1xCommand.execute(
            packageName = WallSync1xCommand.REQUIRED_PACKAGE,
            readWallMs = {
                reads++
                eightAm + reads * 10_000L
            },
            clock = clock,
        )
        assertEquals(WallSync1xCommand.STATUS_REJECTED_SKEW_TOO_LARGE, result.status)
        assertTrue(abs(result.skewMs) > WallSync1xCommand.WALL_SYNC_MAX_SKEW_MS)
        assertTrue(result.mutatedClock)
    }

    @Test
    fun commandNameIsWallSync1x() {
        assertEquals("wall_sync_1x", WallSync1xCommand.COMMAND)
        assertEquals(500L, WallSync1xCommand.WALL_SYNC_MAX_SKEW_MS)
    }

    private inner class FakeClock(
        private var virtualNow: Long,
        private var speed: Int,
        private var paused: Boolean,
        private var checkpointFloor: Long = virtualNow,
    ) : AcceleratedClockController {
        var aligned = false
        var alignedTo: Long = 0L
        var checkpointed = false

        override fun pauseVirtualClock() {
            paused = true
        }

        override fun resumeVirtualClock() {
            paused = false
        }

        override fun setSpeedMultiplier(multiplier: Int) {
            speed = multiplier
        }

        override fun advanceByVirtualMinutes(minutes: Long) = Unit

        override fun advanceToVirtualEpochMillis(target: Long) {
            if (target <= virtualNow) {
                throw AcceleratedClockCommandException("advance only forward")
            }
            virtualNow = target
        }

        override fun alignVirtualEpochMillis(targetEpochMillis: Long) {
            aligned = true
            alignedTo = targetEpochMillis
            virtualNow = targetEpochMillis
            checkpointFloor = targetEpochMillis
        }

        override fun checkpoint() {
            checkpointed = true
        }

        override fun currentVirtualNow(): Long = virtualNow

        override fun currentState(): AcceleratedClockState = AcceleratedClockState(
            bootCount = 1,
            realAnchorElapsedRealtimeMillis = 0L,
            virtualAnchorEpochMillis = virtualNow,
            speedMultiplier = speed,
            isVirtualClockPaused = paused,
            pausedVirtualEpochMillis = if (paused) virtualNow else null,
            zoneId = zoneId,
            lastCheckpointVirtualEpochMillis = checkpointFloor,
        )
    }
}
// 14.08.2026 Accelerated device-setup harness cursor by Me4Hik END

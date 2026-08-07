// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - JVM tests accelerated clock
package com.me4hik.praktika.accelerated

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AcceleratedTimeProviderTest {
    private val zoneId = "Europe/Kiev"
    private val virtualAnchor = epochAtLocal(2026, 8, 4, 8, 0)

    @Test
    fun formulaAt60x() {
        val provider = createRunningProvider(multiplier = 60, elapsedDelta = 1_000L)
        assertEquals(virtualAnchor + 60_000L, provider.currentVirtualNow())
    }

    @Test
    fun formulaAt240x() {
        val provider = createRunningProvider(multiplier = 240, elapsedDelta = 1_000L)
        assertEquals(virtualAnchor + 240_000L, provider.currentVirtualNow())
    }

    @Test
    fun formulaAt600x() {
        val provider = createRunningProvider(multiplier = 600, elapsedDelta = 1_000L)
        assertEquals(virtualAnchor + 600_000L, provider.currentVirtualNow())
    }

    @Test
    fun pausedClockReturnsConstant() {
        val provider = createPausedProvider()
        val first = provider.currentVirtualNow()
        monotonic.setElapsedRealtimeMillis(999_999L)
        assertEquals(first, provider.currentVirtualNow())
    }

    @Test
    fun resumeDoesNotJumpVirtualNow() {
        val provider = createPausedProvider()
        val before = provider.currentVirtualNow()
        provider.resumeVirtualClock()
        assertEquals(before, provider.currentVirtualNow())
    }

    @Test
    fun speedChange240To60WithoutJump() {
        val provider = createRunningProvider(multiplier = 240, elapsedDelta = 2_000L)
        val before = provider.currentVirtualNow()
        provider.setSpeedMultiplier(60)
        assertEquals(before, provider.currentVirtualNow())
    }

    @Test
    fun speedChange240To600WithoutJump() {
        val provider = createRunningProvider(multiplier = 240, elapsedDelta = 2_000L)
        val before = provider.currentVirtualNow()
        provider.setSpeedMultiplier(600)
        assertEquals(before, provider.currentVirtualNow())
    }

    @Test
    fun invalidMultiplierRejected() {
        val provider = createPausedProvider()
        try {
            provider.setSpeedMultiplier(100)
            fail("Expected AcceleratedClockCommandException")
        } catch (_: AcceleratedClockCommandException) {
        }
    }

    @Test
    fun advanceOnlyForward() {
        val provider = createPausedProvider()
        try {
            provider.advanceToVirtualEpochMillis(virtualAnchor)
            fail("Expected AcceleratedClockCommandException")
        } catch (_: AcceleratedClockCommandException) {
        }
    }

    @Test
    fun multiplicationOverflowRejected() {
        try {
            AcceleratedTimeProvider.safeMultiply(Long.MAX_VALUE, 2L)
            fail("Expected AcceleratedClockOverflowException")
        } catch (_: AcceleratedClockOverflowException) {
        }
    }

    @Test
    fun additionOverflowRejected() {
        try {
            AcceleratedTimeProvider.safeAdd(Long.MAX_VALUE, 1L)
            fail("Expected AcceleratedClockOverflowException")
        } catch (_: AcceleratedClockOverflowException) {
        }
    }

    @Test
    fun virtualNowNotLessThanCheckpoint() {
        val provider = createRunningProvider(multiplier = 240, elapsedDelta = 0L)
        provider.checkpoint()
        monotonic.setElapsedRealtimeMillis(10_000L)
        assertTrue(provider.currentVirtualNow() >= virtualAnchor)
    }

    @Test
    fun initialStateUsesEightAm() {
        val start = epochAtLocal(2026, 8, 4, 8, 0)
        val state = AcceleratedClockState.createInitial(
            zoneId = zoneId,
            virtualStartEpochMillis = start,
            bootCount = 1,
            realAnchorElapsedRealtimeMillis = 0L,
        )
        assertEquals(start, state.pausedVirtualEpochMillis)
        assertTrue(state.isVirtualClockPaused)
        assertEquals(240, state.speedMultiplier)
    }

    @Test
    fun invalidZoneRejected() {
        try {
            AcceleratedClockState.createInitial(
                zoneId = "Invalid/Zone",
                virtualStartEpochMillis = 1L,
                bootCount = 1,
                realAnchorElapsedRealtimeMillis = 0L,
            )
            fail("Expected AcceleratedClockCorruptionException")
        } catch (_: AcceleratedClockCorruptionException) {
        }
    }

    @Test
    fun corruptedStateRejected() {
        try {
            AcceleratedClockState(
                bootCount = 1,
                realAnchorElapsedRealtimeMillis = 0L,
                virtualAnchorEpochMillis = 1L,
                speedMultiplier = 240,
                isVirtualClockPaused = true,
                pausedVirtualEpochMillis = null,
                zoneId = zoneId,
                lastCheckpointVirtualEpochMillis = 1L,
            )
            fail("Expected AcceleratedClockCorruptionException")
        } catch (_: AcceleratedClockCorruptionException) {
        }
    }

    @Test
    fun bootPolicyPausesAfterReboot() {
        val loaded = AcceleratedClockState.createInitial(
            zoneId = zoneId,
            virtualStartEpochMillis = virtualAnchor,
            bootCount = 1,
            realAnchorElapsedRealtimeMillis = 100_000L,
        ).copy(lastCheckpointVirtualEpochMillis = virtualAnchor + 10_000L)
        monotonic.setBootCount(2)
        val adjusted = AcceleratedTimeProvider.applyBootPolicy(loaded, monotonic, databaseFloorEpochMillis = virtualAnchor + 5_000L)
        assertTrue(adjusted.isVirtualClockPaused)
        assertEquals(virtualAnchor + 10_000L, adjusted.pausedVirtualEpochMillis)
    }

    private val monotonic = FakeMonotonicTimeSource(
        elapsedRealtimeMillis = 0L,
        wallClockEpochMillis = virtualAnchor,
        bootCount = 1,
        zoneId = zoneId,
    )

    private fun createPausedProvider(): AcceleratedTimeProvider {
        val state = AcceleratedClockState.createInitial(
            zoneId = zoneId,
            virtualStartEpochMillis = virtualAnchor,
            bootCount = 1,
            realAnchorElapsedRealtimeMillis = 0L,
        )
        return AcceleratedTimeProvider(state, InMemoryPersister(), monotonic)
    }

    private fun createRunningProvider(multiplier: Int, elapsedDelta: Long): AcceleratedTimeProvider {
        monotonic.setElapsedRealtimeMillis(elapsedDelta)
        val state = AcceleratedClockState(
            bootCount = 1,
            realAnchorElapsedRealtimeMillis = 0L,
            virtualAnchorEpochMillis = virtualAnchor,
            speedMultiplier = multiplier,
            isVirtualClockPaused = false,
            pausedVirtualEpochMillis = null,
            zoneId = zoneId,
            lastCheckpointVirtualEpochMillis = virtualAnchor,
        )
        return AcceleratedTimeProvider(state, InMemoryPersister(), monotonic)
    }

    private class InMemoryPersister : AcceleratedClockPersister {
        override suspend fun save(state: AcceleratedClockState) = Unit
    }

    private fun epochAtLocal(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of(zoneId))
            .toInstant()
            .toEpochMilli()
    }
}
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END

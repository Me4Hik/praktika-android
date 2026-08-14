// 14.08.2026 Accelerated device-setup harness cursor by Me4Hik START
package com.me4hik.praktika.accelerated

import com.me4hik.praktika.data.cycle.ScheduleCalculator
import com.me4hik.praktika.data.cycle.ScheduleSlotUpdate
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrepareNotificationBoundaryTestCommandTest {
    private val zoneId = "Europe/Kiev"
    private val zone = ZoneId.of(zoneId)

    @Test
    fun morningAligned_preparesWholeMinuteTAndThreeSlots() = runBlocking {
        val now = local(2026, 8, 14, 10, 0, 30)
        val captured = mutableListOf<List<ScheduleSlotUpdate>>()
        val result = successExecute(now, captured)
        assertEquals(PrepareNotificationBoundaryTestCommand.STATUS_BOUNDARY_TEST_PREPARED, result.status)
        assertEquals(1, captured.size)
        assertLeadAndSlots(now, result)
        assertNextOccurrenceIsT(now, result)
        assertTrue(result.targetLeadMs in 181_000L..239_999L)
    }

    @Test
    fun afternoonAligned_preparesValidSchedule() = runBlocking {
        val now = local(2026, 8, 14, 15, 30, 15)
        val captured = mutableListOf<List<ScheduleSlotUpdate>>()
        val result = successExecute(now, captured)
        assertEquals(PrepareNotificationBoundaryTestCommand.STATUS_BOUNDARY_TEST_PREPARED, result.status)
        assertLeadAndSlots(now, result)
        assertNextOccurrenceIsT(now, result)
        assertTrue(result.targetLeadMs in 181_000L..239_999L)
    }

    @Test
    fun midnight2350_selectsTodayT() = runBlocking {
        val now = local(2026, 8, 14, 23, 50, 0, nano = 1_000_000)
        val result = successExecute(now, mutableListOf())
        assertEquals(PrepareNotificationBoundaryTestCommand.STATUS_BOUNDARY_TEST_PREPARED, result.status)
        assertEquals(23 * 60 + 54, result.slot1Minutes)
        assertEquals(54, result.slot2Minutes)
        assertEquals(114, result.slot3Minutes)
        assertNextOccurrenceIsT(now, result)
        val tLocal = java.time.Instant.ofEpochMilli(result.targetPlannedAtMs!!)
            .atZone(zone)
        assertEquals(14, tLocal.dayOfMonth)
        assertEquals(23, tLocal.hour)
        assertEquals(54, tLocal.minute)
    }

    @Test
    fun midnight2358_selectsNextDayT() = runBlocking {
        val now = local(2026, 8, 14, 23, 58, 20)
        val result = successExecute(now, mutableListOf())
        assertEquals(PrepareNotificationBoundaryTestCommand.STATUS_BOUNDARY_TEST_PREPARED, result.status)
        assertEquals(2, result.slot1Minutes)
        assertEquals(62, result.slot2Minutes)
        assertEquals(122, result.slot3Minutes)
        assertNextOccurrenceIsT(now, result)
        val tLocal = java.time.Instant.ofEpochMilli(result.targetPlannedAtMs!!)
            .atZone(zone)
        assertEquals(15, tLocal.dayOfMonth)
        assertEquals(0, tLocal.hour)
        assertEquals(2, tLocal.minute)
    }

    @Test
    fun midnight2359x_selectsNextDayT() = runBlocking {
        val now = local(2026, 8, 14, 23, 59, 0, nano = 400_000_000)
        val result = successExecute(now, mutableListOf())
        assertEquals(PrepareNotificationBoundaryTestCommand.STATUS_BOUNDARY_TEST_PREPARED, result.status)
        assertEquals(3, result.slot1Minutes)
        assertNextOccurrenceIsT(now, result)
        val tLocal = java.time.Instant.ofEpochMilli(result.targetPlannedAtMs!!)
            .atZone(zone)
        assertEquals(15, tLocal.dayOfMonth)
        assertEquals(0, tLocal.hour)
        assertEquals(3, tLocal.minute)
        assertTrue(result.slot1Minutes != result.slot2Minutes)
        assertTrue(result.slot2Minutes != result.slot3Minutes)
    }

    @Test
    fun speedNotOne_rejectsWithoutUpdate() = runBlocking {
        val now = local(2026, 8, 14, 10, 0, 30)
        var updates = 0
        val result = PrepareNotificationBoundaryTestCommand.execute(
            packageName = PrepareNotificationBoundaryTestCommand.REQUIRED_PACKAGE,
            wallNowMs = now,
            virtualNowMs = now,
            speedMultiplier = 240,
            clockRunning = true,
            practiceStarted = false,
            occurrenceCount = 0,
            zoneId = zoneId,
            updateSchedule = { updates++ },
        )
        assertEquals(
            PrepareNotificationBoundaryTestCommand.STATUS_REJECTED_WALL_SYNC_REQUIRED,
            result.status,
        )
        assertEquals(0, updates)
    }

    @Test
    fun pausedClock_rejectsWithoutUpdate() = runBlocking {
        val now = local(2026, 8, 14, 10, 0, 30)
        var updates = 0
        val result = PrepareNotificationBoundaryTestCommand.execute(
            packageName = PrepareNotificationBoundaryTestCommand.REQUIRED_PACKAGE,
            wallNowMs = now,
            virtualNowMs = now,
            speedMultiplier = 1,
            clockRunning = false,
            practiceStarted = false,
            occurrenceCount = 0,
            zoneId = zoneId,
            updateSchedule = { updates++ },
        )
        assertEquals(
            PrepareNotificationBoundaryTestCommand.STATUS_REJECTED_WALL_SYNC_REQUIRED,
            result.status,
        )
        assertEquals(0, updates)
    }

    @Test
    fun skewAbove500_rejectsWithoutUpdate() = runBlocking {
        val now = local(2026, 8, 14, 10, 0, 30)
        var updates = 0
        val result = PrepareNotificationBoundaryTestCommand.execute(
            packageName = PrepareNotificationBoundaryTestCommand.REQUIRED_PACKAGE,
            wallNowMs = now,
            virtualNowMs = now + 501L,
            speedMultiplier = 1,
            clockRunning = true,
            practiceStarted = false,
            occurrenceCount = 0,
            zoneId = zoneId,
            updateSchedule = { updates++ },
        )
        assertEquals(
            PrepareNotificationBoundaryTestCommand.STATUS_REJECTED_WALL_SYNC_REQUIRED,
            result.status,
        )
        assertEquals(0, updates)
    }

    @Test
    fun practiceAlreadyStarted_rejectsWithoutUpdate() = runBlocking {
        val now = local(2026, 8, 14, 10, 0, 30)
        var updates = 0
        val result = PrepareNotificationBoundaryTestCommand.execute(
            packageName = PrepareNotificationBoundaryTestCommand.REQUIRED_PACKAGE,
            wallNowMs = now,
            virtualNowMs = now,
            speedMultiplier = 1,
            clockRunning = true,
            practiceStarted = true,
            occurrenceCount = 0,
            zoneId = zoneId,
            updateSchedule = { updates++ },
        )
        assertEquals(
            PrepareNotificationBoundaryTestCommand.STATUS_REJECTED_PRACTICE_ALREADY_STARTED,
            result.status,
        )
        assertEquals(0, updates)
    }

    @Test
    fun existingOccurrences_rejectsWithoutUpdate() = runBlocking {
        val now = local(2026, 8, 14, 10, 0, 30)
        var updates = 0
        val result = PrepareNotificationBoundaryTestCommand.execute(
            packageName = PrepareNotificationBoundaryTestCommand.REQUIRED_PACKAGE,
            wallNowMs = now,
            virtualNowMs = now,
            speedMultiplier = 1,
            clockRunning = true,
            practiceStarted = false,
            occurrenceCount = 1,
            zoneId = zoneId,
            updateSchedule = { updates++ },
        )
        assertEquals(
            PrepareNotificationBoundaryTestCommand.STATUS_REJECTED_EXISTING_OCCURRENCES,
            result.status,
        )
        assertEquals(0, updates)
    }

    @Test
    fun successInvokesDomainUpdateScheduleOnce() = runBlocking {
        val now = local(2026, 8, 14, 10, 0, 30)
        val captured = mutableListOf<List<ScheduleSlotUpdate>>()
        successExecute(now, captured)
        assertEquals(1, captured.size)
        assertEquals(3, captured.single().size)
        assertEquals(
            listOf(1, 2, 3),
            captured.single().map { it.slotIndex },
        )
    }

    @Test
    fun successDoesNotStartPracticeOrInsertOccurrence() = runBlocking {
        val now = local(2026, 8, 14, 10, 0, 30)
        var startPractice = 0
        var occurrenceInsert = 0
        PrepareNotificationBoundaryTestCommand.execute(
            packageName = PrepareNotificationBoundaryTestCommand.REQUIRED_PACKAGE,
            wallNowMs = now,
            virtualNowMs = now,
            speedMultiplier = 1,
            clockRunning = true,
            practiceStarted = false,
            occurrenceCount = 0,
            zoneId = zoneId,
            updateSchedule = { },
        )
        assertEquals(0, startPractice)
        assertEquals(0, occurrenceInsert)
    }

    @Test
    fun hostScheduleExtrasAreIgnoredByEvaluateApi() {
        val now = local(2026, 8, 14, 10, 0, 30)
        val result = PrepareNotificationBoundaryTestCommand.evaluate(
            packageName = PrepareNotificationBoundaryTestCommand.REQUIRED_PACKAGE,
            wallNowMs = now,
            virtualNowMs = now,
            speedMultiplier = 1,
            clockRunning = true,
            practiceStarted = false,
            occurrenceCount = 0,
            zoneId = zoneId,
        )
        val expectedSlot1 = 10 * 60 + 4
        assertEquals(expectedSlot1, result.slot1Minutes)
        assertTrue(result.slot1Minutes != 1)
        assertTrue(result.slot2Minutes != 2)
        assertTrue(result.slot3Minutes != 3)
        assertTrue(result.targetPlannedAtMs != 0L)
    }

    @Test
    fun wrongPackage_rejects() = runBlocking {
        val now = local(2026, 8, 14, 10, 0, 30)
        var updates = 0
        val result = PrepareNotificationBoundaryTestCommand.execute(
            packageName = "com.me4hik.praktika",
            wallNowMs = now,
            virtualNowMs = now,
            speedMultiplier = 1,
            clockRunning = true,
            practiceStarted = false,
            occurrenceCount = 0,
            zoneId = zoneId,
            updateSchedule = { updates++ },
        )
        assertEquals(
            PrepareNotificationBoundaryTestCommand.STATUS_REJECTED_WRONG_PACKAGE,
            result.status,
        )
        assertEquals(0, updates)
        assertNull(result.slot1Minutes)
    }

    @Test
    fun commandNameIsPrepareNotificationBoundaryTest() {
        assertEquals("prepare_notification_boundary_test", PrepareNotificationBoundaryTestCommand.COMMAND)
    }

    private suspend fun successExecute(
        now: Long,
        captured: MutableList<List<ScheduleSlotUpdate>>,
    ) = PrepareNotificationBoundaryTestCommand.execute(
        packageName = PrepareNotificationBoundaryTestCommand.REQUIRED_PACKAGE,
        wallNowMs = now,
        virtualNowMs = now,
        speedMultiplier = 1,
        clockRunning = true,
        practiceStarted = false,
        occurrenceCount = 0,
        zoneId = zoneId,
        updateSchedule = { captured.add(it) },
    )

    private fun assertLeadAndSlots(now: Long, result: PrepareNotificationBoundaryTestCommand.CommandResult) {
        val tLocal = java.time.Instant.ofEpochMilli(now).atZone(zone)
            .truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
            .plusMinutes(1)
            .plusMinutes(3)
        val expectedSlot1 = tLocal.hour * 60 + tLocal.minute
        assertEquals(expectedSlot1, result.slot1Minutes)
        assertEquals((expectedSlot1 + 60) % 1440, result.slot2Minutes)
        assertEquals((expectedSlot1 + 120) % 1440, result.slot3Minutes)
        assertEquals(0, result.targetPlannedAtMs!! % 60_000L)
        assertEquals(tLocal.toInstant().toEpochMilli(), result.targetPlannedAtMs)
        assertEquals(3, setOf(result.slot1Minutes, result.slot2Minutes, result.slot3Minutes).size)
    }

    private fun assertNextOccurrenceIsT(
        now: Long,
        result: PrepareNotificationBoundaryTestCommand.CommandResult,
    ) {
        val entities = listOf(
            ScheduleSlotEntity(1, result.slot1Minutes!!),
            ScheduleSlotEntity(2, result.slot2Minutes!!),
            ScheduleSlotEntity(3, result.slot3Minutes!!),
        )
        val start = ScheduleCalculator().findStartSlot(now, zoneId, entities)
        assertEquals(result.targetPlannedAtMs, start.plannedAtEpochMillis)
    }

    private fun local(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        nano: Int = 0,
    ): Long {
        return ZonedDateTime.of(year, month, day, hour, minute, second, nano, zone)
            .toInstant()
            .toEpochMilli()
    }
}
// 14.08.2026 Accelerated device-setup harness cursor by Me4Hik END

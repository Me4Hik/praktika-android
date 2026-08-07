// 04.08.2026 Cycle Engine cursor by Me4Hik START - JVM-тесты ScheduleCalculator
package com.me4hik.praktika.data.cycle

import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ScheduleCalculatorTest {
    private lateinit var calculator: ScheduleCalculator
    private val slots = listOf(
        ScheduleSlotEntity(slotIndex = 1, timeOfDayMinutes = 660),
        ScheduleSlotEntity(slotIndex = 2, timeOfDayMinutes = 900),
        ScheduleSlotEntity(slotIndex = 3, timeOfDayMinutes = 1140),
    )

    @Before
    fun setUp() {
        calculator = ScheduleCalculator()
    }

    @Test
    fun inclusiveMinuteBeforeElevenSelectsElevenSlot() {
        val moment = calculator.findStartSlot(epochAt(10, 59, 59), ZONE_KIEV, slots)
        assertSlot(moment, 1, 11, 0)
    }

    @Test
    fun inclusiveMinuteAtElevenStartSelectsElevenSlot() {
        val moment = calculator.findStartSlot(epochAt(11, 0, 0), ZONE_KIEV, slots)
        assertSlot(moment, 1, 11, 0)
    }

    @Test
    fun inclusiveMinuteInsideElevenSelectsElevenSlot() {
        val moment = calculator.findStartSlot(epochAt(11, 0, 59, 999_000_000), ZONE_KIEV, slots)
        assertSlot(moment, 1, 11, 0)
    }

    @Test
    fun minuteAfterElevenSelectsFifteenSlot() {
        val moment = calculator.findStartSlot(epochAt(11, 1, 0), ZONE_KIEV, slots)
        assertSlot(moment, 2, 15, 0)
    }

    @Test
    fun inclusiveMinuteAtFifteenSelectsFifteenSlot() {
        val moment = calculator.findStartSlot(epochAt(15, 0, 0), ZONE_KIEV, slots)
        assertSlot(moment, 2, 15, 0)
    }

    @Test
    fun inclusiveMinuteAtNineteenSelectsNineteenSlot() {
        val moment = calculator.findStartSlot(epochAt(19, 0, 0), ZONE_KIEV, slots)
        assertSlot(moment, 3, 19, 0)
    }

    @Test
    fun strictlyNextFromTenThirtySelectsEleven() {
        val moment = calculator.findStrictlyNextSlot(epochAt(10, 30, 0), ZONE_KIEV, slots)
        assertSlot(moment, 1, 11, 0)
    }

    @Test
    fun strictlyNextFromElevenStartSelectsFifteen() {
        val moment = calculator.findStrictlyNextSlot(epochAt(11, 0, 0), ZONE_KIEV, slots)
        assertSlot(moment, 2, 15, 0)
    }

    @Test
    fun strictlyNextFromElevenTwentyFiveSelectsFifteen() {
        val moment = calculator.findStrictlyNextSlot(epochAt(11, 0, 25), ZONE_KIEV, slots)
        assertSlot(moment, 2, 15, 0)
    }

    @Test
    fun strictlyNextFromNineteenSelectsNextDayEleven() {
        val moment = calculator.findStrictlyNextSlot(epochAt(19, 0, 0), ZONE_KIEV, slots)
        assertSlotOnDate(moment, 2026, 8, 5, 11, 0)
    }

    @Test
    fun availableUntilElevenIsFifteen() {
        val planned = calculator.findStartSlot(epochAt(10, 0, 0), ZONE_KIEV, slots)
        val until = calculator.calculateAvailableUntil(planned, slots)
        assertEquals(epochAt(15, 0, 0), until)
    }

    @Test
    fun availableUntilFifteenIsNineteen() {
        val planned = calculator.findStartSlot(epochAt(11, 1, 0), ZONE_KIEV, slots)
        val until = calculator.calculateAvailableUntil(planned, slots)
        assertEquals(epochAt(19, 0, 0), until)
    }

    @Test
    fun availableUntilNineteenIsNextDayEleven() {
        val planned = calculator.findStartSlot(epochAt(18, 0, 0), ZONE_KIEV, slots)
        val until = calculator.calculateAvailableUntil(planned, slots)
        assertEquals(epochAtOnDate(2026, 8, 5, 11, 0), until)
    }

    @Test
    fun nextSlotAfterMomentCrossesMidnight() {
        val current = calculator.findStartSlot(epochAt(19, 0, 0), ZONE_KIEV, slots)
        val next = calculator.findNextSlotAfterMoment(current, slots)
        assertSlotOnDate(next, 2026, 8, 5, 11, 0)
    }

    @Test
    fun worksInUtcZone() {
        val moment = calculator.findStartSlot(epochAtUtc(11, 0, 0), "UTC", slots)
        assertEquals(1, moment.slotIndex)
        assertEquals("UTC", moment.zoneId)
    }

    @Test
    fun unknownZoneThrows() {
        try {
            calculator.findStartSlot(epochAt(11, 0, 0), "Not/AZone", slots)
            fail("Expected CycleInvalidScheduleException")
        } catch (exception: CycleInvalidScheduleException) {
            assertTrue(exception.message!!.contains("Not/AZone"))
        }
    }

    @Test
    fun dstGapIsHandledByJavaTimeRules() {
        val zone = ZoneId.of("Europe/Kiev")
        val gapInstant = ZonedDateTime.of(2026, 3, 29, 3, 30, 0, 0, zone)
        val moment = calculator.findStartSlot(gapInstant.toInstant().toEpochMilli(), ZONE_KIEV, slots)
        assertTrue(moment.plannedAtEpochMillis > 0)
    }

    private fun assertSlot(moment: SlotMoment, slotIndex: Int, hour: Int, minute: Int) {
        assertEquals(slotIndex, moment.slotIndex)
        assertEquals(epochAt(hour, minute, 0), moment.plannedAtEpochMillis)
    }

    private fun assertSlotOnDate(
        moment: SlotMoment,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ) {
        assertEquals(epochAtOnDate(year, month, day, hour, minute), moment.plannedAtEpochMillis)
    }

    private fun epochAt(hour: Int, minute: Int, second: Int, nano: Int = 0): Long {
        return epochAtOnDate(2026, 8, 4, hour, minute, second, nano)
    }

    private fun epochAtOnDate(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0,
        nano: Int = 0,
    ): Long {
        return ZonedDateTime.of(year, month, day, hour, minute, second, nano, ZoneId.of(ZONE_KIEV))
            .toInstant()
            .toEpochMilli()
    }

    private fun epochAtUtc(hour: Int, minute: Int, second: Int): Long {
        return ZonedDateTime.of(2026, 8, 4, hour, minute, second, 0, ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
    }

    private companion object {
        const val ZONE_KIEV = "Europe/Kiev"
    }
}
// 04.08.2026 Cycle Engine cursor by Me4Hik END

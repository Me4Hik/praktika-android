// 05.08.2026 Main Screen cursor by Me4Hik START - unit tests PracticeTimeFormatter
package com.me4hik.praktika.ui.practice

import com.me4hik.praktika.data.preferences.AppLanguage
import com.me4hik.praktika.data.preferences.AppLocaleController
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PracticeTimeFormatterTest {
    private val formatter = PracticeTimeFormatter()

    @Before
    fun setUp() {
        AppLocaleController.apply(AppLanguage.RU)
    }

    @Test
    fun formatEuropeKiev_containsDateAndTime() {
        val epoch = ZonedDateTime.of(2026, 8, 5, 15, 0, 0, 0, ZoneId.of("Europe/Kiev"))
            .toInstant()
            .toEpochMilli()
        val formatted = formatter.format(epoch, "Europe/Kiev")
        assertTrue(formatted.contains("2026"))
        assertTrue(formatted.contains("15:00"))
        assertTrue(formatted.contains("августа"))
    }

    @Test
    fun formatUtc_containsDateAndTime() {
        val epoch = ZonedDateTime.of(2026, 8, 5, 12, 0, 0, 0, ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
        val formatted = formatter.format(epoch, "UTC")
        assertTrue(formatted.contains("2026"))
        assertTrue(formatted.contains("12:00"))
    }

    @Test
    fun unknownZoneThrowsControlledException() {
        try {
            formatter.format(0L, "Not/AZone")
            fail("Expected PracticeTimeFormatException")
        } catch (exception: PracticeTimeFormatException) {
            assertEquals("Unknown zoneId: Not/AZone", exception.message)
        }
    }

    @Test
    fun formatDoesNotContainCountdownLabels() {
        val epoch = ZonedDateTime.of(2026, 8, 5, 15, 0, 0, 0, ZoneId.of("Europe/Kiev"))
            .toInstant()
            .toEpochMilli()
        val formatted = formatter.format(epoch, "Europe/Kiev")
        assertEquals(false, formatted.contains("через"))
        assertEquals(false, formatted.contains("сегодня"))
        assertEquals(false, formatted.contains("завтра"))
    }
}
// 05.08.2026 Main Screen cursor by Me4Hik END

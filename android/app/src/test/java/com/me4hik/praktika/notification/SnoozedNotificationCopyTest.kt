package com.me4hik.praktika.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class SnoozedNotificationCopyTest {
    @Test
    fun formatRepeatAt_usesZoneLocalClock() {
        // 1970-01-01 12:00 UTC
        assertEquals("12:00", SnoozedNotificationCopy.formatRepeatAt(12 * 60 * 60 * 1000L, "UTC"))
        // 1970-01-01 15:00 in UTC+3
        assertEquals(
            "15:00",
            SnoozedNotificationCopy.formatRepeatAt(12 * 60 * 60 * 1000L, "UTC+03:00"),
        )
    }

    @Test
    fun formatRepeatAt_invalidZoneFallsBackToUtc() {
        assertEquals(
            "12:00",
            SnoozedNotificationCopy.formatRepeatAt(12 * 60 * 60 * 1000L, "Not/AZone"),
        )
    }
}

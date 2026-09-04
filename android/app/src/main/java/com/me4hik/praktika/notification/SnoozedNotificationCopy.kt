package com.me4hik.praktika.notification

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Pure copy helpers for Clock-like snoozed practice notification. */
object SnoozedNotificationCopy {
    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun formatRepeatAt(deferredUntilEpochMillis: Long, zoneId: String): String {
        val zone = try {
            ZoneId.of(zoneId)
        } catch (_: Exception) {
            ZoneId.of("UTC")
        }
        return TIME_FORMATTER
            .withZone(zone)
            .format(Instant.ofEpochMilli(deferredUntilEpochMillis))
    }
}

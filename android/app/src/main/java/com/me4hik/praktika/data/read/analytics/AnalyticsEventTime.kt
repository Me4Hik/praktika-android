// PROMPT 149 — shared analytics event-time helper (stored zoneId only)
package com.me4hik.praktika.data.read.analytics

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Instant + stored event timezone → wall-clock in that zone.
 * Never uses [ZoneId.systemDefault] / device timezone.
 */
object AnalyticsEventTime {
    fun resolveZoneId(zoneId: String): ZoneId {
        return runCatching { ZoneId.of(zoneId) }.getOrElse { ZoneId.of("UTC") }
    }

    fun atZone(epochMillis: Long, zoneId: String): ZonedDateTime {
        return Instant.ofEpochMilli(epochMillis).atZone(resolveZoneId(zoneId))
    }

    fun dayOfWeek(epochMillis: Long, zoneId: String): DayOfWeek {
        return atZone(epochMillis, zoneId).dayOfWeek
    }
}

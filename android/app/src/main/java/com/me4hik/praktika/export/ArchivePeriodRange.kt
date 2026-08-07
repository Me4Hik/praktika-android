// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - DST-safe границы периода
package com.me4hik.praktika.export

import java.time.LocalDate
import java.time.ZoneId

object ArchivePeriodRange {
    data class Bounds(
        val startInclusiveEpochMillis: Long,
        val endExclusiveEpochMillis: Long,
    )

    fun bounds(
        startEpochDay: Long,
        endEpochDayInclusive: Long,
        zoneId: ZoneId,
    ): Bounds {
        require(endEpochDayInclusive >= startEpochDay) {
            "endEpochDayInclusive must be >= startEpochDay"
        }
        val startDate = LocalDate.ofEpochDay(startEpochDay)
        val endDate = LocalDate.ofEpochDay(endEpochDayInclusive)
        val startInclusive = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endExclusive = endDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return Bounds(startInclusive, endExclusive)
    }
}
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END

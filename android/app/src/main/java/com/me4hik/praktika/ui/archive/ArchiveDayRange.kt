// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - границы календарного дня
package com.me4hik.praktika.ui.archive

import java.time.LocalDate
import java.time.ZoneId

object ArchiveDayRange {
    data class Bounds(
        val startInclusiveEpochMillis: Long,
        val endExclusiveEpochMillis: Long,
    )

    fun bounds(epochDay: Long, zoneId: ZoneId): Bounds {
        val date = LocalDate.ofEpochDay(epochDay)
        val startInclusive = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endExclusive = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return Bounds(startInclusive, endExclusive)
    }
}
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END

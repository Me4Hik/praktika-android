// 06.09.2026 Archive period bounds cursor by Me4Hik START - Material DatePicker UTC day mapping
package com.me4hik.praktika.ui.archive

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Material3 [androidx.compose.material3.DatePicker] uses UTC midnight millis for calendar cells.
 * Archive day identity for selection is [LocalDate.toEpochDay] of that UTC civil date —
 * matching the numeric epochDay of zone-local [LocalDate]s from [ArchiveDateGrouping].
 */
object ArchivePeriodUtcDates {
    fun millisToEpochDay(selectedMillis: Long): Long {
        return Instant.ofEpochMilli(selectedMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .toEpochDay()
    }

    fun epochDayToUtcMidnightMillis(epochDay: Long): Long {
        return LocalDate.ofEpochDay(epochDay)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }
}
// 06.09.2026 Archive period bounds cursor by Me4Hik END

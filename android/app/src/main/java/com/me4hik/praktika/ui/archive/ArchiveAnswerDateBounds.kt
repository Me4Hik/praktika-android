// 06.09.2026 Archive period bounds cursor by Me4Hik START - answer-derived selectable date range
package com.me4hik.praktika.ui.archive

import com.me4hik.praktika.data.read.ArchiveEntry
import java.time.ZoneId

/**
 * Continuous inclusive calendar range of archive answers, matching «Архив по дням»
 * ([ArchiveDateGrouping] zone-local [java.time.LocalDate]s).
 */
data class ArchiveAnswerDateBounds(
    val earliestEpochDay: Long,
    val latestEpochDay: Long,
) {
    init {
        require(latestEpochDay >= earliestEpochDay) {
            "latestEpochDay must be >= earliestEpochDay"
        }
    }

    fun contains(epochDay: Long): Boolean {
        return epochDay in earliestEpochDay..latestEpochDay
    }

    companion object {
        fun fromEntries(
            entries: List<ArchiveEntry>,
            zoneId: ZoneId,
        ): ArchiveAnswerDateBounds? {
            val dates = ArchiveDateGrouping.groupDates(entries, zoneId)
            if (dates.isEmpty()) {
                return null
            }
            val earliest = dates.minOf { it.date }
            val latest = dates.maxOf { it.date }
            return ArchiveAnswerDateBounds(
                earliestEpochDay = earliest.toEpochDay(),
                latestEpochDay = latest.toEpochDay(),
            )
        }

        /** Drops initials outside bounds or when bounds are missing (empty archive). */
        fun sanitizeInitialEpochDay(
            epochDay: Long?,
            bounds: ArchiveAnswerDateBounds?,
        ): Long? {
            if (epochDay == null || bounds == null) {
                return null
            }
            return epochDay.takeIf { bounds.contains(it) }
        }
    }
}
// 06.09.2026 Archive period bounds cursor by Me4Hik END

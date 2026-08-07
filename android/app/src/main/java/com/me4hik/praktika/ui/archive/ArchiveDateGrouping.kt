// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - группировка архива по датам
package com.me4hik.praktika.ui.archive

import com.me4hik.praktika.data.read.ArchiveEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ArchiveDateItem(
    val date: LocalDate,
    val answerCount: Int,
)

object ArchiveDateGrouping {
    fun groupDates(
        entries: List<ArchiveEntry>,
        zoneId: ZoneId,
    ): List<ArchiveDateItem> {
        if (entries.isEmpty()) {
            return emptyList()
        }
        return entries
            .groupBy { entry ->
                Instant.ofEpochMilli(entry.answeredAtEpochMillis)
                    .atZone(zoneId)
                    .toLocalDate()
            }
            .map { (date, groupedEntries) ->
                ArchiveDateItem(
                    date = date,
                    answerCount = groupedEntries.size,
                )
            }
            .sortedByDescending { it.date }
    }
}
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END

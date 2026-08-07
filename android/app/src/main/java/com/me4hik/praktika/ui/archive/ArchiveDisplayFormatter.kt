// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - форматирование даты/времени архива
package com.me4hik.praktika.ui.archive

import com.me4hik.praktika.ui.practice.PracticeTimeFormatException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ArchiveDisplayFormatter(
    private val locale: Locale = Locale.forLanguageTag("ru"),
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)

    fun formatDate(epochMillis: Long, zoneId: ZoneId): String {
        return instantAt(epochMillis, zoneId).format(dateFormatter)
    }

    fun formatTime(epochMillis: Long, zoneId: ZoneId): String {
        return instantAt(epochMillis, zoneId).format(timeFormatter)
    }

    fun formatDateTimeLine(epochMillis: Long, zoneId: ZoneId): String {
        return "${formatDate(epochMillis, zoneId)} · ${formatTime(epochMillis, zoneId)}"
    }

    private fun instantAt(epochMillis: Long, zoneId: ZoneId) =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId)
}
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END

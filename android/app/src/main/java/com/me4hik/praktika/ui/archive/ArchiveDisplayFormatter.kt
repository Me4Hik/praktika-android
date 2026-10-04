// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - форматирование даты/времени архива
package com.me4hik.praktika.ui.archive

import com.me4hik.praktika.data.preferences.AppLocaleController
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ArchiveDisplayFormatter {
    fun formatDate(epochMillis: Long, zoneId: ZoneId): String {
        val formatter = DateTimeFormatter.ofPattern(
            "d MMMM yyyy",
            AppLocaleController.currentLocale(),
        )
        return instantAt(epochMillis, zoneId).format(formatter)
    }

    fun formatTime(epochMillis: Long, zoneId: ZoneId): String {
        val formatter = DateTimeFormatter.ofPattern(
            "HH:mm",
            AppLocaleController.currentLocale(),
        )
        return instantAt(epochMillis, zoneId).format(formatter)
    }

    fun formatDateTimeLine(epochMillis: Long, zoneId: ZoneId): String {
        return "${formatDate(epochMillis, zoneId)} · ${formatTime(epochMillis, zoneId)}"
    }

    private fun instantAt(epochMillis: Long, zoneId: ZoneId) =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId)
}
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END

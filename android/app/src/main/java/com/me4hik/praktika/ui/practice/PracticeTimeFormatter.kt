// 05.08.2026 Main Screen cursor by Me4Hik START - форматирование абсолютного локального времени
package com.me4hik.praktika.ui.practice

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class PracticeTimeFormatException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

class PracticeTimeFormatter(
    private val locale: Locale = Locale.forLanguageTag("ru"),
) {
    private val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", locale)

    fun format(epochMillis: Long, zoneId: String): String {
        val zone = try {
            ZoneId.of(zoneId)
        } catch (exception: Exception) {
            throw PracticeTimeFormatException("Unknown zoneId: $zoneId", exception)
        }
        return Instant.ofEpochMilli(epochMillis)
            .atZone(zone)
            .format(formatter)
    }
}
// 05.08.2026 Main Screen cursor by Me4Hik END

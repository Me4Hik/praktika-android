// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 5.0 restore preview date formatting
package com.me4hik.praktika.ui.restore

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ProductionRestoreDateFormatter(
    private val locale: Locale = Locale.forLanguageTag("ru"),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)

    fun formatCreatedAt(epochMillis: Long): String {
        val zoned = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
        return "${dateFormatter.format(zoned)} · ${timeFormatter.format(zoned)}"
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END

// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - selection model экспорта
package com.me4hik.praktika.export

sealed interface ExportSelection {
    data object All : ExportSelection

    data class Day(
        val epochDay: Long,
    ) : ExportSelection

    data class Range(
        val startEpochDay: Long,
        val endEpochDayInclusive: Long,
    ) : ExportSelection

    data class Question(
        val questionId: Int,
    ) : ExportSelection
}
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END

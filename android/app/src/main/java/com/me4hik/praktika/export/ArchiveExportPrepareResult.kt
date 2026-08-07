// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - результат подготовки export
package com.me4hik.praktika.export

sealed interface ArchiveExportPrepareResult {
    data object NoAnswers : ArchiveExportPrepareResult

    data class Ready(
        val document: ExportDocument,
    ) : ArchiveExportPrepareResult
}
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END

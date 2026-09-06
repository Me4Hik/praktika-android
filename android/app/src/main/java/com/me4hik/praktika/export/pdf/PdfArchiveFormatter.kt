// PROMPT 176 — PDF archive formatter via android.graphics.pdf.PdfDocument
package com.me4hik.praktika.export.pdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.me4hik.praktika.data.read.ArchiveEntry
import com.me4hik.praktika.export.ArchiveExportFilenamePolicy
import com.me4hik.praktika.export.ExportDocument
import com.me4hik.praktika.export.ExportFormat
import com.me4hik.praktika.export.ExportSelection
import com.me4hik.praktika.ui.archive.ArchiveDisplayFormatter
import java.io.ByteArrayOutputStream
import java.time.ZoneId

open class PdfArchiveFormatter(
    private val displayFormatter: ArchiveDisplayFormatter = ArchiveDisplayFormatter(),
) {
    open fun format(
        selection: ExportSelection,
        entries: List<ArchiveEntry>,
        zoneId: ZoneId,
    ): ExportDocument {
        val orderedEntries = when (selection) {
            is ExportSelection.Question -> sortOldestFirst(entries)
            else -> sortNewestFirst(entries)
        }
        val bytes = render(selection, orderedEntries, zoneId)
        return ExportDocument(
            suggestedFileName = ArchiveExportFilenamePolicy.suggestedFileName(
                selection,
                ExportFormat.PDF,
            ),
            mimeType = MIME_TYPE,
            bytes = bytes,
        )
    }

    private fun render(
        selection: ExportSelection,
        entries: List<ArchiveEntry>,
        zoneId: ZoneId,
    ): ByteArray {
        val titlePaint = createPaint(TITLE_TEXT_SIZE, bold = true)
        val subtitlePaint = createPaint(SUBTITLE_TEXT_SIZE, bold = false)
        val metaPaint = createPaint(META_TEXT_SIZE, bold = true)
        val labelPaint = createPaint(LABEL_TEXT_SIZE, bold = true)
        val bodyPaint = createPaint(BODY_TEXT_SIZE, bold = false)

        val contentWidth = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT
        val contentBottom = PAGE_HEIGHT - MARGIN_BOTTOM

        val document = PdfDocument()
        try {
            var pageNumber = 1
            var page = startPage(document, pageNumber)
            var canvas = page.canvas
            var y = MARGIN_TOP

            fun ensureSpace(needed: Float) {
                if (y + needed <= contentBottom) {
                    return
                }
                document.finishPage(page)
                pageNumber += 1
                page = startPage(document, pageNumber)
                canvas = page.canvas
                y = MARGIN_TOP
            }

            fun drawWrapped(
                text: String,
                paint: Paint,
                lineHeight: Float,
            ) {
                val lines = PdfArchiveTextLayout.wrapLines(text, contentWidth) { sample ->
                    paint.measureText(sample)
                }
                for (line in lines) {
                    ensureSpace(lineHeight)
                    canvas.drawText(line, MARGIN_LEFT, y + paint.textSize, paint)
                    y += lineHeight
                }
            }

            drawWrapped(TITLE, titlePaint, TITLE_LINE_HEIGHT)
            y += SECTION_GAP

            val selectionLabel = PdfArchiveSelectionLabel.forSelection(
                selection = selection,
                zoneId = zoneId,
                displayFormatter = displayFormatter,
                entries = entries,
            )
            drawWrapped(selectionLabel, subtitlePaint, SUBTITLE_LINE_HEIGHT)
            y += SECTION_GAP * 1.5f

            entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    y += ENTRY_GAP
                }
                val dateTime = displayFormatter.formatDateTimeLine(
                    entry.answeredAtEpochMillis,
                    zoneId,
                )
                drawWrapped(dateTime, metaPaint, META_LINE_HEIGHT)
                y += FIELD_GAP

                drawWrapped("Вопрос", labelPaint, LABEL_LINE_HEIGHT)
                drawWrapped(entry.questionText, bodyPaint, BODY_LINE_HEIGHT)
                y += FIELD_GAP

                drawWrapped("Ответ", labelPaint, LABEL_LINE_HEIGHT)
                drawWrapped(entry.answerText, bodyPaint, BODY_LINE_HEIGHT)
            }

            document.finishPage(page)
            val output = ByteArrayOutputStream()
            document.writeTo(output)
            return output.toByteArray()
        } finally {
            document.close()
        }
    }

    private fun startPage(document: PdfDocument, pageNumber: Int): PdfDocument.Page {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), pageNumber)
            .create()
        return document.startPage(pageInfo)
    }

    private fun createPaint(textSize: Float, bold: Boolean): Paint {
        val style = if (bold) Typeface.BOLD else Typeface.NORMAL
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            this.textSize = textSize
            typeface = Typeface.create(Typeface.SANS_SERIF, style)
        }
    }

    private fun sortNewestFirst(entries: List<ArchiveEntry>): List<ArchiveEntry> {
        return entries.sortedWith(
            compareByDescending<ArchiveEntry> { it.answeredAtEpochMillis }
                .thenByDescending { it.answerId },
        )
    }

    private fun sortOldestFirst(entries: List<ArchiveEntry>): List<ArchiveEntry> {
        return entries.sortedWith(
            compareBy<ArchiveEntry> { it.answeredAtEpochMillis }
                .thenBy { it.answerId },
        )
    }

    companion object {
        const val MIME_TYPE = "application/pdf"
        const val TITLE = "Практика — Архив"

        // A4 @ 72 dpi
        const val PAGE_WIDTH = 595f
        const val PAGE_HEIGHT = 842f
        const val MARGIN_LEFT = 48f
        const val MARGIN_RIGHT = 48f
        const val MARGIN_TOP = 48f
        const val MARGIN_BOTTOM = 48f

        private const val TITLE_TEXT_SIZE = 18f
        private const val SUBTITLE_TEXT_SIZE = 12f
        private const val META_TEXT_SIZE = 11f
        private const val LABEL_TEXT_SIZE = 11f
        private const val BODY_TEXT_SIZE = 11f

        private const val TITLE_LINE_HEIGHT = 24f
        private const val SUBTITLE_LINE_HEIGHT = 16f
        private const val META_LINE_HEIGHT = 15f
        private const val LABEL_LINE_HEIGHT = 15f
        private const val BODY_LINE_HEIGHT = 15f

        private const val SECTION_GAP = 8f
        private const val FIELD_GAP = 4f
        private const val ENTRY_GAP = 14f
    }
}

// 06.09.2026 Android Sheets XLSX Export cursor by Me4Hik START - lightweight OOXML ZIP+XML archive export
package com.me4hik.praktika.export.xlsx

import com.me4hik.praktika.data.read.ArchiveEntry
import com.me4hik.praktika.export.ArchiveExportFilenamePolicy
import com.me4hik.praktika.export.ExportDocument
import com.me4hik.praktika.export.ExportFormat
import com.me4hik.praktika.export.ExportSelection
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxArchiveFormatter {
    fun format(
        selection: ExportSelection,
        entries: List<ArchiveEntry>,
        zoneId: ZoneId,
    ): ExportDocument {
        val orderedEntries = when (selection) {
            is ExportSelection.Question -> sortOldestFirst(entries)
            else -> sortNewestFirst(entries)
        }
        val rows = buildList {
            add(HEADER_COLUMNS)
            orderedEntries.forEach { entry ->
                add(formatRowValues(entry, zoneId))
            }
        }
        return ExportDocument(
            suggestedFileName = ArchiveExportFilenamePolicy.suggestedFileName(
                selection,
                ExportFormat.XLSX,
            ),
            mimeType = MIME_TYPE,
            bytes = buildWorkbookBytes(rows),
        )
    }

    private fun formatRowValues(entry: ArchiveEntry, zoneId: ZoneId): List<String> {
        val zonedDateTime = Instant.ofEpochMilli(entry.answeredAtEpochMillis).atZone(zoneId)
        return listOf(
            entry.answerId.toString(),
            entry.questionId.toString(),
            entry.questionText,
            "",
            entry.answerText,
            zonedDateTime.format(DATE_FORMATTER),
            zonedDateTime.format(TIME_FORMATTER),
            entry.cycleNumber.toString(),
        )
    }

    private fun buildWorkbookBytes(rows: List<List<String>>): ByteArray {
        val sharedStrings = LinkedHashMap<String, Int>()
        var totalStringReferences = 0
        fun stringIndex(value: String): Int {
            val sanitized = sanitizeXml10Text(value)
            totalStringReferences += 1
            return sharedStrings.getOrPut(sanitized) { sharedStrings.size }
        }
        val sheetRowsXml = buildString {
            rows.forEachIndexed { rowIndex, columns ->
                val rowNumber = rowIndex + 1
                append("<row r=\"$rowNumber\">")
                columns.forEachIndexed { columnIndex, value ->
                    val cellRef = cellReference(columnIndex, rowNumber)
                    val index = stringIndex(value)
                    append("<c r=\"$cellRef\" t=\"s\"><v>$index</v></c>")
                }
                append("</row>")
            }
        }
        val sharedStringsXml = buildSharedStringsXml(
            values = sharedStrings.keys.toList(),
            totalStringReferences = totalStringReferences,
        )
        val worksheetXml = buildString {
            append(XML_DECLARATION)
            append(
                "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">",
            )
            append("<sheetData>")
            append(sheetRowsXml)
            append("</sheetData>")
            append("</worksheet>")
        }

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            writeZipEntry(zip, CONTENT_TYPES_PATH, CONTENT_TYPES_XML)
            writeZipEntry(zip, ROOT_RELS_PATH, ROOT_RELS_XML)
            writeZipEntry(zip, WORKBOOK_PATH, WORKBOOK_XML)
            writeZipEntry(zip, WORKBOOK_RELS_PATH, WORKBOOK_RELS_XML)
            writeZipEntry(zip, WORKSHEET_PATH, worksheetXml)
            writeZipEntry(zip, SHARED_STRINGS_PATH, sharedStringsXml)
        }
        return output.toByteArray()
    }

    private fun buildSharedStringsXml(
        values: List<String>,
        totalStringReferences: Int,
    ): String {
        val uniqueCount = values.size
        return buildString {
            append(XML_DECLARATION)
            append(
                "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                    "count=\"$totalStringReferences\" uniqueCount=\"$uniqueCount\">",
            )
            values.forEach { value ->
                append("<si>")
                append(sharedStringTextElement(value))
                append("</si>")
            }
            append("</sst>")
        }
    }

    private fun writeZipEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    internal companion object {
        val HEADER_COLUMNS = listOf(
            "answer_id",
            "question_id",
            "question",
            "topic",
            "answer",
            "date",
            "time",
            "cycle_number",
        )
        const val MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        const val CONTENT_TYPES_PATH = "[Content_Types].xml"
        const val ROOT_RELS_PATH = "_rels/.rels"
        const val WORKBOOK_PATH = "xl/workbook.xml"
        const val WORKBOOK_RELS_PATH = "xl/_rels/workbook.xml.rels"
        const val WORKSHEET_PATH = "xl/worksheets/sheet1.xml"
        const val SHARED_STRINGS_PATH = "xl/sharedStrings.xml"

        private const val XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

        // 06.09.2026 Android Sheets XLSX Export harden cursor by Me4Hik START - package XML constants for tests
        internal val CONTENT_TYPES_XML = XML_DECLARATION +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" " +
            "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet1.xml\" " +
            "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
            "<Override PartName=\"/xl/sharedStrings.xml\" " +
            "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>" +
            "</Types>"

        internal val ROOT_RELS_XML = XML_DECLARATION +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" " +
            "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" " +
            "Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"

        internal val WORKBOOK_XML = XML_DECLARATION +
            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
            "<sheets>" +
            "<sheet name=\"Archive\" sheetId=\"1\" r:id=\"rId1\"/>" +
            "</sheets>" +
            "</workbook>"

        internal val WORKBOOK_RELS_XML = XML_DECLARATION +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" " +
            "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" " +
            "Target=\"worksheets/sheet1.xml\"/>" +
            "<Relationship Id=\"rId2\" " +
            "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" " +
            "Target=\"sharedStrings.xml\"/>" +
            "</Relationships>"
        // 06.09.2026 Android Sheets XLSX Export harden cursor by Me4Hik END

        /**
         * Drops XML 1.0 forbidden C0 controls. Keeps tab / LF / CR.
         */
        fun sanitizeXml10Text(value: String): String {
            if (value.none { isForbiddenXml10ControlChar(it) }) {
                return value
            }
            return buildString(value.length) {
                value.forEach { character ->
                    if (!isForbiddenXml10ControlChar(character)) {
                        append(character)
                    }
                }
            }
        }

        fun isForbiddenXml10ControlChar(character: Char): Boolean {
            val code = character.code
            return code < 0x20 && code != 0x9 && code != 0xA && code != 0xD
        }

        fun escapeXmlText(value: String): String {
            return buildString(value.length) {
                value.forEach { character ->
                    when (character) {
                        '&' -> append("&amp;")
                        '<' -> append("&lt;")
                        '>' -> append("&gt;")
                        '"' -> append("&quot;")
                        '\'' -> append("&apos;")
                        else -> append(character)
                    }
                }
            }
        }

        fun sharedStringTextElement(value: String): String {
            val escaped = escapeXmlText(value)
            val needsPreserve = value.any { character ->
                character == ' ' || character == '\t' || character == '\n' || character == '\r'
            } || value != value.trim()
            return if (needsPreserve) {
                "<t xml:space=\"preserve\">$escaped</t>"
            } else {
                "<t>$escaped</t>"
            }
        }

        fun cellReference(columnIndex: Int, rowNumber: Int): String {
            require(columnIndex in 0 until 26) { "columnIndex out of A-Z range: $columnIndex" }
            val column = ('A'.code + columnIndex).toChar()
            return "$column$rowNumber"
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
    }
}
// 06.09.2026 Android Sheets XLSX Export cursor by Me4Hik END

// 06.09.2026 Android Sheets XLSX Export cursor by Me4Hik START - unit tests XlsxArchiveFormatter
package com.me4hik.praktika.export.xlsx

import com.me4hik.praktika.data.read.ArchiveEntry
import com.me4hik.praktika.export.ExportSelection
import java.io.ByteArrayInputStream
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.zip.ZipInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XlsxArchiveFormatterTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val formatter = XlsxArchiveFormatter()

    private companion object {
        val SHARED_STRING_INDEX_REGEX = Regex("""<c[^>]*t="s"[^>]*><v>(\d+)</v></c>""")
    }

    @Test
    fun mimeFilenameAndZipMagic() {
        val document = formatter.format(
            selection = ExportSelection.All,
            entries = listOf(sampleEntry(answerId = 1)),
            zoneId = zone,
        )
        assertEquals("praktika-all.xlsx", document.suggestedFileName)
        assertEquals(XlsxArchiveFormatter.MIME_TYPE, document.mimeType)
        assertTrue(document.bytes.size >= 4)
        assertEquals('P'.code.toByte(), document.bytes[0])
        assertEquals('K'.code.toByte(), document.bytes[1])
    }

    @Test
    fun zipContainsRequiredOoxmlParts() {
        val document = formatter.format(ExportSelection.All, listOf(sampleEntry()), zone)
        val names = zipEntryNames(document.bytes)
        assertTrue(names.contains(XlsxArchiveFormatter.CONTENT_TYPES_PATH))
        assertTrue(names.contains(XlsxArchiveFormatter.ROOT_RELS_PATH))
        assertTrue(names.contains(XlsxArchiveFormatter.WORKBOOK_PATH))
        assertTrue(names.contains(XlsxArchiveFormatter.WORKBOOK_RELS_PATH))
        assertTrue(names.contains(XlsxArchiveFormatter.WORKSHEET_PATH))
        assertTrue(names.contains(XlsxArchiveFormatter.SHARED_STRINGS_PATH))
    }

    @Test
    fun eightHeaderColumnsPresentInSharedStrings() {
        val shared = readZipEntryUtf8(
            formatter.format(ExportSelection.All, listOf(sampleEntry()), zone).bytes,
            XlsxArchiveFormatter.SHARED_STRINGS_PATH,
        )
        XlsxArchiveFormatter.HEADER_COLUMNS.forEach { column ->
            assertTrue("missing column $column in:\n$shared", shared.contains(column))
        }
        assertEquals(8, XlsxArchiveFormatter.HEADER_COLUMNS.size)
    }

    @Test
    fun cyrillicQuotesCommaMultilineAndEmojiPreservedInSharedStrings() {
        val question = "Он сказал \"да\", правда?"
        val answer = "Первая строка, с запятой\nВторая строка: «кириллица» 🎉"
        val entry = sampleEntry(
            answerId = 42,
            questionId = 7,
            questionText = question,
            answerText = answer,
            at = epochMillis(2026, 9, 6, 15, 30),
            cycleNumber = 2,
        )
        val document = formatter.format(ExportSelection.All, listOf(entry), zone)
        val shared = readZipEntryUtf8(document.bytes, XlsxArchiveFormatter.SHARED_STRINGS_PATH)
        assertTrue(shared.contains(XlsxArchiveFormatter.escapeXmlText(question)))
        assertTrue(shared.contains("«кириллица»"))
        assertTrue(shared.contains("🎉"))
        assertTrue(shared.contains("xml:space=\"preserve\""))
        assertTrue(shared.contains("Первая строка, с запятой"))
        assertTrue(shared.contains("Вторая строка"))
        assertTrue(shared.contains("42"))
        assertTrue(shared.contains("7"))
        assertTrue(shared.contains("2026-09-06"))
        assertTrue(shared.contains("15:30"))
        assertTrue(shared.contains("2"))
    }

    @Test
    fun worksheetReferencesEightColumnsAndTwoRows() {
        val sheet = readZipEntryUtf8(
            formatter.format(ExportSelection.All, listOf(sampleEntry()), zone).bytes,
            XlsxArchiveFormatter.WORKSHEET_PATH,
        )
        assertTrue(sheet.contains("r=\"A1\""))
        assertTrue(sheet.contains("r=\"H1\""))
        assertTrue(sheet.contains("r=\"A2\""))
        assertTrue(sheet.contains("r=\"H2\""))
        assertTrue(sheet.contains("<row r=\"1\">"))
        assertTrue(sheet.contains("<row r=\"2\">"))
    }

    @Test
    fun allScopeOrdersNewestFirstInSharedStringsOrder() {
        val newer = sampleEntry(answerId = 2, at = epochMillis(2026, 8, 7, 18, 0), answerText = "Newer")
        val older = sampleEntry(answerId = 1, at = epochMillis(2026, 8, 5, 10, 0), answerText = "Older")
        val shared = readZipEntryUtf8(
            formatter.format(ExportSelection.All, listOf(older, newer), zone).bytes,
            XlsxArchiveFormatter.SHARED_STRINGS_PATH,
        )
        val newerIndex = shared.indexOf("Newer")
        val olderIndex = shared.indexOf("Older")
        assertTrue(newerIndex >= 0 && olderIndex >= 0)
        assertTrue(newerIndex < olderIndex)
    }

    @Test
    fun questionScopeOrdersOldestFirst() {
        val newer = sampleEntry(answerId = 2, at = epochMillis(2026, 8, 7, 18, 0), answerText = "Newer")
        val older = sampleEntry(answerId = 1, at = epochMillis(2026, 8, 1, 11, 3), answerText = "Older")
        val shared = readZipEntryUtf8(
            formatter.format(ExportSelection.Question(1), listOf(newer, older), zone).bytes,
            XlsxArchiveFormatter.SHARED_STRINGS_PATH,
        )
        val newerIndex = shared.indexOf("Newer")
        val olderIndex = shared.indexOf("Older")
        assertTrue(newerIndex >= 0 && olderIndex >= 0)
        assertTrue(olderIndex < newerIndex)
    }

    @Test
    fun deterministicOutput() {
        val entries = listOf(sampleEntry(answerId = 1))
        val first = formatter.format(ExportSelection.All, entries, zone).bytes
        val second = formatter.format(ExportSelection.All, entries, zone).bytes
        assertArrayEquals(first, second)
    }

    @Test
    fun xmlEscapeHandlesSpecialCharacters() {
        assertEquals("&amp;&lt;&gt;&quot;&apos;", XlsxArchiveFormatter.escapeXmlText("&<>\"'"))
        assertTrue(
            XlsxArchiveFormatter.sharedStringTextElement(" a\nb ")
                .startsWith("<t xml:space=\"preserve\">"),
        )
    }

    // 06.09.2026 Android Sheets XLSX Export harden cursor by Me4Hik START
    @Test
    fun contentTypesXmlMatchesPackageParts() {
        val document = formatter.format(ExportSelection.All, listOf(sampleEntry()), zone)
        val contentTypes = readZipEntryUtf8(document.bytes, XlsxArchiveFormatter.CONTENT_TYPES_PATH)
        assertEquals(XlsxArchiveFormatter.CONTENT_TYPES_XML, contentTypes)
        assertTrue(contentTypes.contains("PartName=\"/xl/workbook.xml\""))
        assertTrue(contentTypes.contains("PartName=\"/xl/worksheets/sheet1.xml\""))
        assertTrue(contentTypes.contains("PartName=\"/xl/sharedStrings.xml\""))
        assertTrue(
            contentTypes.contains(
                "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"",
            ),
        )
        assertTrue(
            contentTypes.contains(
                "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"",
            ),
        )
        assertTrue(
            contentTypes.contains(
                "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"",
            ),
        )
    }

    @Test
    fun workbookRelationshipsPointToWorksheetAndSharedStrings() {
        val document = formatter.format(ExportSelection.All, listOf(sampleEntry()), zone)
        val workbookRels = readZipEntryUtf8(document.bytes, XlsxArchiveFormatter.WORKBOOK_RELS_PATH)
        assertEquals(XlsxArchiveFormatter.WORKBOOK_RELS_XML, workbookRels)
        assertTrue(
            workbookRels.contains(
                "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\"",
            ),
        )
        assertTrue(workbookRels.contains("Target=\"worksheets/sheet1.xml\""))
        assertTrue(
            workbookRels.contains(
                "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\"",
            ),
        )
        assertTrue(workbookRels.contains("Target=\"sharedStrings.xml\""))
        val workbook = readZipEntryUtf8(document.bytes, XlsxArchiveFormatter.WORKBOOK_PATH)
        assertTrue(workbook.contains("r:id=\"rId1\""))
    }

    @Test
    fun sharedStringCellIndexesAreWithinSstRangeAndCountAttributesMatch() {
        val document = formatter.format(
            ExportSelection.All,
            listOf(
                sampleEntry(answerId = 1, answerText = "alpha"),
                sampleEntry(answerId = 2, answerText = "beta"),
            ),
            zone,
        )
        val sheet = readZipEntryUtf8(document.bytes, XlsxArchiveFormatter.WORKSHEET_PATH)
        val shared = readZipEntryUtf8(document.bytes, XlsxArchiveFormatter.SHARED_STRINGS_PATH)
        val indexes = SHARED_STRING_INDEX_REGEX.findAll(sheet)
            .map { match -> match.groupValues[1].toInt() }
            .toList()
        assertTrue(indexes.isNotEmpty())
        val count = Regex("""count="(\d+)"""").find(shared)!!.groupValues[1].toInt()
        val uniqueCount = Regex("""uniqueCount="(\d+)"""").find(shared)!!.groupValues[1].toInt()
        assertEquals(indexes.size, count)
        assertTrue(uniqueCount <= count)
        assertTrue(uniqueCount < count) // empty topic repeated across rows ⇒ dedupe
        indexes.forEach { index ->
            assertTrue("index $index out of SST range uniqueCount=$uniqueCount", index in 0 until uniqueCount)
        }
    }

    @Test
    fun headerColumnsMatchCsvHeadersExactly() {
        assertEquals(
            com.me4hik.praktika.export.csv.CsvArchiveFormatter.HEADER.split(','),
            XlsxArchiveFormatter.HEADER_COLUMNS,
        )
        assertEquals(8, XlsxArchiveFormatter.HEADER_COLUMNS.size)
    }

    @Test
    fun forbiddenXml10ControlCharsAreStripped_newlinesTabsCrPreserved() {
        val raw = "ok\u0000line\u0007\nnext\tcol\rend"
        assertEquals("okline\nnext\tcol\rend", XlsxArchiveFormatter.sanitizeXml10Text(raw))
        val document = formatter.format(
            ExportSelection.All,
            listOf(sampleEntry(answerText = raw, questionText = "q\u0001")),
            zone,
        )
        val shared = readZipEntryUtf8(document.bytes, XlsxArchiveFormatter.SHARED_STRINGS_PATH)
        assertTrue(shared.contains("okline"))
        assertTrue(shared.contains("next"))
        assertTrue(shared.contains("xml:space=\"preserve\""))
        assertTrue(!shared.contains("\u0000"))
        assertTrue(!shared.contains("\u0007"))
        assertTrue(!shared.contains("\u0001"))
        assertTrue(shared.contains("\n") || shared.contains("okline"))
    }
    // 06.09.2026 Android Sheets XLSX Export harden cursor by Me4Hik END

    private fun sampleEntry(
        answerId: Long = 1,
        questionId: Int = 1,
        questionText: String = "Question $answerId",
        answerText: String = "Answer $answerId",
        at: Long = epochMillis(2026, 8, 7, 12, 0),
        cycleNumber: Int = 1,
    ) = ArchiveEntry(
        answerId = answerId,
        occurrenceId = answerId,
        questionId = questionId,
        questionText = questionText,
        answerText = answerText,
        answeredAtEpochMillis = at,
        plannedAtEpochMillis = at - 1_000,
        cycleNumber = cycleNumber,
        cyclePosition = cycleNumber,
    )

    private fun epochMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
    }

    private fun zipEntryNames(bytes: ByteArray): Set<String> {
        val names = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                names.add(entry.name)
                zip.closeEntry()
            }
        }
        return names
    }

    private fun readZipEntryUtf8(bytes: ByteArray, path: String): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == path) {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
            }
        }
        throw AssertionError("ZIP entry not found: $path")
    }
}
// 06.09.2026 Android Sheets XLSX Export cursor by Me4Hik END

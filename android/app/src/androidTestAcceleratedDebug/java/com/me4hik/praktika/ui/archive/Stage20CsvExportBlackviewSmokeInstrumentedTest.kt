// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik START - Blackview assisted CSV export smoke
package com.me4hik.praktika.ui.archive

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.export.ArchiveExportPrepareResult
import com.me4hik.praktika.export.ArchiveExportUseCase
import com.me4hik.praktika.export.ExportFormat
import com.me4hik.praktika.export.ExportSelection
import com.me4hik.praktika.export.csv.CsvArchiveFormatter
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeTestTags
import java.io.FileInputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage20CsvExportBlackviewSmokeInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun assumeOptIn() {
        assumeTrue(
            "stage20_csv_smoke opt-in required",
            InstrumentationRegistry.getArguments().getString("stage20_csv_smoke") == "true",
        )
        PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
    }

    @Test
    fun exportAllSmoke_preparesCsvWithExpectedContent() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val runtime = prepareRuntime(context)
            val zone = ZoneId.of(runtime.timeProvider.currentZoneId())
            val zoneIdProvider = TimeProviderArchiveZoneIdProvider(runtime.timeProvider)
            val useCase = ArchiveExportUseCase(
                archiveReadRepository = runtime.archiveReadRepository,
                zoneIdProvider = zoneIdProvider,
            )
            val result = useCase.prepare(ExportSelection.All, ExportFormat.CSV)
                as ArchiveExportPrepareResult.Ready
            assertEquals("praktika-all.csv", result.document.suggestedFileName)
            assertArrayEquals(CsvArchiveFormatter.UTF8_BOM, result.document.bytes.copyOfRange(0, 3))
            val text = String(
                result.document.bytes.copyOfRange(3, result.document.bytes.size),
                Charsets.UTF_8,
            )
            assertTrue(text.startsWith(CsvArchiveFormatter.HEADER))
            assertTrue(text.contains("\r\n"))
            assertTrue(text.contains("Smoke snapshot Q1"))
            assertTrue(text.contains("hello, world"))
            assertTrue(text.contains("\"Многострочный\nответ 🎉\""))
            assertTrue(text.contains(",=1+1,"))
            assertTrue(text.indexOf("Smoke newer Q1") < text.indexOf("Smoke older Q1"))
            assertFalse(text.contains("Deleted smoke answer"))
            assertFalse(text.contains("Ghost answered without answer"))
        }
    }

    @Test
    fun exportAllSmoke_uiStartsCreateDocumentFlow() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            prepareRuntime(context)
        }
        composeRule.waitUntil(timeoutMillis = 20_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_ARCHIVE) ||
                PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_POSITION)
        }
        ArchiveComposeTestSupport.openArchiveFromHome(composeRule)
        ArchiveComposeTestSupport.waitForArchiveDatesScreen(composeRule)
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_ALL).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_CSV).performClick()
        composeRule.waitForIdle()
        waitForNonEmptySavedCsv(timeoutMillis = 600_000)
        verifySavedCsvBytes()
    }

    private fun waitForNonEmptySavedCsv(timeoutMillis: Long) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (readSavedCsvSizeBytes() > 0L) {
                return
            }
            Thread.sleep(2_000)
        }
        org.junit.Assert.fail(
            "Expected non-empty $SAVED_CSV_PATH after SAF save within ${timeoutMillis}ms",
        )
    }

    private fun verifySavedCsvBytes() {
        val bytes = readSavedCsvBytes()
        assertTrue(bytes.isNotEmpty())
        assertArrayEquals(CsvArchiveFormatter.UTF8_BOM, bytes.copyOfRange(0, 3))
        val text = String(bytes.copyOfRange(3, bytes.size), Charsets.UTF_8)
        assertTrue(text.startsWith(CsvArchiveFormatter.HEADER))
        assertEquals(6, text.split("\r\n").filter { it.isNotEmpty() }.size)
        assertTrue(text.contains("hello, world"))
        assertTrue(text.contains("=1+1"))
        assertFalse(text.contains("Deleted smoke answer"))
    }

    private fun readSavedCsvSizeBytes(): Long {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand(
            "stat -c %s $SAVED_CSV_PATH 2>/dev/null || echo 0",
        ).use { parcelFileDescriptor ->
            return FileInputStream(parcelFileDescriptor.fileDescriptor)
                .bufferedReader(Charsets.UTF_8)
                .readText()
                .trim()
                .toLongOrNull() ?: 0L
        }
    }

    private fun readSavedCsvBytes(): ByteArray {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("cat $SAVED_CSV_PATH").use { parcelFileDescriptor ->
            return FileInputStream(parcelFileDescriptor.fileDescriptor).readBytes()
        }
    }

    companion object {
        private const val SAVED_CSV_PATH = "/storage/emulated/0/Download/praktika-all.csv"
    }

    private suspend fun prepareRuntime(
        context: android.content.Context,
    ): com.me4hik.praktika.runtime.PraktikaRuntime {
        val runtime = PraktikaRuntimeHolder.get(context)
        val practiceState = runtime.database.practiceStateDao().get()!!
        if (!practiceState.isPracticeStarted) {
            runtime.cycleRepository.startPractice()
        }
        clearAnswers(runtime)
        val zone = ZoneId.of(runtime.timeProvider.currentZoneId())
        seedExportSmokeData(runtime, zone)
        assertEquals(5, runtime.archiveReadRepository.observeEntries().first().size)
        return runtime
    }

    private suspend fun clearAnswers(runtime: com.me4hik.praktika.runtime.PraktikaRuntime) {
        runtime.archiveReadRepository.observeEntries().first()
            .forEach { runtime.answerDeleteRepository.deleteAnswer(it.answerId) }
    }

    private suspend fun seedExportSmokeData(
        runtime: com.me4hik.praktika.runtime.PraktikaRuntime,
        zone: ZoneId,
    ) {
        val nextPosition = runtime.database.questionOccurrenceDao()
            .getAllOrderedByPlannedAt()
            .maxOfOrNull { it.cyclePosition }
            ?.plus(1)
            ?: 100
        val dayOne = LocalDate.of(2026, 8, 5)
        val dayTwo = LocalDate.of(2026, 8, 7)
        seedAnswer(
            runtime = runtime,
            zone = zone,
            questionId = 1,
            cycleNumber = nextPosition,
            cyclePosition = nextPosition,
            questionText = "Smoke snapshot Q1",
            answerText = "Smoke older Q1",
            createdAt = epochMillis(dayOne, 11, 0, zone),
        )
        seedAnswer(
            runtime = runtime,
            zone = zone,
            questionId = 1,
            cycleNumber = nextPosition + 1,
            cyclePosition = nextPosition + 1,
            questionText = "Smoke snapshot Q1",
            answerText = "Smoke newer Q1",
            createdAt = epochMillis(dayTwo, 18, 30, zone),
        )
        seedAnswer(
            runtime = runtime,
            zone = zone,
            questionId = 2,
            cycleNumber = nextPosition + 2,
            cyclePosition = nextPosition + 2,
            questionText = "Smoke snapshot Q2",
            answerText = "hello, world",
            createdAt = epochMillis(dayTwo, 9, 15, zone),
        )
        seedAnswer(
            runtime = runtime,
            zone = zone,
            questionId = 2,
            cycleNumber = nextPosition + 3,
            cyclePosition = nextPosition + 3,
            questionText = "Smoke snapshot Q2",
            answerText = "Многострочный\nответ 🎉",
            createdAt = epochMillis(dayTwo, 9, 30, zone),
        )
        seedAnswer(
            runtime = runtime,
            zone = zone,
            questionId = 2,
            cycleNumber = nextPosition + 4,
            cyclePosition = nextPosition + 4,
            questionText = "Smoke formula Q",
            answerText = "=1+1",
            createdAt = epochMillis(dayTwo, 9, 45, zone),
        )
        val (_, deletedAnswerId) = seedAnswer(
            runtime = runtime,
            zone = zone,
            questionId = 2,
            cycleNumber = nextPosition + 5,
            cyclePosition = nextPosition + 5,
            questionText = "Smoke deleted question",
            answerText = "Deleted smoke answer",
            createdAt = epochMillis(dayTwo, 10, 0, zone),
        )
        runtime.answerDeleteRepository.deleteAnswer(deletedAnswerId)
        runtime.database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = 2,
                questionTextSnapshot = "Ghost answered without answer",
                cycleNumber = nextPosition + 6,
                cyclePosition = nextPosition + 6,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = epochMillis(dayTwo, 8, 0, zone) - 3_600_000,
                availableUntilEpochMillis = epochMillis(dayTwo, 8, 0, zone) + 3_600_000,
                status = QuestionOccurrenceStatus.ANSWERED,
                completedAtEpochMillis = epochMillis(dayTwo, 8, 0, zone),
                zoneId = zone.id,
            ),
        )
    }

    private suspend fun seedAnswer(
        runtime: com.me4hik.praktika.runtime.PraktikaRuntime,
        zone: ZoneId,
        questionId: Int,
        cycleNumber: Int,
        cyclePosition: Int,
        questionText: String,
        answerText: String,
        createdAt: Long,
    ): Pair<Long, Long> {
        val occurrenceId = runtime.database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = questionId,
                questionTextSnapshot = questionText,
                cycleNumber = cycleNumber,
                cyclePosition = cyclePosition,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = createdAt - 3_600_000,
                availableUntilEpochMillis = createdAt + 3_600_000,
                status = QuestionOccurrenceStatus.ANSWERED,
                completedAtEpochMillis = createdAt,
                zoneId = zone.id,
            ),
        )
        val answerId = runtime.database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = answerText,
                createdAtEpochMillis = createdAt,
            ),
        )
        return occurrenceId to answerId
    }

    private fun epochMillis(day: LocalDate, hour: Int, minute: Int, zone: ZoneId): Long {
        return ZonedDateTime.of(day.year, day.monthValue, day.dayOfMonth, hour, minute, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
    }
}
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik END

// 07.08.2026 Stage 22 Stability cursor by Me4Hik START - long Answer persistence
package com.me4hik.praktika.stage22

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.read.RoomArchiveReadRepository
import com.me4hik.praktika.export.ExportFormat
import com.me4hik.praktika.export.ExportSelection
import com.me4hik.praktika.export.ArchiveExportPrepareResult
import com.me4hik.praktika.export.ArchiveExportUseCase
import com.me4hik.praktika.export.csv.CsvArchiveFormatter
import com.me4hik.praktika.export.markdown.MarkdownArchiveFormatter
import com.me4hik.praktika.ui.archive.ArchiveZoneIdProvider
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage22LongAnswerPersistenceTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var repository: RoomArchiveReadRepository
    private val zone = ZoneId.of("Europe/Moscow")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
        repository = RoomArchiveReadRepository(database)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun tenThousandCharacterAnswerPersistsAndExportsWithoutTruncation() {
        runBlocking {
        val longText = buildDeterministicLongAnswer(10_000)
        val occurrenceId = seedAnsweredOccurrence(longText)

        val stored = database.answerDao().getByOccurrenceId(occurrenceId)!!
        assertEquals(10_000, stored.text.length)
        assertEquals(longText, stored.text)

        val archiveEntry = repository.observeEntries().first().single()
        assertEquals(longText, archiveEntry.answerText)

        val markdown = MarkdownArchiveFormatter().format(
            ExportSelection.All,
            listOf(archiveEntry),
            zone,
        ).textContent()
        assertTrue(markdown.contains(longText.take(120)))
        assertTrue(markdown.contains(longText.takeLast(120)))

        val csv = CsvArchiveFormatter().format(
            ExportSelection.All,
            listOf(archiveEntry),
            zone,
        ).textWithoutBom()
        assertTrue(csv.contains(longText.take(120)))
        assertTrue(csv.contains(longText.takeLast(120)))

        val exportUseCase = ArchiveExportUseCase(
            archiveReadRepository = repository,
            zoneIdProvider = FixedZoneIdProvider(zone),
        )
        val markdownExport = exportUseCase.prepare(ExportSelection.All, ExportFormat.MARKDOWN)
            as ArchiveExportPrepareResult.Ready
        val csvExport = exportUseCase.prepare(ExportSelection.All, ExportFormat.CSV)
            as ArchiveExportPrepareResult.Ready
        assertTrue(String(markdownExport.document.bytes, Charsets.UTF_8).contains(longText.takeLast(80)))
        assertTrue(String(csvExport.document.bytes, Charsets.UTF_8).contains(longText.takeLast(80)))
        }
    }

    @Test
    fun tenThousandCharacterAnswerSurvivesDatabaseReopen() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbFile = File(context.cacheDir, "stage22_long_answer.db")
        dbFile.delete()

        val persistentDatabase = Room.databaseBuilder(
            context,
            PraktikaDatabase::class.java,
            dbFile.absolutePath,
        ).build()
        val persistentRepository = RoomArchiveReadRepository(persistentDatabase)
        val longText = buildDeterministicLongAnswer(10_000)
        val occurrenceId = seedAnsweredOccurrence(longText, persistentDatabase)
        persistentDatabase.close()

        val reopenedDatabase = Room.databaseBuilder(
            context,
            PraktikaDatabase::class.java,
            dbFile.absolutePath,
        ).build()
        val reopenedRepository = RoomArchiveReadRepository(reopenedDatabase)

        val stored = reopenedDatabase.answerDao().getByOccurrenceId(occurrenceId)!!
        assertEquals(longText, stored.text)

        val archiveEntry = reopenedRepository.observeEntries().first().single()
        assertEquals(longText, archiveEntry.answerText)

        reopenedDatabase.close()
        dbFile.delete()
        }
    }

    @Test
    fun fiftyThousandCharacterAnswerRoomOnlyStress() {
        runBlocking {
        val longText = buildDeterministicLongAnswer(50_000)
        val occurrenceId = seedAnsweredOccurrence(longText)

        val stored = database.answerDao().getByOccurrenceId(occurrenceId)!!
        assertEquals(50_000, stored.text.length)
        assertEquals(longText, stored.text)
        }
    }

    private suspend fun seedAnsweredOccurrence(
        answerText: String,
        targetDatabase: PraktikaDatabase = database,
    ): Long {
        targetDatabase.questionDao().insertAll(
            listOf(QuestionEntity(id = 1, cyclePosition = 1, text = "Question 1")),
        )
        targetDatabase.scheduleSlotDao().insertAll(listOf(ScheduleSlotEntity(1, 660)))
        targetDatabase.practiceStateDao().insert(
            PracticeStateEntity(
                id = 1,
                isPracticeStarted = true,
                isPaused = false,
                practiceStartedAtEpochMillis = 100L,
                currentCycleNumber = 1,
                nextCyclePosition = 2,
                lastProcessedAtEpochMillis = 100L,
                pausedAtEpochMillis = null,
                activeZoneId = zone.id,
                seedVersion = 1,
            ),
        )
        val occurrenceId = targetDatabase.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = 1,
                questionTextSnapshot = "Long answer snapshot",
                cycleNumber = 1,
                cyclePosition = 1,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = 1_000L,
                availableUntilEpochMillis = 2_000L,
                completedAtEpochMillis = 1_500L,
                status = QuestionOccurrenceStatus.ANSWERED,
                zoneId = zone.id,
            ),
        )
        targetDatabase.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = answerText,
                createdAtEpochMillis = 1_500L,
            ),
        )
        return occurrenceId
    }

    private fun buildDeterministicLongAnswer(length: Int): String {
        val segments = listOf(
            "Кириллица ",
            "ASCII ",
            "line\n",
            "emoji 🎉 ",
        )
        val builder = StringBuilder(length)
        var index = 0
        while (builder.length < length) {
            builder.append(segments[index % segments.size])
            index += 1
        }
        return builder.substring(0, length)
    }

    private class FixedZoneIdProvider(
        private val zoneId: ZoneId,
    ) : ArchiveZoneIdProvider {
        override fun currentZoneId(): ZoneId = zoneId
    }

    private fun com.me4hik.praktika.export.ExportDocument.textContent(): String =
        String(bytes, Charsets.UTF_8)

    private fun com.me4hik.praktika.export.ExportDocument.textWithoutBom(): String {
        val bomLength = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            3
        } else {
            0
        }
        return String(bytes, bomLength, bytes.size - bomLength, Charsets.UTF_8)
    }
}
// 07.08.2026 Stage 22 Stability cursor by Me4Hik END

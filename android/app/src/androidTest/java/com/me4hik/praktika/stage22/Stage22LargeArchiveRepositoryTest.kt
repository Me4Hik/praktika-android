// 07.08.2026 Stage 22 Stability cursor by Me4Hik START - large archive dataset performance
package com.me4hik.praktika.stage22

import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.read.RoomArchiveReadRepository
import com.me4hik.praktika.export.ArchiveExportPrepareResult
import com.me4hik.praktika.export.ArchiveExportUseCase
import com.me4hik.praktika.export.ExportFormat
import com.me4hik.praktika.export.ExportSelection
import com.me4hik.praktika.ui.archive.ArchiveDateGrouping
import com.me4hik.praktika.ui.archive.ArchiveDayRange
import com.me4hik.praktika.ui.archive.ArchiveQuestionGrouping
import com.me4hik.praktika.ui.archive.ArchiveZoneIdProvider
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
class Stage22LargeArchiveRepositoryTest {
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
    fun acceptanceArchiveWith1500Answers() = runBlocking {
        runLargeArchiveScenario(answerCount = 1_500, label = "1500")
    }

    @LargeTest
    @Test
    fun stressArchiveWith5000Answers() = runBlocking {
        runLargeArchiveScenario(answerCount = 5_000, label = "5000")
    }

    private suspend fun runLargeArchiveScenario(answerCount: Int, label: String) {
        val seedMs = measureMillis {
            Stage22ArchiveSeedSupport.seedAnsweredArchive(database, answerCount)
        }
        assertEquals(answerCount, database.answerDao().count())
        assertEquals(answerCount, database.questionOccurrenceDao().count())

        val readMs = measureMillis {
            val entries = repository.observeEntries().first()
            assertEquals(answerCount, entries.size)
            assertTrue(entries.first().answeredAtEpochMillis >= entries.last().answeredAtEpochMillis)
        }

        val groupingMs = measureMillis {
            val entries = repository.observeEntries().first()
            val dates = ArchiveDateGrouping.groupDates(entries, zone)
            val questions = ArchiveQuestionGrouping.groupQuestions(entries)
            assertTrue(dates.isNotEmpty())
            assertTrue(questions.isNotEmpty())
        }

        val rangeMs = measureMillis {
            val firstEntry = repository.observeEntries().first().last()
            val bounds = ArchiveDayRange.bounds(
                java.time.Instant.ofEpochMilli(firstEntry.answeredAtEpochMillis)
                    .atZone(zone)
                    .toLocalDate()
                    .toEpochDay(),
                zone,
            )
            val rangeEntries = repository.observeEntriesInRange(
                bounds.startInclusiveEpochMillis,
                bounds.endExclusiveEpochMillis,
            ).first()
            assertTrue(rangeEntries.isNotEmpty())
        }

        val questionMs = measureMillis {
            val questionEntries = repository.observeEntriesForQuestion(1).first()
            assertTrue(questionEntries.isNotEmpty())
        }

        val exportUseCase = ArchiveExportUseCase(
            archiveReadRepository = repository,
            zoneIdProvider = FixedZoneIdProvider(zone),
        )

        val markdownMs = measureMillis {
            val result = exportUseCase.prepare(ExportSelection.All, ExportFormat.MARKDOWN)
                as ArchiveExportPrepareResult.Ready
            assertTrue(result.document.bytes.isNotEmpty())
            logMetric("$label markdownBytes", result.document.bytes.size.toLong())
        }

        val csvMs = measureMillis {
            val result = exportUseCase.prepare(ExportSelection.All, ExportFormat.CSV)
                as ArchiveExportPrepareResult.Ready
            assertTrue(result.document.bytes.isNotEmpty())
            logMetric("$label csvBytes", result.document.bytes.size.toLong())
        }

        database.openHelper.writableDatabase.query("PRAGMA quick_check").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ok", cursor.getString(0))
        }
        database.openHelper.writableDatabase.query("PRAGMA user_version").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }

        logMetric("$label seedMs", seedMs)
        logMetric("$label readMs", readMs)
        logMetric("$label groupingMs", groupingMs)
        logMetric("$label rangeMs", rangeMs)
        logMetric("$label questionMs", questionMs)
        logMetric("$label markdownMs", markdownMs)
        logMetric("$label csvMs", csvMs)
    }

    private inline fun measureMillis(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000L
    }

    private fun logMetric(name: String, value: Long) {
        Log.i(TAG, "STAGE22_PERF $name=$value")
    }

    private class FixedZoneIdProvider(
        private val zoneId: ZoneId,
    ) : ArchiveZoneIdProvider {
        override fun currentZoneId(): ZoneId = zoneId
    }

    private companion object {
        const val TAG = "Stage22LargeArchive"
    }
}
// 07.08.2026 Stage 22 Stability cursor by Me4Hik END

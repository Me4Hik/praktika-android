// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - unit tests ArchiveDatesViewModel
package com.me4hik.praktika.ui.archive

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveDatesViewModelTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeArchiveReadRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeArchiveReadRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadingThenEmpty() = runTest {
        val viewModel = createViewModel()
        repository.emit(emptyList())
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ArchiveDatesUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun loadingThenContentWithCorrectCount() = runTest {
        val day = LocalDate.of(2026, 8, 7)
        repository.emit(
            listOf(
                sampleArchiveEntry(1, epoch(day, 10, 0)),
                sampleArchiveEntry(2, epoch(day, 18, 0)),
            ),
        )
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        val content = viewModel.uiState.value as ArchiveDatesUiState.Content
        assertEquals(1, content.dates.size)
        assertEquals(2, content.dates.single().answerCount)
    }

    @Test
    fun flowUpdateAddsNewDate() = runTest {
        val dayOne = LocalDate.of(2026, 8, 5)
        val dayTwo = LocalDate.of(2026, 8, 7)
        repository.emit(listOf(sampleArchiveEntry(1, epoch(dayOne, 12, 0))))
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        repository.emit(
            listOf(
                sampleArchiveEntry(1, epoch(dayOne, 12, 0)),
                sampleArchiveEntry(2, epoch(dayTwo, 9, 0)),
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()
        val content = viewModel.uiState.value as ArchiveDatesUiState.Content
        assertEquals(2, content.dates.size)
        assertEquals(dayTwo.toEpochDay(), content.dates.first().epochDay)
    }

    @Test
    fun deletingLastEntryEmptiesArchive() = runTest {
        val day = LocalDate.of(2026, 8, 7)
        repository.emit(listOf(sampleArchiveEntry(1, epoch(day, 12, 0))))
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        repository.emit(emptyList())
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ArchiveDatesUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun repositoryFailureProducesError() = runTest {
        val failingRepository = object : com.me4hik.praktika.data.read.ArchiveReadRepository {
            override fun observeEntries() = kotlinx.coroutines.flow.flow<List<com.me4hik.praktika.data.read.ArchiveEntry>> {
                throw IllegalStateException("boom")
            }

            override fun observeEntriesInRange(
                startInclusiveEpochMillis: Long,
                endExclusiveEpochMillis: Long,
            ) = observeEntries()

            override fun observeEntriesForQuestion(questionId: Int) = observeEntries()

            override fun observeOccurrenceHistoryForQuestion(questionId: Int) =
                kotlinx.coroutines.flow.flowOf(emptyList<com.me4hik.praktika.data.read.ArchiveOccurrenceUnit>())

            override fun observeAllOccurrenceHistory() =
                kotlinx.coroutines.flow.flowOf(emptyList<com.me4hik.praktika.data.read.ArchiveOccurrenceUnit>())
        }
        val viewModel = ArchiveDatesViewModel(
            archiveReadRepository = failingRepository,
            zoneIdProvider = FixedArchiveZoneIdProvider(zone),
            displayFormatter = ArchiveDisplayFormatter(),
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ArchiveDatesUiState.Error)
    }

    private fun createViewModel(): ArchiveDatesViewModel {
        return ArchiveDatesViewModel(
            archiveReadRepository = repository,
            zoneIdProvider = FixedArchiveZoneIdProvider(zone),
            displayFormatter = ArchiveDisplayFormatter(),
        )
    }

    private fun epoch(day: LocalDate, hour: Int, minute: Int): Long =
        ZonedDateTime.of(day.year, day.monthValue, day.dayOfMonth, hour, minute, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
}
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END

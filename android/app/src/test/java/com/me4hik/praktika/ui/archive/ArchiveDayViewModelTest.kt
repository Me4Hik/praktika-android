// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - unit tests ArchiveDayViewModel
package com.me4hik.praktika.ui.archive

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ArchiveDayViewModelTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeArchiveReadRepository
    private lateinit var deleteRepository: FakeAnswerDeleteRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeArchiveReadRepository()
        deleteRepository = FakeAnswerDeleteRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectedDayShowsOnlyMatchingRange() = runTest {
        val day = LocalDate.of(2026, 8, 7)
        val otherDay = LocalDate.of(2026, 8, 5)
        repository.emit(
            listOf(
                sampleArchiveEntry(1, epoch(day, 10, 0)),
                sampleArchiveEntry(2, epoch(otherDay, 12, 0)),
            ),
        )
        val viewModel = createViewModel(day.toEpochDay())
        dispatcher.scheduler.advanceUntilIdle()
        val content = viewModel.uiState.value as ArchiveDayUiState.Content
        assertEquals(1, content.entries.size)
        assertEquals(1L, content.entries.single().answerId)
    }

    @Test
    fun invalidEpochDayDoesNotCrash() = runTest {
        val viewModel = ArchiveDayViewModel(
            archiveReadRepository = repository,
            answerDeleteRepository = deleteRepository,
            zoneIdProvider = FixedArchiveZoneIdProvider(zone),
            displayFormatter = ArchiveDisplayFormatter(),
            epochDay = null,
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ArchiveDayUiState.Error)
    }

    @Test
    fun emptyDayShowsEmptyState() = runTest {
        val day = LocalDate.of(2026, 8, 7)
        repository.emit(emptyList())
        val viewModel = createViewModel(day.toEpochDay())
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ArchiveDayUiState.Empty, viewModel.uiState.value)
    }

    private fun createViewModel(epochDay: Long): ArchiveDayViewModel {
        return ArchiveDayViewModel(
            archiveReadRepository = repository,
            answerDeleteRepository = deleteRepository,
            zoneIdProvider = FixedArchiveZoneIdProvider(zone),
            displayFormatter = ArchiveDisplayFormatter(),
            epochDay = epochDay,
        )
    }

    private fun epoch(day: LocalDate, hour: Int, minute: Int): Long =
        ZonedDateTime.of(day.year, day.monthValue, day.dayOfMonth, hour, minute, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
}
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END

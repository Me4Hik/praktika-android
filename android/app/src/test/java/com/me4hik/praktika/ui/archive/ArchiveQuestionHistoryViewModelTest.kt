// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - unit tests ArchiveQuestionHistoryViewModel
// PROMPT 119 — detail VM maps mixed history ASC
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveQuestionHistoryViewModelTest {
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
    fun twoDeferThenAnswer_preservesAscOrder() = runTest {
        val day = LocalDate.of(2026, 8, 7)
        repository.emitHistory(
            listOf(
                sampleDeferredEvent(1, 10, epoch(day, 10, 0), questionId = 1, durationMinutes = 15),
                sampleDeferredEvent(2, 10, epoch(day, 11, 0), questionId = 1, durationMinutes = 5),
                sampleAnswerEvent(
                    occurrenceId = 10,
                    eventAt = epoch(day, 18, 42),
                    questionId = 1,
                    answerId = 55,
                    answerText = "Final",
                    questionText = "Snapshot B",
                    cycleNumber = 2,
                ),
                sampleMissedEvent(99, epoch(day, 12, 0), questionId = 2),
            ),
        )
        val viewModel = createViewModel(questionId = 1)
        dispatcher.scheduler.advanceUntilIdle()
        val content = viewModel.uiState.value as ArchiveQuestionHistoryUiState.Content
        assertEquals(3, content.entries.size)
        assertEquals(ArchiveHistoryItemKind.Deferred, content.entries[0].kind)
        assertEquals(15, content.entries[0].durationMinutes)
        assertEquals(ArchiveHistoryItemKind.Deferred, content.entries[1].kind)
        assertEquals(5, content.entries[1].durationMinutes)
        assertEquals(ArchiveHistoryItemKind.Answer, content.entries[2].kind)
        assertEquals(55L, content.entries[2].answerId)
        assertEquals("Final", content.entries[2].answerText)
        assertTrue(content.entries[2].canShare)
        assertTrue(content.entries[2].canDelete)
        assertTrue(content.entries[0].dateTimeText.contains("10:00"))
    }

    @Test
    fun mapsMissedRejectedDeferredKinds() = runTest {
        repository.emitHistory(
            listOf(
                sampleMissedEvent(1, 1_000L, questionId = 1, questionText = "M"),
                sampleRejectedEvent(2, 2_000L, questionId = 1, questionText = "R"),
                sampleDeferredEvent(3, 2, 3_000L, questionId = 1, durationMinutes = 30, questionText = "D"),
            ),
        )
        val viewModel = createViewModel(questionId = 1)
        dispatcher.scheduler.advanceUntilIdle()
        val content = viewModel.uiState.value as ArchiveQuestionHistoryUiState.Content
        assertEquals(
            listOf(
                ArchiveHistoryItemKind.Missed,
                ArchiveHistoryItemKind.Rejected,
                ArchiveHistoryItemKind.Deferred,
            ),
            content.entries.map { it.kind },
        )
        content.entries.forEach { entry ->
            assertFalse(entry.canShare)
            assertFalse(entry.canDelete)
            assertNull(entry.answerId)
        }
        assertEquals(30, content.entries[2].durationMinutes)
    }

    @Test
    fun deletedAnswerDetail_noShareDelete() = runTest {
        repository.emitHistory(
            listOf(
                sampleAnswerEvent(
                    occurrenceId = 7,
                    eventAt = 7_000L,
                    questionId = 1,
                    answerId = null,
                    answerText = null,
                    questionText = "Gone",
                ),
            ),
        )
        val viewModel = createViewModel(questionId = 1)
        dispatcher.scheduler.advanceUntilIdle()
        val entry = (viewModel.uiState.value as ArchiveQuestionHistoryUiState.Content).entries.single()
        assertEquals(ArchiveHistoryItemKind.Answer, entry.kind)
        assertEquals("o:7:answered", entry.stableKey)
        assertNull(entry.answerId)
        assertNull(entry.answerText)
        assertFalse(entry.canShare)
        assertFalse(entry.canDelete)
    }

    @Test
    fun invalidQuestionIdShowsError() = runTest {
        val viewModel = createViewModel(questionId = null)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ArchiveQuestionHistoryUiState.Error)
    }

    @Test
    fun emptyQuestionHistoryShowsEmptyState() = runTest {
        repository.emitHistory(emptyList())
        val viewModel = createViewModel(questionId = 1)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ArchiveQuestionHistoryUiState.Empty, viewModel.uiState.value)
    }

    private fun createViewModel(questionId: Int?): ArchiveQuestionHistoryViewModel {
        return ArchiveQuestionHistoryViewModel(
            archiveReadRepository = repository,
            answerDeleteRepository = deleteRepository,
            zoneIdProvider = FixedArchiveZoneIdProvider(zone),
            displayFormatter = ArchiveDisplayFormatter(),
            questionId = questionId,
        )
    }

    private fun epoch(day: LocalDate, hour: Int, minute: Int): Long =
        ZonedDateTime.of(day.year, day.monthValue, day.dayOfMonth, hour, minute, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
}
// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END

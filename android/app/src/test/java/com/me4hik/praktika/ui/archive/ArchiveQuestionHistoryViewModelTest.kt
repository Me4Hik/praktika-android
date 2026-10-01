// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - unit tests ArchiveQuestionHistoryViewModel
// PROMPT 119 — detail VM maps mixed history ASC
// 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik START - one unit → one card
// 01.10.2026 Archive T4 occurrence defer line cursor by Me4Hik START - deferCount on cards
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
    fun answerWithFiveDefers_oneCardDeferCountFive() = runTest {
        val day = LocalDate.of(2026, 8, 7)
        repository.emitHistory(
            listOf(
                sampleAnsweredUnit(
                    occurrenceId = 10,
                    eventAt = epoch(day, 18, 42),
                    questionId = 1,
                    answerId = 55,
                    answerText = "Final",
                    questionText = "Snapshot B",
                    cycleNumber = 2,
                    deferEvents = (1..5).map { index ->
                        sampleDeferDetail(index.toLong(), epoch(day, 10, index), durationMinutes = 5)
                    },
                ),
                sampleMissedUnit(99, epoch(day, 12, 0), questionId = 2),
            ),
        )
        val viewModel = createViewModel(questionId = 1)
        dispatcher.scheduler.advanceUntilIdle()
        val content = viewModel.uiState.value as ArchiveQuestionHistoryUiState.Content
        assertEquals(1, content.entries.size)
        val entry = content.entries.single()
        assertEquals(ArchiveHistoryItemKind.Answer, entry.kind)
        assertEquals(5, entry.deferCount)
        assertEquals(55L, entry.answerId)
        assertEquals("Final", entry.answerText)
        assertTrue(entry.canShare)
        assertTrue(entry.canDelete)
        assertTrue(entry.dateTimeText.contains("18:42"))
        assertFalse(content.entries.any { it.kind.name == "Deferred" })
    }

    @Test
    fun answerWithoutDefer_deferCountZero() = runTest {
        repository.emitHistory(
            listOf(
                sampleAnsweredUnit(
                    occurrenceId = 7,
                    eventAt = 7_000L,
                    questionId = 1,
                    answerId = 7,
                    answerText = "Plain",
                ),
            ),
        )
        val viewModel = createViewModel(questionId = 1)
        dispatcher.scheduler.advanceUntilIdle()
        val entry = (viewModel.uiState.value as ArchiveQuestionHistoryUiState.Content).entries.single()
        assertEquals(ArchiveHistoryItemKind.Answer, entry.kind)
        assertEquals(0, entry.deferCount)
    }

    @Test
    fun skipWithOneDefer_oneRejectedCard() = runTest {
        repository.emitHistory(
            listOf(
                sampleRejectedUnit(
                    occurrenceId = 2,
                    eventAt = 2_000L,
                    questionId = 1,
                    questionText = "R",
                    deferEvents = listOf(sampleDeferDetail(1, 1_500L)),
                ),
            ),
        )
        val viewModel = createViewModel(questionId = 1)
        dispatcher.scheduler.advanceUntilIdle()
        val entry = (viewModel.uiState.value as ArchiveQuestionHistoryUiState.Content).entries.single()
        assertEquals(ArchiveHistoryItemKind.Rejected, entry.kind)
        assertEquals(1, entry.deferCount)
        assertFalse(entry.canShare)
        assertFalse(entry.canDelete)
        assertNull(entry.answerId)
    }

    @Test
    fun missedWithTwoDefers_oneMissedCard() = runTest {
        repository.emitHistory(
            listOf(
                sampleMissedUnit(
                    occurrenceId = 3,
                    eventAt = 3_000L,
                    questionId = 1,
                    questionText = "M",
                    deferEvents = listOf(
                        sampleDeferDetail(1, 1_000L),
                        sampleDeferDetail(2, 2_000L),
                    ),
                ),
            ),
        )
        val viewModel = createViewModel(questionId = 1)
        dispatcher.scheduler.advanceUntilIdle()
        val entry = (viewModel.uiState.value as ArchiveQuestionHistoryUiState.Content).entries.single()
        assertEquals(ArchiveHistoryItemKind.Missed, entry.kind)
        assertEquals(2, entry.deferCount)
    }

    @Test
    fun mapsMissedAndRejectedKinds_orderByTerminalTimestamp() = runTest {
        repository.emitHistory(
            listOf(
                sampleMissedUnit(1, 1_000L, questionId = 1, questionText = "M"),
                sampleRejectedUnit(2, 2_000L, questionId = 1, questionText = "R"),
            ),
        )
        val viewModel = createViewModel(questionId = 1)
        dispatcher.scheduler.advanceUntilIdle()
        val content = viewModel.uiState.value as ArchiveQuestionHistoryUiState.Content
        assertEquals(
            listOf(
                ArchiveHistoryItemKind.Missed,
                ArchiveHistoryItemKind.Rejected,
            ),
            content.entries.map { it.kind },
        )
        assertEquals(listOf(0, 0), content.entries.map { it.deferCount })
    }

    @Test
    fun deletedAnswerDetail_noShareDelete() = runTest {
        repository.emitHistory(
            listOf(
                sampleAnsweredUnit(
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
        assertEquals(0, entry.deferCount)
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
// 01.10.2026 Archive T4 occurrence defer line cursor by Me4Hik END
// 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik END
// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END

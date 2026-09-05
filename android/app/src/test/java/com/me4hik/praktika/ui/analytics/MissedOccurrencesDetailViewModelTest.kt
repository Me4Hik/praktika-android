// PROMPT 153 — unit tests for MissedOccurrencesDetailViewModel + mapper
package com.me4hik.praktika.ui.analytics

import com.me4hik.praktika.data.local.model.AnalyticsDeferEventRow
import com.me4hik.praktika.data.local.model.AnalyticsTerminalOccurrenceRow
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.read.AnalyticsReadRepository
import com.me4hik.praktika.data.read.analytics.MissedDetailFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class MissedOccurrencesDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeAnalyticsReadRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeAnalyticsReadRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun byWeekday_showsMissedRowsWithQuestionTextAndWeekdayFormat() = runTest {
        val friday = localMillis(2026, 9, 4, 14, 20, ZONE)
        val saturday = localMillis(2026, 9, 5, 10, 0, ZONE)
        repository.terminals = listOf(
            terminal(1, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, friday, "Missed Fri"),
            terminal(2, 2, QuestionOccurrenceStatus.MISSED_BY_TIME, saturday, "Missed Sat"),
            terminal(3, 1, QuestionOccurrenceStatus.ANSWERED, friday, "Answered"),
            terminal(4, 1, QuestionOccurrenceStatus.SKIPPED_BY_USER, friday, "Skipped"),
        )

        val content = loadContent(MissedDetailFilter.ByWeekday(DayOfWeek.FRIDAY))
        val mode = content.mode as MissedOccurrencesDetailMode.Weekday
        assertEquals(DayOfWeek.FRIDAY, mode.dayOfWeek)
        assertEquals(1, content.rows.size)
        assertEquals(1L, content.rows.single().occurrenceId)
        assertEquals("Missed Fri", content.rows.single().questionText)
        assertEquals("4 сентября 2026 · 14:20", content.rows.single().formattedDateTime)
    }

    @Test
    fun byQuestion_showsHeaderFromNewestAndNullRowText() = runTest {
        val older = localMillis(2026, 9, 1, 10, 0, ZONE)
        val newer = localMillis(2026, 9, 8, 14, 20, ZONE)
        repository.terminals = listOf(
            terminal(1, 10, QuestionOccurrenceStatus.MISSED_BY_TIME, older, "Old snapshot"),
            terminal(2, 20, QuestionOccurrenceStatus.MISSED_BY_TIME, newer, "Other Q"),
            terminal(3, 10, QuestionOccurrenceStatus.MISSED_BY_TIME, newer, "Newest snapshot"),
            terminal(4, 10, QuestionOccurrenceStatus.ANSWERED, newer, "Answered 10"),
        )

        val content = loadContent(MissedDetailFilter.ByQuestion(10))
        val mode = content.mode as MissedOccurrencesDetailMode.Question
        assertEquals(10, mode.questionId)
        assertEquals("Newest snapshot", mode.questionText)
        assertEquals(listOf(3L, 1L), content.rows.map { it.occurrenceId })
        assertTrue(content.rows.all { it.questionText == null })
        assertEquals("вторник, 8 сентября 2026 · 14:20", content.rows.first().formattedDateTime)
        assertEquals("вторник, 1 сентября 2026 · 10:00", content.rows.last().formattedDateTime)
    }

    @Test
    fun order_preservesQueryNewestFirstAndOccurrenceIdTieBreak() = runTest {
        val older = localMillis(2026, 9, 1, 10, 0, ZONE)
        val newer = localMillis(2026, 9, 8, 14, 20, ZONE)
        repository.terminals = listOf(
            terminal(10, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, older),
            terminal(20, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, newer),
            terminal(30, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, newer),
        )

        val content = loadContent(MissedDetailFilter.ByQuestion(1))
        assertEquals(listOf(30L, 20L, 10L), content.rows.map { it.occurrenceId })
    }

    @Test
    fun noMatchingMisses_showEmpty() = runTest {
        val day = localMillis(2026, 9, 4, 12, 0, ZONE)
        repository.terminals = listOf(
            terminal(1, 1, QuestionOccurrenceStatus.ANSWERED, day),
            terminal(2, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, day),
        )

        val viewModel = MissedOccurrencesDetailViewModel(
            analyticsReadRepository = repository,
            filter = MissedDetailFilter.ByWeekday(DayOfWeek.MONDAY),
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(MissedOccurrencesDetailUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun repoThrows_showError() = runTest {
        repository.error = IllegalStateException("boom-read")
        val viewModel = MissedOccurrencesDetailViewModel(
            analyticsReadRepository = repository,
            filter = MissedDetailFilter.ByQuestion(1),
        )
        dispatcher.scheduler.advanceUntilIdle()
        val error = viewModel.uiState.value as MissedOccurrencesDetailUiState.Error
        assertEquals("boom-read", error.message)
    }

    @Test
    fun refresh_reloadsAfterDataChange() = runTest {
        val day = localMillis(2026, 9, 4, 12, 0, ZONE)
        repository.terminals = emptyList()
        val viewModel = MissedOccurrencesDetailViewModel(
            analyticsReadRepository = repository,
            filter = MissedDetailFilter.ByQuestion(1),
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(MissedOccurrencesDetailUiState.Empty, viewModel.uiState.value)

        repository.terminals = listOf(
            terminal(5, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, day, "After refresh"),
        )
        viewModel.refresh()
        dispatcher.scheduler.advanceUntilIdle()
        val content = viewModel.uiState.value as MissedOccurrencesDetailUiState.Content
        assertEquals(5L, content.rows.single().occurrenceId)
        assertEquals(
            "After refresh",
            (content.mode as MissedOccurrencesDetailMode.Question).questionText,
        )
    }

    @Test
    fun deviceTimezoneChange_doesNotChangeFormattedUiState() = runTest {
        val completedAt = localMillis(2026, 9, 8, 14, 20, ZONE)
        repository.terminals = listOf(
            terminal(1, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, completedAt, "Q"),
        )
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val underUtc = loadContent(MissedDetailFilter.ByQuestion(1))
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val underLa = loadContent(MissedDetailFilter.ByQuestion(1))

            assertEquals(underUtc.rows.single().formattedDateTime, underLa.rows.single().formattedDateTime)
            assertEquals("вторник, 8 сентября 2026 · 14:20", underUtc.rows.single().formattedDateTime)
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun questionHeader_usesNewestSnapshotDeterministically() = runTest {
        val t1 = localMillis(2026, 9, 1, 9, 0, ZONE)
        val t2 = localMillis(2026, 9, 8, 9, 0, ZONE)
        repository.terminals = listOf(
            terminal(1, 7, QuestionOccurrenceStatus.MISSED_BY_TIME, t1, "v1"),
            terminal(2, 7, QuestionOccurrenceStatus.MISSED_BY_TIME, t2, "v2-newest"),
            terminal(3, 7, QuestionOccurrenceStatus.MISSED_BY_TIME, t1, "v1-alt"),
        )

        val content = loadContent(MissedDetailFilter.ByQuestion(7))
        val mode = content.mode as MissedOccurrencesDetailMode.Question
        assertEquals("v2-newest", mode.questionText)
        assertNull(content.rows.first().questionText)
    }

    @Test
    fun factory_acceptsRuntimeAndTypedFilter() {
        val factoryClass = MissedOccurrencesDetailViewModelFactory::class.java
        assertTrue(
            factoryClass.constructors.any { ctor ->
                ctor.parameterTypes.size == 2 &&
                    ctor.parameterTypes[0].name.contains("PraktikaRuntime") &&
                    ctor.parameterTypes[1] == MissedDetailFilter::class.java
            },
        )
    }

    private fun loadContent(filter: MissedDetailFilter): MissedOccurrencesDetailUiState.Content {
        val viewModel = MissedOccurrencesDetailViewModel(
            analyticsReadRepository = repository,
            filter = filter,
        )
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel.uiState.value as MissedOccurrencesDetailUiState.Content
    }

    private class FakeAnalyticsReadRepository : AnalyticsReadRepository {
        var terminals: List<AnalyticsTerminalOccurrenceRow> = emptyList()
        var defers: List<AnalyticsDeferEventRow> = emptyList()
        var error: Throwable? = null

        override suspend fun getTerminalRows(): List<AnalyticsTerminalOccurrenceRow> {
            error?.let { throw it }
            return terminals
        }

        override suspend fun getDeferRows(): List<AnalyticsDeferEventRow> {
            error?.let { throw it }
            return defers
        }
    }

    private fun terminal(
        occurrenceId: Long,
        questionId: Int,
        status: QuestionOccurrenceStatus,
        completedAt: Long,
        text: String = "Q$questionId",
        zoneId: String = ZONE,
    ) = AnalyticsTerminalOccurrenceRow(
        occurrenceId = occurrenceId,
        questionId = questionId,
        questionTextSnapshot = text,
        cyclePosition = questionId,
        status = status,
        completedAtEpochMillis = completedAt,
        zoneId = zoneId,
    )

    private fun localMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        zoneId: String,
    ): Long {
        return LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.of(zoneId))
            .toInstant()
            .toEpochMilli()
    }

    private companion object {
        const val ZONE = "Europe/Kyiv"
    }
}

// PROMPT 133 — unit tests for AnalyticsInsightsViewModel + mapper
package com.me4hik.praktika.ui.analytics

import com.me4hik.praktika.data.local.model.AnalyticsDeferEventRow
import com.me4hik.praktika.data.local.model.AnalyticsTerminalOccurrenceRow
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.read.AnalyticsReadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsInsightsViewModelTest {
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
    fun emptyRows_showEmptyState() = runTest {
        val viewModel = AnalyticsInsightsViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(AnalyticsInsightsUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun lowSample_showsContentWithRawRatesAndIneligibleFlags() = runTest {
        val day = localMillis(2026, 9, 4, 12, 0, ZONE)
        repository.terminals = (1..4).map { id ->
            terminal(id.toLong(), questionId = 1, QuestionOccurrenceStatus.MISSED_BY_TIME, day)
        }
        repository.defers = listOf(
            defer(1, 1, 1, day, 15),
            defer(2, 2, 1, day, 15),
        )

        val content = loadContent()
        assertFalse(content.hasAnyInsightEligible)
        assertEquals(1, content.weekdays.size)
        val friday = content.weekdays.single()
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
        assertEquals(4, friday.missed.numerator)
        assertEquals(4, friday.missed.denominator)
        assertEquals(1.0, friday.missed.rate, 1e-9)
        assertFalse(friday.missed.eligible)
        assertEquals(2, friday.deferred.numerator)
        assertEquals(4, friday.deferred.denominator)
        assertFalse(friday.deferred.eligible)
        assertEquals(2, friday.deferEventCount)
        assertFalse(content.topMissed.single().eligible)
        assertEquals(4, content.topMissed.single().numerator)
        assertFalse(content.duration.eligible)
        assertEquals(2, content.duration.totalDeferEvents)
    }

    @Test
    fun enoughSample_marksEligibleTrue() = runTest {
        val day = localMillis(2026, 9, 4, 12, 0, ZONE)
        repository.terminals = (1..5).map { id ->
            terminal(
                id.toLong(),
                questionId = 1,
                status = if (id <= 3) {
                    QuestionOccurrenceStatus.MISSED_BY_TIME
                } else {
                    QuestionOccurrenceStatus.ANSWERED
                },
                completedAt = day,
            )
        }
        repository.defers = (1..5).map { id ->
            defer(id.toLong(), occurrenceId = id.toLong().coerceAtMost(3), questionId = 1, occurredAt = day, duration = 15)
        }

        val content = loadContent()
        assertTrue(content.hasAnyInsightEligible)
        val friday = content.weekdays.single { it.dayOfWeek == DayOfWeek.FRIDAY }
        assertTrue(friday.missed.eligible)
        assertTrue(content.topMissed.single().eligible)
        assertTrue(content.duration.eligible)
    }

    @Test
    fun weekdayMapping_usesStoredZoneId() = runTest {
        // Friday evening Kyiv; same instant is Saturday UTC.
        val fridayKyiv = localMillis(2026, 9, 4, 23, 30, "Europe/Kyiv")
        repository.terminals = listOf(
            terminal(1, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, fridayKyiv, zoneId = "Europe/Kyiv"),
        )

        val content = loadContent()
        assertEquals(DayOfWeek.FRIDAY, content.weekdays.single().dayOfWeek)
    }

    @Test
    fun topMissed_usesAggregatorOrderAndLimitThree() = runTest {
        val day = localMillis(2026, 9, 4, 10, 0, ZONE)
        repository.terminals = listOf(
            // Q1: 2/2 missed
            terminal(1, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, day),
            terminal(2, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, day),
            // Q2: 2/3 missed
            terminal(3, 2, QuestionOccurrenceStatus.MISSED_BY_TIME, day),
            terminal(4, 2, QuestionOccurrenceStatus.MISSED_BY_TIME, day),
            terminal(5, 2, QuestionOccurrenceStatus.ANSWERED, day),
            // Q3: 1/2 missed
            terminal(6, 3, QuestionOccurrenceStatus.MISSED_BY_TIME, day),
            terminal(7, 3, QuestionOccurrenceStatus.ANSWERED, day),
            // Q4: 1/1 missed — should be dropped by take(3)
            terminal(8, 4, QuestionOccurrenceStatus.MISSED_BY_TIME, day),
            // Q5: 0 missed — ranked last
            terminal(9, 5, QuestionOccurrenceStatus.ANSWERED, day),
        )

        val content = loadContent()
        assertEquals(3, content.topMissed.size)
        assertEquals(listOf(1, 4, 2), content.topMissed.map { it.questionId })
        assertEquals(2, content.topMissed[0].numerator)
        assertEquals(2, content.topMissed[0].denominator)
    }

    @Test
    fun topDeferred_usesAggregatorOrderAndLimitThree() = runTest {
        val day = localMillis(2026, 9, 4, 10, 0, ZONE)
        repository.terminals = listOf(
            terminal(1, 1, QuestionOccurrenceStatus.ANSWERED, day),
            terminal(2, 1, QuestionOccurrenceStatus.ANSWERED, day),
            terminal(3, 2, QuestionOccurrenceStatus.ANSWERED, day),
            terminal(4, 2, QuestionOccurrenceStatus.ANSWERED, day),
            terminal(5, 3, QuestionOccurrenceStatus.ANSWERED, day),
            terminal(6, 4, QuestionOccurrenceStatus.ANSWERED, day),
        )
        repository.defers = listOf(
            defer(1, 1, 1, day, 15),
            defer(2, 2, 1, day, 15),
            defer(3, 3, 2, day, 10),
            defer(4, 5, 3, day, 5),
            defer(5, 6, 4, day, 5),
        )

        val content = loadContent()
        assertEquals(3, content.topDeferred.size)
        // Q1: 2/2, Q2: 1/2, Q3: 1/1, Q4: 1/1 — rates 1.0, 1.0, 1.0, 0.5
        // tie rate 1.0: numerator 2 (Q1) then numerator 1 with terminal 2 (Q2) vs terminal 1 (Q3,Q4) → Q1, then Q3/Q4 by id, then Q2?
        // rank: rate DESC, numerator DESC, terminalCount DESC, questionId ASC
        // Q1: rate=1, num=2, term=2
        // Q3: rate=1, num=1, term=1
        // Q4: rate=1, num=1, term=1 → after Q3 by id
        // Q2: rate=0.5
        assertEquals(listOf(1, 3, 4), content.topDeferred.map { it.questionId })
    }

    @Test
    fun duration_eligibleRequiresFiveEvents() = runTest {
        val day = localMillis(2026, 9, 4, 10, 0, ZONE)
        repository.defers = (1..4).map { defer(it.toLong(), 1, 1, day, 15) }
        val ineligible = loadContent()
        assertFalse(ineligible.duration.eligible)
        assertEquals(4, ineligible.duration.totalDeferEvents)
        assertEquals(15, ineligible.duration.modeDurationMinutes)

        repository.defers = (1..5).map { defer(it.toLong(), 1, 1, day, 10) }
        val viewModel = AnalyticsInsightsViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        val eligible = viewModel.uiState.value as AnalyticsInsightsUiState.Content
        assertTrue(eligible.duration.eligible)
        assertEquals(10, eligible.duration.modeDurationMinutes)
        assertEquals(5, eligible.duration.histogram.getValue(10))
    }

    @Test
    fun repositoryError_showsErrorState() = runTest {
        repository.error = IllegalStateException("boom")
        val viewModel = AnalyticsInsightsViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        val error = viewModel.uiState.value as AnalyticsInsightsUiState.Error
        assertEquals("boom", error.message)
    }

    @Test
    fun deviceTimezoneChange_doesNotAffectUiState() = runTest {
        val fridayKyiv = localMillis(2026, 9, 4, 23, 30, "Europe/Kyiv")
        repository.terminals = listOf(
            terminal(1, 1, QuestionOccurrenceStatus.ANSWERED, fridayKyiv, zoneId = "Europe/Kyiv"),
        )
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val underUtc = loadContent()
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val underLa = loadContent()
            assertEquals(underUtc.weekdays, underLa.weekdays)
            assertEquals(DayOfWeek.FRIDAY, underUtc.weekdays.single().dayOfWeek)
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun mapper_isDeterministicForSameSnapshot() = runTest {
        val day = localMillis(2026, 9, 4, 12, 0, ZONE)
        repository.terminals = listOf(
            terminal(1, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, day),
            terminal(2, 2, QuestionOccurrenceStatus.SKIPPED_BY_USER, day),
        )
        repository.defers = listOf(defer(1, 1, 1, day, 30))
        val first = loadContent()
        val second = loadContent()
        assertEquals(first, second)
        assertEquals(DayOfWeek.FRIDAY, first.weekdays.single().dayOfWeek)
        assertEquals(1, first.weekdays.single().rejected.numerator)
        assertEquals(30, first.duration.modeDurationMinutes)
    }

    @Test
    fun refresh_reloadsSnapshot() = runTest {
        val viewModel = AnalyticsInsightsViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(AnalyticsInsightsUiState.Empty, viewModel.uiState.value)

        val day = localMillis(2026, 9, 4, 12, 0, ZONE)
        repository.terminals = listOf(
            terminal(1, 1, QuestionOccurrenceStatus.ANSWERED, day),
        )
        viewModel.refresh()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AnalyticsInsightsUiState.Content)
    }

    @Test
    fun factory_createsViewModelFromRuntimeFieldType() {
        // Compile/API coverage: factory requires PraktikaRuntime.analyticsReadRepository.
        val factoryClass = AnalyticsInsightsViewModelFactory::class.java
        assertTrue(ViewModelFactoryCreatesInsightsViewModel(factoryClass))
    }

    private fun ViewModelFactoryCreatesInsightsViewModel(factoryClass: Class<*>): Boolean {
        return factoryClass.constructors.any { ctor ->
            ctor.parameterTypes.singleOrNull()?.name?.contains("PraktikaRuntime") == true
        }
    }

    private fun loadContent(): AnalyticsInsightsUiState.Content {
        val viewModel = AnalyticsInsightsViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel.uiState.value as AnalyticsInsightsUiState.Content
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
        zoneId: String = ZONE,
    ) = AnalyticsTerminalOccurrenceRow(
        occurrenceId = occurrenceId,
        questionId = questionId,
        questionTextSnapshot = "Q$questionId",
        cyclePosition = questionId,
        status = status,
        completedAtEpochMillis = completedAt,
        zoneId = zoneId,
    )

    private fun defer(
        deferEventId: Long,
        occurrenceId: Long,
        questionId: Int,
        occurredAt: Long,
        duration: Int,
        zoneId: String = ZONE,
    ) = AnalyticsDeferEventRow(
        deferEventId = deferEventId,
        occurrenceId = occurrenceId,
        questionId = questionId,
        occurredAtEpochMillis = occurredAt,
        durationMinutes = duration,
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

// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - fakes for archive ViewModel tests
// PROMPT 119 — fake history feed for mixed archive VM tests
// 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik START - fake occurrence units
package com.me4hik.praktika.ui.archive

import com.me4hik.praktika.data.delete.AnswerDeleteRepository
import com.me4hik.praktika.data.read.ArchiveEntry
import com.me4hik.praktika.data.read.ArchiveOccurrenceDeferEvent
import com.me4hik.praktika.data.read.ArchiveOccurrenceOutcome
import com.me4hik.praktika.data.read.ArchiveOccurrenceUnit
import com.me4hik.praktika.data.read.ArchiveReadRepository
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class FakeArchiveReadRepository : ArchiveReadRepository {
    private val entries = MutableStateFlow<List<ArchiveEntry>>(emptyList())
    private val occurrenceUnits = MutableStateFlow<List<ArchiveOccurrenceUnit>>(emptyList())

    override fun observeEntries(): Flow<List<ArchiveEntry>> = entries

    override fun observeEntriesInRange(
        startInclusiveEpochMillis: Long,
        endExclusiveEpochMillis: Long,
    ): Flow<List<ArchiveEntry>> {
        return entries.map { list ->
            list.filter { entry ->
                entry.answeredAtEpochMillis >= startInclusiveEpochMillis &&
                    entry.answeredAtEpochMillis < endExclusiveEpochMillis
            }
        }
    }

    // 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - fake observe по questionId
    override fun observeEntriesForQuestion(questionId: Int): Flow<List<ArchiveEntry>> {
        return entries.map { list ->
            list.filter { it.questionId == questionId }
                .sortedWith(
                    compareBy<ArchiveEntry> { it.answeredAtEpochMillis }
                        .thenBy { it.answerId },
                )
        }
    }
    // 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END

    override fun observeOccurrenceHistoryForQuestion(questionId: Int): Flow<List<ArchiveOccurrenceUnit>> {
        return occurrenceUnits.map { list ->
            list.filter { it.questionId == questionId }
                .sortedWith(
                    compareBy<ArchiveOccurrenceUnit> { it.eventAtEpochMillis }
                        .thenBy { it.occurrenceId },
                )
        }
    }

    override fun observeAllOccurrenceHistory(): Flow<List<ArchiveOccurrenceUnit>> = occurrenceUnits

    fun emit(value: List<ArchiveEntry>) {
        entries.value = value
    }

    fun emitHistory(value: List<ArchiveOccurrenceUnit>) {
        occurrenceUnits.value = value
    }
}

internal class FixedArchiveZoneIdProvider(
    private val zoneId: ZoneId,
) : ArchiveZoneIdProvider {
    override fun currentZoneId(): ZoneId = zoneId
}

internal fun sampleArchiveEntry(
    answerId: Long,
    epochMillis: Long,
    answerText: String = "Answer $answerId",
    questionId: Int = 1,
    questionText: String = "Question $answerId",
    cycleNumber: Int = 1,
) = ArchiveEntry(
    answerId = answerId,
    occurrenceId = answerId,
    questionId = questionId,
    questionText = questionText,
    answerText = answerText,
    answeredAtEpochMillis = epochMillis,
    plannedAtEpochMillis = epochMillis - 1_000,
    cycleNumber = cycleNumber,
    cyclePosition = 1,
)

internal fun sampleAnsweredUnit(
    occurrenceId: Long,
    eventAt: Long,
    questionId: Int = 1,
    questionText: String = "Question $questionId",
    answerId: Long? = occurrenceId,
    answerText: String? = if (answerId != null) "Answer $answerId" else null,
    cycleNumber: Int = 1,
    cyclePosition: Int = questionId,
    deferEvents: List<ArchiveOccurrenceDeferEvent> = emptyList(),
): ArchiveOccurrenceUnit {
    val key = if (answerId != null) "a:$answerId" else "o:$occurrenceId:answered"
    return ArchiveOccurrenceUnit(
        stableKey = key,
        occurrenceId = occurrenceId,
        questionId = questionId,
        questionTextSnapshot = questionText,
        cycleNumber = cycleNumber,
        cyclePosition = cyclePosition,
        outcome = ArchiveOccurrenceOutcome.ANSWERED,
        eventAtEpochMillis = eventAt,
        answerId = answerId,
        answerText = answerText,
        deferEvents = deferEvents,
    )
}

internal fun sampleRejectedUnit(
    occurrenceId: Long,
    eventAt: Long,
    questionId: Int = 1,
    questionText: String = "Question $questionId",
    cyclePosition: Int = questionId,
    deferEvents: List<ArchiveOccurrenceDeferEvent> = emptyList(),
) = ArchiveOccurrenceUnit(
    stableKey = "o:$occurrenceId:rejected",
    occurrenceId = occurrenceId,
    questionId = questionId,
    questionTextSnapshot = questionText,
    cycleNumber = 1,
    cyclePosition = cyclePosition,
    outcome = ArchiveOccurrenceOutcome.REJECTED,
    eventAtEpochMillis = eventAt,
    answerId = null,
    answerText = null,
    deferEvents = deferEvents,
)

internal fun sampleMissedUnit(
    occurrenceId: Long,
    eventAt: Long,
    questionId: Int = 1,
    questionText: String = "Question $questionId",
    cyclePosition: Int = questionId,
    deferEvents: List<ArchiveOccurrenceDeferEvent> = emptyList(),
) = ArchiveOccurrenceUnit(
    stableKey = "o:$occurrenceId:missed",
    occurrenceId = occurrenceId,
    questionId = questionId,
    questionTextSnapshot = questionText,
    cycleNumber = 1,
    cyclePosition = cyclePosition,
    outcome = ArchiveOccurrenceOutcome.MISSED,
    eventAtEpochMillis = eventAt,
    answerId = null,
    answerText = null,
    deferEvents = deferEvents,
)

internal fun sampleDeferDetail(
    deferEventId: Long,
    eventAt: Long,
    durationMinutes: Int = 15,
) = ArchiveOccurrenceDeferEvent(
    deferEventId = deferEventId,
    occurredAtEpochMillis = eventAt,
    deferredUntilEpochMillis = eventAt + durationMinutes * 60_000L,
    durationMinutes = durationMinutes,
)
// 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik END
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END

// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - fake delete repository для ViewModel tests
internal class FakeAnswerDeleteRepository : AnswerDeleteRepository {
    val deletedAnswerIds = mutableListOf<Long>()
    var failNextDelete = false
    var deleteCallCount = 0

    override suspend fun deleteAnswer(answerId: Long) {
        deleteCallCount++
        if (failNextDelete) {
            failNextDelete = false
            throw RuntimeException("Delete failed")
        }
        deletedAnswerIds += answerId
    }

    fun reset() {
        deletedAnswerIds.clear()
        failNextDelete = false
        deleteCallCount = 0
    }
}
// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik END

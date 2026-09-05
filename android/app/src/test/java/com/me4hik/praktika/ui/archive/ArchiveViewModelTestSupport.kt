// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - fakes for archive ViewModel tests
// PROMPT 119 — fake history feed for mixed archive VM tests
package com.me4hik.praktika.ui.archive

import com.me4hik.praktika.data.delete.AnswerDeleteRepository
import com.me4hik.praktika.data.read.ArchiveEntry
import com.me4hik.praktika.data.read.ArchiveHistoryEvent
import com.me4hik.praktika.data.read.ArchiveReadRepository
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class FakeArchiveReadRepository : ArchiveReadRepository {
    private val entries = MutableStateFlow<List<ArchiveEntry>>(emptyList())
    private val historyEvents = MutableStateFlow<List<ArchiveHistoryEvent>>(emptyList())

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

    override fun observeHistoryForQuestion(questionId: Int): Flow<List<ArchiveHistoryEvent>> {
        return historyEvents.map { list ->
            list.filter { it.questionId == questionId }
                .sortedWith(
                    compareBy<ArchiveHistoryEvent> { it.eventAtEpochMillis }
                        .thenBy { kindTieRank(it) }
                        .thenBy { it.stableKey },
                )
        }
    }

    override fun observeAllHistoryEvents(): Flow<List<ArchiveHistoryEvent>> = historyEvents

    fun emit(value: List<ArchiveEntry>) {
        entries.value = value
    }

    fun emitHistory(value: List<ArchiveHistoryEvent>) {
        historyEvents.value = value
    }

    private fun kindTieRank(event: ArchiveHistoryEvent): Int {
        return when (event) {
            is ArchiveHistoryEvent.Deferred -> 0
            is ArchiveHistoryEvent.Answer,
            is ArchiveHistoryEvent.Rejected,
            is ArchiveHistoryEvent.Missed,
            -> 1
        }
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

internal fun sampleAnswerEvent(
    occurrenceId: Long,
    eventAt: Long,
    questionId: Int = 1,
    questionText: String = "Question $questionId",
    answerId: Long? = occurrenceId,
    answerText: String? = if (answerId != null) "Answer $answerId" else null,
    cycleNumber: Int = 1,
    cyclePosition: Int = questionId,
): ArchiveHistoryEvent.Answer {
    val key = if (answerId != null) "a:$answerId" else "o:$occurrenceId:answered"
    return ArchiveHistoryEvent.Answer(
        stableKey = key,
        questionId = questionId,
        occurrenceId = occurrenceId,
        questionTextSnapshot = questionText,
        cycleNumber = cycleNumber,
        cyclePosition = cyclePosition,
        eventAtEpochMillis = eventAt,
        answerId = answerId,
        answerText = answerText,
    )
}

internal fun sampleRejectedEvent(
    occurrenceId: Long,
    eventAt: Long,
    questionId: Int = 1,
    questionText: String = "Question $questionId",
    cyclePosition: Int = questionId,
) = ArchiveHistoryEvent.Rejected(
    stableKey = "o:$occurrenceId:rejected",
    questionId = questionId,
    occurrenceId = occurrenceId,
    questionTextSnapshot = questionText,
    cycleNumber = 1,
    cyclePosition = cyclePosition,
    eventAtEpochMillis = eventAt,
)

internal fun sampleMissedEvent(
    occurrenceId: Long,
    eventAt: Long,
    questionId: Int = 1,
    questionText: String = "Question $questionId",
    cyclePosition: Int = questionId,
) = ArchiveHistoryEvent.Missed(
    stableKey = "o:$occurrenceId:missed",
    questionId = questionId,
    occurrenceId = occurrenceId,
    questionTextSnapshot = questionText,
    cycleNumber = 1,
    cyclePosition = cyclePosition,
    eventAtEpochMillis = eventAt,
)

internal fun sampleDeferredEvent(
    deferEventId: Long,
    occurrenceId: Long,
    eventAt: Long,
    durationMinutes: Int = 15,
    questionId: Int = 1,
    questionText: String = "Question $questionId",
    cyclePosition: Int = questionId,
) = ArchiveHistoryEvent.Deferred(
    stableKey = "d:$deferEventId",
    questionId = questionId,
    occurrenceId = occurrenceId,
    questionTextSnapshot = questionText,
    cycleNumber = 1,
    cyclePosition = cyclePosition,
    eventAtEpochMillis = eventAt,
    deferEventId = deferEventId,
    durationMinutes = durationMinutes,
    deferredUntilEpochMillis = eventAt + durationMinutes * 60_000L,
)
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

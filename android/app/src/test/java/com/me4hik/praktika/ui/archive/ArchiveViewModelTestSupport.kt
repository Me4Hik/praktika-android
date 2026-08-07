// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - fakes for archive ViewModel tests
package com.me4hik.praktika.ui.archive

import com.me4hik.praktika.data.delete.AnswerDeleteRepository
import com.me4hik.praktika.data.read.ArchiveEntry
import com.me4hik.praktika.data.read.ArchiveReadRepository
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class FakeArchiveReadRepository : ArchiveReadRepository {
    private val entries = MutableStateFlow<List<ArchiveEntry>>(emptyList())

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

    fun emit(value: List<ArchiveEntry>) {
        entries.value = value
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

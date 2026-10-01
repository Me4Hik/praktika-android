// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - androidTest fakes export
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik START - byte-based androidTest writer
package com.me4hik.praktika.ui.archive

import android.net.Uri
import com.me4hik.praktika.data.read.ArchiveEntry
import com.me4hik.praktika.data.read.ArchiveOccurrenceUnit
import com.me4hik.praktika.data.read.ArchiveReadRepository
import com.me4hik.praktika.export.ExportDocumentWriter
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal class AndroidTestArchiveReadRepository : ArchiveReadRepository {
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

    override fun observeEntriesForQuestion(questionId: Int): Flow<List<ArchiveEntry>> {
        return entries.map { list ->
            list.filter { it.questionId == questionId }
                .sortedWith(
                    compareBy<ArchiveEntry> { it.answeredAtEpochMillis }
                        .thenBy { it.answerId },
                )
        }
    }

    // 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik START - empty occurrence history stub
    override fun observeOccurrenceHistoryForQuestion(questionId: Int): Flow<List<ArchiveOccurrenceUnit>> =
        flowOf(emptyList())

    override fun observeAllOccurrenceHistory(): Flow<List<ArchiveOccurrenceUnit>> =
        flowOf(emptyList())
    // 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik END

    fun emit(value: List<ArchiveEntry>) {
        entries.value = value
    }
}

internal class AndroidTestExportDocumentWriter(
    private val shouldFail: Boolean,
) : ExportDocumentWriter {
    var lastBytes: ByteArray? = null

    override suspend fun write(uri: Uri, bytes: ByteArray) {
        if (shouldFail) {
            throw java.io.IOException("write failed")
        }
        lastBytes = bytes.copyOf()
    }
}

internal fun androidTestArchiveEntry(
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

internal class FixedAndroidTestZoneIdProvider(
    private val zoneId: ZoneId = ZoneId.of("Europe/Moscow"),
) : ArchiveZoneIdProvider {
    override fun currentZoneId(): ZoneId = zoneId
}
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik END
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END

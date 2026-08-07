// 07.08.2026 Stage 21 Share cursor by Me4Hik START - plain text payload для entry share
package com.me4hik.praktika.export.share

class ArchiveShareTextFormatter {
    fun format(
        mode: EntryShareMode,
        questionText: String,
        answerText: String,
    ): String {
        return when (mode) {
            EntryShareMode.QUESTION_ONLY -> questionText
            EntryShareMode.QUESTION_AND_ANSWER -> buildString {
                append(questionText)
                append('\n')
                append('\n')
                append(answerText)
            }
        }
    }
}
// 07.08.2026 Stage 21 Share cursor by Me4Hik END

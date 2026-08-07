// 07.08.2026 Stage 21 Share cursor by Me4Hik START - unit tests ArchiveShareTextFormatter
package com.me4hik.praktika.export.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ArchiveShareTextFormatterTest {
    private val formatter = ArchiveShareTextFormatter()

    @Test
    fun questionOnlyReturnsExactQuestionSnapshot() {
        val payload = formatter.format(
            mode = EntryShareMode.QUESTION_ONLY,
            questionText = "Historical snapshot question",
            answerText = "Should be ignored",
        )
        assertEquals("Historical snapshot question", payload)
    }

    @Test
    fun questionAndAnswerUsesBlankLineSeparator() {
        val payload = formatter.format(
            mode = EntryShareMode.QUESTION_AND_ANSWER,
            questionText = "Historical snapshot question",
            answerText = "My answer",
        )
        assertEquals(
            "Historical snapshot question\n\nMy answer",
            payload,
        )
    }

    @Test
    fun multilineQuestionAndAnswerPreserved() {
        val payload = formatter.format(
            mode = EntryShareMode.QUESTION_AND_ANSWER,
            questionText = "Line one\nLine two",
            answerText = "Answer line one\nAnswer line two",
        )
        assertEquals(
            "Line one\nLine two\n\nAnswer line one\nAnswer line two",
            payload,
        )
    }

    @Test
    fun cyrillicAndEmojiPreserved() {
        val payload = formatter.format(
            mode = EntryShareMode.QUESTION_AND_ANSWER,
            questionText = "Какой настоящий я? 🎨",
            answerText = "Тёплый вишнёвый 🎉",
        )
        assertEquals("Какой настоящий я? 🎨\n\nТёплый вишнёвый 🎉", payload)
    }

    @Test
    fun payloadDoesNotContainTechnicalIds() {
        val payload = formatter.format(
            mode = EntryShareMode.QUESTION_AND_ANSWER,
            questionText = "Question text",
            answerText = "Answer text",
        )
        assertFalse(payload.contains("answerId"))
        assertFalse(payload.contains("questionId"))
        assertFalse(payload.contains("occurrenceId"))
    }
}
// 07.08.2026 Stage 21 Share cursor by Me4Hik END

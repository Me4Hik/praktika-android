// PROMPT 123 — pure mapping rules for history card label/body/actions
// 01.10.2026 Archive T4 occurrence defer line cursor by Me4Hik START - no Deferred peer kind
package com.me4hik.praktika.ui.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveHistoryCardContentTest {
    @Test
    fun liveAnswer_showsBodyAndActions() {
        val item = historyItem(
            kind = ArchiveHistoryItemKind.Answer,
            answerId = 9L,
            answerText = "Live text",
            canShare = true,
            canDelete = true,
        )
        assertEquals("Live text", resolveBody(item, deletedBody = "Ответ удалён"))
        assertTrue(item.canShare)
        assertTrue(item.canDelete)
        assertEquals(0, item.deferCount)
    }

    @Test
    fun deletedAnswer_showsDeletedBodyWithoutActions() {
        val item = historyItem(
            kind = ArchiveHistoryItemKind.Answer,
            answerId = null,
            answerText = null,
            canShare = false,
            canDelete = false,
        )
        assertEquals("Ответ удалён", resolveBody(item, deletedBody = "Ответ удалён"))
        assertFalse(item.canShare)
        assertFalse(item.canDelete)
    }

    @Test
    fun rejected_noBodyNoActions() {
        val item = historyItem(kind = ArchiveHistoryItemKind.Rejected, deferCount = 1)
        assertNull(resolveBody(item, deletedBody = "Ответ удалён"))
        assertFalse(item.canShare)
        assertFalse(item.canDelete)
        assertEquals(1, item.deferCount)
    }

    @Test
    fun missed_noBodyNoActions() {
        val item = historyItem(kind = ArchiveHistoryItemKind.Missed, deferCount = 2)
        assertNull(resolveBody(item, deletedBody = "Ответ удалён"))
        assertEquals(2, item.deferCount)
    }

    @Test
    fun answerWithDefers_keepsDeferCountWithoutDeferredKind() {
        val item = historyItem(
            kind = ArchiveHistoryItemKind.Answer,
            answerId = 1L,
            answerText = "A",
            deferCount = 5,
            canShare = true,
            canDelete = true,
        )
        assertEquals(5, item.deferCount)
        assertEquals(ArchiveHistoryItemKind.Answer, item.kind)
        assertTrue(ArchiveHistoryItemKind.entries.none { it.name == "Deferred" })
    }

    private fun resolveBody(
        item: ArchiveQuestionHistoryItem,
        deletedBody: String,
    ): String? {
        return when (item.kind) {
            ArchiveHistoryItemKind.Answer -> item.answerText ?: deletedBody
            ArchiveHistoryItemKind.Rejected,
            ArchiveHistoryItemKind.Missed,
            -> null
        }
    }

    private fun historyItem(
        kind: ArchiveHistoryItemKind,
        answerId: Long? = null,
        answerText: String? = null,
        deferCount: Int = 0,
        canShare: Boolean = false,
        canDelete: Boolean = false,
    ) = ArchiveQuestionHistoryItem(
        stableKey = "test",
        kind = kind,
        occurrenceId = 1L,
        questionText = "Q",
        answerId = answerId,
        answerText = answerText,
        dateTimeText = "date",
        cycleNumber = 1,
        cyclePosition = 1,
        deferCount = deferCount,
        canShare = canShare,
        canDelete = canDelete,
    )
}
// 01.10.2026 Archive T4 occurrence defer line cursor by Me4Hik END

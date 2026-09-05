// PROMPT 123 — pure mapping rules for history card label/body/actions
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
        val item = historyItem(kind = ArchiveHistoryItemKind.Rejected)
        assertNull(resolveBody(item, deletedBody = "Ответ удалён"))
        assertFalse(item.canShare)
        assertFalse(item.canDelete)
    }

    @Test
    fun missed_noBodyNoActions() {
        val item = historyItem(kind = ArchiveHistoryItemKind.Missed)
        assertNull(resolveBody(item, deletedBody = "Ответ удалён"))
    }

    @Test
    fun deferred_keepsDuration_noBody() {
        val item = historyItem(
            kind = ArchiveHistoryItemKind.Deferred,
            durationMinutes = 15,
        )
        assertEquals(15, item.durationMinutes)
        assertNull(resolveBody(item, deletedBody = "Ответ удалён"))
    }

    private fun resolveBody(
        item: ArchiveQuestionHistoryItem,
        deletedBody: String,
    ): String? {
        return when (item.kind) {
            ArchiveHistoryItemKind.Answer -> item.answerText ?: deletedBody
            ArchiveHistoryItemKind.Rejected,
            ArchiveHistoryItemKind.Missed,
            ArchiveHistoryItemKind.Deferred,
            -> null
        }
    }

    private fun historyItem(
        kind: ArchiveHistoryItemKind,
        answerId: Long? = null,
        answerText: String? = null,
        durationMinutes: Int? = null,
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
        durationMinutes = durationMinutes,
        canShare = canShare,
        canDelete = canDelete,
    )
}

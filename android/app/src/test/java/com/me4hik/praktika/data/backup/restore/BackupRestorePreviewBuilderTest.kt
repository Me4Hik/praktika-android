// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Restore Coordinator tests
package com.me4hik.praktika.data.backup.restore

import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestorePreviewBuilderTest {
    @Test
    fun preview_notStartedA_countsCorrectly() {
        val envelope = BackupRestoreFixtures.envelope(
            payload = BackupRestoreFixtures.notStartedPayload(),
        ).copyWithSequence(1L)

        val preview = BackupRestorePreviewBuilder.fromEnvelope(BackupSlotId.A, envelope)

        assertFalse(preview.isPracticeStarted)
        assertEquals(0, preview.occurrenceCount)
        assertEquals(0, preview.answerCount)
        assertEquals(0, preview.deletedTextCount)
        assertEquals(0, preview.completedSlots)
    }

    @Test
    fun preview_richB_countsCorrectly() {
        val envelope = BackupRestoreFixtures.richEnvelope().copyWithSequence(2L)

        val preview = BackupRestorePreviewBuilder.fromEnvelope(BackupSlotId.B, envelope)

        assertTrue(preview.isPracticeStarted)
        assertEquals(4, preview.occurrenceCount)
        assertEquals(1, preview.answerCount)
        assertEquals(1, preview.deletedTextCount)
        assertEquals(3, preview.completedSlots)
    }

    private fun PraktikaBackupEnvelope.copyWithSequence(sequence: Long): PraktikaBackupEnvelope {
        return PraktikaBackupEnvelope(
            backupSchemaVersion = backupSchemaVersion,
            backupSequence = sequence,
            createdAtEpochMillis = createdAtEpochMillis,
            sourceAppVersionCode = sourceAppVersionCode,
            sourceAppVersionName = sourceAppVersionName,
            sourceSeedVersion = sourceSeedVersion,
            backupChecksumSha256 = backupChecksumSha256,
            payload = payload,
        )
    }
}

// 10.08.2026 Post-release fixes cursor by Me4Hik END

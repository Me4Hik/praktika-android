package com.me4hik.praktika.ui.restore

import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.restore.BackupRestoreFixtures
import com.me4hik.praktika.data.backup.restore.BackupRestorePreviewBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class BackupRestorePreviewMapperTest {
    private val mapper = BackupRestorePreviewMapper(
        dateFormatter = ProductionRestoreDateFormatter(
            zoneId = ZoneId.of("UTC"),
        ),
    )

    @Test
    fun mapsSafeFieldsOnly() {
        val preview = BackupRestorePreviewBuilder.fromEnvelope(
            slot = BackupSlotId.B,
            envelope = BackupRestoreFixtures.richEnvelope(),
        )
        val uiModel = mapper.toUiModel(preview)
        assertTrue(uiModel.createdAtText.contains("2023"))
        assertEquals(preview.answerCount, uiModel.answerCount)
        assertEquals(preview.completedSlots, uiModel.completedCount)
        assertTrue(uiModel.practiceStarted)
        assertEquals(1, uiModel.deletedTextCount)
        assertTrue(uiModel.scheduleWillBeRestored)
        val serialized = uiModel.toString()
        assertFalse(serialized.contains("checksum", ignoreCase = true))
        assertFalse(serialized.contains("BackupSlotId", ignoreCase = true))
        assertFalse(serialized.contains("selectedSlot", ignoreCase = true))
    }
}

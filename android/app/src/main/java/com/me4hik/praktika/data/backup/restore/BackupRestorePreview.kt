// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Restore Coordinator
package com.me4hik.praktika.data.backup.restore

import com.me4hik.praktika.data.backup.model.BackupSlotId

data class BackupRestorePreview(
    val selectedSlot: BackupSlotId,
    val backupSequence: Long,
    val createdAtEpochMillis: Long,
    val sourceAppVersionCode: Int,
    val sourceAppVersionName: String,
    val sourceSeedVersion: Int,
    val answerCount: Int,
    val completedSlots: Int,
    val deletedTextCount: Int,
    val occurrenceCount: Int,
    val isPracticeStarted: Boolean,
    val identity: BackupRestoreSelectedIdentity,
)

// 10.08.2026 Post-release fixes cursor by Me4Hik END

// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 5.0 safe restore preview mapper
package com.me4hik.praktika.ui.restore

import com.me4hik.praktika.data.backup.restore.BackupRestorePreview

class BackupRestorePreviewMapper(
    private val dateFormatter: ProductionRestoreDateFormatter,
) {
    fun toUiModel(preview: BackupRestorePreview): ProductionRestorePreviewUiModel {
        return ProductionRestorePreviewUiModel(
            createdAtText = dateFormatter.formatCreatedAt(preview.createdAtEpochMillis),
            answerCount = preview.answerCount,
            completedCount = preview.completedSlots,
            practiceStarted = preview.isPracticeStarted,
            deletedTextCount = preview.deletedTextCount,
            scheduleWillBeRestored = true,
        )
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END

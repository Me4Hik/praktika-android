// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Restore Core v1
package com.me4hik.praktika.data.backup.restore

import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.seed.SeedDataValidator

class BackupRestoreTargetValidator {
    suspend fun validateFreshEmptyTarget(database: PraktikaDatabase): BackupRestoreResult? {
        val state = database.practiceStateDao().get()
            ?: return BackupRestoreResult.TargetDatabaseUnsafe(
                TargetDatabaseUnsafeReason.MISSING_PRACTICE_STATE,
            )

        if (state.id != 1) {
            return BackupRestoreResult.TargetDatabaseUnsafe(
                TargetDatabaseUnsafeReason.INVALID_PRACTICE_STATE_ID,
            )
        }

        if (database.questionDao().count() != SeedDataValidator.EXPECTED_QUESTION_COUNT) {
            return BackupRestoreResult.TargetDatabaseUnsafe(
                TargetDatabaseUnsafeReason.INVALID_QUESTION_COUNT,
            )
        }

        if (database.scheduleSlotDao().count() != SeedDataValidator.EXPECTED_SLOT_COUNT) {
            return BackupRestoreResult.TargetDatabaseUnsafe(
                TargetDatabaseUnsafeReason.INVALID_SCHEDULE_COUNT,
            )
        }

        if (state.seedVersion != SeedDataValidator.EXPECTED_SEED_VERSION) {
            return BackupRestoreResult.TargetDatabaseUnsafe(
                TargetDatabaseUnsafeReason.INVALID_TARGET_SEED,
            )
        }

        if (state.isPracticeStarted ||
            database.questionOccurrenceDao().count() > 0 ||
            database.answerDao().count() > 0 ||
            database.deferEventDao().count() > 0
        ) {
            return BackupRestoreResult.TargetNotEmpty
        }

        return null
    }
}

// 10.08.2026 Post-release fixes cursor by Me4Hik END

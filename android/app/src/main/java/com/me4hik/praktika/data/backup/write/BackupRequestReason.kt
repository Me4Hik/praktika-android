// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.0 backup request reason
package com.me4hik.praktika.data.backup.write

enum class BackupRequestReason {
    ANSWER_SAVED,
    ANSWER_DELETED,
    SCHEDULE_CHANGED,
    PRACTICE_STARTED,
    PRACTICE_STATE_CHANGED,
    RUNTIME_RECONCILED,
    STARTUP_CATCHUP,
    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2A restore catch-up reason
    RESTORE_CATCHUP,
    // 10.08.2026 Post-release fixes cursor by Me4Hik END
    MANUAL,
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END

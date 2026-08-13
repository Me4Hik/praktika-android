// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.1 failure retry policy
package com.me4hik.praktika.data.backup.write

enum class BackupFailureRetryGroup {
    TRANSIENT,
    RECONNECT_REQUIRED,
    USER_ACTION_REQUIRED,
    DATABASE_STATE,
    UNKNOWN,
}

enum class BackupAttemptTrigger {
    MUTATION,
    STARTUP,
    MANUAL,
    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2A restore catch-up trigger
    RESTORE_CATCHUP,
    // 10.08.2026 Post-release fixes cursor by Me4Hik END
}

/**
 * Pure retry policy derived from persisted [BackupFailureStatus].
 * Policy itself is never persisted.
 */
object BackupFailureRetryPolicy {
    fun groupOf(status: BackupFailureStatus?): BackupFailureRetryGroup {
        return when (status) {
            null -> BackupFailureRetryGroup.TRANSIENT
            BackupFailureStatus.PERMISSION_LOST -> BackupFailureRetryGroup.RECONNECT_REQUIRED
            BackupFailureStatus.AMBIGUOUS_SLOT,
            BackupFailureStatus.UNTRUSTED_ARTIFACTS,
            BackupFailureStatus.SEQUENCE_EXHAUSTED,
            -> BackupFailureRetryGroup.USER_ACTION_REQUIRED
            BackupFailureStatus.UNSAFE_DATABASE -> BackupFailureRetryGroup.DATABASE_STATE
            BackupFailureStatus.UNKNOWN -> BackupFailureRetryGroup.UNKNOWN
            BackupFailureStatus.WRITE_FAILED,
            BackupFailureStatus.EXPORT_FAILED,
            BackupFailureStatus.STORAGE_UNAVAILABLE,
            BackupFailureStatus.STORAGE_READ_FAILED,
            BackupFailureStatus.STORAGE_ACCESS_UNRELIABLE,
            BackupFailureStatus.POST_WRITE_VALIDATION_FAILED,
            -> BackupFailureRetryGroup.TRANSIENT
        }
    }

    fun allowsAttempt(
        status: BackupFailureStatus?,
        trigger: BackupAttemptTrigger,
        needsReconnect: Boolean,
    ): Boolean {
        if (needsReconnect || status == BackupFailureStatus.PERMISSION_LOST) {
            return false
        }
        return when (groupOf(status)) {
            BackupFailureRetryGroup.TRANSIENT,
            BackupFailureRetryGroup.DATABASE_STATE,
            BackupFailureRetryGroup.UNKNOWN,
            -> true
            BackupFailureRetryGroup.USER_ACTION_REQUIRED ->
                trigger == BackupAttemptTrigger.STARTUP ||
                    trigger == BackupAttemptTrigger.MANUAL ||
                    trigger == BackupAttemptTrigger.RESTORE_CATCHUP
            BackupFailureRetryGroup.RECONNECT_REQUIRED -> false
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END

// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.1 backup failure status
package com.me4hik.praktika.data.backup.write

/**
 * Safe persisted failure categories for automatic backup status.
 * Never persist exception messages, URIs, answer data, or raw JSON.
 */
enum class BackupFailureStatus {
    PERMISSION_LOST,
    STORAGE_UNAVAILABLE,
    STORAGE_READ_FAILED,
    STORAGE_ACCESS_UNRELIABLE,
    AMBIGUOUS_SLOT,
    UNTRUSTED_ARTIFACTS,
    SEQUENCE_EXHAUSTED,
    UNSAFE_DATABASE,
    WRITE_FAILED,
    POST_WRITE_VALIDATION_FAILED,
    EXPORT_FAILED,
    UNKNOWN,
    ;

    companion object {
        fun fromPersistedName(raw: String?): BackupFailureStatus? {
            if (raw.isNullOrBlank()) {
                return null
            }
            return entries.firstOrNull { it.name == raw } ?: UNKNOWN
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END

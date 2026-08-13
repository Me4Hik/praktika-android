// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.0 backup write failure category
package com.me4hik.praktika.data.backup.write

enum class BackupWriteFailureCategory {
    PERMISSION_LOST,
    STORAGE_UNAVAILABLE,
    PROVIDER_FAILURE,
    SLOT_AMBIGUOUS,
    DELETE_FAILED,
    CREATE_FAILED,
    NAME_MISMATCH,
    WRITE_FAILED,
    POST_WRITE_READ_FAILED,
    POST_WRITE_VALIDATION_FAILED,
    EXPECTED_IDENTITY_MISMATCH,
    ENVELOPE_ASSEMBLY_FAILED,
    EXPORT_READ_FAILURE,
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END

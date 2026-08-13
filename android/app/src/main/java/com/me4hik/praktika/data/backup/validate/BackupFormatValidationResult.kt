// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - structured backup validation results
package com.me4hik.praktika.data.backup.validate

enum class BackupFormatFailureReason {
    TooLarge,
    BomNotAllowed,
    MalformedJson,
    MissingField,
    UnknownField,
    NullNotAllowed,
    WrongType,
    UnsupportedSchema,
    InvalidMetadata,
    InvalidChecksumFormat,
    ChecksumMismatch,
    InvalidOccurrenceStatus,
}

sealed class BackupFormatValidationResult {
    data class Valid(
        val envelope: com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope,
    ) : BackupFormatValidationResult()

    data class Invalid(
        val reason: BackupFormatFailureReason,
        val detail: String,
    ) : BackupFormatValidationResult()
}

sealed class BackupJsonDecodeResult {
    data class Success(
        val envelope: com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope,
    ) : BackupJsonDecodeResult()

    data class Failure(
        val reason: BackupFormatFailureReason,
        val detail: String,
    ) : BackupJsonDecodeResult()
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END

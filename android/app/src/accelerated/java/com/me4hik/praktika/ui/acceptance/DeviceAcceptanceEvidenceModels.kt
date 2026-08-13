// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik START - sanitized evidence report models
package com.me4hik.praktika.ui.acceptance

enum class DeviceAcceptanceSlotId {
    A,
    B,
}

enum class DeviceAcceptanceDecodeStatus {
    MISSING,
    VALID,
    INVALID,
    CHECKSUM_INVALID,
    TOO_LARGE,
    UNREADABLE,
    AMBIGUOUS,
    ACCESS_UNAVAILABLE,
    PROVIDER_FAILURE,
}

enum class DeviceAcceptanceResultStatus {
    OK,
    NOT_CONFIGURED,
    NO_AUTH,
    ACCESS_UNAVAILABLE,
    PERMISSION_DENIED,
    PROVIDER_FAILURE,
    CANCELLED,
    UNKNOWN_ACTION,
    REVOKED,
    ALREADY_ABSENT,
    RELEASE_FAILED,
    STASHED_A,
    STASHED_B,
    RUNTIME_UNBOUND,
    FAILED,
    // 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik START - Room evidence failure categories
    ROOM_UNAVAILABLE,
    ROOM_READ_FAILED,
    ROOM_UNSAFE,
    // 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik END
}

data class DeviceAcceptanceSlotSummary(
    val slot: DeviceAcceptanceSlotId,
    val exists: Boolean,
    val byteSize: Long?,
    val fullFileSha256: String?,
    val decodeStatus: DeviceAcceptanceDecodeStatus,
    val checksumValid: Boolean?,
    val sequence: Long?,
    val createdAtEpochMillis: Long?,
)

data class DeviceAcceptanceSemanticSummary(
    val backupSchemaVersion: Int,
    val sequence: Long,
    val createdAtEpochMillis: Long,
    val sourceVersionCode: Int,
    val sourceVersionName: String,
    val practiceStarted: Boolean,
    val isPaused: Boolean,
    val currentCycleNumber: Int,
    val nextCyclePosition: Int,
    val scheduleMinutes: List<Int>,
    val occurrenceCount: Int,
    val answerCount: Int,
    val deletedTextCount: Int,
    val answeredCount: Int,
    val skippedCount: Int,
    val scheduledCount: Int,
    val missedCount: Int,
)

// 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik START - Room-only safe state summary
data class DeviceAcceptanceRoomStateSummary(
    val practiceStarted: Boolean,
    val isPaused: Boolean,
    val currentCycleNumber: Int,
    val nextCyclePosition: Int,
    val scheduleMinutes: List<Int>,
    val occurrenceCount: Int,
    val answerCount: Int,
    val deletedTextCount: Int,
    val answeredCount: Int,
    val skippedCount: Int,
    val scheduledCount: Int,
    val missedCount: Int,
    val availableCount: Int,
)
// 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik END

data class DeviceAcceptanceEvidenceReport(
    val action: String,
    val resultStatus: DeviceAcceptanceResultStatus,
    val harnessExecutedAtEpochMillis: Long,
    val slotA: DeviceAcceptanceSlotSummary?,
    val slotB: DeviceAcceptanceSlotSummary?,
    val latestValidSlot: DeviceAcceptanceSlotId?,
    val latestValidSequence: Long?,
    val latestValidSemantics: DeviceAcceptanceSemanticSummary?,
    val folderFingerprintSha256: String?,
    // 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik START - optional Room summary field
    val roomSummary: DeviceAcceptanceRoomStateSummary? = null,
    // 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik END
)
// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik END

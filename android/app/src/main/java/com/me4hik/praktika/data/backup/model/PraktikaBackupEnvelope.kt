// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - portable backup envelope DTO
package com.me4hik.praktika.data.backup.model

class PraktikaBackupEnvelope(
    val backupSchemaVersion: Int,
    val backupSequence: Long,
    val createdAtEpochMillis: Long,
    val sourceAppVersionCode: Int,
    val sourceAppVersionName: String,
    val sourceSeedVersion: Int,
    val backupChecksumSha256: String,
    val payload: PraktikaBackupPayload,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PraktikaBackupEnvelope) return false
        return backupSchemaVersion == other.backupSchemaVersion &&
            backupSequence == other.backupSequence &&
            createdAtEpochMillis == other.createdAtEpochMillis &&
            sourceAppVersionCode == other.sourceAppVersionCode &&
            sourceAppVersionName == other.sourceAppVersionName &&
            sourceSeedVersion == other.sourceSeedVersion &&
            backupChecksumSha256 == other.backupChecksumSha256 &&
            payload == other.payload
    }

    override fun hashCode(): Int {
        var result = backupSchemaVersion
        result = 31 * result + backupSequence.hashCode()
        result = 31 * result + createdAtEpochMillis.hashCode()
        result = 31 * result + sourceAppVersionCode
        result = 31 * result + sourceAppVersionName.hashCode()
        result = 31 * result + sourceSeedVersion
        result = 31 * result + backupChecksumSha256.hashCode()
        result = 31 * result + payload.hashCode()
        return result
    }

    override fun toString(): String {
        return "PraktikaBackupEnvelope(schema=$backupSchemaVersion, sequence=$backupSequence, createdAt=$createdAtEpochMillis, versionCode=$sourceAppVersionCode)"
    }
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END

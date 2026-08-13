// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - integrity encoder registry contract
package com.me4hik.praktika.data.backup.integrity

import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope

interface BackupIntegrityEncoder {
    fun encode(envelope: PraktikaBackupEnvelope): ByteArray
}

object BackupIntegrityEncoders {
    fun forSchemaVersion(schemaVersion: Int): BackupIntegrityEncoder? {
        return when (schemaVersion) {
            1 -> BackupIntegrityEncoderV1
            else -> null
        }
    }
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END

// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - SHA-256 checksum over integrity material
package com.me4hik.praktika.data.backup.checksum

import com.me4hik.praktika.data.backup.integrity.BackupIntegrityEncoders
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import java.security.MessageDigest

object BackupChecksum {
    fun calculate(envelope: PraktikaBackupEnvelope): String {
        val encoder = BackupIntegrityEncoders.forSchemaVersion(envelope.backupSchemaVersion)
            ?: error("Unsupported backup schema version: ${envelope.backupSchemaVersion}")
        val material = encoder.encode(envelope)
        val digest = MessageDigest.getInstance("SHA-256").digest(material)
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END

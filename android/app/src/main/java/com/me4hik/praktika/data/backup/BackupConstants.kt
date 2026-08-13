// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - frozen backup format constants
package com.me4hik.praktika.data.backup

object BackupConstants {
    const val BACKUP_SCHEMA_VERSION_V1 = 1
    const val MIN_SUPPORTED_SCHEMA_VERSION = 1
    const val MAX_SUPPORTED_SCHEMA_VERSION = 1

    const val MAX_BACKUP_BYTES = 10 * 1024 * 1024 // 10 MiB

    const val CHECKSUM_HEX_LENGTH = 64

    val BOM_BYTES = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END

// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - A/B backup slot identity
package com.me4hik.praktika.data.backup.model

enum class BackupSlotId(val fileName: String) {
    A("praktika-backup-A.json"),
    B("praktika-backup-B.json"),
    ;

    fun other(): BackupSlotId = when (this) {
        A -> B
        B -> A
    }
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END

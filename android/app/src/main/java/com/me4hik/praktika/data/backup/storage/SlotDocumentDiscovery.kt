// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 SAF storage
package com.me4hik.praktika.data.backup.storage

import com.me4hik.praktika.data.backup.model.BackupSlotId

data class TreeChildDocument(
    val documentId: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
)

sealed class SlotDocumentMatch {
    data object Missing : SlotDocumentMatch()

    data class Located(
        val document: TreeChildDocument,
    ) : SlotDocumentMatch()

    data class Ambiguous(
        val matchCount: Int,
    ) : SlotDocumentMatch()
}

object SlotDocumentDiscovery {
    fun matchSlot(
        children: List<TreeChildDocument>,
        slot: BackupSlotId,
    ): SlotDocumentMatch {
        val matches = children.filter { it.displayName == slot.fileName }
        return when {
            matches.isEmpty() -> SlotDocumentMatch.Missing
            matches.size == 1 -> SlotDocumentMatch.Located(matches.single())
            else -> SlotDocumentMatch.Ambiguous(matches.size)
        }
    }

    fun discoverAll(children: List<TreeChildDocument>): Map<BackupSlotId, SlotDocumentMatch> {
        return BackupSlotId.entries.associateWith { slot ->
            matchSlot(children, slot)
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END

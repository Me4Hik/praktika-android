// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 SAF storage tests
package com.me4hik.praktika.data.backup.storage

import com.me4hik.praktika.data.backup.model.BackupSlotId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotDocumentDiscoveryTest {
    @Test
    fun slotAMissingWhenNoMatch() {
        val result = SlotDocumentDiscovery.matchSlot(emptyList(), BackupSlotId.A)
        assertEquals(SlotDocumentMatch.Missing, result)
    }

    @Test
    fun slotAExactlyOnce() {
        val child = TreeChildDocument("doc-a", BackupSlotId.A.fileName, "application/json", 100L)
        val result = SlotDocumentDiscovery.matchSlot(listOf(child), BackupSlotId.A)
        assertTrue(result is SlotDocumentMatch.Located)
        assertEquals(child, (result as SlotDocumentMatch.Located).document)
    }

    @Test
    fun slotADuplicateIsAmbiguous() {
        val children = listOf(
            TreeChildDocument("doc-a-1", BackupSlotId.A.fileName, "application/json", 100L),
            TreeChildDocument("doc-a-2", BackupSlotId.A.fileName, "text/plain", 200L),
        )
        val result = SlotDocumentDiscovery.matchSlot(children, BackupSlotId.A)
        assertEquals(SlotDocumentMatch.Ambiguous(2), result)
    }

    @Test
    fun slotBMissingWhenOnlyAExists() {
        val children = listOf(
            TreeChildDocument("doc-a", BackupSlotId.A.fileName, "application/json", 100L),
        )
        val result = SlotDocumentDiscovery.matchSlot(children, BackupSlotId.B)
        assertEquals(SlotDocumentMatch.Missing, result)
    }

    @Test
    fun slotBDuplicateIsAmbiguous() {
        val children = listOf(
            TreeChildDocument("doc-b-1", BackupSlotId.B.fileName, "application/json", 100L),
            TreeChildDocument("doc-b-2", BackupSlotId.B.fileName, "application/json", 200L),
        )
        val result = SlotDocumentDiscovery.matchSlot(children, BackupSlotId.B)
        assertEquals(SlotDocumentMatch.Ambiguous(2), result)
    }

    @Test
    fun discoverAllTreatsSlotsIndependently() {
        val children = listOf(
            TreeChildDocument("doc-a", BackupSlotId.A.fileName, "application/json", 100L),
            TreeChildDocument("other", "notes.txt", "text/plain", 10L),
        )
        val discovered = SlotDocumentDiscovery.discoverAll(children)
        assertTrue(discovered.getValue(BackupSlotId.A) is SlotDocumentMatch.Located)
        assertEquals(SlotDocumentMatch.Missing, discovered.getValue(BackupSlotId.B))
    }

    @Test
    fun providerOrderDoesNotAffectDuplicateDetection() {
        val first = TreeChildDocument("doc-1", BackupSlotId.A.fileName, null, null)
        val second = TreeChildDocument("doc-2", BackupSlotId.A.fileName, null, null)
        assertEquals(
            SlotDocumentMatch.Ambiguous(2),
            SlotDocumentDiscovery.matchSlot(listOf(first, second), BackupSlotId.A),
        )
        assertEquals(
            SlotDocumentMatch.Ambiguous(2),
            SlotDocumentDiscovery.matchSlot(listOf(second, first), BackupSlotId.A),
        )
    }

    @Test
    fun unrelatedFilenamesIgnored() {
        val children = listOf(
            TreeChildDocument("x", "random.json", "application/json", 1L),
            TreeChildDocument("y", "praktika-backup-C.json", "application/json", 1L),
        )
        assertEquals(SlotDocumentMatch.Missing, SlotDocumentDiscovery.matchSlot(children, BackupSlotId.A))
        assertEquals(SlotDocumentMatch.Missing, SlotDocumentDiscovery.matchSlot(children, BackupSlotId.B))
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END

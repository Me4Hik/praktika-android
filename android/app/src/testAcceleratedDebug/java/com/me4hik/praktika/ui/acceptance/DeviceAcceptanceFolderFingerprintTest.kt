// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik START - folder fingerprint host tests
package com.me4hik.praktika.ui.acceptance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DeviceAcceptanceFolderFingerprintTest {
    @Test
    fun sameSlotBytes_sameFingerprint() {
        val first = DeviceAcceptanceFolderFingerprint.compute(
            slotAFullFileSha256 = "aaa",
            slotAExists = true,
            slotBFullFileSha256 = "bbb",
            slotBExists = true,
        )
        val second = DeviceAcceptanceFolderFingerprint.compute(
            slotAFullFileSha256 = "aaa",
            slotAExists = true,
            slotBFullFileSha256 = "bbb",
            slotBExists = true,
        )
        assertEquals(first, second)
    }

    @Test
    fun aChanged_fingerprintChanges() {
        val base = DeviceAcceptanceFolderFingerprint.compute("aaa", true, "bbb", true)
        val changed = DeviceAcceptanceFolderFingerprint.compute("aac", true, "bbb", true)
        assertNotEquals(base, changed)
    }

    @Test
    fun bChanged_fingerprintChanges() {
        val base = DeviceAcceptanceFolderFingerprint.compute("aaa", true, "bbb", true)
        val changed = DeviceAcceptanceFolderFingerprint.compute("aaa", true, "bbc", true)
        assertNotEquals(base, changed)
    }

    @Test
    fun presenceChanged_fingerprintChanges() {
        val present = DeviceAcceptanceFolderFingerprint.compute("aaa", true, null, false)
        val absentBoth = DeviceAcceptanceFolderFingerprint.compute(null, false, null, false)
        assertNotEquals(present, absentBoth)
    }

    @Test
    fun orderingDeterministic_aThenB() {
        val direct = DeviceAcceptanceFolderFingerprint.compute("111", true, "222", true)
        val fromSlots = DeviceAcceptanceFolderFingerprint.computeFromSlots(
            DeviceAcceptanceSlotSummary(
                slot = DeviceAcceptanceSlotId.A,
                exists = true,
                byteSize = 1,
                fullFileSha256 = "111",
                decodeStatus = DeviceAcceptanceDecodeStatus.VALID,
                checksumValid = true,
                sequence = 1,
                createdAtEpochMillis = 1,
            ),
            DeviceAcceptanceSlotSummary(
                slot = DeviceAcceptanceSlotId.B,
                exists = true,
                byteSize = 1,
                fullFileSha256 = "222",
                decodeStatus = DeviceAcceptanceDecodeStatus.VALID,
                checksumValid = true,
                sequence = 2,
                createdAtEpochMillis = 2,
            ),
        )
        assertEquals(direct, fromSlots)
    }

    @Test
    fun latestUnchangedButOppositeSlotChanged_fingerprintChanges() {
        val before = DeviceAcceptanceFolderFingerprint.compute("latest", true, "stale-old", true)
        val after = DeviceAcceptanceFolderFingerprint.compute("latest", true, "stale-new", true)
        assertNotEquals(before, after)
    }
}
// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik END

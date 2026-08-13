// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik START - both-slot folder fingerprint
package com.me4hik.praktika.ui.acceptance

import java.security.MessageDigest

object DeviceAcceptanceFolderFingerprint {
    fun compute(
        slotAFullFileSha256: String?,
        slotAExists: Boolean,
        slotBFullFileSha256: String?,
        slotBExists: Boolean,
    ): String {
        val canonical = buildString {
            append(canonicalLine(DeviceAcceptanceSlotId.A, slotAExists, slotAFullFileSha256))
            append('\n')
            append(canonicalLine(DeviceAcceptanceSlotId.B, slotBExists, slotBFullFileSha256))
            append('\n')
        }
        return sha256Hex(canonical.toByteArray(Charsets.UTF_8))
    }

    fun computeFromSlots(
        slotA: DeviceAcceptanceSlotSummary,
        slotB: DeviceAcceptanceSlotSummary,
    ): String {
        return compute(
            slotAFullFileSha256 = slotA.fullFileSha256,
            slotAExists = slotA.exists,
            slotBFullFileSha256 = slotB.fullFileSha256,
            slotBExists = slotB.exists,
        )
    }

    private fun canonicalLine(
        slot: DeviceAcceptanceSlotId,
        exists: Boolean,
        fullFileSha256: String?,
    ): String {
        val presence = if (exists) "PRESENT" else "ABSENT"
        val hash = if (exists && !fullFileSha256.isNullOrBlank()) fullFileSha256.lowercase() else "-"
        return "${slot.name}|$presence|$hash"
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik END

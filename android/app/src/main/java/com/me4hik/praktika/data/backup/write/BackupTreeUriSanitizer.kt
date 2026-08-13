// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.1 backup URI validation
package com.me4hik.praktika.data.backup.write

/**
 * Minimal URI usability checks for persisted backup tree strings.
 * Pure string rules (no Android Uri dependency) so host unit tests and runtime agree.
 * Does not log raw URI content.
 */
internal object BackupTreeUriSanitizer {
    fun isUsableTreeUriString(raw: String?): Boolean {
        if (raw.isNullOrBlank()) {
            return false
        }
        // Reject leading/trailing whitespace — Stage5 persists Uri.toString() without padding.
        if (raw.trim() != raw) {
            return false
        }
        val schemeSeparator = raw.indexOf("://")
        if (schemeSeparator <= 0) {
            return false
        }
        val scheme = raw.substring(0, schemeSeparator)
        if (scheme.isBlank()) {
            return false
        }
        val afterScheme = raw.substring(schemeSeparator + 3)
        return afterScheme.isNotBlank()
    }

    fun normalizeOrNull(raw: String?): String? {
        return if (isUsableTreeUriString(raw)) raw else null
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END

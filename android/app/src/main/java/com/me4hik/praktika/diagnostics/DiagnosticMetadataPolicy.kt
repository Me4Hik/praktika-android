// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika.diagnostics

object DiagnosticMetadataPolicy {
    private val forbiddenKeyFragments = listOf(
        "question_text",
        "answer_text",
        "markdown",
        "csv",
        "notification_body",
        "secret",
        "password",
        "token",
        "dsn",
    )

    private val maxKeyLength = 64
    private val maxValueLength = 256
    private val maxEntries = 24

    fun sanitize(raw: Map<String, String>): Map<String, String> {
        if (raw.isEmpty()) {
            return emptyMap()
        }
        val sanitized = LinkedHashMap<String, String>()
        for ((key, value) in raw) {
            if (sanitized.size >= maxEntries) {
                break
            }
            if (!isAllowedKey(key)) {
                continue
            }
            sanitized[key] = value.take(maxValueLength)
        }
        return sanitized
    }

    fun isAllowedKey(key: String): Boolean {
        if (key.isBlank() || key.length > maxKeyLength) {
            return false
        }
        val normalized = key.lowercase()
        return forbiddenKeyFragments.none { fragment -> normalized.contains(fragment) }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END

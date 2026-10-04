package com.me4hik.praktika.measurement

object AnalyticsPrivacyPolicy {
    private val forbiddenKeyFragments = listOf(
        "answer_text",
        "answer",
        "question_text",
        "questiontext",
        "notification_body",
        "notification_title",
        "markdown",
        "csv",
        "pdf",
        "content",
        "body",
        "uri",
        "path",
        "file",
        "secret",
        "password",
        "token",
        "dsn",
        "mood_level",
        "level",
    )

    private const val MAX_KEY_LENGTH = 64
    private const val MAX_VALUE_LENGTH = 128
    private const val MAX_ENTRIES = 16

    fun isAllowedKey(key: String): Boolean {
        if (key.isBlank() || key.length > MAX_KEY_LENGTH) {
            return false
        }
        val normalized = key.lowercase()
        return forbiddenKeyFragments.none { fragment -> normalized.contains(fragment) }
    }

    fun sanitizeValue(value: String): String = value.take(MAX_VALUE_LENGTH)

    fun maxEntries(): Int = MAX_ENTRIES
}

package com.me4hik.praktika.actiontoursmoke

/**
 * Java `.properties` value unescape (`\:` → `:`, `\\` → `\`, etc.).
 * Pure logic — shared by PowerShell runner contract tests.
 */
object JavaPropertiesUnescape {
    fun unescape(value: String): String {
        if (value.isEmpty()) return value
        return Regex("""\\(.)""").replace(value) { match ->
            match.groupValues[1]
        }
    }
}

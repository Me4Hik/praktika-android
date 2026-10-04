package com.me4hik.praktika.measurement

/**
 * Typed/safe analytics parameters. Feature code must not pass free-form sensitive maps.
 */
class SafeAnalyticsParams private constructor(
    private val values: Map<String, String>,
) {
    fun asMap(): Map<String, String> = values

    fun isEmpty(): Boolean = values.isEmpty()

    companion object {
        val EMPTY: SafeAnalyticsParams = SafeAnalyticsParams(emptyMap())

        fun builder(): Builder = Builder()

        fun empty(): SafeAnalyticsParams = EMPTY
    }

    class Builder {
        private val values = LinkedHashMap<String, String>()

        fun language(tag: String): Builder = put(Keys.LANGUAGE, tag)

        fun source(value: String): Builder = put(Keys.SOURCE, value)

        fun questionId(id: Int): Builder = put(Keys.QUESTION_ID, id.toString())

        fun durationMinutes(minutes: Int): Builder = put(Keys.DURATION_MINUTES, minutes.toString())

        fun exportFormat(format: String): Builder = put(Keys.EXPORT_FORMAT, format)

        fun flavor(value: String): Builder = put(Keys.FLAVOR, value)

        fun previousLanguage(tag: String): Builder = put(Keys.PREVIOUS_LANGUAGE, tag)

        fun result(value: String): Builder = put(Keys.RESULT, value)

        /**
         * Restricted put for allow-listed keys only. Throws on forbidden keys.
         */
        fun put(key: String, value: String): Builder {
            require(AnalyticsPrivacyPolicy.isAllowedKey(key)) {
                "Forbidden analytics parameter key: $key"
            }
            if (values.size >= AnalyticsPrivacyPolicy.maxEntries()) {
                return this
            }
            values[key] = AnalyticsPrivacyPolicy.sanitizeValue(value)
            return this
        }

        fun build(): SafeAnalyticsParams = SafeAnalyticsParams(values.toMap())
    }

    object Keys {
        const val LANGUAGE = "language"
        const val PREVIOUS_LANGUAGE = "previous_language"
        const val SOURCE = "source"
        const val QUESTION_ID = "question_id"
        const val DURATION_MINUTES = "duration_minutes"
        const val EXPORT_FORMAT = "export_format"
        const val FLAVOR = "flavor"
        const val RESULT = "result"
    }

    object Sources {
        const val CHOOSER = "chooser"
        const val SETTINGS = "settings"
        const val HOME = "home"
        const val NOTIFICATION = "notification"
        const val ONBOARDING = "onboarding"
        const val RESTORE = "restore"
        const val DIAGNOSTICS = "diagnostics"
    }
}

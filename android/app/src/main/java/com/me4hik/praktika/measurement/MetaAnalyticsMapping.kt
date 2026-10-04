package com.me4hik.praktika.measurement

/**
 * Meta-safe projection. Firebase-safe params must never be forwarded raw.
 */
object MetaAnalyticsMapping {
    data class MappedEvent(
        val name: String,
        val params: Map<String, String>,
    )

    val ALLOWED_EVENT_NAMES: Set<String> = setOf(
        AnalyticsEventNames.PRACTICE_STARTED,
        AnalyticsEventNames.ANSWER_SAVED,
        AnalyticsEventNames.NOTIFICATION_OPENED,
    )

    fun map(event: AnalyticsEvent): MappedEvent? {
        if (event.debugOnly) {
            return null
        }
        if (event.name !in ALLOWED_EVENT_NAMES) {
            return null
        }
        return MappedEvent(name = event.name, params = emptyMap())
    }
}

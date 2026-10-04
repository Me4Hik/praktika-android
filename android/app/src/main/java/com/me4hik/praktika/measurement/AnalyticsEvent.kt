package com.me4hik.praktika.measurement

data class AnalyticsEvent(
    val name: String,
    val params: SafeAnalyticsParams = SafeAnalyticsParams.empty(),
    /**
     * When true, routing must keep the event on Debug only (never Firebase/Meta).
     */
    val debugOnly: Boolean = false,
)

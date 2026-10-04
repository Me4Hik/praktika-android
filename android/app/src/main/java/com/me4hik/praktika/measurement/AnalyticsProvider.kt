package com.me4hik.praktika.measurement

interface AnalyticsProvider {
    val id: AnalyticsProviderId

    fun track(event: AnalyticsEvent)
}

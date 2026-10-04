package com.me4hik.praktika.measurement

import java.util.concurrent.CopyOnWriteArrayList

class FakeAnalyticsTracker : AnalyticsTracker {
    private val events = CopyOnWriteArrayList<AnalyticsEvent>()

    override fun track(event: AnalyticsEvent) {
        events += event
    }

    fun recorded(): List<AnalyticsEvent> = events.toList()

    fun names(): List<String> = events.map { it.name }

    fun clear() {
        events.clear()
    }

    fun count(name: String): Int = events.count { it.name == name }
}

class ThrowingAnalyticsProvider(
    override val id: AnalyticsProviderId = AnalyticsProviderId.DEBUG,
) : AnalyticsProvider {
    override fun track(event: AnalyticsEvent) {
        throw IllegalStateException("provider boom for ${event.name}")
    }
}

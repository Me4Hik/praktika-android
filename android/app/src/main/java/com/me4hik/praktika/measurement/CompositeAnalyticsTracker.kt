package com.me4hik.praktika.measurement

import android.util.Log

class CompositeAnalyticsTracker(
    private val providers: List<AnalyticsProvider>,
    private val routingPolicy: AnalyticsRoutingPolicy,
) : AnalyticsTracker {
    override fun track(event: AnalyticsEvent) {
        val targets = routingPolicy.providersFor(event)
        if (targets.isEmpty()) {
            return
        }
        for (provider in providers) {
            if (provider.id !in targets) {
                continue
            }
            try {
                provider.track(event)
            } catch (exception: Exception) {
                Log.w(TAG, "Analytics provider ${provider.id} failed for ${event.name}", exception)
            }
        }
    }

    private companion object {
        const val TAG = "AnalyticsTracker"
    }
}

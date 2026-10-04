package com.me4hik.praktika.measurement

/**
 * Phase 2 Firebase / GA4 adapter. Features must never call Firebase APIs directly.
 */
class FirebaseAnalyticsProvider(
    private val client: FirebaseAnalyticsClient,
) : AnalyticsProvider {
    override val id: AnalyticsProviderId = AnalyticsProviderId.FIREBASE

    override fun track(event: AnalyticsEvent) {
        if (event.debugOnly) {
            return
        }
        client.logEvent(event.name, event.params.asMap())
    }
}

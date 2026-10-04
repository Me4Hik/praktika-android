package com.me4hik.praktika.measurement

/**
 * Phase 3 Meta App Events adapter. Features must never call Meta SDK APIs directly.
 */
class MetaAppEventsProvider(
    private val client: MetaAppEventsClient,
    private val mapping: MetaAnalyticsMapping = MetaAnalyticsMapping,
) : AnalyticsProvider {
    override val id: AnalyticsProviderId = AnalyticsProviderId.META

    override fun track(event: AnalyticsEvent) {
        val mapped = mapping.map(event) ?: return
        client.logEvent(mapped.name, mapped.params)
    }
}

package com.me4hik.praktika.measurement

/**
 * Routes product events to provider ids.
 * Phase 2 activates Debug + Firebase; Meta remains encoded but not configured.
 */
class AnalyticsRoutingPolicy(
    private val configuredProviders: Set<AnalyticsProviderId>,
) {
    fun providersFor(event: AnalyticsEvent): Set<AnalyticsProviderId> {
        val intended = intendedProviders(event)
        return intended.intersect(configuredProviders)
    }

    fun intendedProviders(event: AnalyticsEvent): Set<AnalyticsProviderId> {
        if (event.debugOnly) {
            return setOf(AnalyticsProviderId.DEBUG)
        }
        val providers = linkedSetOf(AnalyticsProviderId.DEBUG, AnalyticsProviderId.FIREBASE)
        if (event.name in META_ELIGIBLE_EVENTS) {
            providers += AnalyticsProviderId.META
        }
        return providers
    }

    companion object {
        /**
         * Future Meta ads contour only. Explicitly excludes mood and most product detail.
         */
        val META_ELIGIBLE_EVENTS: Set<String> = setOf(
            AnalyticsEventNames.LANGUAGE_SELECTED,
            AnalyticsEventNames.PRACTICE_STARTED,
            AnalyticsEventNames.ANSWER_SAVED,
            AnalyticsEventNames.NOTIFICATION_OPENED,
        )

        fun phase1(): AnalyticsRoutingPolicy =
            AnalyticsRoutingPolicy(configuredProviders = setOf(AnalyticsProviderId.DEBUG))

        fun phase2(): AnalyticsRoutingPolicy =
            AnalyticsRoutingPolicy(
                configuredProviders = setOf(
                    AnalyticsProviderId.DEBUG,
                    AnalyticsProviderId.FIREBASE,
                ),
            )
    }
}

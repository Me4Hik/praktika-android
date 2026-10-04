package com.me4hik.praktika.measurement

/**
 * Routes product events to provider ids.
 * Phase 3 activates Debug + Firebase + Meta (when Meta is configured).
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
         * Lean Meta ads contour. No language/mood/archive/export/backup detail.
         */
        val META_ELIGIBLE_EVENTS: Set<String> = setOf(
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

        fun phase3(): AnalyticsRoutingPolicy =
            AnalyticsRoutingPolicy(
                configuredProviders = setOf(
                    AnalyticsProviderId.DEBUG,
                    AnalyticsProviderId.FIREBASE,
                    AnalyticsProviderId.META,
                ),
            )
    }
}

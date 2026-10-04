package com.me4hik.praktika.measurement

import android.content.Context

data class MeasurementRuntime(
    val tracker: AnalyticsTracker,
    val debugProvider: DebugAnalyticsProvider,
    val routingPolicy: AnalyticsRoutingPolicy,
    val firebaseProvider: FirebaseAnalyticsProvider? = null,
    val metaProvider: MetaAppEventsProvider? = null,
)

object MeasurementRuntimeFactory {
    fun createPhase1(): MeasurementRuntime {
        val routingPolicy = AnalyticsRoutingPolicy.phase1()
        val debugProvider = DebugAnalyticsProvider(routingPolicy = routingPolicy)
        val tracker = CompositeAnalyticsTracker(
            providers = listOf(debugProvider),
            routingPolicy = routingPolicy,
        )
        return MeasurementRuntime(
            tracker = tracker,
            debugProvider = debugProvider,
            routingPolicy = routingPolicy,
            firebaseProvider = null,
            metaProvider = null,
        )
    }

    fun createPhase2(appContext: Context): MeasurementRuntime =
        createPhase2(DefaultFirebaseAnalyticsClient(appContext.applicationContext))

    fun createPhase2(firebaseClient: FirebaseAnalyticsClient): MeasurementRuntime {
        val routingPolicy = AnalyticsRoutingPolicy.phase2()
        val debugProvider = DebugAnalyticsProvider(routingPolicy = routingPolicy)
        val firebaseProvider = FirebaseAnalyticsProvider(firebaseClient)
        val tracker = CompositeAnalyticsTracker(
            providers = listOf(debugProvider, firebaseProvider),
            routingPolicy = routingPolicy,
        )
        return MeasurementRuntime(
            tracker = tracker,
            debugProvider = debugProvider,
            routingPolicy = routingPolicy,
            firebaseProvider = firebaseProvider,
            metaProvider = null,
        )
    }

    fun createPhase3(appContext: Context): MeasurementRuntime =
        createPhase3(
            firebaseClient = DefaultFirebaseAnalyticsClient(appContext.applicationContext),
            metaClient = DefaultMetaAppEventsClient(
                context = appContext.applicationContext,
            ),
        )

    fun createPhase3(
        firebaseClient: FirebaseAnalyticsClient,
        metaClient: MetaAppEventsClient,
    ): MeasurementRuntime {
        val routingPolicy = AnalyticsRoutingPolicy.phase3()
        val debugProvider = DebugAnalyticsProvider(routingPolicy = routingPolicy)
        val firebaseProvider = FirebaseAnalyticsProvider(firebaseClient)
        val metaProvider = MetaAppEventsProvider(metaClient)
        val tracker = CompositeAnalyticsTracker(
            providers = listOf(debugProvider, firebaseProvider, metaProvider),
            routingPolicy = routingPolicy,
        )
        return MeasurementRuntime(
            tracker = tracker,
            debugProvider = debugProvider,
            routingPolicy = routingPolicy,
            firebaseProvider = firebaseProvider,
            metaProvider = metaProvider,
        )
    }
}

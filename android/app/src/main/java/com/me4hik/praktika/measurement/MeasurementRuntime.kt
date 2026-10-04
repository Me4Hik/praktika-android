package com.me4hik.praktika.measurement

data class MeasurementRuntime(
    val tracker: AnalyticsTracker,
    val debugProvider: DebugAnalyticsProvider,
    val routingPolicy: AnalyticsRoutingPolicy,
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
        )
    }
}

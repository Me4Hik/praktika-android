package com.me4hik.praktika.measurement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaAppEventsProviderTest {

    private class RecordingMetaClient : MetaAppEventsClient {
        data class Call(val name: String, val params: Map<String, String>)

        val calls = mutableListOf<Call>()
        var throwOnTrack: Boolean = false

        override fun logEvent(name: String, params: Map<String, String>) {
            if (throwOnTrack) {
                throw IllegalStateException("meta unavailable")
            }
            calls += Call(name, params)
        }
    }

    @Test
    fun allowList_exactlyThreeEvents_withEmptyParams() {
        val client = RecordingMetaClient()
        val provider = MetaAppEventsProvider(client)

        provider.track(ProductAnalyticsEvents.practiceStarted("accelerated"))
        provider.track(ProductAnalyticsEvents.answerSaved(42))
        provider.track(ProductAnalyticsEvents.notificationOpened(7))

        assertEquals(3, client.calls.size)
        assertEquals(
            listOf(
                AnalyticsEventNames.PRACTICE_STARTED,
                AnalyticsEventNames.ANSWER_SAVED,
                AnalyticsEventNames.NOTIFICATION_OPENED,
            ),
            client.calls.map { it.name },
        )
        assertTrue(client.calls.all { it.params.isEmpty() })
        assertEquals(3, MetaAnalyticsMapping.ALLOWED_EVENT_NAMES.size)
        assertEquals(MetaAnalyticsMapping.ALLOWED_EVENT_NAMES, AnalyticsRoutingPolicy.META_ELIGIBLE_EVENTS)
    }

    @Test
    fun forbiddenEvents_neverReachMeta() {
        val client = RecordingMetaClient()
        val provider = MetaAppEventsProvider(client)

        provider.track(ProductAnalyticsEvents.languageSelected("en"))
        provider.track(ProductAnalyticsEvents.languageChanged("uk", "en"))
        provider.track(ProductAnalyticsEvents.questionOpened(1, SafeAnalyticsParams.Sources.HOME))
        provider.track(ProductAnalyticsEvents.questionSkipped(2))
        provider.track(ProductAnalyticsEvents.questionDeferred(3, 15))
        provider.track(ProductAnalyticsEvents.moodCheckinSaved())
        provider.track(ProductAnalyticsEvents.archiveOpened())
        provider.track(ProductAnalyticsEvents.archiveExported("markdown"))
        provider.track(ProductAnalyticsEvents.backupCreated())
        provider.track(ProductAnalyticsEvents.backupRestored(SafeAnalyticsParams.Sources.RESTORE))
        provider.track(ProductAnalyticsEvents.debugTestEvent())

        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun languageSelected_noLongerMetaEligible() {
        assertFalse(
            AnalyticsRoutingPolicy.META_ELIGIBLE_EVENTS.contains(
                AnalyticsEventNames.LANGUAGE_SELECTED,
            ),
        )
        assertNull(MetaAnalyticsMapping.map(ProductAnalyticsEvents.languageSelected("ru")))

        val phase3 = AnalyticsRoutingPolicy.phase3()
        assertFalse(
            phase3.intendedProviders(ProductAnalyticsEvents.languageSelected("ru"))
                .contains(AnalyticsProviderId.META),
        )
    }

    @Test
    fun debugTestEvent_neverMeta() {
        val client = RecordingMetaClient()
        MetaAppEventsProvider(client).track(ProductAnalyticsEvents.debugTestEvent())
        assertTrue(client.calls.isEmpty())

        val phase3 = AnalyticsRoutingPolicy.phase3()
        assertEquals(
            setOf(AnalyticsProviderId.DEBUG),
            phase3.providersFor(ProductAnalyticsEvents.debugTestEvent()),
        )
    }

    @Test
    fun metaThrow_doesNotBreakDebugOrFirebase() {
        val meta = RecordingMetaClient().apply { throwOnTrack = true }
        val firebase = RecordingFirebaseClient()
        val routing = AnalyticsRoutingPolicy.phase3()
        val debug = DebugAnalyticsProvider(routingPolicy = routing)
        val tracker = CompositeAnalyticsTracker(
            providers = listOf(
                debug,
                FirebaseAnalyticsProvider(firebase),
                MetaAppEventsProvider(meta),
            ),
            routingPolicy = routing,
        )

        tracker.track(ProductAnalyticsEvents.answerSaved(1))

        assertEquals(1, debug.snapshot().size)
        assertEquals(AnalyticsEventNames.ANSWER_SAVED, debug.snapshot().single().eventName)
        assertEquals(
            listOf("DEBUG", "FIREBASE", "META"),
            debug.snapshot().single().routedProviders,
        )
        assertEquals(1, firebase.calls.size)
        assertTrue(meta.calls.isEmpty())
    }

    @Test
    fun phase2ProductionRouting_unchanged_noMetaDelivery() {
        val policy = AnalyticsRoutingPolicy.phase2()
        val answer = ProductAnalyticsEvents.answerSaved(1)
        assertEquals(
            setOf(AnalyticsProviderId.DEBUG, AnalyticsProviderId.FIREBASE),
            policy.providersFor(answer),
        )
        assertTrue(policy.intendedProviders(answer).contains(AnalyticsProviderId.META))
        assertFalse(policy.providersFor(answer).contains(AnalyticsProviderId.META))

        val runtime = MeasurementRuntimeFactory.createPhase2(RecordingFirebaseClient())
        assertNull(runtime.metaProvider)
        assertEquals(
            setOf(AnalyticsProviderId.DEBUG, AnalyticsProviderId.FIREBASE),
            runtime.routingPolicy.providersFor(answer),
        )
        // Mapping still allow-list-only (activation fix must not widen Meta product events).
        assertEquals(
            setOf(
                AnalyticsEventNames.PRACTICE_STARTED,
                AnalyticsEventNames.ANSWER_SAVED,
                AnalyticsEventNames.NOTIFICATION_OPENED,
            ),
            MetaAnalyticsMapping.ALLOWED_EVENT_NAMES,
        )
    }

    @Test
    fun eventMapping_allowListOnly_emptyParams() {
        assertEquals(
            MetaAnalyticsMapping.MappedEvent(AnalyticsEventNames.PRACTICE_STARTED, emptyMap()),
            MetaAnalyticsMapping.map(ProductAnalyticsEvents.practiceStarted("accelerated")),
        )
        assertEquals(
            MetaAnalyticsMapping.MappedEvent(AnalyticsEventNames.ANSWER_SAVED, emptyMap()),
            MetaAnalyticsMapping.map(ProductAnalyticsEvents.answerSaved(9)),
        )
        assertNull(MetaAnalyticsMapping.map(ProductAnalyticsEvents.moodCheckinSaved()))
        assertNull(MetaAnalyticsMapping.map(ProductAnalyticsEvents.archiveOpened()))
        assertNull(MetaAnalyticsMapping.map(ProductAnalyticsEvents.debugTestEvent()))
    }

    @Test
    fun phase3AcceleratedRouting_includesMetaForAllowListOnly() {
        val firebase = RecordingFirebaseClient()
        val meta = RecordingMetaClient()
        val runtime = MeasurementRuntimeFactory.createPhase3(firebase, meta)

        assertNotNull(runtime.metaProvider)
        assertEquals(
            setOf(AnalyticsProviderId.DEBUG, AnalyticsProviderId.FIREBASE, AnalyticsProviderId.META),
            runtime.routingPolicy.providersFor(ProductAnalyticsEvents.practiceStarted("accelerated")),
        )
        assertEquals(
            setOf(AnalyticsProviderId.DEBUG, AnalyticsProviderId.FIREBASE),
            runtime.routingPolicy.providersFor(ProductAnalyticsEvents.archiveOpened()),
        )

        runtime.tracker.track(ProductAnalyticsEvents.practiceStarted("accelerated"))
        runtime.tracker.track(ProductAnalyticsEvents.moodCheckinSaved())
        runtime.tracker.track(ProductAnalyticsEvents.debugTestEvent())

        assertEquals(listOf(AnalyticsEventNames.PRACTICE_STARTED), meta.calls.map { it.name })
        assertTrue(meta.calls.single().params.isEmpty())
        assertEquals(2, firebase.calls.size)
        assertEquals(3, runtime.debugProvider.snapshot().size)
        assertEquals(
            listOf("DEBUG", "FIREBASE"),
            runtime.debugProvider.snapshot()[1].routedProviders,
        )
    }

    private class RecordingFirebaseClient : FirebaseAnalyticsClient {
        data class Call(val name: String, val params: Map<String, String>)

        val calls = mutableListOf<Call>()

        override fun logEvent(name: String, params: Map<String, String>) {
            calls += Call(name, params)
        }
    }
}

package com.me4hik.praktika.measurement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FirebaseAnalyticsProviderTest {

    private class RecordingFirebaseClient : FirebaseAnalyticsClient {
        data class Call(val name: String, val params: Map<String, String>)

        val calls = mutableListOf<Call>()
        var throwOnTrack: Boolean = false

        override fun logEvent(name: String, params: Map<String, String>) {
            if (throwOnTrack) {
                throw IllegalStateException("firebase unavailable")
            }
            calls += Call(name, params)
        }
    }

    @Test
    fun provider_mapsSafeEventsAndParams() {
        val client = RecordingFirebaseClient()
        val provider = FirebaseAnalyticsProvider(client)

        provider.track(ProductAnalyticsEvents.languageSelected("en"))
        provider.track(ProductAnalyticsEvents.practiceStarted("accelerated"))
        provider.track(ProductAnalyticsEvents.questionOpened(1, SafeAnalyticsParams.Sources.HOME))
        provider.track(ProductAnalyticsEvents.answerSaved(1))
        provider.track(ProductAnalyticsEvents.moodCheckinSaved())
        provider.track(ProductAnalyticsEvents.archiveOpened())
        provider.track(ProductAnalyticsEvents.archiveExported("markdown"))
        provider.track(ProductAnalyticsEvents.backupCreated())
        provider.track(ProductAnalyticsEvents.backupRestored(SafeAnalyticsParams.Sources.RESTORE))
        provider.track(ProductAnalyticsEvents.questionSkipped(2))
        provider.track(ProductAnalyticsEvents.questionDeferred(3, 15))
        provider.track(ProductAnalyticsEvents.notificationOpened(4))
        provider.track(ProductAnalyticsEvents.languageChanged("uk", "en"))

        assertEquals(13, client.calls.size)
        assertEquals(AnalyticsEventNames.LANGUAGE_SELECTED, client.calls[0].name)
        assertEquals("en", client.calls[0].params["language"])
        assertEquals(SafeAnalyticsParams.Sources.CHOOSER, client.calls[0].params["source"])
        assertEquals("1", client.calls[2].params["question_id"])
        assertTrue(client.calls[4].params.isEmpty())
        assertFalse(client.calls.any { call -> call.params.keys.any { AnalyticsPrivacyPolicy.isAllowedKey(it).not() } })
        assertFalse(client.calls.any { it.params.containsKey("mood_level") })
        assertFalse(client.calls.any { it.params.values.any { value -> value.contains("content://") } })
    }

    @Test
    fun provider_skipsDebugOnlyEvents() {
        val client = RecordingFirebaseClient()
        FirebaseAnalyticsProvider(client).track(ProductAnalyticsEvents.debugTestEvent())
        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun phase2Routing_productGoesToDebugAndFirebase() {
        val policy = AnalyticsRoutingPolicy.phase2()
        val answer = ProductAnalyticsEvents.answerSaved(1)
        assertEquals(
            setOf(AnalyticsProviderId.DEBUG, AnalyticsProviderId.FIREBASE),
            policy.providersFor(answer),
        )
        assertFalse(policy.providersFor(answer).contains(AnalyticsProviderId.META))
    }

    @Test
    fun phase2Routing_debugTestEvent_debugOnly() {
        val policy = AnalyticsRoutingPolicy.phase2()
        assertEquals(
            setOf(AnalyticsProviderId.DEBUG),
            policy.providersFor(ProductAnalyticsEvents.debugTestEvent()),
        )
    }

    @Test
    fun forbiddenParams_stillRejectedBySafeBuilder() {
        try {
            SafeAnalyticsParams.builder().put("answer_text", "secret")
            fail("Expected forbidden key rejection")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun composite_firebaseThrow_doesNotBreakDebug() {
        val client = RecordingFirebaseClient().apply { throwOnTrack = true }
        val routing = AnalyticsRoutingPolicy.phase2()
        val debug = DebugAnalyticsProvider(routingPolicy = routing)
        val tracker = CompositeAnalyticsTracker(
            providers = listOf(debug, FirebaseAnalyticsProvider(client)),
            routingPolicy = routing,
        )

        tracker.track(ProductAnalyticsEvents.archiveOpened())

        assertEquals(1, debug.snapshot().size)
        assertEquals(AnalyticsEventNames.ARCHIVE_OPENED, debug.snapshot().single().eventName)
        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun phase2Factory_wiresDebugAndFirebase() {
        val client = RecordingFirebaseClient()
        val runtime = MeasurementRuntimeFactory.createPhase2(client)

        assertNotNull(runtime.firebaseProvider)
        assertEquals(AnalyticsProviderId.FIREBASE, runtime.firebaseProvider!!.id)

        runtime.tracker.track(ProductAnalyticsEvents.practiceStarted("production"))
        runtime.tracker.track(ProductAnalyticsEvents.debugTestEvent())

        assertEquals(1, client.calls.size)
        assertEquals(AnalyticsEventNames.PRACTICE_STARTED, client.calls.single().name)
        assertEquals(2, runtime.debugProvider.snapshot().size)
        assertEquals(
            setOf(AnalyticsProviderId.DEBUG, AnalyticsProviderId.FIREBASE),
            runtime.routingPolicy.providersFor(ProductAnalyticsEvents.archiveOpened()),
        )
    }
}

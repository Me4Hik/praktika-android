package com.me4hik.praktika.measurement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MeasurementCoreTest {

    @Test
    fun safeParams_rejectsForbiddenKeys() {
        try {
            SafeAnalyticsParams.builder().put("answer_text", "secret")
            fail("Expected forbidden key rejection")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        try {
            SafeAnalyticsParams.builder().put("question_text", "q")
            fail("Expected forbidden key rejection")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        try {
            SafeAnalyticsParams.builder().put("mood_level", "LOW")
            fail("Expected forbidden key rejection")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        try {
            SafeAnalyticsParams.builder().put("export_uri", "content://x")
            fail("Expected forbidden key rejection")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun safeParams_allowsApprovedKeys() {
        val params = SafeAnalyticsParams.builder()
            .language("uk")
            .source("chooser")
            .questionId(3)
            .durationMinutes(10)
            .exportFormat("markdown")
            .flavor("production")
            .build()
            .asMap()
        assertEquals("uk", params["language"])
        assertEquals("3", params["question_id"])
        assertFalse(params.containsKey("mood_level"))
    }

    @Test
    fun moodCheckinSaved_hasNoLevelParam() {
        val event = ProductAnalyticsEvents.moodCheckinSaved()
        assertEquals(AnalyticsEventNames.MOOD_CHECKIN_SAVED, event.name)
        assertTrue(event.params.isEmpty())
    }

    @Test
    fun routingPolicy_metaExcludesMoodAndDebugOnly() {
        val policy = AnalyticsRoutingPolicy(
            configuredProviders = setOf(
                AnalyticsProviderId.DEBUG,
                AnalyticsProviderId.FIREBASE,
                AnalyticsProviderId.META,
            ),
        )
        val mood = ProductAnalyticsEvents.moodCheckinSaved()
        assertFalse(policy.intendedProviders(mood).contains(AnalyticsProviderId.META))
        assertTrue(policy.intendedProviders(mood).contains(AnalyticsProviderId.FIREBASE))

        val answer = ProductAnalyticsEvents.answerSaved(1)
        assertTrue(policy.intendedProviders(answer).contains(AnalyticsProviderId.META))

        val debugOnly = ProductAnalyticsEvents.debugTestEvent()
        assertEquals(setOf(AnalyticsProviderId.DEBUG), policy.intendedProviders(debugOnly))
        assertEquals(setOf(AnalyticsProviderId.DEBUG), policy.providersFor(debugOnly))
    }

    @Test
    fun phase1Routing_onlyDebugConfigured() {
        val policy = AnalyticsRoutingPolicy.phase1()
        val answer = ProductAnalyticsEvents.answerSaved(1)
        assertEquals(setOf(AnalyticsProviderId.DEBUG), policy.providersFor(answer))
        assertTrue(policy.intendedProviders(answer).contains(AnalyticsProviderId.FIREBASE))
        assertTrue(policy.intendedProviders(answer).contains(AnalyticsProviderId.META))
    }

    @Test
    fun composite_swallowsProviderFailure() {
        val tracker = CompositeAnalyticsTracker(
            providers = listOf(ThrowingAnalyticsProvider()),
            routingPolicy = AnalyticsRoutingPolicy.phase1(),
        )
        tracker.track(ProductAnalyticsEvents.practiceStarted("production"))
    }

    @Test
    fun noOpTracker_doesNothing() {
        NoOpAnalyticsTracker.track(ProductAnalyticsEvents.archiveOpened())
    }

    @Test
    fun debugProvider_ringBufferAndClear() {
        var now = 1_000L
        val provider = DebugAnalyticsProvider(
            capacity = 3,
            clock = { now },
        )
        provider.track(ProductAnalyticsEvents.archiveOpened())
        now = 2_000L
        provider.track(ProductAnalyticsEvents.practiceStarted("production"))
        now = 3_000L
        provider.track(ProductAnalyticsEvents.answerSaved(1))
        now = 4_000L
        provider.track(ProductAnalyticsEvents.questionSkipped(2))

        val snapshot = provider.snapshot()
        assertEquals(3, snapshot.size)
        assertEquals(AnalyticsEventNames.PRACTICE_STARTED, snapshot[0].eventName)
        assertEquals(AnalyticsEventNames.QUESTION_SKIPPED, snapshot[2].eventName)

        provider.clear()
        assertTrue(provider.snapshot().isEmpty())
        assertTrue(provider.entries.value.isEmpty())
    }

    @Test
    fun debugTestEvent_isDebugOnlyAndNotMetaEligible() {
        val event = ProductAnalyticsEvents.debugTestEvent()
        assertTrue(event.debugOnly)
        assertFalse(AnalyticsRoutingPolicy.META_ELIGIBLE_EVENTS.contains(event.name))
        val policy = AnalyticsRoutingPolicy(
            configuredProviders = setOf(
                AnalyticsProviderId.DEBUG,
                AnalyticsProviderId.FIREBASE,
                AnalyticsProviderId.META,
            ),
        )
        assertEquals(setOf(AnalyticsProviderId.DEBUG), policy.providersFor(event))
    }

    @Test
    fun phase1Factory_wiresDebugOnly() {
        val runtime = MeasurementRuntimeFactory.createPhase1()
        runtime.tracker.track(ProductAnalyticsEvents.languageSelected("ru"))
        assertEquals(1, runtime.debugProvider.snapshot().size)
        assertEquals(AnalyticsEventNames.LANGUAGE_SELECTED, runtime.debugProvider.snapshot().single().eventName)
    }
}

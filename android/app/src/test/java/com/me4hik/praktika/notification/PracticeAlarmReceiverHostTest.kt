// 03.09.2026 Case2 minimal observability cursor by Me4Hik START - planned alarm abort host proofs
package com.me4hik.praktika.notification

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.diagnostics.DiagnosticsRecorder
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PracticeAlarmReceiverHostTest {
    private lateinit var eventsFile: File

    @Before
    fun setUp() {
        DiagnosticsRecorder.resetForTests()
        PraktikaRuntimeHolder.resetForTests()
        val context = ApplicationProvider.getApplicationContext<Context>()
        eventsFile = File(context.filesDir, "diagnostics/case2-planned-alarm-abort-events.jsonl")
        eventsFile.parentFile?.mkdirs()
        if (eventsFile.exists()) {
            eventsFile.delete()
        }
        DiagnosticsRecorder.installForTests(eventsFile)
    }

    @After
    fun tearDown() {
        PraktikaRuntimeHolder.resetForTests()
        DiagnosticsRecorder.resetForTests()
        if (::eventsFile.isInitialized && eventsFile.exists()) {
            eventsFile.delete()
        }
    }

    @Test
    fun plannedBoundary_initFalse_recordsPlannedAlarmInitFailed_withoutPost() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val runtime = PraktikaRuntimeHolder.get(context)
        runtime.initializer.completeActivityInit(false)

        PracticeAlarmReceiver().onReceive(context, plannedBoundaryIntent(occurrenceId = 39L))
        awaitNamedEvent("planned_alarm_init_failed")

        val initFailed = DiagnosticsRecorder.get().getBufferedEvents()
            .last { it.name == "planned_alarm_init_failed" }
        assertEquals("39", initFailed.metadata["occurrence_id"])
        assertEquals(BoundaryEventType.PLANNED_BOUNDARY.name, initFailed.metadata["receiver_event_type"])
        assertEquals("ensureInitialized", initFailed.metadata["failure_stage"])
        assertTrue(initFailed.metadata["wall_clock_ms"].orEmpty().isNotBlank())
        assertTrue(initFailed.metadata["elapsed_realtime_ms"].orEmpty().isNotBlank())
        assertFalse(
            DiagnosticsRecorder.get().getBufferedEvents().any { it.name == "notification_post_result" },
        )
        assertFalse(
            DiagnosticsRecorder.get().getBufferedEvents().any { it.name == "notification_post_attempt" },
        )
    }

    @Test
    fun plannedBoundary_handleAlarmThrows_recordsPlannedAlarmReceiverFailed() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        // Runtime create uses applicationContext; alarm lookup uses the passed context.
        val throwingContext = object : ContextWrapper(base) {
            override fun getSystemService(name: String): Any? {
                if (name == Context.ALARM_SERVICE) {
                    throw IllegalStateException("forced_alarm_service_failure")
                }
                return super.getSystemService(name)
            }
        }
        PraktikaRuntimeHolder.get(base)
        // Ensure init is not the abort path — throw happens before ensureInitialized.
        PraktikaRuntimeHolder.get(base).initializer.completeActivityInit(true)

        PracticeAlarmReceiver().onReceive(throwingContext, plannedBoundaryIntent(occurrenceId = 40L))
        awaitNamedEvent("planned_alarm_receiver_failed")

        val failed = DiagnosticsRecorder.get().getBufferedEvents()
            .last { it.name == "planned_alarm_receiver_failed" }
        assertEquals("40", failed.metadata["occurrence_id"])
        assertEquals(BoundaryEventType.PLANNED_BOUNDARY.name, failed.metadata["receiver_event_type"])
        assertEquals(IllegalStateException::class.java.name, failed.metadata["exception_class"])
        assertFalse(failed.metadata.containsKey("exception_message"))
        assertTrue(failed.metadata["wall_clock_ms"].orEmpty().isNotBlank())
    }

    private fun plannedBoundaryIntent(occurrenceId: Long): Intent {
        val plannedAt = 1_000_000L
        val triggerAt = plannedAt + PlannedBoundaryTiming.PLANNED_BOUNDARY_TRIGGER_BIAS_MS
        val plan = BoundaryAlarmPlan(
            occurrenceId = occurrenceId,
            eventType = BoundaryEventType.PLANNED_BOUNDARY,
            triggerAtEpochMillis = triggerAt,
            plannedAtEpochMillis = plannedAt,
        )
        return Intent(PracticeAlarmIdentity.actionFor(plan)).apply {
            putExtra(PracticeAlarmReceiver.EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(PracticeAlarmReceiver.EXTRA_EVENT_TYPE, BoundaryEventType.PLANNED_BOUNDARY.name)
            putExtra(PracticeAlarmReceiver.EXTRA_BOUNDARY_EPOCH_MILLIS, triggerAt)
            putExtra(PracticeAlarmReceiver.EXTRA_PLANNED_AT_EPOCH_MILLIS, plannedAt)
        }
    }

    private fun awaitNamedEvent(name: String) {
        val mainLooper = Looper.getMainLooper()
        val shadowMain = shadowOf(mainLooper)
        val deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (System.nanoTime() < deadlineNs) {
            shadowMain.idle()
            val found = DiagnosticsRecorder.get().getBufferedEvents().any { it.name == name }
            if (found) {
                shadowMain.idle()
                return
            }
            Thread.sleep(20L)
        }
        shadowMain.idle()
        val names = DiagnosticsRecorder.get().getBufferedEvents().map { it.name }
        fail(
            "$name not observed within 2s. bufferedNames=$names mainLooperIdle=${shadowMain.isIdle}",
        )
    }
}
// 03.09.2026 Case2 minimal observability cursor by Me4Hik END

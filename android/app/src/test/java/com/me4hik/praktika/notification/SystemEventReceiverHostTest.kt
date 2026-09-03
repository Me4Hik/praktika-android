// 02.09.2026 Case1 reboot system-event filter fix cursor by Me4Hik START - action mapping + boot diagnostic
package com.me4hik.praktika.notification

import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.diagnostics.DiagnosticCategory
import com.me4hik.praktika.diagnostics.DiagnosticsRecorder
import com.me4hik.praktika.diagnostics.TargetedBugDiagnostics
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class SystemEventReceiverHostTest {
    private lateinit var eventsFile: File

    @Before
    fun setUp() {
        DiagnosticsRecorder.resetForTests()
        PraktikaRuntimeHolder.resetForTests()
        val context = ApplicationProvider.getApplicationContext<Context>()
        eventsFile = File(context.filesDir, "diagnostics/case1-system-event-events.jsonl")
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
    fun actionMappings_matchProductionReceiverSemantics() {
        assertEquals(
            NotificationSyncReason.BOOT,
            SystemEventSyncReasonResolver.resolve(Intent.ACTION_BOOT_COMPLETED),
        )
        assertEquals(
            NotificationSyncReason.TIME_CHANGED,
            SystemEventSyncReasonResolver.resolve(Intent.ACTION_TIME_CHANGED),
        )
        assertEquals(
            NotificationSyncReason.TIME_CHANGED,
            SystemEventSyncReasonResolver.resolve("android.intent.action.TIME_SET"),
        )
        assertEquals(
            NotificationSyncReason.TIMEZONE_CHANGED,
            SystemEventSyncReasonResolver.resolve(Intent.ACTION_TIMEZONE_CHANGED),
        )
        assertEquals(
            NotificationSyncReason.PACKAGE_REPLACED,
            SystemEventSyncReasonResolver.resolve(Intent.ACTION_MY_PACKAGE_REPLACED),
        )
        assertEquals(null, SystemEventSyncReasonResolver.resolve("android.intent.action.UNKNOWN"))
    }

    @Test
    fun recordSystemEventReceived_persistsActionAndReason() {
        TargetedBugDiagnostics.recordSystemEventReceived(
            action = Intent.ACTION_BOOT_COMPLETED,
            syncReason = NotificationSyncReason.BOOT.name,
            wallClockMs = 1_000L,
            elapsedRealtimeMs = 2_000L,
        )
        val event = DiagnosticsRecorder.get().getBufferedEvents()
            .single { it.name == "system_event_received" }
        assertEquals(DiagnosticCategory.NOTIFICATION, event.category)
        assertEquals(Intent.ACTION_BOOT_COMPLETED, event.metadata["action"])
        assertEquals("BOOT", event.metadata["sync_reason"])
        assertEquals("1000", event.metadata["wall_clock_ms"])
        assertEquals("2000", event.metadata["elapsed_realtime_ms"])
    }

    @Test
    fun bootCompleted_onReceive_emitsSystemEventReceived() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Warm runtime so receiver sync path can complete without Application bootstrap.
        val runtime = PraktikaRuntimeHolder.get(context)
        runtime.initializer.completeActivityInit(true)

        SystemEventReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        awaitSystemEventReceived()

        val event = DiagnosticsRecorder.get().getBufferedEvents()
            .lastOrNull { it.name == "system_event_received" }
        assertNotNull(event)
        assertEquals(Intent.ACTION_BOOT_COMPLETED, event!!.metadata["action"])
        assertEquals("BOOT", event.metadata["sync_reason"])
        assertTrue(event.metadata["wall_clock_ms"].orEmpty().isNotBlank())
        assertTrue(event.metadata["elapsed_realtime_ms"].orEmpty().isNotBlank())
    }

    /**
     * Robolectric 4.14 PAUSED + production Dispatchers.IO: drain main looper each poll and
     * bound wait with real monotonic time (not kotlinx delay / virtual clock).
     */
    private fun awaitSystemEventReceived() {
        val mainLooper = Looper.getMainLooper()
        val shadowMain = shadowOf(mainLooper)
        val deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (System.nanoTime() < deadlineNs) {
            shadowMain.idle()
            val found = DiagnosticsRecorder.get().getBufferedEvents()
                .any { it.name == "system_event_received" }
            if (found) {
                // Drain follow-on main work from sync/PendingResult so PAUSED teardown stays clean.
                shadowMain.idle()
                return
            }
            // Scheduling cooperation only — not the primary wait mechanism.
            Thread.sleep(20L)
        }
        shadowMain.idle()
        val buffered = DiagnosticsRecorder.get().getBufferedEvents()
        val names = buffered.map { it.name }
        fail(
            "system_event_received not observed within 2s real monotonic timeout. " +
                "bufferedNames=$names mainLooperIdle=${shadowMain.isIdle} " +
                "recent=${buffered.takeLast(5)}",
        )
    }
}
// 02.09.2026 Case1 reboot system-event filter fix cursor by Me4Hik END

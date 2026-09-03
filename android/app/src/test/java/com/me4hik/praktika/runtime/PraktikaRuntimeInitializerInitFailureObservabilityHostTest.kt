// 03.09.2026 Case2 init internal observability cursor by Me4Hik START - runtime_init_failed host proofs
package com.me4hik.praktika.runtime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.diagnostics.DiagnosticsRecorder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PraktikaRuntimeInitializerInitFailureObservabilityHostTest {
    private lateinit var context: Context
    private lateinit var eventsFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DiagnosticsRecorder.resetForTests()
        PraktikaRuntimeHolder.resetForTests()
        eventsFile = File(context.filesDir, "diagnostics/case2-runtime-init-failed-events.jsonl")
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
    fun seedFailure_recordsRuntimeInitFailed_stageSeed_stickyFalse() = runBlocking {
        val runtime = PraktikaRuntimeHolder.get(context)
        val seedCalls = AtomicInteger(0)
        runtime.initializer.appStartSyncOverrideForTests = {}
        runtime.initializer.seedFromAssetsOverrideForTests = {
            seedCalls.incrementAndGet()
            error("forced_seed_failure")
        }

        assertFalse(runtime.initializer.ensureInitialized())
        assertEquals(1, seedCalls.get())

        val failed = requireRuntimeInitFailed()
        assertEquals("SEED", failed.metadata["failure_stage"])
        assertEquals(IllegalStateException::class.java.name, failed.metadata["exception_class"])
        assertTrue(failed.metadata["wall_clock_ms"].orEmpty().isNotBlank())
        assertTrue(failed.metadata["elapsed_realtime_ms"].orEmpty().isNotBlank())
        assertFalse(failed.metadata.containsKey("exception_message"))

        assertFalse(runtime.initializer.ensureInitialized())
        assertEquals(1, seedCalls.get())
    }

    @Test
    fun reconcileFailure_recordsRuntimeInitFailed_stageReconcile_stickyFalse() = runBlocking {
        val runtime = PraktikaRuntimeHolder.get(context)
        val reconcileCalls = AtomicInteger(0)
        runtime.initializer.appStartSyncOverrideForTests = {}
        runtime.initializer.seedFromAssetsOverrideForTests = {}
        runtime.initializer.syncEnvironmentAndReconcileOverrideForTests = {
            reconcileCalls.incrementAndGet()
            throw IllegalStateException("forced_reconcile_failure")
        }

        assertFalse(runtime.initializer.ensureInitialized())
        assertEquals(1, reconcileCalls.get())

        val failed = requireRuntimeInitFailed()
        assertEquals("RECONCILE", failed.metadata["failure_stage"])
        assertEquals(IllegalStateException::class.java.name, failed.metadata["exception_class"])

        assertFalse(runtime.initializer.ensureInitialized())
        assertEquals(1, reconcileCalls.get())
    }

    @Test
    fun cancellationFailure_recordsRuntimeInitFailed_stageCancelled_stickyFalse() = runBlocking {
        val runtime = PraktikaRuntimeHolder.get(context)
        runtime.initializer.appStartSyncOverrideForTests = {}
        runtime.initializer.seedFromAssetsOverrideForTests = {
            throw CancellationException("forced_init_cancellation")
        }

        assertFalse(runtime.initializer.ensureInitialized())

        val failed = requireRuntimeInitFailed()
        assertEquals("CANCELLED", failed.metadata["failure_stage"])
        assertEquals(CancellationException::class.java.name, failed.metadata["exception_class"])

        assertFalse(runtime.initializer.ensureInitialized())
    }

    @Test
    fun successPath_emitsNoRuntimeInitFailed() = runBlocking {
        val runtime = PraktikaRuntimeHolder.get(context)
        // Host process uses production RuntimeFactory; isolate init stages with no-op seams
        // so this asserts initializer success/event semantics, not Robolectric asset/DB open.
        runtime.initializer.seedFromAssetsOverrideForTests = {}
        runtime.initializer.syncEnvironmentAndReconcileOverrideForTests = {}
        runtime.initializer.appStartSyncOverrideForTests = {}

        assertTrue(runtime.initializer.ensureInitialized())
        assertTrue(runtime.initializer.ensureInitialized())

        assertFalse(
            DiagnosticsRecorder.get().getBufferedEvents().any { it.name == "runtime_init_failed" },
        )
    }

    @Test
    fun freshReset_afterStickyFalse_allowsSuccessfulInit() = runBlocking {
        val runtime = PraktikaRuntimeHolder.get(context)
        runtime.initializer.appStartSyncOverrideForTests = {}
        runtime.initializer.seedFromAssetsOverrideForTests = {
            error("forced_seed_failure")
        }
        assertFalse(runtime.initializer.ensureInitialized())
        requireRuntimeInitFailed()

        // Host process-reset model only (existing resetForTests seam).
        runtime.initializer.resetForTests()
        runtime.initializer.seedFromAssetsOverrideForTests = {}
        runtime.initializer.syncEnvironmentAndReconcileOverrideForTests = {}
        runtime.initializer.appStartSyncOverrideForTests = {}
        assertTrue(runtime.initializer.ensureInitialized())
        assertEquals(
            1,
            DiagnosticsRecorder.get().getBufferedEvents().count { it.name == "runtime_init_failed" },
        )
    }

    private fun requireRuntimeInitFailed() =
        DiagnosticsRecorder.get().getBufferedEvents().last { it.name == "runtime_init_failed" }
}
// 03.09.2026 Case2 init internal observability cursor by Me4Hik END

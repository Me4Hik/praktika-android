// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika.diagnostics

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.stage22.Stage22ProductionFingerprint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticsProductionPersistenceProbeInstrumentedTest {
    @Test
    fun logProductionPersistenceFingerprint() {
        assumeTrue(
            "Runs only via prompt107 orchestration",
            InstrumentationRegistry.getArguments().getString(OPT_IN_ARGUMENT).toBoolean(),
        )
        val label = InstrumentationRegistry.getArguments().getString(LABEL_ARGUMENT) ?: "UNLABELED"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = runBlocking {
            PraktikaRuntimeHolder.get(context).also { it.initializer.ensureInitialized() }
        }
        val soundEnabled = runBlocking { runtime.soundPreferenceRepository.soundEnabled.first() }
        val fingerprint = Stage22ProductionFingerprint.capture(
            database = runtime.database,
            soundEnabled = soundEnabled,
            label = label,
        )
        android.util.Log.i(TAG, "$PROBE_PREFIX$label:$fingerprint")
    }

    private companion object {
        const val OPT_IN_ARGUMENT = "prompt107_production_probe"
        const val LABEL_ARGUMENT = "prompt107_probe_label"
        const val TAG = "DiagnosticsProdProbe"
        const val PROBE_PREFIX = "PRAKTIKA_PROBE_JSON:"
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END

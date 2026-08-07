// 07.08.2026 Stage 22 Stability cursor by Me4Hik START - production persistence probe
package com.me4hik.praktika.stage22

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assume.assumeTrue

@RunWith(AndroidJUnit4::class)
class Stage22ProductionStateProbeInstrumentedTest {
    @Test
    fun probeMatchesExpectedFingerprint() {
        assumeTrue(
            "Stage 22 production persistence runs only via stage22_production_persistence.ps1",
            InstrumentationRegistry.getArguments()
                .getString(OPT_IN_ARGUMENT)
                .toBoolean(),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expectedLabel = InstrumentationRegistry.getArguments()
            .getString(EXPECTED_LABEL_ARGUMENT)
            ?: "BASELINE"
        val baselineFile = File(context.filesDir, Stage22ProductionFingerprint.STATE_FILE_NAME)
        require(baselineFile.exists()) { "Baseline state file missing: ${baselineFile.absolutePath}" }
        val baseline = JSONObject(baselineFile.readText())

        val runtime = runBlocking {
            PraktikaRuntimeHolder.get(context).also { it.initializer.ensureInitialized() }
        }
        val soundEnabled = runBlocking {
            Stage22ProductionFingerprint.readSoundEnabled(runtime.soundPreferenceRepository)
        }
        val actual = Stage22ProductionFingerprint.capture(
            database = runtime.database,
            soundEnabled = soundEnabled,
            label = expectedLabel,
        )

        Stage22ProductionFingerprint.assertEquivalent(baseline, actual)
        assertEquals("ok", runtime.database.openHelper.writableDatabase.query("PRAGMA quick_check").use {
            it.moveToFirst()
            it.getString(0)
        })
        assertEquals(2, runtime.database.openHelper.writableDatabase.query("PRAGMA user_version").use {
            it.moveToFirst()
            it.getInt(0)
        })
        android.util.Log.i(TAG, "STAGE22_PRODUCTION_PROBE_GREEN label=$expectedLabel")
    }

    @Test
    fun capturePreReinstallFingerprint() {
        assumeTrue(
            "Stage 22 production persistence runs only via stage22_production_persistence.ps1",
            InstrumentationRegistry.getArguments()
                .getString(OPT_IN_ARGUMENT)
                .toBoolean(),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = runBlocking {
            PraktikaRuntimeHolder.get(context).also { it.initializer.ensureInitialized() }
        }
        val soundEnabled = runBlocking {
            Stage22ProductionFingerprint.readSoundEnabled(runtime.soundPreferenceRepository)
        }
        val preReinstall = Stage22ProductionFingerprint.capture(
            database = runtime.database,
            soundEnabled = soundEnabled,
            label = "PRE_REINSTALL",
        )
        Stage22ProductionFixtureSupport.writeState(context, preReinstall)
        android.util.Log.i(TAG, "STAGE22_PRODUCTION_PRE_REINSTALL_GREEN")
    }

    @Test
    fun capturePostReinstallFingerprint() {
        assumeTrue(
            "Stage 22 production persistence runs only via stage22_production_persistence.ps1",
            InstrumentationRegistry.getArguments()
                .getString(OPT_IN_ARGUMENT)
                .toBoolean(),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val baselineFile = File(context.filesDir, Stage22ProductionFingerprint.STATE_FILE_NAME)
        require(baselineFile.exists()) { "PRE_REINSTALL state file missing" }
        val preReinstall = JSONObject(baselineFile.readText())
        require(preReinstall.getString("label") == "PRE_REINSTALL") {
            "Expected PRE_REINSTALL baseline, found ${preReinstall.optString("label")}"
        }

        val runtime = runBlocking {
            PraktikaRuntimeHolder.get(context).also { it.initializer.ensureInitialized() }
        }
        val soundEnabled = runBlocking {
            Stage22ProductionFingerprint.readSoundEnabled(runtime.soundPreferenceRepository)
        }
        val postReinstall = Stage22ProductionFingerprint.capture(
            database = runtime.database,
            soundEnabled = soundEnabled,
            label = "POST_REINSTALL",
        )
        Stage22ProductionFingerprint.assertEquivalent(preReinstall, postReinstall)
        android.util.Log.i(TAG, "STAGE22_PRODUCTION_POST_REINSTALL_GREEN")
    }

    private companion object {
        const val OPT_IN_ARGUMENT = "stage22_production_persistence"
        const val EXPECTED_LABEL_ARGUMENT = "stage22_expected_label"
        const val TAG = "Stage22ProductionProbe"
    }
}
// 07.08.2026 Stage 22 Stability cursor by Me4Hik END

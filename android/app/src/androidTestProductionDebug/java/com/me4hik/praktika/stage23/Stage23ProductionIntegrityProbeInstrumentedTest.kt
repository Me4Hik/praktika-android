// 07.08.2026 Stage 23 Functional Acceptance cursor by Me4Hik START - production integrity probe
package com.me4hik.praktika.stage23

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.stage22.Stage22ProductionFingerprint
import com.me4hik.praktika.stage22.Stage22ProductionFixtureSupport
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assume.assumeTrue

@RunWith(AndroidJUnit4::class)
class Stage23ProductionIntegrityProbeInstrumentedTest {
    @Test
    fun captureBaselineStage23() {
        assumeOptIn()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = runBlocking {
            PraktikaRuntimeHolder.get(context).also { it.initializer.ensureInitialized() }
        }
        val soundEnabled = runBlocking {
            Stage22ProductionFingerprint.readSoundEnabled(runtime.soundPreferenceRepository)
        }
        val baseline = Stage22ProductionFingerprint.capture(
            database = runtime.database,
            soundEnabled = soundEnabled,
            label = Stage23ProductionExpectedFixture.BASELINE_LABEL,
        )
        assertStructuralInvariants(baseline)
        Stage22ProductionFixtureSupport.writeState(context, baseline)
        android.util.Log.i(TAG, "STAGE23_BASELINE_GREEN")
    }

    @Test
    fun postJourneyMatchesBaselineStage23() {
        assumeOptIn()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val baselineFile = File(context.filesDir, Stage22ProductionFingerprint.STATE_FILE_NAME)
        require(baselineFile.exists()) { "BASELINE_STAGE23 file missing" }
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
            label = "POST_STAGE23",
        )
        Stage22ProductionFingerprint.assertEquivalent(baseline, actual)
        assertEquals("ok", runtime.database.openHelper.writableDatabase.query("PRAGMA quick_check").use {
            it.moveToFirst()
            it.getString(0)
        })
        android.util.Log.i(TAG, "STAGE23_POST_INTEGRITY_GREEN")
    }

    private fun assertStructuralInvariants(fingerprint: JSONObject) {
        assertEquals(Stage23ProductionExpectedFixture.EXPECTED_USER_VERSION, fingerprint.getInt("userVersion"))
        assertEquals(Stage23ProductionExpectedFixture.EXPECTED_QUICK_CHECK, fingerprint.getString("quickCheck"))
        assertEquals(Stage23ProductionExpectedFixture.EXPECTED_ANSWERS_COUNT, fingerprint.getInt("answersCount"))
        assertEquals(Stage23ProductionExpectedFixture.EXPECTED_PRACTICE_STARTED, fingerprint.getBoolean("practiceStarted"))
        assertEquals(Stage23ProductionExpectedFixture.EXPECTED_PRACTICE_PAUSED, fingerprint.getBoolean("practicePaused"))
        assertEquals(Stage23ProductionExpectedFixture.EXPECTED_SOUND_ENABLED, fingerprint.getBoolean("soundEnabled"))
        val schedule = fingerprint.getJSONArray("scheduleSlotMinutes")
        assertEquals(Stage23ProductionExpectedFixture.EXPECTED_SCHEDULE_MINUTES.size, schedule.length())
        Stage23ProductionExpectedFixture.EXPECTED_SCHEDULE_MINUTES.forEachIndexed { index, minutes ->
            assertEquals(minutes, schedule.getInt(index))
        }
    }

    private fun assumeOptIn() {
        assumeTrue(
            "Stage 23 runs only via stage23_functional_acceptance.ps1",
            InstrumentationRegistry.getArguments().getString(OPT_IN_ARGUMENT).toBoolean(),
        )
    }

    private companion object {
        const val OPT_IN_ARGUMENT = "stage23_functional_acceptance"
        const val TAG = "Stage23IntegrityProbe"
    }
}
// 07.08.2026 Stage 23 Functional Acceptance cursor by Me4Hik END

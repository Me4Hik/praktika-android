// 07.08.2026 Stage 22 Stability cursor by Me4Hik START - production persistence setup
package com.me4hik.praktika.stage22

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assume.assumeTrue

@RunWith(AndroidJUnit4::class)
class Stage22ProductionPersistenceSetupInstrumentedTest {
    @Test
    fun setupControlledProductionFixtureAndBaseline() {
        assumeTrue(
            "Stage 22 production persistence runs only via stage22_production_persistence.ps1",
            InstrumentationRegistry.getArguments()
                .getString(OPT_IN_ARGUMENT)
                .toBoolean(),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = runBlocking {
            PraktikaRuntimeHolder.get(context)
            val loaded = PraktikaRuntimeHolder.get(context)
            loaded.initializer.ensureInitialized()
            loaded
        }

        runBlocking {
            Stage22ProductionFixtureSupport.assertFreshInstallation(runtime)
            Stage22ProductionFixtureSupport.createControlledFixture(runtime)
        }

        val soundEnabled = runBlocking {
            Stage22ProductionFingerprint.readSoundEnabled(runtime.soundPreferenceRepository)
        }
        val baseline = Stage22ProductionFingerprint.capture(
            database = runtime.database,
            soundEnabled = soundEnabled,
            label = "BASELINE",
        )
        Stage22ProductionFixtureSupport.writeState(context, baseline)

        runBlocking {
            assertEquals(2, runtime.database.answerDao().count())
            assertEquals(5, runtime.database.questionOccurrenceDao().count())
        }
        assertEquals(false, soundEnabled)
        assertEquals("ok", runtime.database.openHelper.writableDatabase.query("PRAGMA quick_check").use {
            it.moveToFirst()
            it.getString(0)
        })
        assertEquals(2, runtime.database.openHelper.writableDatabase.query("PRAGMA user_version").use {
            it.moveToFirst()
            it.getInt(0)
        })
        android.util.Log.i(TAG, "STAGE22_PRODUCTION_SETUP_GREEN label=BASELINE")
    }

    private companion object {
        const val OPT_IN_ARGUMENT = "stage22_production_persistence"
        const val TAG = "Stage22ProductionSetup"
    }
}
// 07.08.2026 Stage 22 Stability cursor by Me4Hik END

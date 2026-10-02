package com.me4hik.praktika.actiontoursmoke

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.assertProductionAlive
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.clickText
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.enterInteractiveTourFromKnownState
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.exists
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.markFail
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.nearestClickable
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.outputDir
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.sleepMs
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.waitFor
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.writeStage
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.device
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-device regression: technical target timeout must not auto-advance / reach Gate.
 * Post-Skip proof uses Task2 actionable hole (Settings CTA), not Archive under scrim.
 */
@RunWith(AndroidJUnit4::class)
class ActionTourTimeoutRegressionTest {

    @Before
    fun setUp() {
        outputDir() // ensure smoke-owned artifact dir exists early
        ActionTourSmokeSupport.assertProductionInstalled() // pid may be empty (cold start)
    }

    @Test
    fun technicalTimeout_doesNotAdvance_thenExplicitSkipMovesToTask2() {
        enterInteractiveTourFromKnownState()
        clickText(ProductionTourTargets.TOUR_NEXT)

        waitFor(
            By.text(ProductionTourTargets.progressText(1)),
            ActionTourSmokeSupport.LONG_TIMEOUT_MS,
            "Task1 progress after Intro",
        )
        // Task1 hole target (Archive) — valid route/action probe while Task1 armed.
        waitFor(
            By.text(ProductionTourTargets.HOME_ARCHIVE),
            ActionTourSmokeSupport.LONG_TIMEOUT_MS,
            "Archive on Home for Task1",
        )

        // Do not tap targets; wait past 5s technical timeout (+ buffer).
        sleepMs(7_000)

        if (!exists(By.text(ProductionTourTargets.progressText(1)), 3_000)) {
            markFail(
                SmokeStage.TIMEOUT_REGRESSION,
                ProductionTourTargets.progressText(1),
                "progress changed after idle >5s",
                "text='1 из 5'",
            )
        }
        assertFalse(
            "Task2 progress must not appear after technical timeout",
            exists(By.text(ProductionTourTargets.progressText(2)), 1_500),
        )
        assertFalse(
            "Gate must not appear after technical timeout",
            exists(By.text(ProductionTourTargets.GATE_FINISH), 1_500),
        )
        assertProductionAlive(SmokeStage.TIMEOUT_REGRESSION)

        clickText(ProductionTourTargets.TOUR_SKIP)
        waitFor(
            By.text(ProductionTourTargets.progressText(2)),
            ActionTourSmokeSupport.LONG_TIMEOUT_MS,
            "Task2 chrome after explicit Skip",
        )

        // Chrome alone is not route proof. Task2 hole = Home Settings CTA.
        // Schedule CD must be absent on Home (stale Settings route would already show it).
        assertFalse(
            "Schedule marker must not be present before Task2 Settings tap (stale route)",
            exists(
                By.descStartsWith(ProductionTourTargets.SCHEDULE_CHANGE_CD_PREFIX),
                1_200,
            ),
        )

        val settingsCta = waitForClickableSettingsOnHome()
        assertTrue("Task2 Settings CTA must be enabled", settingsCta.isEnabled)
        settingsCta.click()
        device().waitForIdle(2_000)

        waitFor(
            By.descStartsWith(ProductionTourTargets.SCHEDULE_CHANGE_CD_PREFIX),
            ActionTourSmokeSupport.LONG_TIMEOUT_MS,
            "Settings schedule target after Task2 Settings tap",
        )
        assertProductionAlive(SmokeStage.TIMEOUT_REGRESSION)

        // Safe close tour without finishing remaining tasks.
        if (exists(By.text(ProductionTourTargets.TOUR_EXIT), 3_000)) {
            clickText(ProductionTourTargets.TOUR_EXIT)
        }
        writeStage(SmokeStage.TIMEOUT_REGRESSION, StageResult.PASS)
    }

    /**
     * Wait for clickable Home «Настройки» (Task2 hole). Do not use Archive under scrim.
     */
    private fun waitForClickableSettingsOnHome(): androidx.test.uiautomator.UiObject2 {
        val deadline = System.currentTimeMillis() + ActionTourSmokeSupport.LONG_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val candidates = device().findObjects(By.text(ProductionTourTargets.HOME_SETTINGS))
            val clickable = candidates.firstOrNull { it.isClickable && it.isEnabled }
                ?: candidates.firstOrNull { it.isEnabled }?.let { nearestClickable(it) }
            if (clickable != null && clickable.isEnabled) {
                return clickable
            }
            device().waitForIdle(250)
        }
        markFail(
            SmokeStage.TIMEOUT_REGRESSION,
            "clickable ${ProductionTourTargets.HOME_SETTINGS}",
            "not found visible=${ActionTourSmokeSupport.visibleTexts().take(20)}",
            "Task2 hole Settings CTA",
        )
    }
}

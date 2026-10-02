package com.me4hik.praktika.actiontoursmoke

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.assertProductionAlive
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.assertProgress
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.clickCheckedOptionAmong
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.clickScheduleSlotChange
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.clickSelectedSound
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.clickText
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.confirmTimePickerWithoutChanging
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.device
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.enterInteractiveTourFromKnownState
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.exists
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.findOrScrollTo
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.markFail
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.openNotificationsFromSettings
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.waitFor
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.waitGone
import com.me4hik.praktika.actiontoursmoke.ActionTourSmokeSupport.writeStage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P0 production black-box smoke: real entry → 5 tasks → Gate → Закончить.
 * Does not instrument com.me4hik.praktika.
 */
@RunWith(AndroidJUnit4::class)
class ActionTourDeviceSmokeTest {

    @Before
    fun setUp() {
        ActionTourSmokeSupport.outputDir() // smoke-owned external artifacts for this run
        ActionTourSmokeSupport.assertProductionInstalled() // pid may be empty (cold start)
    }

    @Test
    fun actionTourDeviceSmoke_fullPhaseA_toGateFinish() {
        // --- Entry ---
        try {
            enterInteractiveTourFromKnownState()
            writeStage(SmokeStage.START_ENTRY, StageResult.PASS)
        } catch (t: Throwable) {
            if (t is AssertionError) throw t
            markFail(SmokeStage.START_ENTRY, "tour entry", t.message ?: "error", "entry")
        }

        // --- Intro ---
        try {
            assertTrue(
                "Intro tip should be visible with Далее",
                exists(By.text(ProductionTourTargets.TOUR_NEXT), ActionTourSmokeSupport.DEFAULT_TIMEOUT_MS),
            )
            // Tip mentions practice / actions (Tone-02 intro).
            assertTrue(
                "Intro body expected",
                exists(By.textContains("познакомиться"), 3_000) ||
                    exists(By.textContains("Практик"), 3_000),
            )
            clickText(ProductionTourTargets.TOUR_NEXT)
            writeStage(SmokeStage.INTRO, StageResult.PASS)
        } catch (t: Throwable) {
            if (t is AssertionError && t.message?.startsWith("stage=") == true) throw t
            markFail(SmokeStage.INTRO, "Intro+Далее", t.message ?: "error", "Далее")
        }

        // --- Route normalize → Task1 on Home ---
        try {
            waitFor(
                By.text(ProductionTourTargets.progressText(1)),
                ActionTourSmokeSupport.LONG_TIMEOUT_MS,
                "progress 1 из 5 after normalize",
            )
            // Must not remain action-ready only on Settings: Home Archive must appear.
            waitFor(
                By.text(ProductionTourTargets.HOME_ARCHIVE),
                ActionTourSmokeSupport.LONG_TIMEOUT_MS,
                "Архив ответов on Home",
            )
            assertFalse(
                "Tour should not stay action-ready on Settings About after Далее",
                exists(By.text(ProductionTourTargets.START_TOUR), 1_200),
            )
            val archive = waitFor(
                By.text(ProductionTourTargets.HOME_ARCHIVE),
                ActionTourSmokeSupport.DEFAULT_TIMEOUT_MS,
                "Archive clickable",
            )
            assertTrue("Archive must be enabled/clickable", archive.isEnabled)
            writeStage(SmokeStage.TASK_1_ARCHIVE, StageResult.PENDING)
        } catch (t: Throwable) {
            if (t is AssertionError && t.message?.startsWith("stage=") == true) throw t
            markFail(
                SmokeStage.TASK_1_ARCHIVE,
                "Home + 1 из 5 + Archive",
                t.message ?: "error",
                "Архив ответов",
            )
        }

        // Task 1 actions: Home → Archive hub → Archive by days info → Далее → Home / Task2
        try {
            clickText(ProductionTourTargets.HOME_ARCHIVE)
            waitFor(
                By.text(ProductionTourTargets.ARCHIVE_OPEN_DAYS),
                ActionTourSmokeSupport.LONG_TIMEOUT_MS,
                "Archive hub open days",
            )
            assertProgress(1, SmokeStage.TASK_1_ARCHIVE)
            clickText(ProductionTourTargets.ARCHIVE_OPEN_DAYS)
            // 1C proof: unique tip arm’ed only after RouteMatch(archive/days).
            // Do not wait for title «Архив по дням» — outside hole / under scrim (not a11y).
            waitFor(
                By.text(ProductionTourTargets.ARCHIVE_DAYS_INFO_TIP),
                ActionTourSmokeSupport.LONG_TIMEOUT_MS,
                "Task1 info tip 1C",
            )
            assertProgress(1, SmokeStage.TASK_1_ARCHIVE)
            val next = waitFor(
                By.text(ProductionTourTargets.TOUR_NEXT),
                ActionTourSmokeSupport.DEFAULT_TIMEOUT_MS,
                "Далее on 1C",
            )
            assertTrue("Далее must be enabled/clickable on 1C", next.isEnabled)
            assertFalse(
                "Пропустить must be hidden on confirmation 1C",
                exists(By.text(ProductionTourTargets.TOUR_SKIP), 1_500),
            )
            next.click()
            device().waitForIdle(1_500)
            waitFor(
                By.text(ProductionTourTargets.progressText(2)),
                ActionTourSmokeSupport.LONG_TIMEOUT_MS,
                "progress 2 из 5 after archive info Далее",
            )
            waitFor(
                By.text(ProductionTourTargets.HOME_SETTINGS),
                ActionTourSmokeSupport.LONG_TIMEOUT_MS,
                "Home after archive info auto-nav",
            )
            assertProductionAlive(SmokeStage.TASK_1_ARCHIVE)
            writeStage(SmokeStage.TASK_1_ARCHIVE, StageResult.PASS)
        } catch (t: Throwable) {
            if (t is AssertionError && t.message?.startsWith("stage=") == true) throw t
            markFail(SmokeStage.TASK_1_ARCHIVE, "Archive flow", t.message ?: "error", "Архив по дням")
        }

        // --- Task 2 Schedule ---
        try {
            clickText(ProductionTourTargets.HOME_SETTINGS)
            assertProgress(2, SmokeStage.TASK_2_SCHEDULE)
            clickScheduleSlotChange()
            confirmTimePickerWithoutChanging()
            waitFor(
                By.text(ProductionTourTargets.progressText(3)),
                ActionTourSmokeSupport.LONG_TIMEOUT_MS,
                "progress 3 из 5 after schedule",
            )
            waitGone(
                By.text(ProductionTourTargets.TIME_PICKER_CONFIRM),
                ActionTourSmokeSupport.DEFAULT_TIMEOUT_MS,
                "TimePicker closed",
            )
            assertProductionAlive(SmokeStage.TASK_2_SCHEDULE)
            writeStage(SmokeStage.TASK_2_SCHEDULE, StageResult.PASS)
        } catch (t: Throwable) {
            if (t is AssertionError && t.message?.startsWith("stage=") == true) throw t
            markFail(
                SmokeStage.TASK_2_SCHEDULE,
                "same-value schedule confirm",
                t.message ?: "error",
                "Изменить время… / Готово",
            )
        }

        // --- Task 3 Wording ---
        try {
            findOrScrollTo(
                By.text(ProductionTourTargets.WORDING_OPTIONS.first()),
                "wording options",
                maxAttempts = 6,
            )
            clickCheckedOptionAmong(ProductionTourTargets.WORDING_OPTIONS, SmokeStage.TASK_3_WORDING)
            waitFor(
                By.text(ProductionTourTargets.progressText(4)),
                ActionTourSmokeSupport.LONG_TIMEOUT_MS,
                "progress 4 из 5 after wording",
            )
            assertProductionAlive(SmokeStage.TASK_3_WORDING)
            writeStage(SmokeStage.TASK_3_WORDING, StageResult.PASS)
        } catch (t: Throwable) {
            if (t is AssertionError && t.message?.startsWith("stage=") == true) throw t
            markFail(SmokeStage.TASK_3_WORDING, "same-value wording", t.message ?: "error", "wording checked")
        }

        // --- Task 4 Sound ---
        try {
            openNotificationsFromSettings()
            findOrScrollTo(
                By.text(ProductionTourTargets.SOUND_LIBRARY_ENTRY),
                "Sound library entry",
            ).click()
            device().waitForIdle(2_000)
            // Title "Звук уведомления" is outside tour hole — not a required marker.
            ActionTourSmokeSupport.assertTask4SoundLibraryReady(SmokeStage.TASK_4_SOUND)
            clickSelectedSound()
            waitFor(
                By.text(ProductionTourTargets.progressText(5)),
                ActionTourSmokeSupport.LONG_TIMEOUT_MS,
                "progress 5 из 5 after sound",
            )
            // Returned to Notifications after POP_ONCE (entry and/or defer visible).
            assertTrue(
                "Expected Notifications after sound POP_ONCE",
                exists(By.text(ProductionTourTargets.SOUND_LIBRARY_ENTRY), 8_000) ||
                    ProductionTourTargets.DEFER_OPTIONS.any { exists(By.text(it), 1_500) },
            )
            assertProductionAlive(SmokeStage.TASK_4_SOUND)
            writeStage(SmokeStage.TASK_4_SOUND, StageResult.PASS)
        } catch (t: Throwable) {
            if (t is AssertionError && t.message?.startsWith("stage=") == true) throw t
            markFail(SmokeStage.TASK_4_SOUND, "same-value sound", t.message ?: "error", "Выбрано / sound")
        }

        // --- Task 5 Defer → Gate ---
        try {
            findOrScrollTo(
                By.text(ProductionTourTargets.DEFER_OPTIONS.first()),
                "defer options area",
                maxAttempts = 8,
            )
            // If first option not the selected one, clickCheckedOptionAmong will scroll/find.
            clickCheckedOptionAmong(ProductionTourTargets.DEFER_OPTIONS, SmokeStage.TASK_5_DEFER)
            waitFor(
                By.text(ProductionTourTargets.GATE_FINISH),
                ActionTourSmokeSupport.LONG_TIMEOUT_MS,
                "Gate Закончить",
            )
            assertTrue(
                "Gate continue present",
                exists(By.text(ProductionTourTargets.GATE_CONTINUE), 5_000),
            )
            assertFalse(
                "Must not show 6 из N progress",
                exists(By.text(ProductionTourTargets.progressText(6)), 1_000),
            )
            assertProductionAlive(SmokeStage.TASK_5_DEFER)
            writeStage(SmokeStage.TASK_5_DEFER, StageResult.PASS)
            writeStage(SmokeStage.GATE, StageResult.PENDING)
        } catch (t: Throwable) {
            if (t is AssertionError && t.message?.startsWith("stage=") == true) throw t
            markFail(SmokeStage.TASK_5_DEFER, "defer → Gate", t.message ?: "error", "defer checked")
        }

        // --- Gate finish ---
        try {
            clickText(ProductionTourTargets.GATE_FINISH)
            waitGone(
                By.text(ProductionTourTargets.GATE_FINISH),
                ActionTourSmokeSupport.LONG_TIMEOUT_MS,
                "Gate overlay closed",
            )
            assertFalse(
                "Tour Далее should be gone",
                exists(By.text(ProductionTourTargets.TOUR_NEXT), 2_000),
            )
            val pkg = device().currentPackageName
            if (pkg != ProductionTourTargets.PRODUCTION_PACKAGE) {
                markFail(
                    SmokeStage.GATE,
                    ProductionTourTargets.PRODUCTION_PACKAGE,
                    pkg ?: "null",
                    "currentPackageName",
                )
            }
            assertProductionAlive(SmokeStage.GATE)
            writeStage(SmokeStage.GATE, StageResult.PASS)
            writeStage(SmokeStage.TIMEOUT_REGRESSION, StageResult.SKIPPED)
        } catch (t: Throwable) {
            if (t is AssertionError && t.message?.startsWith("stage=") == true) throw t
            markFail(SmokeStage.GATE, "Gate Закончить", t.message ?: "error", "Закончить")
        }
    }
}

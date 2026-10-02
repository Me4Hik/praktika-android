package com.me4hik.praktika.actiontoursmoke

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

/**
 * Black-box helpers: drive [ProductionTourTargets.PRODUCTION_PACKAGE] via Accessibility.
 * Instrumentation process = smoke APK (never production).
 */
object ActionTourSmokeSupport {
    const val DEFAULT_TIMEOUT_MS = 15_000L
    const val SHORT_TIMEOUT_MS = 5_000L
    const val LONG_TIMEOUT_MS = 25_000L
    const val MAX_SCROLL_ATTEMPTS = 12

    fun device(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun targetContext(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Volatile
    private var cachedOutputDir: File? = null

    /**
     * Writable smoke-owned external Documents dir for this run.
     * Never uses shell-owned `/data/local/tmp`.
     */
    fun outputDir(): File {
        cachedOutputDir?.let { return it }
        val dir = resolveOutputDir()
        if (!dir.exists() && !dir.mkdirs()) {
            error("Cannot create smoke output dir: ${dir.absolutePath}")
        }
        File(dir, "run_id.txt").writeText(resolveRunId() + "\n")
        cachedOutputDir = dir
        return dir
    }

    fun resolveRunId(): String {
        val args = InstrumentationRegistry.getArguments()
        val fromArg = args.getString(SmokeArtifactPaths.ARG_RUN_ID)?.trim().orEmpty()
        if (fromArg.isNotEmpty()) {
            return SmokeArtifactPaths.requireValidRunId(fromArg)
        }
        // Local androidTest without runner: unique per process.
        return SmokeArtifactPaths.requireValidRunId(
            "local_${System.currentTimeMillis()}",
        )
    }

    private fun resolveOutputDir(): File {
        val args = InstrumentationRegistry.getArguments()
        val override = args.getString(SmokeArtifactPaths.ARG_OUTPUT_DIR)?.trim().orEmpty()
        if (override.isNotEmpty()) {
            require(!SmokeArtifactPaths.isForbiddenShellTmpPath(override)) {
                "smoke_output_dir must not be shell-owned /data/local/tmp (got $override)"
            }
            return File(override)
        }
        val external = targetContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: error(
                "Smoke external files dir unavailable " +
                    "(getExternalFilesDir(DOCUMENTS)=null). Cannot write artifacts.",
            )
        return File(external, SmokeArtifactPaths.relativeRunDir(resolveRunId()))
    }

    fun stageFile(): File = File(outputDir(), "stages.properties")

    fun writeStage(stage: SmokeStage, result: StageResult) {
        val file = stageFile()
        val existing = if (file.exists()) {
            file.readLines().associate {
                val parts = it.split("=", limit = 2)
                parts[0] to parts.getOrElse(1) { "" }
            }.toMutableMap()
        } else {
            mutableMapOf()
        }
        existing[stage.name] = result.name
        file.writeText(existing.entries.joinToString("\n") { "${it.key}=${it.value}" } + "\n")
    }

    /**
     * Primary failure is always rethrown; diagnostics are best-effort and suppressed.
     */
    fun markFail(
        stage: SmokeStage,
        expected: String,
        actual: String,
        selector: String,
    ): Nothing {
        val primary = AssertionError(
            "stage=${stage.name} expected=$expected actual=$actual selector=$selector",
        )
        var shotPath: String? = null
        SmokeFailSafe.runBestEffort(
            primary,
            {
                shotPath = captureScreenshot("failure_${stage.name.lowercase()}")?.absolutePath
            },
            { dumpUiHierarchy("failure_${stage.name.lowercase()}") },
            {
                File(outputDir(), "failure_meta.txt").writeText(
                    buildString {
                        appendLine("FAILED_STAGE=${stage.name}")
                        appendLine("EXPECTED=$expected")
                        appendLine("ACTUAL=$actual")
                        appendLine("SELECTOR=$selector")
                        appendLine("VISIBLE_TEXTS=${visibleTexts().joinToString(" | ")}")
                        appendLine("SCREENSHOT=${shotPath ?: "none"}")
                    },
                )
            },
            { writeStage(stage, StageResult.FAIL) },
        )
        throw primary
    }

    fun captureScreenshot(name: String): File? {
        return try {
            val file = File(outputDir(), "$name.png")
            val bitmap: Bitmap? = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            if (bitmap == null) {
                val ok = device().takeScreenshot(file)
                if (ok) file else null
            } else {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                file
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun dumpUiHierarchy(name: String) {
        try {
            val file = File(outputDir(), "$name.xml")
            device().dumpWindowHierarchy(file)
            File(outputDir(), "${name}_texts.txt").writeText(
                visibleTexts().joinToString("\n"),
                Charsets.UTF_8,
            )
        } catch (_: Throwable) {
            // best-effort
        }
    }

    fun visibleTexts(): List<String> {
        return try {
            device().findObjects(By.enabled(true))
                .mapNotNull { it.text?.takeIf { t -> t.isNotBlank() } }
                .distinct()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun shell(command: String): String {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val pfd = automation.executeShellCommand(command)
        return FileInputStreamCompat(pfd).bufferedReader().use { it.readText() }
    }

    private class FileInputStreamCompat(
        private val pfd: android.os.ParcelFileDescriptor,
    ) : java.io.InputStream() {
        private val stream = java.io.FileInputStream(pfd.fileDescriptor)
        override fun read(): Int = stream.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = stream.read(b, off, len)
        override fun close() {
            stream.close()
            pfd.close()
        }
    }

    /**
     * Clears only the Activity task stack (not app data) and lands on Home.
     * Supports cold start: process may be absent before this call.
     */
    fun ensureKnownEntryState() {
        val component =
            "${ProductionTourTargets.PRODUCTION_PACKAGE}/${ProductionTourTargets.PRODUCTION_MAIN_ACTIVITY}"
        // Task-stack reset only — never pm clear / uninstall.
        val startOut = shell(
            "am start -W -n $component " +
                "-a android.intent.action.MAIN " +
                "-c android.intent.category.LAUNCHER " +
                "--activity-clear-task",
        )
        if (startOut.contains("Error:") || startOut.contains("Exception")) {
            markFail(
                SmokeStage.START_ENTRY,
                "am start Status: ok",
                startOut.trim().take(300).ifBlank { "empty am start output" },
                "am start -W --activity-clear-task",
            )
        }
        val d = device()
        val foreground = d.wait(
            Until.hasObject(By.pkg(ProductionTourTargets.PRODUCTION_PACKAGE)),
            LONG_TIMEOUT_MS,
        )
        if (!foreground) {
            markFail(
                SmokeStage.START_ENTRY,
                "production package foreground",
                "pkg not visible after am start; amOut=${startOut.trim().take(200)}",
                "By.pkg(${ProductionTourTargets.PRODUCTION_PACKAGE})",
            )
        }
        d.waitForIdle(2_000)
        // Post-launch: empty pid is START_ENTRY failure (not pre-launch cold-start).
        assertProductionAlive(SmokeStage.START_ENTRY)
        waitFor(
            By.text(ProductionTourTargets.HOME_ARCHIVE),
            LONG_TIMEOUT_MS,
            "Home marker '${ProductionTourTargets.HOME_ARCHIVE}' after clear-task",
        )
    }

    /**
     * Pre-launch precondition: package must be installed.
     * Empty pidof is allowed (cold start). Does not use START_ENTRY stage.
     */
    fun assertProductionInstalled() {
        val pathOut = shell("pm path ${ProductionTourTargets.PRODUCTION_PACKAGE}")
        if (!SmokeProcessLifecycle.isPackageInstalled(pathOut)) {
            throw AssertionError(
                "PRECONDITION: production package not installed " +
                    "(${ProductionTourTargets.PRODUCTION_PACKAGE}); pm path output=" +
                    pathOut.trim().take(200).ifBlank { "<empty>" },
            )
        }
    }

    /**
     * Process must be running. Use only after launch attempt and at mid-test checkpoints.
     */
    fun assertProductionAlive(stage: SmokeStage) {
        val pid = shell("pidof ${ProductionTourTargets.PRODUCTION_PACKAGE}").trim()
        if (!SmokeProcessLifecycle.isPidPresent(pid)) {
            markFail(stage, "pid present", "empty pidof", "pidof")
        }
    }

    fun waitFor(
        selector: BySelector,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        description: String,
    ): UiObject2 {
        val d = device()
        val found = d.wait(Until.hasObject(selector), timeoutMs)
        if (!found) {
            fail(
                "wait timeout for $description selector=$selector " +
                    "currentPkg=${d.currentPackageName} visible=${visibleTexts().take(20)}",
            )
        }
        val obj = d.findObject(selector)
        assertNotNull("object missing after wait: $description", obj)
        return obj!!
    }

    fun waitGone(selector: BySelector, timeoutMs: Long = DEFAULT_TIMEOUT_MS, description: String) {
        val gone = device().wait(Until.gone(selector), timeoutMs)
        if (!gone) {
            fail("still visible after wait: $description selector=$selector")
        }
    }

    fun exists(selector: BySelector, timeoutMs: Long = SHORT_TIMEOUT_MS): Boolean {
        return device().wait(Until.hasObject(selector), timeoutMs)
    }

    fun clickText(text: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        waitFor(By.text(text), timeoutMs, "text='$text'").click()
        device().waitForIdle(1_500)
    }

    /**
     * Primary: UiObject2.scroll(Direction.DOWN) on scrollable=true.
     * Fallback: UiScrollable. Last resort: percentage swipe (not primary).
     */
    fun findOrScrollTo(
        selector: BySelector,
        description: String,
        maxAttempts: Int = MAX_SCROLL_ATTEMPTS,
    ): UiObject2 {
        if (exists(selector, 900)) {
            return waitFor(selector, 2_000, description)
        }

        var attempts = 0
        while (attempts < maxAttempts) {
            attempts++
            val moved = scrollDownOnce()
            device().waitForIdle(700)
            if (exists(selector, 900)) {
                return waitFor(selector, 2_000, description)
            }
            if (!moved) {
                break
            }
        }

        captureScreenshot("scroll_fail_${description.hashCode().toUInt()}")
        dumpUiHierarchy("scroll_fail_${description.hashCode().toUInt()}")
        throw AssertionError(
            "findOrScrollTo exhausted description=$description attempts=$attempts " +
                "selector=$selector pkg=${device().currentPackageName} " +
                "visible=${visibleTexts().take(30)}",
        )
    }

    private fun scrollDownOnce(): Boolean {
        val d = device()
        val scrollable = d.findObject(By.scrollable(true))
        if (scrollable != null) {
            return try {
                scrollable.scroll(Direction.DOWN, 0.85f)
            } catch (_: Throwable) {
                false
            }
        }
        // UiScrollable fallback
        return try {
            val scroller = UiScrollable(UiSelector().scrollable(true))
            scroller.setAsVerticalList()
            scroller.scrollForward()
        } catch (_: Throwable) {
            // Last-resort coordinate swipe — not the primary strategy.
            val height = d.displayHeight
            val width = d.displayWidth
            d.swipe(width / 2, (height * 0.72).toInt(), width / 2, (height * 0.28).toInt(), 35)
            true
        }
    }

    fun isOnHome(): Boolean = exists(By.text(ProductionTourTargets.HOME_ARCHIVE), 1_500)

    fun isOnSettings(): Boolean {
        return exists(By.descStartsWith(ProductionTourTargets.SCHEDULE_CHANGE_CD_PREFIX), 1_200) ||
            exists(By.text(ProductionTourTargets.NOTIFICATIONS_ENTRY), 1_200) ||
            exists(By.text(ProductionTourTargets.WORDING_MASCULINE), 1_200) ||
            exists(By.text(ProductionTourTargets.ABOUT_SECTION), 800)
    }

    /**
     * From known Home, open Settings and confirm via Settings-specific markers
     * (never treat title «Настройки» alone as success).
     */
    fun openSettingsFromHome() {
        if (!isOnHome()) {
            fail(
                "openSettingsFromHome requires Home marker " +
                    "'${ProductionTourTargets.HOME_ARCHIVE}' visible=${visibleTexts().take(15)}",
            )
        }
        clickHomeSettingsCta()
        // Confirm Settings without requiring About (may be below fold).
        val settingsConfirmed =
            exists(By.descStartsWith(ProductionTourTargets.SCHEDULE_CHANGE_CD_PREFIX), 8_000) ||
                exists(By.text(ProductionTourTargets.NOTIFICATIONS_ENTRY), 3_000) ||
                exists(By.text(ProductionTourTargets.WORDING_MASCULINE), 3_000)
        if (!settingsConfirmed) {
            fail(
                "Settings not confirmed after tap (need schedule CD / Уведомления / wording). " +
                    "visible=${visibleTexts().take(20)}",
            )
        }
    }

    private fun clickHomeSettingsCta() {
        val d = device()
        // Prefer clickable node labeled Настройки while Home Archive is present.
        val candidates = d.findObjects(By.text(ProductionTourTargets.HOME_SETTINGS))
        val clickable = candidates.firstOrNull { it.isClickable }
            ?: candidates.firstOrNull()?.let { nearestClickable(it) }
            ?: run { fail("Home Settings CTA not found"); return }
        clickable.click()
        d.waitForIdle(2_000)
    }

    fun nearestClickable(node: UiObject2): UiObject2 {
        var cur: UiObject2? = node
        repeat(8) {
            val c = cur ?: return node
            if (c.isClickable) return c
            cur = c.parent
        }
        return node
    }

    fun ensureTourEntryVisible() {
        // Prefer start-tour if already unlocked and scrolled; else Version / About.
        if (exists(By.text(ProductionTourTargets.START_TOUR), 800)) return
        if (exists(By.textContains(ProductionTourTargets.VERSION_PREFIX), 800)) return
        if (exists(By.text(ProductionTourTargets.ABOUT_SECTION), 800)) return

        // Scroll toward About / Version / Start tour.
        try {
            findOrScrollTo(
                By.text(ProductionTourTargets.START_TOUR),
                "start tour",
                maxAttempts = 4,
            )
            return
        } catch (_: AssertionError) {
            // continue
        }
        try {
            findOrScrollTo(
                By.textContains(ProductionTourTargets.VERSION_PREFIX),
                "version row",
            )
            return
        } catch (_: AssertionError) {
            // continue
        }
        findOrScrollTo(
            By.text(ProductionTourTargets.ABOUT_SECTION),
            "About section",
        )
    }

    fun unlockTesterToolsIfNeeded() {
        ensureTourEntryVisible()
        if (exists(By.text(ProductionTourTargets.START_TOUR), 1_500)) {
            return
        }
        findOrScrollTo(
            By.textContains(ProductionTourTargets.VERSION_PREFIX),
            "version for unlock",
        )
        val versionText = waitFor(
            By.textContains(ProductionTourTargets.VERSION_PREFIX),
            DEFAULT_TIMEOUT_MS,
            "version text",
        )
        val clickTarget = nearestClickable(versionText)
        repeat(5) {
            clickTarget.click()
            device().waitForIdle(450)
        }
        findOrScrollTo(
            By.text(ProductionTourTargets.START_TOUR),
            "Interactive Tour after unlock",
        )
        waitFor(
            By.text(ProductionTourTargets.START_TOUR),
            LONG_TIMEOUT_MS,
            "Interactive Tour after unlock",
        )
    }

    /** Full entry: clear-task Home → Settings → scroll About → unlock if needed → start tour. */
    fun enterInteractiveTourFromKnownState() {
        ensureKnownEntryState()
        openSettingsFromHome()
        unlockTesterToolsIfNeeded()
        findOrScrollTo(By.text(ProductionTourTargets.START_TOUR), "start tour button")
        clickText(ProductionTourTargets.START_TOUR)
        waitFor(By.text(ProductionTourTargets.TOUR_NEXT), LONG_TIMEOUT_MS, "Intro Далее")
    }

    fun assertProgress(ordinal: Int, stage: SmokeStage) {
        val expected = ProductionTourTargets.progressText(ordinal)
        if (!exists(By.text(expected), DEFAULT_TIMEOUT_MS)) {
            markFail(stage, expected, "progress text not found", "text='$expected'")
        }
    }

    fun clickScheduleSlotChange() {
        val byDesc = By.descStartsWith(ProductionTourTargets.SCHEDULE_CHANGE_CD_PREFIX)
        if (exists(byDesc, 3_000)) {
            waitFor(byDesc, DEFAULT_TIMEOUT_MS, "schedule slot contentDescription").click()
            device().waitForIdle(1_500)
            return
        }
        clickText(ProductionTourTargets.SLOT_CHANGE)
    }

    fun confirmTimePickerWithoutChanging() {
        waitFor(
            By.text(ProductionTourTargets.TIME_PICKER_CONFIRM),
            DEFAULT_TIMEOUT_MS,
            "TimePicker Готово",
        ).click()
        device().waitForIdle(2_000)
    }

    fun openNotificationsFromSettings() {
        findOrScrollTo(
            By.text(ProductionTourTargets.NOTIFICATIONS_ENTRY),
            "Уведомления entry",
        ).click()
        device().waitForIdle(2_000)
        findOrScrollTo(
            By.text(ProductionTourTargets.SOUND_LIBRARY_ENTRY),
            "Sound library entry on Notifications",
        )
    }

    fun clickCheckedOptionAmong(labels: List<String>, stage: SmokeStage): String {
        val d = device()
        for (label in labels) {
            if (!exists(By.text(label), 600)) {
                try {
                    findOrScrollTo(By.text(label), "option $label", maxAttempts = 6)
                } catch (_: AssertionError) {
                    continue
                }
            }
            val checked = d.findObject(By.text(label).checked(true))
            if (checked != null) {
                nearestClickable(checked).click()
                d.waitForIdle(1_500)
                return label
            }
            val node = d.findObject(By.text(label))
            if (node != null) {
                val parent = node.parent
                if (parent != null && parent.isChecked) {
                    nearestClickable(parent).click()
                    d.waitForIdle(1_500)
                    return label
                }
            }
        }
        val anyChecked = d.findObjects(By.checked(true))
        for (obj in anyChecked) {
            val text = obj.text
            if (text != null && text in labels) {
                nearestClickable(obj).click()
                d.waitForIdle(1_500)
                return text
            }
            val childTexts = obj.children.mapNotNull { child -> child.text }
            val match = childTexts.firstOrNull { it in labels }
            if (match != null) {
                nearestClickable(obj).click()
                d.waitForIdle(1_500)
                return match
            }
        }
        markFail(
            stage,
            "checked option among $labels",
            "no checked option found visible=${visibleTexts().take(20)}",
            "checked(true)+labels",
        )
    }

    /**
     * Task4 Sound Library ready for same-value tap.
     * Does **not** require top-bar title (often outside tour spotlight hole).
     */
    fun assertTask4SoundLibraryReady(stage: SmokeStage) {
        waitFor(
            By.text(ProductionTourTargets.progressText(4)),
            DEFAULT_TIMEOUT_MS,
            "Task4 progress 4 из 5 on Sound Library",
        )
        waitFor(
            By.textContains(SoundSmokeProof.TIP_CONTAINS),
            DEFAULT_TIMEOUT_MS,
            "Task4 sound tip",
        )
        val hasSelectedCd = exists(
            By.descContains(ProductionTourTargets.SOUND_SELECTED_CD),
            3_000,
        )
        val hasChecked = device().findObjects(By.checked(true)).isNotEmpty()
        if (!SoundSmokeProof.soundLibraryScreenAccepted(
                hasProgressTask4 = true,
                tipLooksLikeSoundTask = true,
                hasSelectedCdOrChecked = hasSelectedCd || hasChecked,
            )
        ) {
            markFail(
                stage,
                "selected sound marker (Выбрано / checked)",
                "missing selected row; title not required; visible=${visibleTexts().take(20)}",
                "descContains('${ProductionTourTargets.SOUND_SELECTED_CD}')|checked(true)",
            )
        }
    }

    /**
     * Taps **current selected** sound only (data-safe). Never falls back to an unselected row.
     */
    fun clickSelectedSound() {
        val byCd = By.descContains(ProductionTourTargets.SOUND_SELECTED_CD)
        if (exists(byCd, 5_000)) {
            val obj = waitFor(byCd, DEFAULT_TIMEOUT_MS, "selected sound contentDescription Выбрано")
            val target = nearestClickable(obj)
            if (!target.isEnabled) {
                fail(
                    "selected sound (Выбрано) found but not enabled; " +
                        "visible=${visibleTexts().take(20)}",
                )
            }
            target.click()
            device().waitForIdle(2_000)
            return
        }
        // Fallback: checked(true) only — still current selection, not an unselected preference change.
        val checked = device().findObjects(By.checked(true))
            .firstOrNull { it.isEnabled }
            ?: device().findObjects(By.checked(true)).firstOrNull()?.let { nearestClickable(it) }
        if (checked != null && checked.isEnabled) {
            nearestClickable(checked).click()
            device().waitForIdle(2_000)
            return
        }
        fail(
            "DATA_SAFE: selected sound not actionable " +
                "(need descContains('${ProductionTourTargets.SOUND_SELECTED_CD}') or checked(true)); " +
                "unselected fallback forbidden; visible=${visibleTexts().take(20)}",
        )
    }

    fun sleepMs(ms: Long) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}

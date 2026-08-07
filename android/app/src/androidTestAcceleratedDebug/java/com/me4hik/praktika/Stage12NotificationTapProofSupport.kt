// 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik START - tap proof helpers
package com.me4hik.praktika

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.me4hik.praktika.accelerated.AcceleratedTimeProvider
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.data.cycle.ScheduleSlotUpdate
import com.me4hik.praktika.data.cycle.ScheduleUpdateResult
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.notification.AndroidPracticeNotificationPresenter
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.runtime.PraktikaRuntime
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeFactory
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeTestTags
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue

object Stage12NotificationTapProofSupport {
    const val TAG = "Stage12NotificationTap"
    const val OPT_IN_ARGUMENT = "stage12_notification_tap"
    const val SETUP_STATE_FILE_NAME = "stage12_notification_tap_setup.json"
    private const val APP_NAME = "Практика"

    fun isOptIn(): Boolean {
        return InstrumentationRegistry.getArguments()
            .getString(OPT_IN_ARGUMENT)
            .toBoolean()
    }

    fun assumeOptIn() {
        assumeTrue(
            "Notification tap proof runs only through scripts/stage12_notification_tap_e2e.ps1",
            isOptIn(),
        )
    }

    fun resetAcceleratedSandbox(context: Context) {
        MainActivity.resetTapProofMarkersForTests()
        MainActivityInitGate.resetForTests()
        PraktikaRuntimeHolder.resetForTests()
        context.filesDir.listFiles()
            ?.filter {
                it.name == AcceleratedTimeStorage.STATE_FILE_NAME ||
                    it.name == SETUP_STATE_FILE_NAME ||
                    it.name == Stage12RealAlarmProofSupport.SETUP_STATE_FILE_NAME
            }
            ?.forEach { it.delete() }
        context.getDatabasePath(RuntimeFactory.ACCELERATED_DATABASE_NAME).delete()
    }

    data class SetupState(
        val occurrenceId: Long,
        val plannedAtEpochMillis: Long,
        val questionTextSnapshot: String,
        val packageName: String,
    )

    fun writeSetupState(context: Context, state: SetupState) {
        val json = JSONObject()
        json.put("occurrenceId", state.occurrenceId)
        json.put("plannedAtEpochMillis", state.plannedAtEpochMillis)
        json.put("questionTextSnapshot", state.questionTextSnapshot)
        json.put("packageName", state.packageName)
        File(context.filesDir, SETUP_STATE_FILE_NAME).writeText(json.toString())
    }

    suspend fun prepareAvailableWithNotification(
        context: Context,
        resetSandbox: Boolean = true,
    ): SetupState {
        if (resetSandbox) {
            resetAcceleratedSandbox(context)
        }
        val runtime = PraktikaRuntimeHolder.get(context)
        check(runtime.initializer.ensureInitialized()) { "Runtime initialization failed" }

        val clock = runtime.timeProvider as AcceleratedTimeProvider
        if (clock.currentState().isVirtualClockPaused) {
            clock.resumeVirtualClock()
        }
        val wallNow = System.currentTimeMillis()
        if (wallNow > clock.currentVirtualNow()) {
            clock.advanceToVirtualEpochMillis(wallNow)
        }
        clock.checkpoint()

        val slotMinutes = nearSlotMinutes(runtime)
        val updateResult = runtime.cycleRepository.updateSchedule(
            scheduleUpdates(slotMinutes),
        )
        assertEquals(ScheduleUpdateResult.Success, updateResult)
        runtime.cycleRepository.startPractice()
        runtime.notificationCoordinator.sync(NotificationSyncReason.PERMISSION_CHANGED)
        runtime.notificationCoordinator.sync(NotificationSyncReason.APP_START)

        val incomplete = runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
        clock.advanceToVirtualEpochMillis(incomplete.plannedAtEpochMillis + 1L)
        runtime.cycleRepository.reconcile()
        clock.checkpoint()
        runtime.notificationCoordinator.sync(NotificationSyncReason.FOREGROUND)

        val occurrence = runtime.database.questionOccurrenceDao().getById(incomplete.id)!!
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, occurrence.status)
        assertNull(occurrence.openedAtEpochMillis)
        waitForActiveNotification(context, occurrence.id)

        return SetupState(
            occurrenceId = occurrence.id,
            plannedAtEpochMillis = occurrence.plannedAtEpochMillis,
            questionTextSnapshot = occurrence.questionTextSnapshot,
            packageName = context.packageName,
        ).also { writeSetupState(context, it) }
    }

    fun nearSlotMinutes(runtime: PraktikaRuntime): Int {
        val zone = ZoneId.of(runtime.timeProvider.currentZoneId())
        val now = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(runtime.timeProvider.nowEpochMillis()),
            zone,
        )
        val nowMinutes = now.hour * 60 + now.minute
        var slotMinutes = ((nowMinutes + 5 + 4) / 5) * 5
        if (slotMinutes <= nowMinutes) {
            slotMinutes += 5
        }
        if (slotMinutes >= 24 * 60) {
            slotMinutes -= 24 * 60
        }
        return slotMinutes
    }

    fun scheduleUpdates(firstSlotMinutes: Int): List<ScheduleSlotUpdate> {
        fun wrap(minutes: Int): Int {
            var value = minutes
            while (value >= 24 * 60) value -= 24 * 60
            while (value < 0) value += 24 * 60
            return value
        }
        val second = wrap(firstSlotMinutes + 60)
        var third = wrap(second + 60)
        if (third == firstSlotMinutes || third == second) {
            third = wrap(third + 60)
        }
        return listOf(
            ScheduleSlotUpdate(1, firstSlotMinutes),
            ScheduleSlotUpdate(2, second),
            ScheduleSlotUpdate(3, third),
        )
    }

    fun hasActiveNotification(context: Context, occurrenceId: Long): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.any {
            it.tag == AndroidPracticeNotificationPresenter.NOTIFICATION_TAG &&
                it.id == AndroidPracticeNotificationPresenter.notificationId(occurrenceId)
        }
    }

    fun assertActiveNotification(context: Context, occurrenceId: Long) {
        waitForActiveNotification(context, occurrenceId)
    }

    fun waitForActiveNotification(
        context: Context,
        occurrenceId: Long,
        timeoutMs: Long = 15_000,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var resynced = false
        while (System.currentTimeMillis() < deadline) {
            if (hasActiveNotification(context, occurrenceId)) {
                return
            }
            if (!resynced && System.currentTimeMillis() + 5_000 > deadline) {
                runBlocking {
                    val runtime = PraktikaRuntimeHolder.get(context)
                    runtime.notificationCoordinator.sync(NotificationSyncReason.FOREGROUND)
                }
                resynced = true
            }
            Thread.sleep(250)
        }
        assertTrue(
            "Expected active notification for occurrence $occurrenceId",
            hasActiveNotification(context, occurrenceId),
        )
    }

    fun assertNotificationAbsent(context: Context, occurrenceId: Long) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val active = manager.activeNotifications.any {
            it.tag == AndroidPracticeNotificationPresenter.NOTIFICATION_TAG &&
                it.id == AndroidPracticeNotificationPresenter.notificationId(occurrenceId)
        }
        assertTrue("Expected notification $occurrenceId to be absent", !active)
    }

    fun pressHome() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
        Thread.sleep(1_000)
    }

    fun killAppProcess() {
        val pkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("am kill $pkg")
            .close()
        Thread.sleep(1_500)
    }

    fun expandNotificationShade(device: UiDevice) {
        device.executeShellCommand("cmd statusbar expand-notifications")
        device.waitForIdle()
        Thread.sleep(800)
    }

    fun pauseAcceleratedClock(runtime: PraktikaRuntime) {
        val clock = runtime.timeProvider as AcceleratedTimeProvider
        if (!clock.currentState().isVirtualClockPaused) {
            clock.pauseVirtualClock()
        }
    }

    fun tapNotificationInShade(device: UiDevice, questionText: String, timeoutMs: Long = 20_000) {
        expandNotificationShade(device)
        val deadline = System.currentTimeMillis() + timeoutMs
        var target = findNotificationObject(device, questionText)
        while (target == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(500)
            expandNotificationShade(device)
            target = findNotificationObject(device, questionText)
        }
        checkNotNull(target) { "Notification not found in shade for question=$questionText" }
        target.click()
        device.waitForIdle()
        Thread.sleep(1_500)
    }

    private fun findNotificationObject(device: UiDevice, questionText: String) =
        device.findObject(By.text(questionText))
            ?: device.findObject(By.textContains(questionText.take(20)))
            ?: device.findObject(By.text(APP_NAME))

    fun waitForQuestionText(device: UiDevice, questionText: String, timeoutMs: Long = 20_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val byFull = device.findObject(By.text(questionText))
            val byPartial = device.findObject(By.textContains(questionText.take(20)))
            if (byFull != null || byPartial != null) {
                device.waitForIdle()
                return
            }
            Thread.sleep(500)
        }
        throw AssertionError("Question text not visible after notification tap: $questionText")
    }

    fun waitForHomeAnswerButton(device: UiDevice, timeoutMs: Long = 15_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val answer = device.findObject(By.text("Ответить"))
            if (answer != null) {
                return
            }
            Thread.sleep(500)
        }
    }

    fun readLogcatSnapshot(): String {
        val pfd = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("logcat -d")
        return try {
            FileInputStream(pfd.fileDescriptor).bufferedReader().readText()
        } finally {
            pfd.close()
        }
    }

    fun logcatContains(pattern: String): Int {
        return Regex(pattern).findAll(readLogcatSnapshot()).count()
    }

    fun dumpsysNotificationText(): String {
        val pfd = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("dumpsys notification --noredact")
        return try {
            FileInputStream(pfd.fileDescriptor).bufferedReader().readText()
        } finally {
            pfd.close()
        }
    }

    fun dumpsysActivityText(): String {
        val pfd = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("dumpsys activity activities")
        return try {
            FileInputStream(pfd.fileDescriptor).bufferedReader().readText()
        } finally {
            pfd.close()
        }
    }

    fun clearLogcat() {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("logcat -c")
            .close()
    }

    fun deliverOnNewIntent(activity: MainActivity, intent: Intent) {
        val method = MainActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java)
        method.isAccessible = true
        method.invoke(activity, intent)
    }

    fun startMainActivityWithTapIntent(context: Context, setup: SetupState) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NOTIFICATION_OCCURRENCE_ID, setup.occurrenceId)
            putExtra(MainActivity.EXTRA_NOTIFICATION_PLANNED_AT, setup.plannedAtEpochMillis)
            putExtra(MainActivity.EXTRA_NOTIFICATION_SOURCE, MainActivity.NOTIFICATION_SOURCE_VALUE)
        }
        context.startActivity(intent)
    }

    fun dismissQuestionToHome(
        composeRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
    ) {
        runCatching {
            if (PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.QUESTION_TEXT)) {
                composeRule.onNodeWithTag(PracticeTestTags.QUESTION_BACK_HOME).performClick()
                composeRule.waitForIdle()
            } else {
                androidx.test.espresso.Espresso.pressBack()
                composeRule.waitForIdle()
            }
            PracticeComposeTestSupport.waitForHome(composeRule)
        }
    }

    fun logMarker(message: String) {
        Log.i(TAG, message)
    }

    fun prepareAvailableForTapUi(
        context: Context,
        composeRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        resetSandbox: Boolean = false,
    ): SetupState {
        val setup = runBlocking { prepareAvailableWithNotification(context, resetSandbox) }
        runBlocking { MainActivityInitGate.awaitInit() }
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_POSITION) ||
                PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_ANSWER)
        }
        return setup
    }
}
// 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik END

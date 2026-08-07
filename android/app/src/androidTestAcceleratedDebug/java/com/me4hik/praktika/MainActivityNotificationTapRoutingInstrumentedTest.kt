// 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik START - intent routing policy tests (not shade milestone)
// 06.08.2026 Stage 12 Final Device Recovery cursor by Me4Hik START - ActivityScenario + UiAutomator avoids Blackview compose teardown hang
package com.me4hik.praktika

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.notification.NotificationTapDecision
import com.me4hik.praktika.notification.NotificationTapSource
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityNotificationTapRoutingInstrumentedTest {
    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Stage12NotificationTapProofSupport.resetAcceleratedSandbox(context)
        Stage12BlackviewE2ESupport.grantPostNotificationsViaShell()
        Stage12NotificationTapProofSupport.clearLogcat()
        PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
    }

    @After
    fun tearDownRuntime() {
        runCatching {
            PraktikaRuntimeHolder.resetForTests()
            MainActivityInitGate.resetForTests()
        }
    }

    @Test
    fun initialIntentRoutesToQuestionOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val setup = runBlocking {
            Stage12NotificationTapProofSupport.prepareAvailableWithNotification(context, resetSandbox = false)
        }

        Stage12NotificationTapProofSupport.pressHome()
        Stage12NotificationTapProofSupport.killAppProcess()
        Stage12NotificationTapProofSupport.clearLogcat()

        val tapIntent = notificationTapIntent(context, setup).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val scenario = ActivityScenario.launch<MainActivity>(tapIntent)
        try {
            scenario.moveToState(Lifecycle.State.RESUMED)
            runBlocking { MainActivityInitGate.awaitInit() }
            Stage12NotificationTapProofSupport.waitForQuestionText(
                device,
                setup.questionTextSnapshot,
                timeoutMs = 30_000,
            )

            val runtime = runBlocking { PraktikaRuntimeHolder.get(context) }
            val occurrence = runBlocking {
                runtime.database.questionOccurrenceDao().getById(setup.occurrenceId)!!
            }
            assertNotNull(occurrence.openedAtEpochMillis)

            val log = Stage12NotificationTapProofSupport.readLogcatSnapshot()
            assertTrue(log.contains("handleIntent source=INITIAL_INTENT"))
            assertTrue(log.contains("navigationResult=publish"))
        } finally {
            finishScenarioSafely(scenario)
        }
    }

    @Test
    fun onNewIntentRoutesToQuestionWithoutRecreatingActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val setup = runBlocking {
            Stage12NotificationTapProofSupport.prepareAvailableWithNotification(context, resetSandbox = false)
        }

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.moveToState(Lifecycle.State.RESUMED)
            runBlocking { MainActivityInitGate.awaitInit() }
            Stage12NotificationTapProofSupport.waitForHomeAnswerButton(device)

            val instanceBefore = MainActivity.activeActivityInstanceId
            val tapIntent = notificationTapIntent(context, setup)
            scenario.onActivity { activity ->
                Stage12NotificationTapProofSupport.deliverOnNewIntent(activity, tapIntent)
            }
            Stage12NotificationTapProofSupport.waitForQuestionText(device, setup.questionTextSnapshot)

            assertEquals(instanceBefore, MainActivity.activeActivityInstanceId)
            val log = Stage12NotificationTapProofSupport.readLogcatSnapshot()
            assertTrue(log.contains("handleIntent source=ON_NEW_INTENT"))
        } finally {
            finishScenarioSafely(scenario)
        }
    }

    @Test
    fun staleIntentIsIgnoredAndStoreRemainsEmpty() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val setup = runBlocking {
            Stage12NotificationTapProofSupport.prepareAvailableWithNotification(context, resetSandbox = false)
        }
        val runtime = runBlocking { PraktikaRuntimeHolder.get(context) }

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.moveToState(Lifecycle.State.RESUMED)
            runBlocking { MainActivityInitGate.awaitInit() }
            Stage12NotificationTapProofSupport.waitForHomeAnswerButton(device)

            runBlocking { runtime.cycleRepository.skipAvailableByUser(setup.occurrenceId) }
            waitForHomeScreen(device)

            val staleIntent = notificationTapIntent(context, setup)
            scenario.onActivity { activity ->
                Stage12NotificationTapProofSupport.deliverOnNewIntent(activity, staleIntent)
            }
            device.waitForIdle()
            Thread.sleep(1_000)

            assertNull(runtime.notificationOpenRequestStore.pendingOpen.value)
            waitForHomeScreen(device)
            val log = Stage12NotificationTapProofSupport.readLogcatSnapshot()
            assertTrue(log.contains("navigationResult=ignore"))
        } finally {
            finishScenarioSafely(scenario)
        }
    }

    @Test
    fun coordinatorStaleTapPolicyMatchesRepositoryState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val setup = runBlocking {
            Stage12NotificationTapProofSupport.prepareAvailableWithNotification(context, resetSandbox = false)
        }
        val runtime = runBlocking { PraktikaRuntimeHolder.get(context) }
        runBlocking { runtime.cycleRepository.skipAvailableByUser(setup.occurrenceId) }

        val decision = runBlocking {
            runtime.notificationCoordinator.handleNotificationTap(
                occurrenceId = setup.occurrenceId,
                plannedAtEpochMillis = setup.plannedAtEpochMillis,
                tapSource = NotificationTapSource.DIRECT_TEST,
            )
        }
        assertTrue(decision is NotificationTapDecision.Ignore)
        val stale = runBlocking {
            runtime.database.questionOccurrenceDao().getById(setup.occurrenceId)!!
        }
        assertEquals(QuestionOccurrenceStatus.SKIPPED_BY_USER, stale.status)
    }

    private fun waitForHomeScreen(device: UiDevice) {
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            if (device.findObject(androidx.test.uiautomator.By.text("Ответить")) != null ||
                device.findObject(androidx.test.uiautomator.By.text("Следующий вопрос:")) != null
            ) {
                return
            }
            device.waitForIdle()
            Thread.sleep(250)
        }
        throw AssertionError("Home screen not visible")
    }

    private fun finishScenarioSafely(scenario: ActivityScenario<MainActivity>) {
        runCatching {
            scenario.onActivity { activity ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.finishAffinity()
                }
            }
        }
        runCatching { scenario.close() }
    }

    private fun notificationTapIntent(
        context: android.content.Context,
        setup: Stage12NotificationTapProofSupport.SetupState,
    ): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NOTIFICATION_OCCURRENCE_ID, setup.occurrenceId)
            putExtra(MainActivity.EXTRA_NOTIFICATION_PLANNED_AT, setup.plannedAtEpochMillis)
            putExtra(MainActivity.EXTRA_NOTIFICATION_SOURCE, MainActivity.NOTIFICATION_SOURCE_VALUE)
        }
    }
}
// 06.08.2026 Stage 12 Final Device Recovery cursor by Me4Hik END
// 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik END

// 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik START - notification tap recreation dedup
// 06.08.2026 Stage 12 Final Device Recovery cursor by Me4Hik START - ActivityScenario flow without compose onboarding hang
package com.me4hik.praktika

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
class MainActivityNotificationTapRecreationInstrumentedTest {
    @Before
    fun resetSandbox() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Stage12FinalAcceptanceSupport.resetAcceleratedSandbox(context)
        Stage12BlackviewE2ESupport.grantPostNotificationsViaShell()
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
    fun openedNotificationTapDoesNotReopenQuestionAfterRecreate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val setup = runBlocking {
            Stage12NotificationTapProofSupport.prepareAvailableWithNotification(context, resetSandbox = false)
        }
        val runtime = runBlocking { PraktikaRuntimeHolder.get(context) }
        val tapIntent = notificationTapIntent(context, setup)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.moveToState(Lifecycle.State.RESUMED)
            runBlocking { MainActivityInitGate.awaitInit() }
            scenario.onActivity { activity ->
                Stage12NotificationTapProofSupport.deliverOnNewIntent(activity, tapIntent)
            }
            val openedAtDeadline = System.currentTimeMillis() + 15_000
            var openedAtBefore: Long? = null
            while (System.currentTimeMillis() < openedAtDeadline) {
                openedAtBefore = runBlocking {
                    runtime.database.questionOccurrenceDao().getById(setup.occurrenceId)!!.openedAtEpochMillis
                }
                if (openedAtBefore != null) {
                    break
                }
                Thread.sleep(250)
            }
            assertNotNull(openedAtBefore)
            assertNull(runtime.notificationOpenRequestStore.pendingOpen.value)

            scenario.onActivity { activity -> activity.recreate() }
            runBlocking { MainActivityInitGate.awaitInit() }
            Thread.sleep(1_000)

            val decision = runBlocking {
                runtime.notificationCoordinator.handleNotificationTap(
                    occurrenceId = setup.occurrenceId,
                    plannedAtEpochMillis = setup.plannedAtEpochMillis,
                    tapSource = NotificationTapSource.DIRECT_TEST,
                )
            }
            assertTrue(decision is NotificationTapDecision.Ignore)
            val openedAtAfter = runBlocking {
                runtime.database.questionOccurrenceDao().getById(setup.occurrenceId)!!.openedAtEpochMillis
            }
            assertEquals(openedAtBefore, openedAtAfter)
            assertNull(runtime.notificationOpenRequestStore.pendingOpen.value)
        } finally {
            runCatching {
                scenario.onActivity { activity ->
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        activity.finishAffinity()
                    }
                }
            }
            runCatching { scenario.close() }
        }
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
// 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik END

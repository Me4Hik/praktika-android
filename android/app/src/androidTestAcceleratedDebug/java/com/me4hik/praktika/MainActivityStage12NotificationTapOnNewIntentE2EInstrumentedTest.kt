// 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik START - onNewIntent shade tap E2E
package com.me4hik.praktika

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityStage12NotificationTapOnNewIntentE2EInstrumentedTest {
    @Before
    fun resetAndWake() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Stage12NotificationTapProofSupport.resetAcceleratedSandbox(context)
        Stage12NotificationTapProofSupport.killAppProcess()
        Stage12NotificationTapProofSupport.clearLogcat()
        PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
    }

    @Test
    fun scenarioOnNewIntentNotificationShadeTap() {
        Stage12NotificationTapProofSupport.assumeOptIn()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val setup = runBlocking {
            Stage12NotificationTapProofSupport.prepareAvailableWithNotification(context, resetSandbox = true)
        }

        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
        )
        runBlocking { MainActivityInitGate.awaitInit() }
        Stage12NotificationTapProofSupport.waitForHomeAnswerButton(device)

        val instanceBeforeTap = MainActivity.activeActivityInstanceId
        assertTrue(instanceBeforeTap > 0)

        Stage12NotificationTapProofSupport.tapNotificationInShade(device, setup.questionTextSnapshot)
        Stage12NotificationTapProofSupport.waitForQuestionText(device, setup.questionTextSnapshot)

        val log = Stage12NotificationTapProofSupport.readLogcatSnapshot()
        assertTrue(log.contains("NotificationTap: onNewIntent"))
        assertTrue(log.contains("handleIntent source=ON_NEW_INTENT"))
        assertTrue(log.contains("navigationResult=publish source=ON_NEW_INTENT occurrenceId=${setup.occurrenceId}"))
        assertTrue(log.contains("validationResult=Open"))

        assertEquals(instanceBeforeTap, MainActivity.activeActivityInstanceId)

        val runtime = runBlocking { PraktikaRuntimeHolder.get(context) }
        val occurrence = runBlocking {
            runtime.database.questionOccurrenceDao().getById(setup.occurrenceId)!!
        }
        assertNotNull(occurrence.openedAtEpochMillis)
        Stage12NotificationTapProofSupport.logMarker("scenarioB_green")
    }
}
// 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik END

// 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik START - cold-start shade tap without ActivityScenario
package com.me4hik.praktika

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.notification.NotificationSyncReason
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
class MainActivityStage12NotificationTapColdStartE2EInstrumentedTest {
    @Before
    fun resetAndWake() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Stage12NotificationTapProofSupport.resetAcceleratedSandbox(context)
        Stage12NotificationTapProofSupport.killAppProcess()
        Stage12NotificationTapProofSupport.clearLogcat()
        PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
    }

    @Test
    fun scenarioColdStartNotificationShadeTap() {
        Stage12NotificationTapProofSupport.assumeOptIn()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val setup = runBlocking {
            Stage12NotificationTapProofSupport.prepareAvailableWithNotification(context, resetSandbox = false)
        }

        Stage12NotificationTapProofSupport.logMarker("scenarioA_setup occurrenceId=${setup.occurrenceId}")
        Stage12NotificationTapProofSupport.pressHome()
        Stage12NotificationTapProofSupport.killAppProcess()

        val notifDumpBeforeTap = Stage12NotificationTapProofSupport.dumpsysNotificationText()
        assertTrue(notifDumpBeforeTap.contains(setup.questionTextSnapshot.take(15)))

        Stage12NotificationTapProofSupport.tapNotificationInShade(device, setup.questionTextSnapshot)
        Stage12NotificationTapProofSupport.waitForQuestionText(device, setup.questionTextSnapshot)

        val log = Stage12NotificationTapProofSupport.readLogcatSnapshot()
        assertTrue(log.contains("NotificationTap: handleIntent source=INITIAL_INTENT"))
        assertTrue(log.contains("occurrenceId=${setup.occurrenceId}"))
        assertTrue(log.contains("navigationResult=publish"))
        assertTrue(log.contains("validationResult=Open"))

        val runtime = runBlocking { PraktikaRuntimeHolder.get(context) }
        val occurrence = runBlocking {
            runtime.database.questionOccurrenceDao().getById(setup.occurrenceId)!!
        }
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, occurrence.status)
        assertNotNull(occurrence.openedAtEpochMillis)
        Stage12NotificationTapProofSupport.assertNotificationAbsent(context, setup.occurrenceId)

        runBlocking {
            runtime.notificationCoordinator.sync(NotificationSyncReason.FOREGROUND)
        }
        Stage12NotificationTapProofSupport.assertNotificationAbsent(context, setup.occurrenceId)
        Stage12NotificationTapProofSupport.logMarker("scenarioA_green")
    }
}
// 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik END

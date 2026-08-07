// 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik START - stale notification tap E2E
package com.me4hik.praktika

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityStage12NotificationTapStaleE2EInstrumentedTest {
    @Before
    fun resetAndWake() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Stage12NotificationTapProofSupport.resetAcceleratedSandbox(context)
        Stage12NotificationTapProofSupport.killAppProcess()
        Stage12NotificationTapProofSupport.clearLogcat()
        PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
    }

    @Test
    fun scenarioStaleNotificationShadeTapOrPolicyFallback() {
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

        val runtime = runBlocking { PraktikaRuntimeHolder.get(context) }
        val staleId = setup.occurrenceId
        val stalePlannedAt = setup.plannedAtEpochMillis
        val staleQuestion = setup.questionTextSnapshot
        Stage12NotificationTapProofSupport.pauseAcceleratedClock(runtime)

        runBlocking {
            runtime.cycleRepository.skipAvailableByUser(staleId)
        }
        val newIncomplete = runBlocking {
            runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
        }
        assertTrue(newIncomplete.id != staleId)
        val newStatusBefore = newIncomplete.status
        val newOpenedAtBefore = newIncomplete.openedAtEpochMillis

        val shadeTapPossible = Stage12NotificationTapProofSupport.hasActiveNotification(context, staleId)

        if (shadeTapPossible) {
            Stage12NotificationTapProofSupport.logMarker("scenarioC_mode=REAL_SHADE_TAP")
            val shadeTapSucceeded = runCatching {
                Stage12NotificationTapProofSupport.tapNotificationInShade(device, staleQuestion, timeoutMs = 5_000)
            }.isSuccess
            if (shadeTapSucceeded) {
                Thread.sleep(2_000)
                Stage12NotificationTapProofSupport.waitForHomeAnswerButton(device)

                val log = Stage12NotificationTapProofSupport.readLogcatSnapshot()
                assertTrue(log.contains("navigationResult=ignore"))
                assertTrue(log.contains("validationResult=Ignore"))

                val staleQuestionVisible = device.findObject(
                    androidx.test.uiautomator.By.text(staleQuestion),
                ) != null || device.findObject(
                    androidx.test.uiautomator.By.textContains(staleQuestion.take(20)),
                ) != null
                assertTrue("Stale tap must not open interactive Question", !staleQuestionVisible)
            } else {
                Stage12NotificationTapProofSupport.logMarker("scenarioC_mode=STALE_PENDINGINTENT_POLICY_TEST")
                deliverStaleIntentPolicyFallback(context, staleId, stalePlannedAt)
            }
        } else {
            Stage12NotificationTapProofSupport.logMarker("scenarioC_mode=STALE_PENDINGINTENT_POLICY_TEST")
            deliverStaleIntentPolicyFallback(context, staleId, stalePlannedAt)
        }

        val newAfter = runBlocking {
            runtime.database.questionOccurrenceDao().getById(newIncomplete.id)!!
        }
        assertEquals(newStatusBefore, newAfter.status)
        assertEquals(newOpenedAtBefore, newAfter.openedAtEpochMillis)
        Stage12NotificationTapProofSupport.assertNotificationAbsent(context, staleId)
        Stage12NotificationTapProofSupport.logMarker("scenarioC_green")
    }

    private fun deliverStaleIntentPolicyFallback(
        context: android.content.Context,
        staleId: Long,
        stalePlannedAt: Long,
    ) {
        val staleIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NOTIFICATION_OCCURRENCE_ID, staleId)
            putExtra(MainActivity.EXTRA_NOTIFICATION_PLANNED_AT, stalePlannedAt)
            putExtra(MainActivity.EXTRA_NOTIFICATION_SOURCE, MainActivity.NOTIFICATION_SOURCE_VALUE)
        }
        context.startActivity(staleIntent)
        Thread.sleep(2_000)
        Stage12NotificationTapProofSupport.waitForHomeAnswerButton(
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()),
        )

        val log = Stage12NotificationTapProofSupport.readLogcatSnapshot()
        assertTrue(log.contains("navigationResult=ignore"))
    }
}
// 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik END

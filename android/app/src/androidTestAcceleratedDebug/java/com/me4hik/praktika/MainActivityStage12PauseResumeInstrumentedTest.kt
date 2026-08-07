// 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik START - pause/resume scenario
// 06.08.2026 Stage 12 Final Device Recovery cursor by Me4Hik START - repository setup without compose onboarding hang
package com.me4hik.praktika

import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.notification.AndroidPracticeNotificationPresenter
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import java.io.FileInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityStage12PauseResumeInstrumentedTest {
    @Before
    fun resetSandbox() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Stage12FinalAcceptanceSupport.resetAcceleratedSandbox(context)
        Stage12BlackviewE2ESupport.grantPostNotificationsViaShell()
        PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
    }

    @Test
    fun pauseCancelsAlarmsAndNotificationThenResumeRestoresSameOccurrence() {
        Stage12FinalAcceptanceSupport.assumeOptIn()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = runBlocking {
            val resolved = PraktikaRuntimeHolder.get(context)
            check(resolved.initializer.ensureInitialized()) { "Runtime initialization failed" }
            resolved
        }
        Stage10BlackviewE2ESupport.startPractice(runtime)
        Stage10BlackviewE2ESupport.stepEvent(runtime)
        val occurrenceId = runBlocking {
            runtime.database.questionOccurrenceDao().getIncompleteOrdered().single().id
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            runBlocking {
                runtime.notificationCoordinator.sync(com.me4hik.praktika.notification.NotificationSyncReason.FOREGROUND)
            }
            assertTrue(hasActiveNotification(context, occurrenceId))

            Stage10BlackviewE2ESupport.sendAcceleratedCommand("pause_practice")
            Stage10BlackviewE2ESupport.awaitAcceleratedCommand(runtime) {
                runtime.database.practiceStateDao().get()?.isPaused == true
            }
            val alarmAfterPause = readAlarmDump()
            assertFalse(hasActiveNotification(context, occurrenceId))
            assertTrue(
                !alarmAfterPause.contains("PLANNED_BOUNDARY") ||
                    !alarmAfterPause.contains(context.packageName) ||
                    alarmAfterPause.contains("Canceled"),
            )

            val pausedOccurrence = runBlocking {
                runtime.database.questionOccurrenceDao().getById(occurrenceId)!!
            }
            assertEquals(QuestionOccurrenceStatus.AVAILABLE, pausedOccurrence.status)

            Stage10BlackviewE2ESupport.sendAcceleratedCommand("resume_practice")
            Stage10BlackviewE2ESupport.awaitAcceleratedCommand(runtime) {
                runtime.database.practiceStateDao().get()?.isPaused == false
            }
        }
        val resumed = runBlocking {
            runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
        }
        assertEquals(occurrenceId, resumed.id)
        assertEquals(1, runBlocking { runtime.database.questionOccurrenceDao().getIncompleteOrdered().size })
    }

    private fun hasActiveNotification(context: Context, occurrenceId: Long): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.any {
            it.tag == AndroidPracticeNotificationPresenter.NOTIFICATION_TAG &&
                it.id == AndroidPracticeNotificationPresenter.notificationId(occurrenceId)
        }
    }

    private fun readAlarmDump(): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pipe = instrumentation.uiAutomation.executeShellCommand("dumpsys alarm")
        return FileInputStream(pipe.fileDescriptor).bufferedReader().use { it.readText() }.also { pipe.close() }
    }
}
// 06.08.2026 Stage 12 Final Device Recovery cursor by Me4Hik END
// 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik END

// 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik START - permission denial scenario
// 06.08.2026 Stage 12 Final Device Recovery cursor by Me4Hik START - UiAutomator flow avoids compose hang and revoke kill
package com.me4hik.praktika

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import java.io.FileInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityStage12PermissionDenialInstrumentedTest {
    @Before
    fun resetSandbox() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Stage12FinalAcceptanceSupport.resetAcceleratedSandbox(context)
        Stage12BlackviewE2ESupport.clearNotificationPermissionFlag()
        PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
    }

    @Test
    fun permissionDenialKeepsPracticeRunningWithoutAutoPromptOnRestart() {
        Stage12FinalAcceptanceSupport.assumeOptIn()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val runtime = runBlocking {
            val resolved = PraktikaRuntimeHolder.get(context)
            check(resolved.initializer.ensureInitialized()) { "Runtime initialization failed" }
            resolved
        }
        Stage10BlackviewE2ESupport.startPractice(runtime)
        runBlocking {
            runtime.notificationCoordinator.sync(com.me4hik.praktika.notification.NotificationSyncReason.APP_START)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            device.waitForIdle()
            if (device.findObject(By.text("Разрешить уведомления")) != null) {
                tapText(device, "Разрешить уведомления")
                Stage12FinalAcceptanceSupport.tapDenyNotificationPermissionDialog()
                device.waitForIdle()
            }
            waitForText(device, "Уведомления выключены")

            val occurrenceBefore = runBlocking {
                runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
            }
            val cycleBefore = runBlocking { runtime.database.practiceStateDao().get()!!.currentCycleNumber }

            val alarmDump = readAlarmDump()
            assertTrue(
                !alarmDump.contains("PLANNED_BOUNDARY") ||
                    !alarmDump.contains(context.packageName) ||
                    alarmDump.contains("Canceled"),
            )

            scenario.recreate()
            device.waitForIdle()
            waitForText(device, "Уведомления выключены")
            waitForText(device, "Следующий вопрос:")

            val occurrenceAfter = runBlocking {
                runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
            }
            assertEquals(occurrenceBefore.id, occurrenceAfter.id)
            assertEquals(occurrenceBefore.status, occurrenceAfter.status)
            assertEquals(cycleBefore, runBlocking { runtime.database.practiceStateDao().get()!!.currentCycleNumber })
            assertEquals(QuestionOccurrenceStatus.SCHEDULED, occurrenceAfter.status)
        }
    }

    private fun waitForText(device: UiDevice, text: String) {
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            if (device.findObject(By.text(text)) != null) {
                return
            }
            device.waitForIdle()
            Thread.sleep(250)
        }
        throw AssertionError("Text not found: $text")
    }

    private fun tapText(device: UiDevice, text: String) {
        val node = device.findObject(By.text(text))
            ?: throw AssertionError("Tap target not found: $text")
        node.click()
        device.waitForIdle()
    }

    private fun readAlarmDump(): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pipe = instrumentation.uiAutomation.executeShellCommand("dumpsys alarm")
        return FileInputStream(pipe.fileDescriptor).bufferedReader().use { it.readText() }.also { pipe.close() }
    }
}
// 06.08.2026 Stage 12 Final Device Recovery cursor by Me4Hik END
// 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik END

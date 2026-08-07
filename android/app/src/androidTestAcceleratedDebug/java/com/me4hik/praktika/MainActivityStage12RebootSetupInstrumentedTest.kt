// 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik START - reboot recovery setup
package com.me4hik.praktika

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityStage12RebootSetupInstrumentedTest {
    @Before
    fun resetSandbox() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Stage12FinalAcceptanceSupport.resetAcceleratedSandbox(context)
        Stage12BlackviewE2ESupport.grantPostNotificationsViaShell()
        PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
    }

    @Test
    fun prepareRebootRecoveryState() {
        Stage12FinalAcceptanceSupport.assumeOptIn()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = runBlocking { PraktikaRuntimeHolder.get(context) }
        runBlocking {
            runtime.initializer.ensureInitialized()
            runtime.cycleRepository.startPractice()
            runtime.notificationCoordinator.sync(com.me4hik.praktika.notification.NotificationSyncReason.APP_START)
        }
        val occurrence = runBlocking {
            runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
        }
        val practice = runBlocking { runtime.database.practiceStateDao().get()!! }
        assertEquals(true, practice.isPracticeStarted)
        Stage12FinalAcceptanceSupport.writeRebootState(context, occurrence.id, practice.currentCycleNumber)
        Stage12BlackviewE2ESupport.dumpsysAlarmContainsPackage()
    }
}
// 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik END

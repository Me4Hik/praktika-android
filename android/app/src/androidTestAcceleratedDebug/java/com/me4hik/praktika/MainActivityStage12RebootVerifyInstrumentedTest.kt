// 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik START - reboot recovery verify
package com.me4hik.praktika

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityStage12RebootVerifyInstrumentedTest {
    @Before
    fun wakeDevice() {
        PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
    }

    @Test
    fun verifyRebootRecoveryPreservesPracticeAndAlarms() {
        Stage12FinalAcceptanceSupport.assumeOptIn()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val (expectedOccurrenceId, expectedCycle) = Stage12FinalAcceptanceSupport.readRebootState(context)
        val runtime = runBlocking { PraktikaRuntimeHolder.get(context) }
        runBlocking {
            runtime.initializer.ensureInitialized()
            runtime.notificationCoordinator.sync(com.me4hik.praktika.notification.NotificationSyncReason.BOOT)
        }
        val practice = runBlocking { runtime.database.practiceStateDao().get()!! }
        assertTrue(practice.isPracticeStarted)
        assertEquals(expectedCycle, practice.currentCycleNumber)
        val occurrence = runBlocking {
            runtime.database.questionOccurrenceDao().getById(expectedOccurrenceId)!!
        }
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, occurrence.status)
        assertEquals(1, runBlocking { runtime.database.questionOccurrenceDao().getIncompleteOrdered().size })
        Stage12BlackviewE2ESupport.dumpsysAlarmContainsPackage()
    }
}
// 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik END

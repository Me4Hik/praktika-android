// 06.08.2026 Stage 10 Blackview E2E cursor by Me4Hik START - accelerated E2E helpers
package com.me4hik.praktika

import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.accelerated.AcceleratedTimeProvider
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.runtime.PraktikaRuntime
import com.me4hik.praktika.ui.settings.formatTimeOfDayMinutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

object Stage10BlackviewE2ESupport {
    private const val RECEIVER =
        "com.me4hik.praktika.accelerated/com.me4hik.praktika.AcceleratedCommandReceiver"
    private const val ACTION = "com.me4hik.praktika.accelerated.ACCELERATED_COMMAND"

    fun sendAcceleratedCommand(command: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand(
            "am broadcast -n $RECEIVER -a $ACTION --es command $command",
        ).close()
    }

    fun awaitAcceleratedCommand(
        runtime: PraktikaRuntime,
        timeoutMillis: Long = 10_000,
        predicate: suspend () -> Boolean,
    ) {
        runBlocking {
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (System.currentTimeMillis() < deadline) {
                if (predicate()) {
                    return@runBlocking
                }
                delay(100)
            }
            throw AssertionError("Accelerated command predicate timed out after ${timeoutMillis}ms")
        }
    }

    fun startPractice(runtime: PraktikaRuntime) {
        sendAcceleratedCommand("start_practice")
        awaitAcceleratedCommand(runtime) {
            runtime.database.practiceStateDao().get()?.isPracticeStarted == true
        }
    }

    fun stepEvent(runtime: PraktikaRuntime) {
        sendAcceleratedCommand("step_event")
        awaitAcceleratedCommand(runtime) {
            val incomplete = runtime.database.questionOccurrenceDao().getIncompleteOrdered()
            incomplete.size == 1 && incomplete.first().status == QuestionOccurrenceStatus.AVAILABLE
        }
    }

    suspend fun assertSlotMinutes(
        runtime: PraktikaRuntime,
        first: Int,
        second: Int,
        third: Int,
    ) {
        val byIndex = runtime.database.scheduleSlotDao().getAllOrderedByTime().associateBy { it.slotIndex }
        assertEquals(first, byIndex.getValue(1).timeOfDayMinutes)
        assertEquals(second, byIndex.getValue(2).timeOfDayMinutes)
        assertEquals(third, byIndex.getValue(3).timeOfDayMinutes)
    }

    suspend fun assertDefaultInitialState(runtime: PraktikaRuntime) {
        assertSlotMinutes(runtime, 660, 900, 1140)
        val practice = runtime.database.practiceStateDao().get()
        assertNotNull(practice)
        assertTrue(practice!!.isPracticeStarted)
        assertEquals(false, practice.isPaused)
        val incomplete = runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
        assertEquals(1, incomplete.cyclePosition)
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, incomplete.status)
        assertEquals(0, runtime.database.answerDao().count())
    }

    fun formatTime(minutes: Int): String = formatTimeOfDayMinutes(minutes)

    suspend fun incompleteOccurrence(runtime: PraktikaRuntime): QuestionOccurrenceEntity {
        return runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
    }

    suspend fun assertQuickCheckOk(runtime: PraktikaRuntime) {
        val db = runtime.database.openHelper.writableDatabase
        db.query("PRAGMA quick_check").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ok", cursor.getString(0))
        }
        db.query("PRAGMA user_version").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
    }

    suspend fun advanceVirtualMinutes(runtime: PraktikaRuntime, minutes: Long) {
        val clock = runtime.timeProvider as AcceleratedTimeProvider
        clock.advanceByVirtualMinutes(minutes)
        runtime.cycleRepository.reconcile()
        clock.checkpoint()
    }

    suspend fun resumeNowEpochMillis(runtime: PraktikaRuntime): Long {
        val clock = runtime.timeProvider as AcceleratedTimeProvider
        return clock.currentVirtualNow()
    }
}
// 06.08.2026 Stage 10 Blackview E2E cursor by Me4Hik END

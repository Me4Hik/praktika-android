// 06.08.2026 Stage 13 Vertical E2E cursor by Me4Hik START - opt-in gate and scenario helpers
package com.me4hik.praktika

import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.notification.AndroidAlarmScheduler
import com.me4hik.praktika.runtime.PraktikaRuntime
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue

object Stage13VerticalE2ESupport {
    const val OPT_IN_ARGUMENT = "stage13_vertical_e2e"
    const val STATE_FILE_NAME = "stage13_vertical_e2e_state.json"
    const val TAG = "Stage13VerticalE2E"

    fun isOptIn(): Boolean {
        return InstrumentationRegistry.getArguments()
            .getString(OPT_IN_ARGUMENT)
            .toBoolean()
    }

    fun assumeOptIn() {
        assumeTrue(
            "Stage 13 vertical E2E runs only through scripts/stage13_vertical_e2e.ps1",
            isOptIn(),
        )
    }

    fun resetSandbox(context: Context) {
        Stage12FinalAcceptanceSupport.resetAcceleratedSandbox(context)
        Stage12BlackviewE2ESupport.clearNotificationPermissionFlag()
        File(context.filesDir, STATE_FILE_NAME).delete()
    }

    fun wrapMinutes(minutes: Int): Int {
        var value = minutes
        while (value >= 24 * 60) {
            value -= 24 * 60
        }
        while (value < 0) {
            value += 24 * 60
        }
        return value
    }

    fun scheduleSlotTriplet(firstSlotMinutes: Int): Triple<Int, Int, Int> {
        val second = wrapMinutes(firstSlotMinutes + 60)
        var third = wrapMinutes(second + 60)
        if (third == firstSlotMinutes || third == second) {
            third = wrapMinutes(third + 60)
        }
        return Triple(firstSlotMinutes, second, third)
    }

    fun writeState(context: Context, phase: String, fields: Map<String, Any?> = emptyMap()) {
        val json = JSONObject()
        json.put("phase", phase)
        json.put("timestampEpochMillis", System.currentTimeMillis())
        fields.forEach { (key, value) ->
            json.put(key, value)
        }
        File(context.filesDir, STATE_FILE_NAME).writeText(json.toString())
        logMarker("state phase=$phase fields=$fields")
    }

    fun logMarker(message: String) {
        Log.i(TAG, message)
    }

    suspend fun waitForBackgroundNotificationDelivery(
        runtime: PraktikaRuntime,
        occurrenceId: Long,
        plannedAtEpochMillis: Long,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val windowLength = AndroidAlarmScheduler.WINDOW_LENGTH_MILLIS
        val latestExpectedDelivery = plannedAtEpochMillis + windowLength
        val testDeadline = latestExpectedDelivery + TimeUnit.MINUTES.toMillis(3)
        var delivered = false
        while (System.currentTimeMillis() < testDeadline) {
            Stage12MilestoneDiagnostics.recordReceiverDeliveryFromLogcat()
            val occurrence = runtime.database.questionOccurrenceDao().getById(occurrenceId)
            if (occurrence?.status == QuestionOccurrenceStatus.AVAILABLE &&
                Stage12NotificationTapProofSupport.hasActiveNotification(context, occurrenceId)
            ) {
                delivered = true
                break
            }
            delay(5_000)
        }
        assertTrue(
            "Occurrence $occurrenceId did not become AVAILABLE with active notification " +
                "before deadline $testDeadline (plannedAt=$plannedAtEpochMillis)",
            delivered,
        )
    }

    suspend fun assertFinalDbState(runtime: PraktikaRuntime) {
        val practiceState = runtime.database.practiceStateDao().get()!!
        assertTrue(practiceState.isPracticeStarted)
        assertFalse(practiceState.isPaused)

        val answers = runtime.database.answerDao().getAllOrderedByCreatedAt()
        assertEquals(1, answers.size)

        val cycleOne = runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.ANSWERED, cycleOne.status)
        assertEquals(1, runtime.database.questionOccurrenceDao().countByStatus(QuestionOccurrenceStatus.SKIPPED_BY_USER))
        val skipped = runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 2)!!
        assertEquals(QuestionOccurrenceStatus.SKIPPED_BY_USER, skipped.status)
        assertNotNull(cycleOne.completedAtEpochMillis)
        assertNotNull(skipped.completedAtEpochMillis)
        assertEquals(answers.single().occurrenceId, cycleOne.id)

        val incomplete = runtime.database.questionOccurrenceDao().getIncompleteOrdered()
        assertEquals(1, incomplete.size)
        assertTrue(incomplete.single().cyclePosition > skipped.cyclePosition)

        val duplicateIncomplete = incomplete.groupBy { it.cyclePosition }.filter { it.value.size > 1 }
        assertTrue("Duplicate incomplete occurrences: $duplicateIncomplete", duplicateIncomplete.isEmpty())

        val duplicateAnswers = answers.groupBy { it.occurrenceId }.filter { it.value.size > 1 }
        assertTrue("Duplicate answers: $duplicateAnswers", duplicateAnswers.isEmpty())

        Stage10BlackviewE2ESupport.assertQuickCheckOk(runtime)
        logMarker(
            "final_db_state answers=${answers.size} pos1=${cycleOne.status} " +
                "pos2=${skipped.status} currentPos=${incomplete.single().cyclePosition}",
        )
    }
}
// 06.08.2026 Stage 13 Vertical E2E cursor by Me4Hik END

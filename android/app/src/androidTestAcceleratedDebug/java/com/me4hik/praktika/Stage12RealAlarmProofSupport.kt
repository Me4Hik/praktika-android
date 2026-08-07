// 06.08.2026 Stage 12 Real Alarm Proof cursor by Me4Hik START - OS alarm delivery proof helpers
package com.me4hik.praktika

import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.accelerated.AcceleratedTimeProvider
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.data.cycle.ScheduleSlotUpdate
import com.me4hik.praktika.data.cycle.ScheduleUpdateResult
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.notification.AndroidAlarmScheduler
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.runtime.PraktikaRuntime
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeFactory
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue

object Stage12RealAlarmProofSupport {
    const val TAG = "Stage12RealAlarmProof"
    const val OPT_IN_ARGUMENT = "stage12_real_alarm_delivery"
    const val SETUP_STATE_FILE_NAME = "stage12_real_alarm_setup.json"
    const val WALL_SYNC_SPEED_MULTIPLIER = 1
    private const val MAX_CLOCK_SKEW_MILLIS = 5_000L

    fun isOptIn(): Boolean {
        return InstrumentationRegistry.getArguments()
            .getString(OPT_IN_ARGUMENT)
            .toBoolean()
    }

    fun assumeOptIn() {
        assumeTrue(
            "Real OS alarm delivery runs only through scripts/stage12_real_alarm_delivery_e2e.ps1",
            isOptIn(),
        )
    }

    fun resetAcceleratedSandbox(context: Context) {
        MainActivityInitGate.resetForTests()
        PraktikaRuntimeHolder.resetForTests()
        context.filesDir.listFiles()
            ?.filter { it.name == AcceleratedTimeStorage.STATE_FILE_NAME }
            ?.forEach { it.delete() }
        context.getDatabasePath(RuntimeFactory.ACCELERATED_DATABASE_NAME).delete()
        File(context.filesDir, SETUP_STATE_FILE_NAME).delete()
    }

    fun prepareWallSyncClock1x(runtime: PraktikaRuntime) {
        val clock = runtime.timeProvider as AcceleratedTimeProvider
        val wallNow = System.currentTimeMillis()
        if (clock.currentState().isVirtualClockPaused) {
            clock.resumeVirtualClock()
        }
        val virtualNow = clock.currentVirtualNow()
        if (wallNow > virtualNow) {
            clock.advanceToVirtualEpochMillis(wallNow)
        }
        clock.setSpeedMultiplier(WALL_SYNC_SPEED_MULTIPLIER)
        clock.checkpoint()
        val skew = kotlin.math.abs(wallNow - clock.currentVirtualNow())
        assertTrue("Clock skew $skew exceeds $MAX_CLOCK_SKEW_MILLIS ms", skew <= MAX_CLOCK_SKEW_MILLIS)
    }

    fun slotMinutesTwoToThreeAhead(runtime: PraktikaRuntime): Int {
        val zone = ZoneId.of(runtime.timeProvider.currentZoneId())
        val acceleratedNow = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(runtime.timeProvider.nowEpochMillis()),
            zone,
        )
        val nowMinutes = acceleratedNow.hour * 60 + acceleratedNow.minute
        var slotMinutes = ((nowMinutes + 2 + 4) / 5) * 5
        if (slotMinutes <= nowMinutes + 1) {
            slotMinutes += 5
        }
        if (slotMinutes >= 24 * 60) {
            slotMinutes -= 24 * 60
        }
        return slotMinutes
    }

    fun scheduleUpdatesForRealAlarmProof(firstSlotMinutes: Int): List<ScheduleSlotUpdate> {
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
        val second = wrapMinutes(firstSlotMinutes + 60)
        var third = wrapMinutes(second + 60)
        if (third == firstSlotMinutes || third == second) {
            third = wrapMinutes(third + 60)
        }
        return listOf(
            ScheduleSlotUpdate(slotIndex = 1, timeOfDayMinutes = firstSlotMinutes),
            ScheduleSlotUpdate(slotIndex = 2, timeOfDayMinutes = second),
            ScheduleSlotUpdate(slotIndex = 3, timeOfDayMinutes = third),
        )
    }

    fun dumpsysAlarmText(): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pfd = instrumentation.uiAutomation.executeShellCommand("dumpsys alarm")
        return try {
            FileInputStream(pfd.fileDescriptor).bufferedReader().readText()
        } finally {
            pfd.close()
        }
    }

    data class SetupState(
        val occurrenceId: Long,
        val plannedAtEpochMillis: Long,
        val availableUntilEpochMillis: Long,
        val alarmTriggerAtEpochMillis: Long,
        val alarmWindowEndEpochMillis: Long,
        val deadlineEpochMillis: Long,
        val systemWallNowAtSetup: Long,
        val acceleratedNowAtSetup: Long,
        val clockDifferenceMs: Long,
        val speedMultiplier: Int,
        val questionTextSnapshot: String,
        val packageName: String,
    )

    fun writeSetupState(context: Context, state: SetupState) {
        val json = JSONObject()
        json.put("occurrenceId", state.occurrenceId)
        json.put("plannedAtEpochMillis", state.plannedAtEpochMillis)
        json.put("availableUntilEpochMillis", state.availableUntilEpochMillis)
        json.put("alarmTriggerAtEpochMillis", state.alarmTriggerAtEpochMillis)
        json.put("alarmWindowEndEpochMillis", state.alarmWindowEndEpochMillis)
        json.put("deadlineEpochMillis", state.deadlineEpochMillis)
        json.put("systemWallNowAtSetup", state.systemWallNowAtSetup)
        json.put("acceleratedNowAtSetup", state.acceleratedNowAtSetup)
        json.put("clockDifferenceMs", state.clockDifferenceMs)
        json.put("speedMultiplier", state.speedMultiplier)
        json.put("questionTextSnapshot", state.questionTextSnapshot)
        json.put("packageName", state.packageName)
        val target = File(context.filesDir, SETUP_STATE_FILE_NAME)
        target.writeText(json.toString())
        Log.i(TAG, "setupStateWritten path=${target.absolutePath} json=$json")
    }

    suspend fun runSetupPhase(context: Context): SetupState {
        resetAcceleratedSandbox(context)
        val runtime = PraktikaRuntimeHolder.get(context)
        check(runtime.initializer.ensureInitialized()) { "Runtime initialization failed" }
        prepareWallSyncClock1x(runtime)

        val slotMinutes = slotMinutesTwoToThreeAhead(runtime)
        val updateResult = runtime.cycleRepository.updateSchedule(
            scheduleUpdatesForRealAlarmProof(slotMinutes),
        )
        assertEquals(ScheduleUpdateResult.Success, updateResult)

        runtime.cycleRepository.startPractice()
        runtime.notificationCoordinator.sync(NotificationSyncReason.PERMISSION_CHANGED)
        runtime.notificationCoordinator.sync(NotificationSyncReason.APP_START)

        val occurrence = runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, occurrence.status)
        assertTrue(occurrence.plannedAtEpochMillis > System.currentTimeMillis())
        assertTrue(occurrence.availableUntilEpochMillis > occurrence.plannedAtEpochMillis)

        val alarmDump = dumpsysAlarmText()
        assertTrue("Alarm dump missing occurrence id", alarmDump.contains(occurrence.id.toString()))
        assertTrue("Alarm dump missing package", alarmDump.contains(context.packageName))
        assertTrue("Alarm dump missing PLANNED boundary", alarmDump.contains("PLANNED_BOUNDARY", ignoreCase = true) ||
            alarmDump.contains("planned_boundary", ignoreCase = true) ||
            alarmDump.contains(occurrence.plannedAtEpochMillis.toString()))

        val wallNow = System.currentTimeMillis()
        val acceleratedNow = runtime.timeProvider.nowEpochMillis()
        val windowLength = AndroidAlarmScheduler.WINDOW_LENGTH_MILLIS
        val alarmWindowEnd = occurrence.plannedAtEpochMillis + windowLength
        val deadline = alarmWindowEnd + TimeUnit.MINUTES.toMillis(3)

        return SetupState(
            occurrenceId = occurrence.id,
            plannedAtEpochMillis = occurrence.plannedAtEpochMillis,
            availableUntilEpochMillis = occurrence.availableUntilEpochMillis,
            alarmTriggerAtEpochMillis = occurrence.plannedAtEpochMillis,
            alarmWindowEndEpochMillis = alarmWindowEnd,
            deadlineEpochMillis = deadline,
            systemWallNowAtSetup = wallNow,
            acceleratedNowAtSetup = acceleratedNow,
            clockDifferenceMs = wallNow - acceleratedNow,
            speedMultiplier = WALL_SYNC_SPEED_MULTIPLIER,
            questionTextSnapshot = occurrence.questionTextSnapshot,
            packageName = context.packageName,
        ).also { writeSetupState(context, it) }
    }

    fun runSetupBlocking(context: Context): SetupState = runBlocking { runSetupPhase(context) }
}
// 06.08.2026 Stage 12 Real Alarm Proof cursor by Me4Hik END

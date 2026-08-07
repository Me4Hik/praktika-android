// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - diagnostics dump
package com.me4hik.praktika.accelerated

import android.content.Context
import android.util.Log
import com.me4hik.praktika.runtime.PraktikaRuntime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking

object AcceleratedDiagnostics {
    private const val TAG = "AcceleratedDiag"
    private val readableFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z")

    fun dump(
        context: Context,
        runtime: PraktikaRuntime,
        monotonic: MonotonicTimeSource,
        clockController: AcceleratedClockController,
    ) {
        val clockState = clockController.currentState()
        val virtualNow = clockController.currentVirtualNow()
        val zone = ZoneId.of(clockState.zoneId)
        val practiceState = runBlocking { runtime.database.practiceStateDao().get() }
        val occurrence = runBlocking { runtime.database.questionOccurrenceDao().getIncompleteOrdered().firstOrNull() }
        val questionsCount = runBlocking { runtime.database.questionDao().count() }
        val slotsCount = runBlocking { runtime.database.scheduleSlotDao().count() }
        val occurrencesCount = runBlocking { runtime.database.questionOccurrenceDao().count() }
        val answersCount = runBlocking { runtime.database.answerDao().count() }

        val lines = buildList {
            add("runtimeMode=${runtime.mode}")
            add("applicationId=${context.packageName}")
            add("databaseName=${runtime.databaseName}")
            add("realWallClock=${monotonic.wallClockEpochMillis()} (${formatEpoch(monotonic.wallClockEpochMillis(), zone)})")
            add("elapsedRealtime=${monotonic.elapsedRealtimeMillis()}")
            add("bootCount=${monotonic.bootCount()}")
            add("virtualNow=$virtualNow (${formatEpoch(virtualNow, zone)})")
            add("virtualZoneId=${clockState.zoneId}")
            add("speedMultiplier=${clockState.speedMultiplier}")
            add("isVirtualClockPaused=${clockState.isVirtualClockPaused}")
            add("isPracticeStarted=${practiceState?.isPracticeStarted ?: false}")
            add("isPracticePaused=${practiceState?.isPaused ?: false}")
            add("currentCycleNumber=${practiceState?.currentCycleNumber ?: 0}")
            add("nextCyclePosition=${practiceState?.nextCyclePosition ?: 1}")
            add("lastProcessedAt=${practiceState?.lastProcessedAtEpochMillis}")
            add("currentOccurrenceId=${occurrence?.id}")
            add("questionId=${occurrence?.questionId}")
            add("cycleNumber=${occurrence?.cycleNumber}")
            add("cyclePosition=${occurrence?.cyclePosition}")
            add("status=${occurrence?.status}")
            add("plannedAt=${occurrence?.plannedAtEpochMillis}")
            add("availableUntil=${occurrence?.availableUntilEpochMillis}")
            add("completedAt=${occurrence?.completedAtEpochMillis}")
            add("questionsCount=$questionsCount")
            add("slotsCount=$slotsCount")
            add("occurrencesCount=$occurrencesCount")
            add("answersCount=$answersCount")
        }

        lines.forEach { line -> Log.i(TAG, line) }
    }

    private fun formatEpoch(epochMillis: Long, zone: ZoneId): String {
        return readableFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
    }
}
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END

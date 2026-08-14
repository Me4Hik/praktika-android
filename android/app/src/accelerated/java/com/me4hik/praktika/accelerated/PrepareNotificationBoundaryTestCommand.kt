// 14.08.2026 Accelerated device-setup harness cursor by Me4Hik START
package com.me4hik.praktika.accelerated

import android.util.Log
import com.me4hik.praktika.data.cycle.ScheduleCalculator
import com.me4hik.praktika.data.cycle.ScheduleSlotUpdate
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Accelerated-only: derive a valid 3-slot schedule so the next occurrence after
 * start_practice is semantic T ≈ now + 3–4 minutes. Does not start practice,
 * insert occurrences, or call AlarmManager. Host extras are ignored.
 */
object PrepareNotificationBoundaryTestCommand {
    const val COMMAND = "prepare_notification_boundary_test"
    const val REQUIRED_PACKAGE = "com.me4hik.praktika.accelerated"
    const val WALL_SYNC_MAX_SKEW_MS = WallSync1xCommand.WALL_SYNC_MAX_SKEW_MS
    const val MINUTES_PER_DAY = 1440
    const val SLOT_GAP_MINUTES = 60
    const val LEAD_MINUTES_AFTER_NEXT_MINUTE = 3L

    const val STATUS_BOUNDARY_TEST_PREPARED = "BOUNDARY_TEST_PREPARED"
    const val STATUS_REJECTED_WRONG_PACKAGE = "REJECTED_WRONG_PACKAGE"
    const val STATUS_REJECTED_WALL_SYNC_REQUIRED = "REJECTED_WALL_SYNC_REQUIRED"
    const val STATUS_REJECTED_PRACTICE_ALREADY_STARTED = "REJECTED_PRACTICE_ALREADY_STARTED"
    const val STATUS_REJECTED_EXISTING_OCCURRENCES = "REJECTED_EXISTING_OCCURRENCES"
    const val STATUS_REJECTED_IMPOSSIBLE_LOCAL_TIME = "REJECTED_IMPOSSIBLE_LOCAL_TIME"
    const val STATUS_REJECTED_START_SLOT_MISMATCH = "REJECTED_START_SLOT_MISMATCH"

    data class CommandResult(
        val status: String,
        val wallNowMs: Long,
        val virtualNowMs: Long,
        val skewMs: Long,
        val zoneId: String? = null,
        val slot1Minutes: Int? = null,
        val slot2Minutes: Int? = null,
        val slot3Minutes: Int? = null,
        val targetPlannedAtMs: Long? = null,
        val targetLocalTime: String? = null,
        val targetLeadMs: Long? = null,
        val scheduleUpdates: List<ScheduleSlotUpdate> = emptyList(),
    )

    fun evaluate(
        packageName: String,
        wallNowMs: Long,
        virtualNowMs: Long,
        speedMultiplier: Int,
        clockRunning: Boolean,
        practiceStarted: Boolean,
        occurrenceCount: Int,
        zoneId: String,
    ): CommandResult {
        val skewMs = virtualNowMs - wallNowMs
        if (packageName != REQUIRED_PACKAGE) {
            return CommandResult(
                status = STATUS_REJECTED_WRONG_PACKAGE,
                wallNowMs = wallNowMs,
                virtualNowMs = virtualNowMs,
                skewMs = skewMs,
                zoneId = zoneId,
            )
        }
        if (speedMultiplier != WallSync1xCommand.TARGET_SPEED ||
            !clockRunning ||
            abs(skewMs) > WALL_SYNC_MAX_SKEW_MS
        ) {
            return CommandResult(
                status = STATUS_REJECTED_WALL_SYNC_REQUIRED,
                wallNowMs = wallNowMs,
                virtualNowMs = virtualNowMs,
                skewMs = skewMs,
                zoneId = zoneId,
            )
        }
        if (practiceStarted) {
            return CommandResult(
                status = STATUS_REJECTED_PRACTICE_ALREADY_STARTED,
                wallNowMs = wallNowMs,
                virtualNowMs = virtualNowMs,
                skewMs = skewMs,
                zoneId = zoneId,
            )
        }
        if (occurrenceCount > 0) {
            return CommandResult(
                status = STATUS_REJECTED_EXISTING_OCCURRENCES,
                wallNowMs = wallNowMs,
                virtualNowMs = virtualNowMs,
                skewMs = skewMs,
                zoneId = zoneId,
            )
        }

        val zone = try {
            ZoneId.of(zoneId)
        } catch (_: Exception) {
            return CommandResult(
                status = STATUS_REJECTED_IMPOSSIBLE_LOCAL_TIME,
                wallNowMs = wallNowMs,
                virtualNowMs = virtualNowMs,
                skewMs = skewMs,
                zoneId = zoneId,
            )
        }

        val tLocal = try {
            val localNow = Instant.ofEpochMilli(virtualNowMs).atZone(zone)
            val nextMinute = localNow.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
            nextMinute.plusMinutes(LEAD_MINUTES_AFTER_NEXT_MINUTE)
        } catch (_: DateTimeException) {
            return CommandResult(
                status = STATUS_REJECTED_IMPOSSIBLE_LOCAL_TIME,
                wallNowMs = wallNowMs,
                virtualNowMs = virtualNowMs,
                skewMs = skewMs,
                zoneId = zoneId,
            )
        }

        val targetPlannedAtMs = tLocal.toInstant().toEpochMilli()
        val slot1 = tLocal.hour * 60 + tLocal.minute
        val slots = uniqueThreeSlots(slot1)
        val updates = listOf(
            ScheduleSlotUpdate(slotIndex = 1, timeOfDayMinutes = slots[0]),
            ScheduleSlotUpdate(slotIndex = 2, timeOfDayMinutes = slots[1]),
            ScheduleSlotUpdate(slotIndex = 3, timeOfDayMinutes = slots[2]),
        )
        val entities = updates.map { ScheduleSlotEntity(it.slotIndex, it.timeOfDayMinutes) }
        val startSlot = try {
            ScheduleCalculator().findStartSlot(virtualNowMs, zoneId, entities)
        } catch (_: Exception) {
            return CommandResult(
                status = STATUS_REJECTED_START_SLOT_MISMATCH,
                wallNowMs = wallNowMs,
                virtualNowMs = virtualNowMs,
                skewMs = skewMs,
                zoneId = zoneId,
                slot1Minutes = slots[0],
                slot2Minutes = slots[1],
                slot3Minutes = slots[2],
                targetPlannedAtMs = targetPlannedAtMs,
            )
        }
        if (startSlot.plannedAtEpochMillis != targetPlannedAtMs) {
            return CommandResult(
                status = STATUS_REJECTED_START_SLOT_MISMATCH,
                wallNowMs = wallNowMs,
                virtualNowMs = virtualNowMs,
                skewMs = skewMs,
                zoneId = zoneId,
                slot1Minutes = slots[0],
                slot2Minutes = slots[1],
                slot3Minutes = slots[2],
                targetPlannedAtMs = targetPlannedAtMs,
                targetLocalTime = LOCAL_TIME_FORMATTER.format(tLocal),
                targetLeadMs = targetPlannedAtMs - virtualNowMs,
            )
        }

        return CommandResult(
            status = STATUS_BOUNDARY_TEST_PREPARED,
            wallNowMs = wallNowMs,
            virtualNowMs = virtualNowMs,
            skewMs = skewMs,
            zoneId = zoneId,
            slot1Minutes = slots[0],
            slot2Minutes = slots[1],
            slot3Minutes = slots[2],
            targetPlannedAtMs = targetPlannedAtMs,
            targetLocalTime = LOCAL_TIME_FORMATTER.format(tLocal),
            targetLeadMs = targetPlannedAtMs - virtualNowMs,
            scheduleUpdates = updates,
        )
    }

    suspend fun execute(
        packageName: String,
        wallNowMs: Long,
        virtualNowMs: Long,
        speedMultiplier: Int,
        clockRunning: Boolean,
        practiceStarted: Boolean,
        occurrenceCount: Int,
        zoneId: String,
        updateSchedule: suspend (List<ScheduleSlotUpdate>) -> Unit,
    ): CommandResult {
        val evaluated = evaluate(
            packageName = packageName,
            wallNowMs = wallNowMs,
            virtualNowMs = virtualNowMs,
            speedMultiplier = speedMultiplier,
            clockRunning = clockRunning,
            practiceStarted = practiceStarted,
            occurrenceCount = occurrenceCount,
            zoneId = zoneId,
        )
        if (evaluated.status != STATUS_BOUNDARY_TEST_PREPARED) {
            logResult(evaluated)
            return evaluated
        }
        updateSchedule(evaluated.scheduleUpdates)
        logResult(evaluated)
        return evaluated
    }

    internal fun uniqueThreeSlots(slot1: Int): List<Int> {
        val first = wrapMinutes(slot1)
        var second = wrapMinutes(first + SLOT_GAP_MINUTES)
        var third = wrapMinutes(first + SLOT_GAP_MINUTES * 2)
        var guard = 0
        while ((second == first || third == first || third == second) && guard < MINUTES_PER_DAY) {
            if (second == first) {
                second = wrapMinutes(second + SLOT_GAP_MINUTES)
            }
            if (third == first || third == second) {
                third = wrapMinutes(third + SLOT_GAP_MINUTES)
            }
            guard++
        }
        return listOf(first, second, third)
    }

    private fun wrapMinutes(minutes: Int): Int {
        var value = minutes % MINUTES_PER_DAY
        if (value < 0) {
            value += MINUTES_PER_DAY
        }
        return value
    }

    private fun logResult(result: CommandResult) {
        val lines = buildList {
            add("command=$COMMAND")
            add("status=${result.status}")
            add("wall_now_ms=${result.wallNowMs}")
            add("virtual_now_ms=${result.virtualNowMs}")
            add("skew_ms=${result.skewMs}")
            result.zoneId?.let { add("zone_id=$it") }
            result.slot1Minutes?.let { add("slot1_minutes=$it") }
            result.slot2Minutes?.let { add("slot2_minutes=$it") }
            result.slot3Minutes?.let { add("slot3_minutes=$it") }
            result.targetPlannedAtMs?.let { add("target_planned_at_ms=$it") }
            result.targetLocalTime?.let { add("target_local_time=$it") }
            result.targetLeadMs?.let { add("target_lead_ms=$it") }
        }
        lines.forEach { Log.i(TAG, it) }
    }

    private val LOCAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
    private const val TAG = "AcceleratedCommand"
}
// 14.08.2026 Accelerated device-setup harness cursor by Me4Hik END

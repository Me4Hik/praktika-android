// 14.08.2026 Accelerated planned-boundary injection harness cursor by Me4Hik START
package com.me4hik.praktika.accelerated

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.notification.BoundaryAlarmPlan
import com.me4hik.praktika.notification.BoundaryEventType
import com.me4hik.praktika.notification.PlannedBoundaryTiming
import com.me4hik.praktika.notification.PracticeAlarmIdentity
import com.me4hik.praktika.notification.PracticeAlarmReceiver

/**
 * Accelerated-only narrow harness: derive the current SCHEDULED occurrence from Room,
 * validate wall-clock injection band, and dispatch one canonical PLANNED_BOUNDARY Intent
 * through the real PracticeAlarmReceiver via Context.sendBroadcast.
 *
 * Does not accept occurrence/time/action identity from the ADB command Intent.
 * Does not mutate Room or call AlarmManager.
 */
object InjectValidPlannedBoundaryCommand {
    const val COMMAND = "inject_valid_planned_boundary"
    const val REQUIRED_PACKAGE = "com.me4hik.praktika.accelerated"

    /** deltaToPlannedMs = wallNow - plannedAt; inclusive band for T−12 device tests. */
    const val INJECTION_BAND_MIN_DELTA_MS = -15_000L
    const val INJECTION_BAND_MAX_DELTA_MS = -8_000L

    const val STATUS_VALID_INTENT_DISPATCHED = "VALID_INTENT_DISPATCHED"
    const val STATUS_REJECTED_WRONG_PACKAGE = "REJECTED_WRONG_PACKAGE"
    const val STATUS_REJECTED_NO_SCHEDULED_OCCURRENCE = "REJECTED_NO_SCHEDULED_OCCURRENCE"
    const val STATUS_REJECTED_AMBIGUOUS_OCCURRENCE = "REJECTED_AMBIGUOUS_OCCURRENCE"
    const val STATUS_REJECTED_OCCURRENCE_NOT_SCHEDULED = "REJECTED_OCCURRENCE_NOT_SCHEDULED"
    const val STATUS_REJECTED_OUTSIDE_INJECTION_BAND = "REJECTED_OUTSIDE_INJECTION_BAND"

    data class IncompleteOccurrence(
        val id: Long,
        val status: QuestionOccurrenceStatus,
        val plannedAtEpochMillis: Long,
    )

    data class CommandResult(
        val status: String,
        val wallNowMs: Long,
        val elapsedRealtimeMs: Long,
        val occurrenceId: Long? = null,
        val occurrenceStatusBefore: QuestionOccurrenceStatus? = null,
        val plannedAtMs: Long? = null,
        val biasedTriggerAtMs: Long? = null,
        val deltaToPlannedMs: Long? = null,
        val intentAction: String? = null,
        val exactAlarmCapability: String? = null,
        val receiverComponent: String? = null,
        val dispatchedIntent: Intent? = null,
    )

    fun evaluate(
        packageName: String,
        wallNowMs: Long,
        elapsedRealtimeMs: Long,
        incomplete: List<IncompleteOccurrence>,
        exactAlarmCapability: String? = null,
    ): CommandResult {
        if (packageName != REQUIRED_PACKAGE) {
            return CommandResult(
                status = STATUS_REJECTED_WRONG_PACKAGE,
                wallNowMs = wallNowMs,
                elapsedRealtimeMs = elapsedRealtimeMs,
                exactAlarmCapability = exactAlarmCapability,
            )
        }
        if (incomplete.isEmpty()) {
            return CommandResult(
                status = STATUS_REJECTED_NO_SCHEDULED_OCCURRENCE,
                wallNowMs = wallNowMs,
                elapsedRealtimeMs = elapsedRealtimeMs,
                exactAlarmCapability = exactAlarmCapability,
            )
        }
        if (incomplete.size > 1) {
            return CommandResult(
                status = STATUS_REJECTED_AMBIGUOUS_OCCURRENCE,
                wallNowMs = wallNowMs,
                elapsedRealtimeMs = elapsedRealtimeMs,
                exactAlarmCapability = exactAlarmCapability,
            )
        }

        val occurrence = incomplete.single()
        if (occurrence.status != QuestionOccurrenceStatus.SCHEDULED) {
            return CommandResult(
                status = STATUS_REJECTED_OCCURRENCE_NOT_SCHEDULED,
                wallNowMs = wallNowMs,
                elapsedRealtimeMs = elapsedRealtimeMs,
                occurrenceId = occurrence.id,
                occurrenceStatusBefore = occurrence.status,
                plannedAtMs = occurrence.plannedAtEpochMillis,
                exactAlarmCapability = exactAlarmCapability,
            )
        }

        val plannedAt = occurrence.plannedAtEpochMillis
        val deltaToPlannedMs = wallNowMs - plannedAt
        if (deltaToPlannedMs < INJECTION_BAND_MIN_DELTA_MS ||
            deltaToPlannedMs > INJECTION_BAND_MAX_DELTA_MS
        ) {
            return CommandResult(
                status = STATUS_REJECTED_OUTSIDE_INJECTION_BAND,
                wallNowMs = wallNowMs,
                elapsedRealtimeMs = elapsedRealtimeMs,
                occurrenceId = occurrence.id,
                occurrenceStatusBefore = occurrence.status,
                plannedAtMs = plannedAt,
                biasedTriggerAtMs = PlannedBoundaryTiming.biasedTriggerAt(plannedAt),
                deltaToPlannedMs = deltaToPlannedMs,
                exactAlarmCapability = exactAlarmCapability,
            )
        }

        val biasedTriggerAt = PlannedBoundaryTiming.biasedTriggerAt(plannedAt)
        val plan = BoundaryAlarmPlan(
            occurrenceId = occurrence.id,
            eventType = BoundaryEventType.PLANNED_BOUNDARY,
            triggerAtEpochMillis = biasedTriggerAt,
            plannedAtEpochMillis = plannedAt,
        )
        return CommandResult(
            status = STATUS_VALID_INTENT_DISPATCHED,
            wallNowMs = wallNowMs,
            elapsedRealtimeMs = elapsedRealtimeMs,
            occurrenceId = occurrence.id,
            occurrenceStatusBefore = occurrence.status,
            plannedAtMs = plannedAt,
            biasedTriggerAtMs = biasedTriggerAt,
            deltaToPlannedMs = deltaToPlannedMs,
            intentAction = PracticeAlarmIdentity.actionFor(plan),
            exactAlarmCapability = exactAlarmCapability,
            receiverComponent = PracticeAlarmReceiver::class.java.name,
        )
    }

    fun buildCanonicalIntent(context: Context, ready: CommandResult): Intent {
        require(ready.status == STATUS_VALID_INTENT_DISPATCHED) {
            "buildCanonicalIntent requires VALID_INTENT_DISPATCHED"
        }
        val occurrenceId = requireNotNull(ready.occurrenceId)
        val plannedAt = requireNotNull(ready.plannedAtMs)
        val biasedTriggerAt = requireNotNull(ready.biasedTriggerAtMs)
        val action = requireNotNull(ready.intentAction)
        return Intent(context, PracticeAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(PracticeAlarmReceiver.EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(PracticeAlarmReceiver.EXTRA_EVENT_TYPE, BoundaryEventType.PLANNED_BOUNDARY.name)
            putExtra(PracticeAlarmReceiver.EXTRA_BOUNDARY_EPOCH_MILLIS, biasedTriggerAt)
            putExtra(PracticeAlarmReceiver.EXTRA_PLANNED_AT_EPOCH_MILLIS, plannedAt)
        }
    }

    /**
     * Evaluate + optionally dispatch. Identity comes only from [incomplete];
     * the ADB command Intent must not supply occurrence/time/action fields here.
     */
    fun execute(
        context: Context,
        incomplete: List<IncompleteOccurrence>,
        exactAlarmCapability: String? = null,
        packageName: String = context.packageName,
        wallNowMs: Long = System.currentTimeMillis(),
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime(),
        sendBroadcast: (Intent) -> Unit = { intent -> context.sendBroadcast(intent) },
    ): CommandResult {
        val evaluated = evaluate(
            packageName = packageName,
            wallNowMs = wallNowMs,
            elapsedRealtimeMs = elapsedRealtimeMs,
            incomplete = incomplete,
            exactAlarmCapability = exactAlarmCapability,
        )
        if (evaluated.status != STATUS_VALID_INTENT_DISPATCHED) {
            logResult(evaluated)
            return evaluated
        }
        val intent = buildCanonicalIntent(context, evaluated)
        sendBroadcast(intent)
        val dispatched = evaluated.copy(
            dispatchedIntent = intent,
            receiverComponent = intent.component?.className
                ?: PracticeAlarmReceiver::class.java.name,
        )
        logResult(dispatched)
        return dispatched
    }

    private fun logResult(result: CommandResult) {
        val lines = buildList {
            add("command=$COMMAND")
            add("status=${result.status}")
            add("wall_now_ms=${result.wallNowMs}")
            add("elapsed_realtime_ms=${result.elapsedRealtimeMs}")
            result.occurrenceId?.let { add("occurrence_id=$it") }
            result.occurrenceStatusBefore?.let { add("occurrence_status_before=$it") }
            result.plannedAtMs?.let { add("planned_at_ms=$it") }
            result.biasedTriggerAtMs?.let { add("biased_trigger_at_ms=$it") }
            result.deltaToPlannedMs?.let { add("delta_to_planned_ms=$it") }
            result.intentAction?.let { add("intent_action=$it") }
            result.exactAlarmCapability?.let { add("exact_alarm_capability=$it") }
            result.receiverComponent?.let { add("receiver_component=$it") }
        }
        lines.forEach { Log.i(TAG, it) }
    }

    private const val TAG = "AcceleratedCommand"
}
// 14.08.2026 Accelerated planned-boundary injection harness cursor by Me4Hik END

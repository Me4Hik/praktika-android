// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - ADB command processor
package com.me4hik.praktika.accelerated

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.notification.ExactAlarmCapabilityResolver
import com.me4hik.praktika.runtime.PraktikaRuntime
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder

class AcceleratedCommandProcessor(
    private val context: Context,
) {
    private val monotonic = AndroidMonotonicTimeSource(context)

    suspend fun process(command: String, intent: Intent) {
        Log.i(TAG, "command=$command")
        val runtime = PraktikaRuntimeHolder.get(context)
        val clock = runtime.timeProvider as AcceleratedTimeProvider

        when (command) {
            "dump" -> {
                clock.checkpoint()
                AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
            }

            "set_speed" -> {
                val multiplier = intent.getIntExtra("multiplier", -1)
                clock.setSpeedMultiplier(multiplier)
                clock.checkpoint()
                AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
            }

            "pause_clock" -> {
                clock.pauseVirtualClock()
                clock.checkpoint()
                AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
            }

            "resume_clock" -> {
                clock.resumeVirtualClock()
                clock.checkpoint()
                AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
            }

            "advance_minutes" -> {
                val minutes = intent.getLongExtra("minutes", -1L)
                clock.advanceByVirtualMinutes(minutes)
                clock.checkpoint()
                AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
            }

            "step_event" -> stepEvent(runtime, clock)

            "start_practice" -> {
                runtime.cycleRepository.startPractice()
                clock.checkpoint()
                AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
            }

            "reconcile" -> {
                runtime.cycleRepository.reconcile()
                clock.checkpoint()
                AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
            }

            "pause_practice" -> {
                runtime.cycleRepository.pausePractice()
                clock.checkpoint()
                AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
            }

            "resume_practice" -> {
                runtime.cycleRepository.resumePractice()
                clock.checkpoint()
                AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
            }

            "skip_available" -> {
                runtime.cycleRepository.skipAvailableByUser()
                clock.checkpoint()
                AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
            }

            // 14.08.2026 Accelerated planned-boundary injection harness cursor by Me4Hik START
            InjectValidPlannedBoundaryCommand.COMMAND -> injectValidPlannedBoundary(runtime)
            // 14.08.2026 Accelerated planned-boundary injection harness cursor by Me4Hik END

            // 04.09.2026 Accelerated deferred-reminder inject harness cursor by Me4Hik START
            InjectDeferredReminderCommand.COMMAND -> injectDeferredReminder(runtime)
            // 04.09.2026 Accelerated deferred-reminder inject harness cursor by Me4Hik END

            // 14.08.2026 Accelerated device-setup harness cursor by Me4Hik START
            WallSync1xCommand.COMMAND -> {
                WallSync1xCommand.execute(
                    packageName = context.packageName,
                    readWallMs = { System.currentTimeMillis() },
                    clock = clock,
                )
            }

            PrepareNotificationBoundaryTestCommand.COMMAND -> prepareNotificationBoundaryTest(runtime, clock)
            // 14.08.2026 Accelerated device-setup harness cursor by Me4Hik END

            else -> throw AcceleratedClockCommandException("Unknown command: $command")
        }
    }

    // 14.08.2026 Accelerated planned-boundary injection harness cursor by Me4Hik START
    /**
     * Host/ADB must not supply occurrence identity; only Room + wall clock are used.
     * The command [Intent] extras for identity are intentionally ignored.
     */
    private suspend fun injectValidPlannedBoundary(runtime: PraktikaRuntime) {
        val incomplete = runtime.database.questionOccurrenceDao().getIncompleteOrdered().map {
            InjectValidPlannedBoundaryCommand.IncompleteOccurrence(
                id = it.id,
                status = it.status,
                plannedAtEpochMillis = it.plannedAtEpochMillis,
            )
        }
        InjectValidPlannedBoundaryCommand.execute(
            context = context,
            incomplete = incomplete,
            exactAlarmCapability = readExactAlarmCapabilityLabel(),
        )
    }

    // 04.09.2026 Accelerated deferred-reminder inject harness cursor by Me4Hik START
    /**
     * Host/ADB must not supply occurrence/deferredUntil identity; only Room is used.
     * The command [Intent] extras for identity are intentionally ignored.
     */
    private suspend fun injectDeferredReminder(runtime: PraktikaRuntime) {
        val incomplete = runtime.database.questionOccurrenceDao().getIncompleteOrdered().map {
            InjectDeferredReminderCommand.IncompleteOccurrence(
                id = it.id,
                status = it.status,
                plannedAtEpochMillis = it.plannedAtEpochMillis,
                deferredUntilEpochMillis = it.deferredUntilEpochMillis,
            )
        }
        InjectDeferredReminderCommand.execute(
            context = context,
            incomplete = incomplete,
        )
        // Dump after dispatch so device acceptance can correlate Room/notif with inject logs.
        // Receiver sync is async (goAsync); dump is best-effort snapshot, not a completion barrier.
        val clock = runtime.timeProvider as AcceleratedTimeProvider
        clock.checkpoint()
        AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
    }
    // 04.09.2026 Accelerated deferred-reminder inject harness cursor by Me4Hik END

    private fun readExactAlarmCapabilityLabel(): String? {
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return null
            ExactAlarmCapabilityResolver.diagnosticLabel(
                ExactAlarmCapabilityResolver.resolve(alarmManager),
            )
        } catch (_: Exception) {
            null
        }
    }
    // 14.08.2026 Accelerated planned-boundary injection harness cursor by Me4Hik END

    // 14.08.2026 Accelerated device-setup harness cursor by Me4Hik START
    /**
     * Host extras for slots/epoch/zone are intentionally ignored.
     * Schedule identity is derived from the wall-aligned TimeProvider only.
     */
    private suspend fun prepareNotificationBoundaryTest(
        runtime: PraktikaRuntime,
        clock: AcceleratedTimeProvider,
    ) {
        val wallNowMs = System.currentTimeMillis()
        val state = clock.currentState()
        val practiceState = runtime.database.practiceStateDao().get()
        val occurrenceCount = runtime.database.questionOccurrenceDao().count()
        PrepareNotificationBoundaryTestCommand.execute(
            packageName = context.packageName,
            wallNowMs = wallNowMs,
            virtualNowMs = clock.currentVirtualNow(),
            speedMultiplier = state.speedMultiplier,
            clockRunning = !state.isVirtualClockPaused,
            practiceStarted = practiceState?.isPracticeStarted == true,
            occurrenceCount = occurrenceCount,
            zoneId = clock.currentZoneId(),
            updateSchedule = { updates -> runtime.cycleRepository.updateSchedule(updates) },
        )
    }
    // 14.08.2026 Accelerated device-setup harness cursor by Me4Hik END

    private suspend fun stepEvent(
        runtime: PraktikaRuntime,
        clock: AcceleratedTimeProvider,
    ) {
        val practiceState = runtime.database.practiceStateDao().get()
        if (practiceState == null || !practiceState.isPracticeStarted) {
            throw AcceleratedClockCommandException("Practice has not been started")
        }

        val incomplete = runtime.database.questionOccurrenceDao().getIncompleteOrdered()
        if (incomplete.isEmpty()) {
            throw AcceleratedClockCommandException("No incomplete occurrence to step")
        }
        if (incomplete.size > 1) {
            throw AcceleratedClockCorruptionException("Multiple incomplete occurrences")
        }

        val occurrence = incomplete.first()
        val target = when (occurrence.status) {
            QuestionOccurrenceStatus.SCHEDULED -> occurrence.plannedAtEpochMillis
            QuestionOccurrenceStatus.AVAILABLE -> occurrence.availableUntilEpochMillis
            else -> throw AcceleratedClockCommandException("Occurrence is not steppable: ${occurrence.status}")
        }

        val currentVirtual = clock.currentVirtualNow()
        if (target > currentVirtual) {
            clock.advanceToVirtualEpochMillis(target)
        }
        runtime.cycleRepository.reconcile()
        clock.checkpoint()
        AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
    }

    companion object {
        private const val TAG = "AcceleratedCommand"
    }
}
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END

// 06.08.2026 Stage 12 Milestone E2E cursor by Me4Hik START - milestone time/delivery diagnostics
package com.me4hik.praktika

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.accelerated.AcceleratedClockState
import com.me4hik.praktika.accelerated.AcceleratedTimeProvider
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.notification.AndroidAlarmScheduler
import com.me4hik.praktika.notification.AndroidPracticeNotificationPresenter
import com.me4hik.praktika.runtime.PraktikaRuntime
import java.io.FileInputStream
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

object Stage12MilestoneDiagnostics {
    const val TAG = "Stage12MilestoneDiag"
    private val receiverDeliveryCount = AtomicInteger(0)

    fun resetReceiverDeliveryCount() {
        receiverDeliveryCount.set(0)
    }

    fun recordReceiverDeliveryFromLogcat() {
        val log = readLogcatSnapshot()
        val matches = Regex("PracticeAlarmReceiver").findAll(log).count()
        if (matches > receiverDeliveryCount.get()) {
            receiverDeliveryCount.set(matches)
        }
    }

    fun receiverDeliveryCount(): Int = receiverDeliveryCount.get()

    fun prepareWallAlignedRunningClock(runtime: PraktikaRuntime) {
        val clock = runtime.timeProvider as AcceleratedTimeProvider
        val wallNow = System.currentTimeMillis()
        val stateBefore = clock.currentState()
        logTimingSnapshot(
            label = "before_clock_prepare",
            runtime = runtime,
            occurrence = null,
            plannedAt = null,
            availableUntil = null,
            testDeadline = null,
        )
        if (stateBefore.isVirtualClockPaused) {
            clock.resumeVirtualClock()
        }
        val virtualNow = clock.currentVirtualNow()
        if (wallNow > virtualNow) {
            clock.advanceToVirtualEpochMillis(wallNow)
        }
        clock.setSpeedMultiplier(AcceleratedClockState.ALLOWED_MULTIPLIERS.min())
        clock.checkpoint()
        logTimingSnapshot(
            label = "after_clock_prepare",
            runtime = runtime,
            occurrence = null,
            plannedAt = null,
            availableUntil = null,
            testDeadline = null,
        )
    }

    fun nearSlotMinutesForMilestone(runtime: PraktikaRuntime): Int {
        val zone = ZoneId.of(runtime.timeProvider.currentZoneId())
        val acceleratedNow = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(runtime.timeProvider.nowEpochMillis()),
            zone,
        )
        val nowMinutes = acceleratedNow.hour * 60 + acceleratedNow.minute
        var nearSlotMinutes = ((nowMinutes + 4 + 4) / 5) * 5
        if (nearSlotMinutes <= nowMinutes) {
            nearSlotMinutes += 5
        }
        if (nearSlotMinutes >= 24 * 60) {
            nearSlotMinutes -= 24 * 60
        }
        return nearSlotMinutes
    }

    fun logPreScheduleTiming(runtime: PraktikaRuntime, occurrence: QuestionOccurrenceEntity) {
        val windowLength = AndroidAlarmScheduler.WINDOW_LENGTH_MILLIS
        val latestExpectedDelivery = occurrence.plannedAtEpochMillis + windowLength
        val testDeadline = latestExpectedDelivery + TimeUnit.MINUTES.toMillis(3)
        logTimingSnapshot(
            label = "pre_schedule",
            runtime = runtime,
            occurrence = occurrence,
            plannedAt = occurrence.plannedAtEpochMillis,
            availableUntil = occurrence.availableUntilEpochMillis,
            testDeadline = testDeadline,
        )
    }

    suspend fun waitForAvailableAndNotification(
        runtime: PraktikaRuntime,
        occurrenceId: Long,
        plannedAtEpochMillis: Long,
    ): QuestionOccurrenceEntity {
        val windowLength = AndroidAlarmScheduler.WINDOW_LENGTH_MILLIS
        val latestExpectedDelivery = plannedAtEpochMillis + windowLength
        val testDeadline = latestExpectedDelivery + TimeUnit.MINUTES.toMillis(3)
        var nextLogAt = System.currentTimeMillis()
        var delivered: QuestionOccurrenceEntity? = null
        while (System.currentTimeMillis() < testDeadline) {
            runtime.notificationCoordinator.sync(
                com.me4hik.praktika.notification.NotificationSyncReason.FOREGROUND,
            )
            recordReceiverDeliveryFromLogcat()
            val occurrence = runtime.database.questionOccurrenceDao().getById(occurrenceId)
            val now = System.currentTimeMillis()
            if (now >= nextLogAt) {
                logTimingSnapshot(
                    label = "wait_loop",
                    runtime = runtime,
                    occurrence = occurrence,
                    plannedAt = plannedAtEpochMillis,
                    availableUntil = occurrence?.availableUntilEpochMillis,
                    testDeadline = testDeadline,
                )
                nextLogAt = now + 20_000L
            }
            if (occurrence?.status == QuestionOccurrenceStatus.AVAILABLE) {
                delivered = occurrence
                break
            }
            delay(5_000)
        }
        logTimingSnapshot(
            label = "wait_loop_final",
            runtime = runtime,
            occurrence = delivered,
            plannedAt = plannedAtEpochMillis,
            availableUntil = delivered?.availableUntilEpochMillis,
            testDeadline = testDeadline,
        )
        assertNotNull(
            "Occurrence $occurrenceId did not become AVAILABLE before deadline $testDeadline " +
                "(plannedAt=$plannedAtEpochMillis latestExpected=$latestExpectedDelivery)",
            delivered,
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val activeId = AndroidPracticeNotificationPresenter.NOTIFICATION_TAG.let { tag ->
            notificationManager.activeNotifications
                .firstOrNull { it.tag == tag }
                ?.id
                ?.toLong()
        }
        assertTrue(
            "Expected active practice notification for occurrence $occurrenceId, activeId=$activeId",
            activeId == occurrenceId,
        )
        return delivered!!
    }

    fun logTimingSnapshot(
        label: String,
        runtime: PraktikaRuntime,
        occurrence: QuestionOccurrenceEntity?,
        plannedAt: Long?,
        availableUntil: Long?,
        testDeadline: Long?,
    ) {
        val systemWallNow = System.currentTimeMillis()
        val acceleratedNow = runtime.timeProvider.nowEpochMillis()
        val alarmDump = runCatching { readAlarmDump() }.getOrDefault("")
        val occurrenceId = occurrence?.id
        val alarmStillScheduled = occurrenceId?.let { id ->
            alarmDump.contains(id.toString())
        } ?: false
        val activeNotificationCount = activeNotificationCount()
        recordReceiverDeliveryFromLogcat()
        Log.i(
            TAG,
            "label=$label systemWallNow=$systemWallNow acceleratedNow=$acceleratedNow " +
                "difference=${systemWallNow - acceleratedNow} " +
                "occurrenceId=$occurrenceId status=${occurrence?.status} " +
                "plannedAt=$plannedAt availableUntil=$availableUntil " +
                "plannedMinusWall=${plannedAt?.minus(systemWallNow)} " +
                "plannedMinusAccelerated=${plannedAt?.minus(acceleratedNow)} " +
                "windowLength=${AndroidAlarmScheduler.WINDOW_LENGTH_MILLIS} " +
                "testDeadline=$testDeadline alarmStillScheduled=$alarmStillScheduled " +
                "receiverDeliveryCount=${receiverDeliveryCount()} " +
                "activeNotificationCount=$activeNotificationCount clockPaused=${
                    (runtime.timeProvider as AcceleratedTimeProvider).currentState().isVirtualClockPaused
                } speedMultiplier=${
                    (runtime.timeProvider as AcceleratedTimeProvider).currentState().speedMultiplier
                }",
        )
    }

    private fun activeNotificationCount(): Int {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.activeNotifications.count {
            it.tag == AndroidPracticeNotificationPresenter.NOTIFICATION_TAG
        }
    }

    private fun readAlarmDump(): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pfd = instrumentation.uiAutomation.executeShellCommand("dumpsys alarm")
        return try {
            FileInputStream(pfd.fileDescriptor).bufferedReader().readText()
        } finally {
            pfd.close()
        }
    }

    private fun readLogcatSnapshot(): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pfd = instrumentation.uiAutomation.executeShellCommand("logcat -d -s $TAG:* PracticeAlarmReceiver:* PracticeNotification:*")
        return try {
            FileInputStream(pfd.fileDescriptor).bufferedReader().readText()
        } finally {
            pfd.close()
        }
    }
}
// 06.08.2026 Stage 12 Milestone E2E cursor by Me4Hik END

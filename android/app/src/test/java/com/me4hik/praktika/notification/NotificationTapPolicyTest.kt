// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - tap policy JVM tests
package com.me4hik.praktika.notification

import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTapPolicyTest {
    private val practiceState = PracticeStateEntity(
        id = 1,
        isPracticeStarted = true,
        isPaused = false,
        practiceStartedAtEpochMillis = 1L,
        currentCycleNumber = 1,
        nextCyclePosition = 2,
        lastProcessedAtEpochMillis = 1L,
        pausedAtEpochMillis = null,
        activeZoneId = "Europe/Kiev",
        seedVersion = 1,
    )

    private val availableOccurrence = QuestionOccurrenceEntity(
        id = 10L,
        questionId = 1,
        questionTextSnapshot = "Q",
        cycleNumber = 1,
        cyclePosition = 1,
        scheduleSlotIndex = 1,
        plannedAtEpochMillis = 1_000L,
        availableUntilEpochMillis = 2_000L,
        status = QuestionOccurrenceStatus.AVAILABLE,
        zoneId = "Europe/Kiev",
        openedAtEpochMillis = null,
    )

    @Test
    fun alreadyOpened_isIgnored() {
        val opened = availableOccurrence.copy(openedAtEpochMillis = 5_000L)
        val decision = NotificationTapPolicy.evaluate(
            practiceState = practiceState,
            currentIncomplete = opened,
            targetOccurrence = opened,
            expectedOccurrenceId = 10L,
            expectedPlannedAtEpochMillis = 1_000L,
        )
        assertTrue(decision is NotificationTapDecision.Ignore)
    }

    @Test
    fun matchingCurrentAvailable_isValid() {
        val decision = NotificationTapPolicy.evaluate(
            practiceState = practiceState,
            currentIncomplete = availableOccurrence,
            targetOccurrence = availableOccurrence,
            expectedOccurrenceId = 10L,
            expectedPlannedAtEpochMillis = 1_000L,
        )
        assertEquals(NotificationTapDecision.Open(10L), decision)
    }

    @Test
    fun wrongId_isIgnored() {
        val decision = NotificationTapPolicy.evaluate(
            practiceState = practiceState,
            currentIncomplete = availableOccurrence,
            targetOccurrence = availableOccurrence.copy(id = 11L),
            expectedOccurrenceId = 11L,
            expectedPlannedAtEpochMillis = 1_000L,
        )
        assertTrue(decision is NotificationTapDecision.Ignore)
    }

    @Test
    fun wrongPlannedAt_isIgnored() {
        val decision = NotificationTapPolicy.evaluate(
            practiceState = practiceState,
            currentIncomplete = availableOccurrence,
            targetOccurrence = availableOccurrence,
            expectedOccurrenceId = 10L,
            expectedPlannedAtEpochMillis = 9_999L,
        )
        assertTrue(decision is NotificationTapDecision.Ignore)
    }

    @Test
    fun paused_isIgnored() {
        val decision = NotificationTapPolicy.evaluate(
            practiceState = practiceState.copy(isPaused = true),
            currentIncomplete = availableOccurrence,
            targetOccurrence = availableOccurrence,
            expectedOccurrenceId = 10L,
            expectedPlannedAtEpochMillis = 1_000L,
        )
        assertTrue(decision is NotificationTapDecision.Ignore)
    }

    @Test
    fun scheduled_isIgnored() {
        val scheduled = availableOccurrence.copy(status = QuestionOccurrenceStatus.SCHEDULED)
        val decision = NotificationTapPolicy.evaluate(
            practiceState = practiceState,
            currentIncomplete = scheduled,
            targetOccurrence = scheduled,
            expectedOccurrenceId = 10L,
            expectedPlannedAtEpochMillis = 1_000L,
        )
        assertTrue(decision is NotificationTapDecision.Ignore)
    }

    @Test
    fun replacedOccurrence_isIgnored() {
        val decision = NotificationTapPolicy.evaluate(
            practiceState = practiceState,
            currentIncomplete = availableOccurrence.copy(id = 99L),
            targetOccurrence = availableOccurrence,
            expectedOccurrenceId = 10L,
            expectedPlannedAtEpochMillis = 1_000L,
        )
        assertTrue(decision is NotificationTapDecision.Ignore)
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END

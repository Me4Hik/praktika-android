// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik START - accelerated E2E helpers
package com.me4hik.praktika

import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.runtime.PraktikaRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

object Stage11BlackviewE2ESupport {
    suspend fun assertCustomSchedulePersisted(
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

    suspend fun assertStartedWithCustomFirstOccurrence(
        runtime: PraktikaRuntime,
        expectedPlannedMinute: Int,
    ) {
        val practice = runtime.database.practiceStateDao().get()
        assertTrue(practice!!.isPracticeStarted)
        val incomplete = runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
        assertEquals(1, incomplete.cyclePosition)
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, incomplete.status)
        val slot = runtime.database.scheduleSlotDao().getAllOrderedByTime()
            .first { it.slotIndex == incomplete.scheduleSlotIndex }
        assertEquals(expectedPlannedMinute, slot.timeOfDayMinutes)
        assertEquals(1, runtime.database.questionOccurrenceDao().count())
    }

    suspend fun assertQuickCheckOk(runtime: PraktikaRuntime) {
        Stage10BlackviewE2ESupport.assertQuickCheckOk(runtime)
    }
}
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik END

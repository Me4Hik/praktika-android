// 04.08.2026 Cycle Engine cursor by Me4Hik START - JVM-тесты CycleCursor
package com.me4hik.praktika.data.cycle

import org.junit.Assert.assertEquals
import org.junit.Test

class CycleCursorTest {
    @Test
    fun advancesWithinSameCycle() {
        val cursor = CycleCursor(cycleNumber = 1, cyclePosition = 5)
        assertEquals(CycleCursor(1, 6), cursor.advance())
    }

    @Test
    fun advancesFromTwentyToTwentyOne() {
        val cursor = CycleCursor(cycleNumber = 1, cyclePosition = 20)
        assertEquals(CycleCursor(1, 21), cursor.advance())
    }

    @Test
    fun wrapsToNextCycleAfterTwentyOne() {
        val cursor = CycleCursor(cycleNumber = 1, cyclePosition = 21)
        assertEquals(CycleCursor(2, 1), cursor.advance())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroCycleNumber() {
        CycleCursor(cycleNumber = 0, cyclePosition = 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidPosition() {
        CycleCursor(cycleNumber = 1, cyclePosition = 22)
    }
}
// 04.08.2026 Cycle Engine cursor by Me4Hik END

package com.me4hik.praktika.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class DeferDurationOptionsTest {
    @Test
    fun sanitizeKeepsAllowedValues() {
        DeferDurationOptions.ALLOWED_MINUTES.forEach { minutes ->
            assertEquals(minutes, DeferDurationOptions.sanitize(minutes))
        }
    }

    @Test
    fun sanitizeFallsBackToDefault() {
        assertEquals(DeferDurationOptions.DEFAULT_MINUTES, DeferDurationOptions.sanitize(0))
        assertEquals(DeferDurationOptions.DEFAULT_MINUTES, DeferDurationOptions.sanitize(7))
        assertEquals(DeferDurationOptions.DEFAULT_MINUTES, DeferDurationOptions.sanitize(60))
        assertEquals(DeferDurationOptions.DEFAULT_MINUTES, DeferDurationOptions.sanitize(-1))
    }

    @Test
    fun defaultIsFifteen() {
        assertEquals(15, DeferDurationOptions.DEFAULT_MINUTES)
    }
}

package com.me4hik.praktika.ui.tour

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TourTargetRegistryTest {
    @Test
    fun clear_removesBounds_andBumpsGeneration() {
        val registry = TourTargetRegistry()
        val gen0 = registry.generation.value
        registry.update(TourTargetId.HOME_ARCHIVE, Rect(0f, 0f, 10f, 10f))
        assertEquals(Rect(0f, 0f, 10f, 10f), registry.bounds(TourTargetId.HOME_ARCHIVE))
        registry.clear()
        assertNull(registry.bounds(TourTargetId.HOME_ARCHIVE))
        assertTrue(registry.generation.value > gen0)
        assertTrue(registry.targets.value.isEmpty())
    }
}

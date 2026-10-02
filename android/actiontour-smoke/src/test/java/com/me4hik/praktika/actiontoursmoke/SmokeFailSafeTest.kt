package com.me4hik.praktika.actiontoursmoke

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SmokeFailSafeTest {
    @Test
    fun diagnosticFailures_areSuppressed_primaryPreserved() {
        val primary = AssertionError("stage=START_ENTRY expected=x actual=y selector=z")
        SmokeFailSafe.runBestEffort(
            primary,
            { error("screenshot failed") },
            { error("xml failed") },
            { /* ok */ },
        )
        assertEquals(2, primary.suppressed.size)
        assertTrue(primary.suppressed[0].message!!.contains("screenshot"))
        assertTrue(primary.suppressed[1].message!!.contains("xml"))
        assertSame(primary, primary)
        assertTrue(primary.message!!.startsWith("stage=START_ENTRY"))
    }

    @Test
    fun allStepsOk_noSuppressed() {
        val primary = RuntimeException("primary")
        SmokeFailSafe.runBestEffort(primary, { }, { })
        assertEquals(0, primary.suppressed.size)
    }
}

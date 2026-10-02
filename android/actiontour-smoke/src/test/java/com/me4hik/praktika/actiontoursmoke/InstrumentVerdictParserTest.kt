package com.me4hik.praktika.actiontoursmoke

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstrumentVerdictParserTest {
    @Test
    fun timeoutPassSample_okAndCodeMinusOne_isPass() {
        val raw = """
            INSTRUMENTATION_STATUS_CODE: 1
            INSTRUMENTATION_STATUS_CODE: 0
            Time: 26,107
            OK (1 test)
            INSTRUMENTATION_CODE: -1
        """.trimIndent()
        val r = InstrumentVerdictParser.parse(raw)
        assertEquals(InstrumentVerdictParser.Verdict.PASS, r.verdict)
        assertEquals(-1, r.instrumentationCode)
        assertTrue(r.hasJunitOk)
        assertFalse(r.hasJunitFailuresBanner)
    }

    @Test
    fun mainFailureSample_failuresAndStatusMinusTwo_isFail_despiteCodeMinusOne() {
        val raw = """
            INSTRUMENTATION_STATUS_CODE: 1
            INSTRUMENTATION_STATUS_CODE: -2
            FAILURES!!!
            Tests run: 1,  Failures: 1
            INSTRUMENTATION_CODE: -1
        """.trimIndent()
        val r = InstrumentVerdictParser.parse(raw)
        assertEquals(InstrumentVerdictParser.Verdict.FAIL, r.verdict)
        assertEquals(-1, r.instrumentationCode)
        assertTrue(r.hasJunitFailuresBanner)
        assertTrue(r.statusCodes.contains(-2))
    }

    @Test
    fun stageFail_forcesCombinedFail() {
        val instrument = InstrumentVerdictParser.parse(
            """
            INSTRUMENTATION_STATUS_CODE: 0
            OK (1 test)
            INSTRUMENTATION_CODE: -1
            """.trimIndent(),
        )
        assertEquals(InstrumentVerdictParser.Verdict.PASS, instrument.verdict)
        val combined = InstrumentVerdictParser.combineWithStages(
            instrument,
            mapOf("TIMEOUT_REGRESSION" to "FAIL"),
            listOf("TIMEOUT_REGRESSION"),
        )
        assertEquals(InstrumentVerdictParser.Verdict.FAIL, combined)
    }

    @Test
    fun emptyOutput_isFail() {
        val r = InstrumentVerdictParser.parse("")
        assertEquals(InstrumentVerdictParser.Verdict.FAIL, r.verdict)
        assertNull(r.instrumentationCode)
    }

    @Test
    fun pendingStages_doNotForceFail() {
        val instrument = InstrumentVerdictParser.parse(
            """
            INSTRUMENTATION_STATUS_CODE: 0
            OK (1 test)
            INSTRUMENTATION_CODE: -1
            """.trimIndent(),
        )
        val combined = InstrumentVerdictParser.combineWithStages(
            instrument,
            mapOf("TASK_5_DEFER" to "PENDING", "GATE" to "PENDING"),
            listOf("TASK_5_DEFER", "GATE"),
        )
        assertEquals(InstrumentVerdictParser.Verdict.PASS, combined)
    }
}

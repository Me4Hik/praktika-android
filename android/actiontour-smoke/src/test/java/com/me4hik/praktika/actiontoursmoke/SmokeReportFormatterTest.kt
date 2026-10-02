package com.me4hik.praktika.actiontoursmoke

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmokeReportFormatterTest {
    @Test
    fun progressText_matchesTourChromeFormat() {
        assertEquals("1 из 5", ProductionTourTargets.progressText(1))
        assertEquals("2 из 5", ProductionTourTargets.progressText(2))
    }

    @Test
    fun formatReport_passWhenAllPass() {
        val stages = SmokeStage.entries.associateWith { StageResult.PASS }
        val text = SmokeReportFormatter.formatReport(
            device = "XPLORE 2",
            serial = "serial",
            packageId = ProductionTourTargets.PRODUCTION_PACKAGE,
            versionCode = "18",
            stages = stages,
            processCrash = false,
        )
        assertTrue(text.contains("ACTION_TOUR_DEVICE_SMOKE = PASS"))
        assertTrue(text.contains("PROCESS_CRASH = NO"))
        assertTrue(text.contains("TASK_1_ARCHIVE = PASS"))
    }

    @Test
    fun formatReport_failOnCrash() {
        val stages = mapOf(SmokeStage.START_ENTRY to StageResult.PASS)
        val text = SmokeReportFormatter.formatReport(
            device = "d",
            serial = "s",
            packageId = ProductionTourTargets.PRODUCTION_PACKAGE,
            versionCode = "18",
            stages = stages,
            processCrash = true,
            failedStage = SmokeStage.TASK_4_SOUND,
            expected = "alive",
            actual = "dead",
            selector = "pidof",
            screenshot = "failure.png",
        )
        assertTrue(text.contains("ACTION_TOUR_DEVICE_SMOKE = FAIL"))
        assertTrue(text.contains("PROCESS_CRASH = YES"))
        assertTrue(text.contains("FAILED_STAGE = TASK_4_SOUND"))
        assertTrue(text.contains("SCREENSHOT = failure.png"))
    }
}
